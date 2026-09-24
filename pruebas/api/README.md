# Batería de pruebas de extremo a extremo

Recorre el programa entero por HTTP, como lo recorrería el taller en un día de
trabajo, y comprueba **346 cosas** en 17 secciones. Se lanza con un comando:

```bash
./pruebas/api/lanzar.sh
```

Crea una base de datos aparte (`motorsport19_pruebas`), levanta el backend en el
puerto 8081 para no molestar al de desarrollo, pasa la batería y lo borra todo al
terminar. Devuelve 0 si todo va bien y 1 si algo falla, así que sirve tal cual
para un servidor de integración continua.

## Qué comprueba

| # | Sección | Qué mira |
|---|---------|----------|
| 1 | Arranque | Tipos de IVA, series de facturación, catálogo de permisos |
| 2 | Datos maestros | Altas de clientes, motos, piezas y proveedores, y las mil formas de teclearlos mal |
| 3 | Listados | Búsquedas, filtros, paginación en los bordes, identificadores inexistentes |
| 4 | Agenda | Citas, reprogramación, entrada al taller, cancelaciones, ausencias, carga semanal |
| 5 | Servicios tipo | Plantillas de trabajo: horas, piezas, activación |
| 6 | Orden con diagnóstico | El camino largo completo, con todas las transiciones ilegales probadas en cada paso |
| 7 | Orden preparada | El jefe compone la OT y el técnico la ejecuta **sin ver un solo importe** |
| 8 | Cuentas | Descuentos de línea y generales, IVA, redondeos, importes de seis cifras |
| 9 | Almacén | Entradas, salidas, ajustes, alertas, consumo y devoluciones |
| 10 | Facturación | Emisión, numeración, rectificativas, series, cadena de huellas |
| 11 | Papeles | PDF de presupuesto, factura e historiales; exportaciones CSV/JSON/ZIP; informes |
| 12 | Permisos | Un rol a medida: lo que puede y lo que no, permiso a permiso |
| 13 | Sesión | Contraseñas, tokens manipulados, bajas, reactivaciones |
| 14 | Concurrencia | Botones pulsados a la vez: facturas, almacén, altas duplicadas |
| 15 | Robustez | Cuerpos rotos, tipos equivocados, textos gigantes, emojis, inyección SQL |
| 16 | Bajas | No hacer desaparecer cosas que se están usando |
| 17 | Auditoría | Fichaje obligatorio, tasa de neumáticos, facturas de una orden, parámetros que faltan, textos largos, notas del alta, corregir parte de una factura sin poder anularla dos veces, solo rectificativas por diferencias, permisos de roles |

Quien no está exento de fichar recibe 423 en todo hasta empezar la jornada, así
que `entrar()` del arnés empieza la jornada de esas sesiones, como haría el
taller a primera hora.

## En qué se diferencia de los tests de JUnit

Los de JUnit (`backend/src/test`) prueban las reglas una a una, sin base de
datos: son rápidos y precisos, y son los que hay que mirar primero cuando algo
falla.

Esta batería prueba lo otro, que es lo que no se ve en un test unitario: que las
piezas encajan **cuando hablan entre ellas por HTTP y contra PostgreSQL de
verdad**. Los fallos que ha encontrado son de ese tipo — una consulta que carga
mal, dos peticiones a la vez, un token que sigue valiendo cuando ya no debería.

## Los ficheros

- `arnes.py` — la sesión HTTP, el registro de resultados y los generadores de
  datos válidos (NIF con letra correcta, matrículas, SKUs).
- `suite.py` — las 17 secciones. Cada comprobación se lee como una acción de
  taller, no como una petición HTTP.
- `lanzar.sh` — lo anterior, automatizado.

## Avisos

Además de OK/FALLO, la batería puede dar **avisos**: cosas que no son fallos pero
convendría mirar. Ahora mismo no sale ninguno. Los dos que salían se decidieron así:

- Que la lista para asignar órdenes incluya al administrador es a propósito: recibe
  trabajo quien puede moverlo, y en un taller pequeño la dirección también repara.
- Dar de alta una pieza por debajo del coste se admite (una liquidación existe); el
  formulario pregunta antes de guardarla.
