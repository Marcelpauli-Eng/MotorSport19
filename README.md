# MotorSport19 — Sistema de gestión para taller de motos

Gestión de clientes, motos, órdenes de trabajo, inventario y facturación para un
taller de motocicletas en España.

> **Estado: fases 1 a 4 completadas.** Esquema de base de datos, entidades JPA,
> datos de demostración, backend de clientes, motos e inventario, órdenes de
> trabajo con máquina de estados y consumo de almacén, y facturación con cadena
> de huellas, PDF con QR y exportación. 197 tests en verde.
>
> **Todavía no hay autenticación** (fase 5): la API está abierta y no debe
> exponerse en internet tal cual. Facturación, seguridad y frontend llegan en las
> fases siguientes; el README definitivo de despliegue se escribe en la fase 7.

---

## Stack

| Capa        | Tecnología                                              |
|-------------|---------------------------------------------------------|
| Backend     | Java 21, Spring Boot 3.5, Spring Data JPA               |
| Base datos  | PostgreSQL 17, migraciones con Flyway                   |
| Frontend    | Angular 21 (standalone components + signals), SCSS      |
| Build       | Maven (wrapper incluido) y npm                          |
| Contenedores| docker-compose: `db`, `api`, `web`                      |

---

## Arrancar con la base de datos en Supabase

```bash
cp .env.example .env      # rellena las tres variables SUPABASE_DB_*
docker compose -f docker-compose.supabase.yml up --build
```

### Qué cadena de conexión usar

Supabase ofrece tres y **solo una sirve**:

| Conexión | Puerto | ¿Sirve? |
|----------|--------|---------|
| Directa `db.<ref>.supabase.co` | 5432 | Solo si tienes IPv6 o el complemento IPv4 |
| **Pooler en modo session** `aws-0-<region>.pooler.supabase.com` | **5432** | **Sí — usa esta** |
| Pooler en modo transaction | 6543 | No: Flyway no puede tomar su bloqueo de migración |

El usuario del pooler tiene la forma `postgres.<ref>`. La cadena la encuentras en
*Project Settings → Database → Connection string → Session pooler*.

```
SUPABASE_DB_URL=jdbc:postgresql://aws-0-eu-west-3.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_DB_USER=postgres.tuproyecto
SUPABASE_DB_PASSWORD=...
```

### Seguridad: el acceso directo queda cerrado

Supabase publica automáticamente toda tabla de `public` a través de PostgREST.
Con la clave `anon` —que es pública por diseño y viaja en el navegador—
cualquiera podría leer y escribir en las tablas saltándose la aplicación entera.

La migración `V8` lo impide de dos formas independientes: activa RLS sin ninguna
política (denegación por defecto) y revoca los permisos de `anon` y
`authenticated` sobre tablas, secuencias y funciones. La aplicación no se ve
afectada porque conecta por JDBC con el rol propietario. En un PostgreSQL normal
la migración detecta que esos roles no existen y no hace nada.

## Arrancar todo en local

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
poblada con datos de demostración. Para arrancar vacío, deja
`SPRING_PROFILES_ACTIVE=docker` en tu `.env`.

## Arrancar sin Docker

Necesitas un PostgreSQL 17 accesible. Después:

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
| `V8` | Blindaje del acceso directo a las tablas (Supabase) |

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

## API (fase 2)

Todas las rutas cuelgan de `/api`. Las respuestas de error comparten el mismo
formato: `{ momento, estado, error, mensaje, ruta, detalles }`, con el mensaje
siempre en español y listo para mostrar al usuario.

### Clientes

| Método | Ruta | Qué hace |
|--------|------|----------|
| `GET` | `/clientes?texto=&soloActivos=` | Busca por nombre, apellidos, documento, teléfono o email |
| `GET` | `/clientes/{id}` | Ficha completa, con el indicador `facturable` |
| `GET` | `/clientes/{id}/motos` | Motos del cliente |
| `POST` | `/clientes` | Alta (solo el nombre es obligatorio) |
| `PUT` | `/clientes/{id}/contacto` | Actualiza nombre, teléfono, email |
| `PUT` | `/clientes/{id}/datos-fiscales` | Completa o corrige los datos fiscales |
| `POST` | `/clientes/{id}/baja` · `/reactivacion` | Baja lógica y reactivación |

### Motos

