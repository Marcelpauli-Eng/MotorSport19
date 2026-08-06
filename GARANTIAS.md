# Garantías del sistema

Este documento explica **qué no puede pasar** en MotorSport19 y, sobre todo, por
qué. Es el documento que enseñar a una gestoría, a un auditor o a quien vaya a
mantener esto dentro de dos años.

La idea de fondo es una sola:

> Las reglas que protegen el dinero y el almacén están en la base de datos, no en
> el programa. Se cumplen aunque la aplicación tenga un fallo, aunque alguien
> entre por `psql`, y aunque quien lo haga sea el administrador.

Todo lo que sigue está en la migración
[`V6__integridad_e_inmutabilidad.sql`](backend/src/main/resources/db/migration/V6__integridad_e_inmutabilidad.sql).

---

## Por qué en la base de datos y no en el código

Un servicio Java puede tener un `if` mal puesto. Puede aparecer un endpoint nuevo
que se olvide de una comprobación. Puede alguien abrir una consola SQL «solo para
arreglar una cosita». En los tres casos, si la regla vive en el código, la regla
se salta.

Poniéndola en la base de datos, el peor escenario es que la aplicación reciba un
error feo. El dato malo no entra.

Tiene un coste: las reglas están en SQL, más lejos de la vista que el código Java.
Por eso existe este documento.

---

## Las once garantías

Cada una se ha comprobado atacándola directamente por SQL, saltándose la
aplicación entera. Los mensajes de abajo son los reales.

### 1. El stock solo cambia mediante movimientos

`pieza.stock_actual` es un valor **derivado**. Lo escribe únicamente el trigger de
`movimiento_stock`, que además bloquea la fila (`FOR UPDATE`) para que dos
consumos simultáneos no se pisen.

```sql
UPDATE pieza SET stock_actual = 999 WHERE id = 1;
-- ERROR: El stock de una pieza no se puede modificar directamente
```

En JPA la columna está mapeada como no insertable ni actualizable, de modo que
Hibernate tampoco puede tocarla por accidente.

**Por qué importa:** el stock y el histórico no pueden discrepar, porque el stock
*es* el histórico. No hay dos versiones de la verdad que reconciliar.

### 2. Nunca hay stock negativo

```sql
INSERT INTO movimiento_stock (pieza_id, tipo, cantidad, motivo)
VALUES (1, 'SALIDA', -99999, 'x');
-- ERROR: Stock insuficiente para la pieza ACE-10W40-1L (1): disponible 36, se piden 99999
```

Cuando esto ocurre por la vía normal, la orden de trabajo pasa a
`ESPERANDO_PIEZAS` en lugar de fallar: el taller sigue trabajando y la pieza queda
pendiente.

### 3. El libro de movimientos es inmutable

```sql
UPDATE movimiento_stock SET cantidad = 1 WHERE id = 1;
-- ERROR: Los registros de "movimiento_stock" son inmutables: no se permite UPDATE

DELETE FROM movimiento_stock WHERE id = 1;
-- ERROR: Los registros de "movimiento_stock" son inmutables: no se permite DELETE
```

Un error no se borra: se corrige con un `AJUSTE` de signo contrario, que queda
registrado con su motivo. El inventario cuenta lo que pasó, incluidas las
equivocaciones.

### 4. Las facturas son inmutables

```sql
UPDATE factura SET total = 0 WHERE id = 1;
-- ERROR: Los registros de "factura" son inmutables: no se permite UPDATE

DELETE FROM factura WHERE id = 1;
-- ERROR: Los registros de "factura" son inmutables: no se permite DELETE
```

Lo mismo para `linea_factura`, `desglose_iva_factura` y `evento_factura`. Una
factura equivocada se corrige emitiendo una **rectificativa**, que es lo que exige
la normativa española.

### 5. Numeración sin huecos

```sql
INSERT INTO factura (..., numero, ...) VALUES (..., 99, ...);
-- ERROR: Numeracion no correlativa en la serie A: se esperaba el numero 4, llego 99
```

Se usa un **contador transaccional** por serie, no una `SEQUENCE`. Es deliberado:
una secuencia de PostgreSQL no retrocede al hacer *rollback*, así que una
transacción fallida dejaría un hueco permanente en la numeración. Con un contador
en una fila bloqueada, si la transacción se deshace el número vuelve a estar
disponible.

Lo mismo protege la posición en el registro:

```sql
-- ERROR: Posicion no correlativa en el registro de facturacion: se esperaba 5, llego 77
```

### 6. La cadena de huellas no se puede romper

Cada factura lleva un SHA-256 de una cadena canónica que incluye la huella de la
factura anterior. Alterar una factura del pasado invalida todas las posteriores.

```sql
-- Declarar una huella anterior inventada:
-- ERROR: Cadena de huellas rota en la factura A/2026/000004: se esperaba la
--        huella anterior d91be40a731f..., llego HUELLA_INVENTADA
```

### 7. La cadena no se puede bifurcar

Este es el ataque sutil: en vez de romper la cadena, colgar una segunda factura de
la misma huella anterior, creando dos historias válidas.

```sql
-- Reutilizar una huella anterior ya consumida:
-- ERROR: Cadena de huellas rota en la factura A/2026/000004
```

Se impide con `huella` y `huella_anterior` declaradas `NOT NULL UNIQUE`: cada
huella se consume **exactamente una vez**. No hay dos ramas posibles.

