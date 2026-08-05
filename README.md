# MotorSport19 — Sistema de gestión para taller de motos

Gestión de clientes, motos, órdenes de trabajo, inventario y facturación para un
taller de motocicletas en España.

> **Estado: Fase 1 completada.** Estructura del proyecto, docker-compose, esquema
> completo de base de datos (Flyway), entidades JPA y datos de demostración.
> Todavía no hay lógica de negocio ni API REST: llegan en las fases siguientes.
> El README definitivo de despliegue y operación se escribe en la fase 7.

---

## Stack

| Capa        | Tecnología                                              |
|-------------|---------------------------------------------------------|
| Backend     | Java 21, Spring Boot 3.5, Spring Data JPA               |
| Base datos  | PostgreSQL 16, migraciones con Flyway                   |
| Frontend    | Angular 21 (standalone components + signals), SCSS      |
| Build       | Maven (wrapper incluido) y npm                          |
| Contenedores| docker-compose: `db`, `api`, `web`                      |

---

## Arrancar con Docker

```bash
cp .env.example .env && docker compose up --build
```

| Servicio | URL                              |
|----------|----------------------------------|
| Web      | http://localhost:4200            |
| API      | http://localhost:8080/api        |
| Salud    | http://localhost:8080/api/actuator/health |
| Postgres | localhost:5432                   |

El perfil por defecto es `docker,demo`, así que la base de datos se levanta ya
poblada con datos de demostración. Para arrancar vacío, edita `SPRING_PROFILES_ACTIVE`
en tu `.env` y déjalo en `docker`.

## Arrancar sin Docker

Necesitas un PostgreSQL 16 accesible. Después:

```bash
cd backend && SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

```bash
cd frontend && npm install && npm start
```

---

## Estructura

```
MotorSport19/
├── docker-compose.yml
├── .env.example
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/motorsport19/taller/
│       │   ├── common/          auditoría y clases base
│       │   ├── configuracion/   datos fiscales del taller, tipos de IVA
│       │   ├── usuario/         usuarios y roles
│       │   ├── cliente/         clientes
│       │   ├── moto/            motos
│       │   ├── inventario/      piezas, proveedores, movimientos de stock
│       │   ├── orden/           órdenes de trabajo, líneas, estados
│       │   └── factura/         facturación y cadena de huellas
│       └── resources/
│           ├── application.yml
│           ├── db/migration/    esquema (V1..V7)
│           └── db/demo/         datos de demostración (perfil `demo`)
└── frontend/
    ├── Dockerfile
    ├── nginx.conf
    └── src/
