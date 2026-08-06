# Copias de seguridad

Este documento explica cómo se protegen los datos del taller: qué se guarda, cómo,
dónde, y —lo que de verdad importa— **cómo comprobar que la copia sirve** antes de
necesitarla.

Aplica al montaje del taller
([`docker-compose.taller.yml`](docker-compose.taller.yml)). En la nube, Supabase
hace sus propias copias.

---

## La regla que rige todo esto

> Una copia que nunca se ha restaurado no es una copia. Es una esperanza.

Casi todos los sistemas de respaldo que fallan lo hacen en silencio: el script se
ejecutó, el fichero está, pesa lo que debe... y el día que hace falta resulta que
lleva ocho meses guardando una base vacía, o cifrada con una clave que ya nadie
tiene.

Por eso el script no se limita a copiar: **verifica cada paso**, y falla ruidosamente
si algo no cuadra.

---

## Qué se guarda

Cada noche, dos ficheros dentro de un mismo paquete cifrado:

| Fichero | Qué es | Para qué |
|---|---|---|
| `…​.sql.gz` | Volcado completo (`pg_dump`) | Restaurar el sistema entero |
| `…​_libro-facturas.json` | Libro de facturas con la cadena de huellas | Comprobar la facturación **sin** este programa |

El segundo es redundante a propósito. Si algún día MotorSport19 deja de existir,
ese JSON sigue siendo legible y verificable con
[diez líneas de Python](GARANTIAS.md#verificarlo-sin-este-programa). El volcado SQL
sirve para volver a funcionar; el JSON sirve para demostrar.

Sale directamente de PostgreSQL, no de la API: así la copia se hace aunque la
aplicación esté caída, y el contenedor de respaldo no necesita credenciales de
nadie.

---

## Qué comprueba el script antes de dar la copia por buena

De [`infra/respaldo/respaldar.sh`](infra/respaldo/respaldar.sh), en orden:

1. **El gzip está íntegro** (`gzip -t`). Un volcado truncado por quedarse sin
   disco es un fichero perfectamente plausible a simple vista.
2. **El volcado contiene las tablas esperadas** (`factura`, `linea_factura`,
   `orden_trabajo`, `cliente`, `movimiento_stock`). Un volcado de una base vacía
   también es un gzip válido y también pesa algo.
3. **El libro de facturas trae las cadenas de huellas**. Sin ellas el JSON no
   sirve para lo único que tiene que servir.
4. **El cifrado se puede deshacer.** Se descifra el paquete contra `/dev/null`
   nada más crearlo. Es la comprobación más importante: detecta *esa misma noche*
   que la clave configurada no es la que se cree, en vez de descubrirlo el día del
   desastre.
5. **El fichero ha llegado al destino remoto.** No basta con que `rclone` termine
   sin error: se vuelve a listar el destino y se comprueba que está.

Si cualquiera de estos pasos falla, el script aborta y lo deja escrito en el log.
No genera una copia «casi buena».

---

## Cómo se cifra

AES-256-CBC con derivación PBKDF2, usando `CLAVE_CIFRADO` del `.env`.

El cifrado ocurre **antes** de subir nada. El proveedor de almacenamiento —Backblaze,
Supabase Storage, lo que sea— solo ve un bloque opaco. Nunca tiene acceso a los
datos de los clientes del taller.

> ⚠️ **`CLAVE_CIFRADO` es lo más importante del `.env`.**
>
> Si se pierde, **todas** las copias quedan ilegibles para siempre. No hay forma
> de recuperarlas: eso es exactamente lo que significa cifrar bien.
>
> Guárdala en un gestor de contraseñas **y** apuntada en papel, en un sitio seguro
> **fuera del taller**. No la dejes solo en el mini-PC: el día que se estropee ese
> disco necesitarás la clave precisamente para recuperar lo que había dentro.

---

## Cuándo se hace

Por defecto a las 03:30, con el taller cerrado. Se configura en el `.env`:

```
HORA_COPIA=03
MINUTO_COPIA=30
DIAS_RETENCION_LOCAL=7
```

El planificador es un bucle con `sleep`, no `cron`. Es a propósito: `cron` dentro
de un contenedor no hereda las variables de entorno y se traga los logs, así que
los fallos pasan desapercibidos. Un bucle escribe en la salida estándar y Docker
lo recoge.

Se guardan 7 días en el propio mini-PC y los que decida el proveedor en el destino
remoto.

---

## Restaurar

```bash
# 1. Ver qué copias hay
docker compose -f docker-compose.taller.yml exec respaldo ls -lh /copias

# 2. Restaurar sobre una base de usar y tirar
docker compose -f docker-compose.taller.yml exec respaldo \
  restaurar /copias/motorsport19_2026-08-06_0330.tar.enc
```

Sin segundo argumento restaura sobre **`motorsport19_prueba`**, no sobre la base
de producción. Es deliberado: la operación más frecuente no es un desastre real,
es un simulacro, y un simulacro nunca debería poder destruir los datos buenos.

Para restaurar de verdad sobre producción hay que decirlo explícitamente:

```bash
docker compose -f docker-compose.taller.yml exec respaldo \
  restaurar /copias/motorsport19_2026-08-06_0330.tar.enc motorsport19
```

Tras restaurar, el script ejecuta `fn_verificar_cadena_facturas()` y **falla si
hay anomalías**. Una restauración que deja la facturación incoherente no es una
restauración correcta, y es mejor enterarse en ese momento.

---

## El simulacro trimestral

Ponlo en el calendario. Son cinco minutos:

```bash
# Restaura la copia de anoche sobre la base de pruebas
docker compose -f docker-compose.taller.yml exec respaldo \
  restaurar /copias/$(ls -t /copias/*.tar.enc | head -1 | xargs basename)
```

Tiene que terminar con `Restauracion terminada` y sin anomalías. Si algún día no
lo hace, lo has descubierto un martes cualquiera y no el día que ardió el taller.

Aprovecha para comprobar también el libro de facturas. Sacarlo de dentro de la
copia cifrada, sin restaurar nada:

```bash
COPIA=$(docker compose -f docker-compose.taller.yml exec -T respaldo sh -c 'ls -t /copias/*.tar.enc | head -1' | tr -d '\r')

docker compose -f docker-compose.taller.yml exec -T respaldo sh -c \
  "openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -pass env:CLAVE_CIFRADO -in '$COPIA' -out /tmp/c.tar \
   && tar -xOf /tmp/c.tar \$(tar -tf /tmp/c.tar | grep libro-facturas)" > libro-facturas.json

python3 verificar.py libro-facturas.json
# Libro íntegro: 4 facturas
```

Son otros diez segundos y prueban lo que de verdad quiere oír una gestoría: que
la facturación es recuperable y verificable **sin** MotorSport19. El script está
en [GARANTIAS.md](GARANTIAS.md#verificarlo-sin-este-programa).

### Descifrar desde cualquier sitio

El cifrado es `openssl` estándar, así que no hace falta ni Docker ni este
proyecto. Con la copia y la clave, desde cualquier máquina:

```bash
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 \
  -pass env:CLAVE_CIFRADO -in motorsport19_2026-08-06_0330.tar.enc -out copia.tar
tar -xf copia.tar
```

Es deliberado: si el día de mañana no hay contenedores, ni scripts, ni nadie que
recuerde cómo iba esto, las copias siguen abriéndose con una herramienta que está
en cualquier Linux y en cualquier Mac.

---

## De qué protege cada cosa

| Riesgo | Qué lo cubre |
|---|---|
| Alguien borra un cliente por error | No se puede: baja lógica ([GARANTIAS.md](GARANTIAS.md)) |
| Alguien modifica una factura | No se puede: son inmutables |
| Se corrompe la base de datos | Volcado nocturno |
| Se estropea el disco del mini-PC | Copia remota cifrada |
| Se incendia o roban el taller | Copia remota cifrada, fuera del edificio |
| Ransomware cifra el mini-PC | Copia remota; el atacante no tiene la clave |
| Se pierde `CLAVE_CIFRADO` | **Nada.** Guárdala fuera del taller |
| Copias que fallan en silencio | Verificación en cada paso + simulacro trimestral |
| Se va la luz a media escritura | PostgreSQL es transaccional; un SAI de 40 € evita el susto |

---

## Comprobar que sigue funcionando

Una vez al mes, treinta segundos:

```bash
docker compose -f docker-compose.taller.yml logs --tail=50 respaldo
```

Busca la línea `Copia terminada` con fecha reciente. Si lo último que hay es de
hace tres semanas, algo dejó de funcionar y conviene mirarlo antes de que haga
falta.