### 8. El contenido tiene que coincidir con su propio sello

Guardar el hash no basta. Si alguien modificase el `total` dejando intacta la
cadena canónica almacenada, el hash seguiría cuadrando consigo mismo.

Por eso la verificación recalcula la cadena canónica **a partir de los campos
actuales** y la compara con la almacenada
([`Factura.contenidoCoincideConElSello()`](backend/src/main/java/com/motorsport19/taller/factura/domain/Factura.java)).
Una discrepancia se reporta como anomalía `CONTENIDO_ALTERADO`.

Esta comprobación se añadió precisamente porque, al intentar el ataque
desactivando los triggers, la verificación original lo daba por bueno.

### 9. Los totales cuadran con las líneas

Los importes de línea son **columnas generadas** por PostgreSQL
(`GENERATED ALWAYS AS ... STORED`), así que no dependen de que el código calcule
bien. Y un *constraint trigger* diferido comprueba **al hacer commit** que
cabecera, líneas y desglose de IVA coinciden al céntimo.

Diferido a propósito: durante la transacción los totales están a medias mientras
se insertan las líneas. Solo tiene sentido comprobarlo al final.

### 10. Una orden entregada no se toca

```sql
UPDATE orden_trabajo SET diagnostico = 'x' WHERE estado = 'ENTREGADA';
-- ERROR: La orden de trabajo OT-2026-00001 esta ENTREGADA y no admite cambios
```

También quedan congeladas sus líneas, y el historial de cambios de estado es
inmutable por separado.

### 11. Baja lógica, nunca borrado

```sql
DELETE FROM cliente WHERE id = 1;
-- ERROR: Los registros de "cliente" no se borran fisicamente: use la baja logica
```

Igual para `moto`, `pieza` y `proveedor`. Un cliente de hace cinco años sigue
existiendo porque sus facturas lo referencian, y esas facturas hay que conservarlas.

---

## Comprobarlo en cualquier momento

Dos funciones de auditoría. Ambas devuelven **cero filas** cuando todo está bien:

```sql
SELECT * FROM fn_verificar_cadena_facturas();
SELECT * FROM fn_verificar_integridad_stock();
```

Con Docker:

```bash
docker exec motorsport19-db psql -U taller -d motorsport19 -c "SELECT * FROM fn_verificar_cadena_facturas()"
```

Desde la aplicación, con perfil ADMIN o MOSTRADOR: **Facturas → Verificar
integridad**, que recorre el registro entero y explica cualquier anomalía.

---

## Verificarlo sin este programa

Es la prueba que de verdad convence a un tercero: comprobar la facturación **sin
usar el software que la generó**.

La exportación JSON (*Facturas → Descargar JSON completo*, y también dentro de
cada copia de seguridad nocturna) incluye, por factura, la cadena canónica y su
huella. Con eso, cualquiera puede recalcular:

```python
import json, hashlib, sys

libro = json.load(open(sys.argv[1]))
facturas = libro["facturas"]

# 1. Cada huella es el SHA-256 de su propia cadena canónica.
for f in facturas:
    sello = f["sello"]
    calculada = hashlib.sha256(sello["cadena_huella"].encode("utf-8")).hexdigest()
    if calculada.upper() != sello["huella"].upper():
        sys.exit(f"Huella alterada en {f['numero']}")

# 2. Cada factura engancha con la anterior.
for anterior, actual in zip(facturas, facturas[1:]):
    if actual["sello"]["huella_anterior"] != anterior["sello"]["huella"]:
        sys.exit(f"Cadena rota entre {anterior['numero']} y {actual['numero']}")

# 3. La primera arranca de la huella génesis, así que no falta ninguna al principio.
if facturas and facturas[0]["sello"]["huella_anterior"] != libro["huella_genesis"]:
    sys.exit("Falta al menos una factura al principio del libro")

print("Libro íntegro:", len(facturas), "facturas")
```

Sin dependencias y sin usar MotorSport19 para nada. Ese es el objetivo: que la
integridad de la facturación no dependa de confiar en el programa.

El mismo script sirve para los dos ficheros —el que se descarga desde la
aplicación y el que va dentro de cada copia nocturna— porque ambos tienen la
misma estructura a propósito:

```bash
python3 verificar.py libro-facturas.json
```

---

## Lo que estas garantías *no* cubren

Conviene ser claro, porque una lista de garantías puede dar más confianza de la
que corresponde:

- **No impiden emitir una factura equivocada.** Impiden modificarla después. Un
  importe mal tecleado se corrige con una rectificativa, no borrando.
- **No sustituyen a las copias de seguridad.** Protegen de la manipulación, no de
  que se estropee el disco. Ver [COPIAS.md](COPIAS.md).
- **No son una certificación Veri*factu.** La cadena de huellas sigue el mismo
  planteamiento (cadena canónica, SHA-256 encadenado, registro de eventos), pero
  este sistema **no está homologado** ni envía nada a la AEAT. Si el taller queda
  obligado a Veri*factu, hay que revisarlo con la gestoría.
- **No protegen contra quien tenga acceso de superusuario a PostgreSQL.** Un
  superusuario puede desactivar triggers. Lo que sí queda es rastro: la cadena de
  huellas se rompería y `fn_verificar_cadena_facturas()` lo detectaría. Ese es
  justamente el punto: no se puede alterar el pasado *en silencio*.