```

Cada módulo del backend se organiza por dominio, no por capa técnica: la fase 2 y
siguientes añaden `repository/`, `service/`, `web/` y `dto/` dentro de cada uno.

---

## El esquema de base de datos

Las migraciones están en `backend/src/main/resources/db/migration`:

| Migración | Contenido |
|-----------|-----------|
| `V1` | Usuarios, tipos de IVA, configuración fiscal del taller |
| `V2` | Clientes y motos |
| `V3` | Proveedores, piezas y movimientos de stock |
| `V4` | Órdenes de trabajo, líneas e historial de estados |
| `V5` | Series, facturas, líneas, desglose de IVA y eventos |
| `V6` | Funciones y triggers de integridad e inmutabilidad |
| `V7` | Vistas de consulta |

### Garantías que impone la base de datos

Estas reglas **no dependen de que la aplicación se porte bien**: se cumplen aunque
alguien entre por `psql`. Están implementadas en `V6` y se documentan a fondo en la
fase 7.

| Garantía | Cómo se impone |
|----------|----------------|
| El stock solo cambia mediante movimientos | `pieza.stock_actual` únicamente lo escribe el trigger de `movimiento_stock`; un trigger de guarda rechaza cualquier otro `UPDATE`. En JPA la columna está mapeada como no insertable ni actualizable. |
| Nunca hay stock negativo | El trigger bloquea la pieza (`FOR UPDATE`) y rechaza el movimiento si el resultado fuera negativo. La OT debe pasar a `ESPERANDO_PIEZAS`. |
| El libro de movimientos es inmutable | Triggers que rechazan `UPDATE` y `DELETE`. Un error se corrige con un `AJUSTE` de signo contrario. |
| Una OT `ENTREGADA` es inmutable | Trigger que rechaza cualquier cambio en la cabecera y en las líneas de una OT entregada. |
| Las facturas son inmutables | Triggers que rechazan `UPDATE` y `DELETE` sobre `factura`, `linea_factura`, `desglose_iva_factura` y `evento_factura`. |
| Numeración de facturas sin huecos | Contador **transaccional** por serie (no una `SEQUENCE`, que dejaría huecos al hacer rollback) más un trigger que verifica en el `INSERT` que el número es exactamente el siguiente. |
| Cadena de huellas sin bifurcaciones | `huella` y `huella_anterior` son `NOT NULL` y `UNIQUE`: cada huella se consume exactamente una vez. Un trigger comprueba además que la huella anterior declarada es la de la factura precedente. |
| Los totales cuadran con las líneas | Los importes de línea son columnas generadas por PostgreSQL. Un *constraint trigger* diferido comprueba al hacer commit que cabecera, líneas y desglose de IVA coinciden al céntimo. |
| Baja lógica, nunca borrado | Triggers que rechazan `DELETE` en `cliente`, `moto`, `pieza` y `proveedor`. |

Dos funciones de auditoría permiten comprobar el estado en cualquier momento;
ambas devuelven **cero filas** si todo está correcto:

```sql
SELECT * FROM fn_verificar_cadena_facturas();
SELECT * FROM fn_verificar_integridad_stock();
```

---

## Datos de demostración

Se cargan con el perfil `demo` y cubren a propósito los casos interesantes:

- Las **nueve situaciones** de la máquina de estados de las OT, incluida una
  rechazada y otra bloqueada por falta de stock.
- Un cliente **sin datos fiscales** (no se le puede facturar) y otro **dado de baja**.
- Tres piezas en **alerta de stock**, una de ellas sin existencias tras un ajuste
  de inventario.
- Cuatro facturas con **huellas SHA-256 reales y encadenadas**, incluida una
  rectificativa por sustitución.

### Usuarios de demostración

| Usuario     | Contraseña      | Rol       |
|-------------|-----------------|-----------|
| `admin`     | `admin1234`     | ADMIN     |
| `mostrador` | `mostrador1234` | MOSTRADOR |
| `jortega`   | `tecnico1234`   | TECNICO   |
| `nsanz`     | `tecnico1234`   | TECNICO   |

Las contraseñas están guardadas con BCrypt y se activarán con la fase 5. **No usar
el perfil `demo` en producción.**

---

## Convenciones

- **Idioma**: español en comentarios, mensajes de error y textos de interfaz;
  inglés en nombres de clases, métodos y variables.
- **Dinero**: `BigDecimal` en Java y `NUMERIC` en PostgreSQL. Nunca `double`.
- **Fechas**: `TIMESTAMPTZ` en base de datos, `Instant` y `LocalDate` en Java.
  Zona horaria del taller: `Europe/Madrid`.
- **Esquema**: lo gobierna Flyway. Hibernate arranca con `ddl-auto: validate`, así
  que nunca crea ni modifica tablas: solo comprueba que las entidades coinciden.

---

## Fases del proyecto

- [x] **Fase 1** — Estructura, docker-compose, esquema, entidades JPA, datos demo
- [ ] **Fase 2** — Backend de clientes, motos e inventario. Tests
- [ ] **Fase 3** — Órdenes de trabajo, máquina de estados y consumo de inventario. Tests
- [ ] **Fase 4** — Facturación: hash encadenado, PDF con QR, eventos y exportación. Tests
- [ ] **Fase 5** — Autenticación JWT, roles y seguridad
- [ ] **Fase 6** — Frontend Angular
- [ ] **Fase 7** — README de despliegue, backup y documentación de inmutabilidad