| Método | Ruta | Qué hace |
|--------|------|----------|
| `GET` | `/motos?texto=&soloActivas=` | Busca por matrícula, marca, modelo o bastidor |
| `GET` | `/motos/{id}` · `/motos/matricula/{matricula}` | Ficha |
| `POST` | `/motos` | Alta |
| `PUT` | `/motos/{id}` | Actualiza datos |
| `PUT` | `/motos/{id}/kilometraje` | Registra kilometraje (solo puede aumentar) |
| `PUT` | `/motos/{id}/propietario` | Cambio de propietario |
| `POST` | `/motos/{id}/baja` · `/reactivacion` | Baja lógica y reactivación |

### Inventario

| Método | Ruta | Qué hace |
|--------|------|----------|
| `GET` | `/piezas?texto=&proveedorId=&soloBajoMinimo=` | Catálogo |
| `GET` | `/piezas/{id}` · `/piezas/sku/{sku}` | Ficha |
| `POST` | `/piezas` | Alta (`stockInicial` genera un movimiento de entrada) |
| `PUT` | `/piezas/{id}` · `/piezas/{id}/precios` | Actualiza catálogo y precios |
| `GET` | `/inventario/alertas` | Piezas al mínimo o por debajo |
| `GET` | `/inventario/movimientos` | Libro de movimientos, con filtros |
| `GET` | `/inventario/piezas/{id}/movimientos` | Historial de una pieza |
| `POST` | `/inventario/piezas/{id}/entradas` | Compra a proveedor |
| `POST` | `/inventario/piezas/{id}/salidas` | Salida justificada (exige motivo) |
| `POST` | `/inventario/piezas/{id}/ajustes` | Ajuste de inventario (con signo, exige motivo) |
| `GET`/`POST`/`PUT` | `/proveedores` | Mantenimiento de proveedores |

**No existe ningún endpoint para fijar el stock de una pieza**, y es deliberado:
las existencias solo cambian registrando movimientos.

### Órdenes de trabajo (fase 3)

Cada transición tiene su propio endpoint con nombre de negocio, en vez de un
genérico "cambiar estado": la API refleja la máquina de estados en lugar de dejar
que el cliente proponga cualquier salto.

| Método | Ruta | Qué hace |
|--------|------|----------|
| `GET` | `/ordenes?estado=&tecnicoId=&soloAbiertas=` | Tablero del taller |
| `GET` | `/ordenes/{id}` · `/ordenes/codigo/{codigo}` | Ficha con líneas, totales e historial |
| `GET` | `/ordenes/moto/{motoId}/historial` | Historial de intervenciones de una moto |
| `POST` | `/ordenes` | Abre una OT (numeración correlativa, tarifa congelada) |
| `PUT` | `/ordenes/{id}/diagnostico` | Registra el diagnóstico del técnico |
| `POST` | `/ordenes/{id}/lineas/mano-de-obra` · `/lineas/piezas` | Añade líneas |
| `PUT` | `/ordenes/{id}/lineas/{lineaId}/cantidad` | Cambia la cantidad |
| `DELETE` | `/ordenes/{id}/lineas/{lineaId}` | Quita una línea (si no consumió almacén) |
| `POST` | `/ordenes/{id}/lineas/{lineaId}/devoluciones` | Devuelve piezas ya consumidas |

Transiciones: `/diagnostico` · `/presupuesto` · `/aprobacion` · `/rechazo` ·
`/reparacion` · `/reanudacion` · `/espera-piezas` · `/lista` · `/entrega`.

Cada respuesta incluye `estadosPosibles`, para que el frontend pinte solo los
botones que tienen sentido en vez de dejar al usuario probar y recibir un error.

### La máquina de estados

```
RECIBIDA → EN_DIAGNOSTICO → PRESUPUESTADA → APROBADA → EN_REPARACION → LISTA → ENTREGADA
                                  │                          ↕
                                  └→ RECHAZADA        ESPERANDO_PIEZAS → LISTA
```

`ENTREGADA` y `RECHAZADA` son terminales. Las transiciones se declaran en el enum
`EstadoOT` y cualquier otro salto se rechaza con un error que dice **a qué estados
sí se puede ir** desde donde está.

### Consumo de inventario

Al entrar en reparación, la OT consume automáticamente las piezas de sus líneas:

- El material disponible **se consume**; el que falta deja la OT en
  `ESPERANDO_PIEZAS` con el detalle de cuántas unidades pedir al proveedor.
  No es un error HTTP: es el resultado normal de que el almacén esté corto.
- Al reanudar, **solo se sirve lo pendiente**. Lo ya consumido no se duplica,
  porque las unidades consumidas se derivan del libro de movimientos y no se
  guardan en la línea.
- Una línea que ya sacó material del almacén **no se puede borrar**: el libro de
  movimientos es inmutable y no se puede borrar el rastro de unas piezas que
  salieron físicamente. Hay que devolverlas primero.
- Añadir una pieza al presupuesto **no** toca el almacén; lo que sí hace es
  congelar su precio de catálogo en la línea.

### Facturación (fase 4)

| Método | Ruta | Qué hace |
|--------|------|----------|
| `GET` | `/facturas?tipo=&desde=&hasta=` | Libro de facturas |
| `GET` | `/facturas/{id}` | Factura completa con líneas, desglose y huella |
| `GET` | `/facturas/{id}/pdf` | PDF con QR y huella impresa |
| `POST` | `/facturas` | Emite desde una OT `LISTA` o `ENTREGADA` |
| `POST` | `/facturas/{id}/rectificativas` | Emite una rectificativa |
| `POST` | `/facturas/verificacion` | Verifica la cadena de extremo a extremo |
| `GET` | `/facturas/exportacion/csv` · `/json` | Exporta el libro registro |
| `GET` | `/facturacion/eventos` | Registro de eventos |

**No hay `PUT` ni `DELETE` en toda esta API.** Una factura emitida no se modifica
ni se borra: lo único que se puede hacer con una equivocada es emitir una
rectificativa que la corrija.

### La cadena de huellas

Cada factura incorpora a su huella la de la anterior, de modo que forman una
cadena. La cadena canónica sigue el formato del registro de facturación español:

```
NIFEmisor=B87654323&NumSerieFactura=A/2026/000001&FechaExpedicion=15-05-2026
&TipoFactura=ORDINARIA&CuotaTotal=42.48&ImporteTotal=244.68
&Huella=<huella anterior>&FechaHoraHusoGenRegistro=2026-05-15T18:25:00+02
```

Ese texto exacto se guarda en la propia factura, así que la huella se puede
reverificar dentro de años **sin este programa**: basta con pasar la cadena
almacenada por SHA-256. La exportación JSON incluye esas cadenas justo para eso —
un libro que solo se puede comprobar con el software que lo escribió no demuestra
gran cosa.

La verificación comprueba cinco cosas en cada factura:

| Comprobación | Qué detecta |
|--------------|-------------|
| La huella corresponde a su cadena canónica | Manipulación de la huella |
| **La cadena canónica describe los valores actuales de la fila** | **Alteración del importe, fecha o número tras sellar** |
| Enlaza con la huella de la anterior | Sustitución de una factura |
| No falta ninguna posición del registro | Borrado de una factura |
| Los totales cuadran con las líneas | Descuadre interno |

La segunda es la que cierra el círculo: sin ella, alguien podría cambiar el
importe de una fila dejando intacta la cadena canónica, y la huella seguiría
cuadrando consigo misma.

### Cálculo de importes: doble red

Los importes de línea se calculan **dos veces**, en Java y en PostgreSQL como
columnas generadas. No es redundancia inútil: la huella se computa antes del
`INSERT`, cuando las columnas generadas aún no existen, así que Java necesita sus
propios números. Que ambos coincidan lo verifica un *constraint trigger* diferido
al hacer commit — si el redondeo se separase un céntimo, la transacción fallaría
en lugar de guardar una factura descuadrada.

### Validación de documentos fiscales

Los NIF, NIE y CIF se validan con su dígito de control antes de guardarlos. Un
documento mal tecleado no se detectaría hasta que Hacienda rechazase la factura,
y para entonces la factura ya sería inmutable. Los documentos extranjeros se
aceptan sin verificar, porque no llevan un control comprobable aquí.

---

## Fases del proyecto

- [x] **Fase 1** — Estructura, docker-compose, esquema, entidades JPA, datos demo
- [x] **Fase 2** — Backend de clientes, motos e inventario. Tests
- [x] **Fase 3** — Órdenes de trabajo, máquina de estados y consumo de inventario. Tests
- [x] **Fase 4** — Facturación: hash encadenado, PDF con QR, eventos y exportación. Tests
- [ ] **Fase 5** — Autenticación JWT, roles y seguridad
- [ ] **Fase 6** — Frontend Angular
- [ ] **Fase 7** — README de despliegue, backup y documentación de inmutabilidad
