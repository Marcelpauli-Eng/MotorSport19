# Montaje híbrido: servidor en el taller + copia cifrada en la nube

Este es el destino real del sistema. La versión en Vercel/Render/Supabase
([DESPLIEGUE.md](DESPLIEGUE.md)) es el entorno de pruebas; **esto** es lo que se
instala en el taller.

```
        TALLER                                    FUERA
  ┌──────────────────────────────┐
  │  Mini-PC                     │
  │  ┌────────┐  ┌─────┐ ┌─────┐ │   túnel saliente   ┌──────────────┐
  │  │Postgres│◄─┤ API │◄┤ web │ │◄──────────────────►│  Cloudflare  │◄── móvil
  │  └───┬────┘  └─────┘ └─────┘ │                    └──────────────┘
  │      │                       │
  │  ┌───▼────────┐              │   copia cifrada    ┌──────────────┐
  │  │  respaldo  │──────────────┼───────────────────►│ Nube (B2/S3) │
  │  └────────────┘              │      cada noche    └──────────────┘
  └──────────────────────────────┘
     tablets y PC del mostrador
     entran por la red local
```

Los datos **nunca salen del taller** salvo cifrados dentro de la copia de
seguridad.

---

# Parte A — Probarlo en tu MacBook

Sirve para ver exactamente lo que verá el cliente, antes de tocar hardware.

## 1. Instalar Docker

Tu Mac **no tiene Docker instalado** todavía. Con Homebrew:

```bash
brew install --cask docker
```

Después abre **Docker Desktop** desde Aplicaciones y espera a que la ballena de
la barra de menús deje de moverse. Comprueba:

```bash
docker --version && docker compose version
```

## 2. Arrancar todo

```bash
cd ~/Documents/GitHub/MotorSport19
cp .env.example .env
docker compose up --build
```

La primera vez tarda unos 5 minutos: compila el backend con Maven y el frontend
con npm. Las siguientes son segundos.

Cuando veas `Started TallerApplication`, abre **http://localhost:4200**.

Viene con los datos de demostración cargados (perfil `docker,demo`), así que
verás las órdenes, las facturas con su cadena de huellas y las alertas de stock.

## 3. Pararlo

```bash
docker compose down          # para los contenedores, conserva los datos
docker compose down -v       # además borra la base de datos
```

---

# Parte B — Montarlo en el taller

## 1. El equipo

| Opción | Precio | Comentario |
|---|---|---|
| **Mini PC Intel N100, 16 GB RAM, SSD 256 GB** | ~180 € | Lo recomendado. Consume 6 W, va sobrado |
| Raspberry Pi 5 8 GB + SSD USB | ~130 € | Funciona (las imágenes tienen ARM64), pero justo |

**No uses tarjeta SD** en la Raspberry: se degradan con la escritura constante de
una base de datos y fallan en meses. SSD por USB3 como mínimo.

Instala **Ubuntu Server 24.04 LTS** (sin escritorio) y déjalo con IP fija en el
router. Anótala: será la dirección por la que entren los equipos del taller.

## 2. Docker en el mini-PC

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# cierra sesión y vuelve a entrar
```

## 3. Descargar el proyecto

```bash
sudo apt install -y git
git clone https://github.com/Marcelpauli-Eng/MotorSport19.git
cd MotorSport19
```

## 4. Configuración

```bash
cp .env.taller.example .env
openssl rand -base64 32    # → POSTGRES_PASSWORD
openssl rand -base64 32    # → CLAVE_CIFRADO
openssl rand -base64 48    # → CLAVE_JWT
nano .env
```

> ⚠️ **`CLAVE_CIFRADO` es lo más importante del fichero.** Si se pierde, todas
> las copias quedan ilegibles para siempre. Guárdala en un gestor de contraseñas
> **y** apuntada en papel en un sitio seguro **fuera del taller**. No la guardes
> solo en el propio mini-PC: el día que se estropee el disco necesitarás la clave
> precisamente para recuperar lo que había en él.

`CLAVE_JWT` es con lo que se firman las sesiones. La API **no arranca** sin ella
—es a propósito— y pide al menos 32 caracteres. No hace falta apuntarla en
ningún sitio: si algún día se pierde, se genera otra y todo el mundo vuelve a
entrar.

Para arrancar en limpio (sin datos de ejemplo), deja:

```
SPRING_PROFILES_ACTIVE=docker
```

### Los usuarios del taller

Con `SPRING_PROFILES_ACTIVE=docker,demo` se crean cuatro usuarios de ejemplo con
contraseñas que están publicadas en este repositorio (`admin`/`admin1234`,
`mostrador`/`mostrador1234`, `jortega` y `nsanz`/`tecnico1234`).

**Antes de dejar el programa en manos del taller**, entra con cada uno y cámbiala
desde *Mi cuenta*, arriba a la derecha.

Con `SPRING_PROFILES_ACTIVE=docker` a secas no se crea ninguno de esos. En su
lugar, la primera vez que arranca sobre una base vacía se crea un administrador
con una contraseña **generada al azar**, que aparece en el log de arranque:

```bash
docker compose -f docker-compose.taller.yml logs api | grep -A6 "PRIMER ARRANQUE"
```

```
 PRIMER ARRANQUE: no habia ningun usuario en la base.
 Se ha creado un administrador para poder entrar.

   Usuario:    admin
   Contrasena: rwZ-7cEJcqO0b6qRDh3K_W_p
```

Apúntala, entra y cámbiala desde *Mi cuenta*. No vuelve a mostrarse, y en los
siguientes arranques no se toca nada: si ya hay usuarios, este paso se salta
entero.

Si prefieres fijarla tú desde el principio, pon `MOTORSPORT19_ADMIN_PASSWORD` en
el `.env` antes del primer arranque.

## 5. Destino de las copias

Cualquier sitio compatible con rclone. **Backblaze B2** sale por unos 6 €/año
para este volumen; también sirve el Storage de Supabase si ya lo usas.

```bash
docker run --rm -it -v ./infra/respaldo:/config/rclone rclone/rclone config
```

Llama al destino `copias`. Al terminar tendrás `infra/respaldo/rclone.conf`
(está en `.gitignore`, no se sube al repositorio).

En el `.env`:

```
RCLONE_DESTINO=copias:motorsport19
```

## 6. Arrancar

```bash
docker compose -f docker-compose.taller.yml up -d --build
docker compose -f docker-compose.taller.yml logs -f
```

Con `COPIA_AL_ARRANCAR=true` hace una copia de prueba nada más levantar. En los
logs debe salir:

```
[...] Volcado correcto: 1.2M
[...] Libro de facturas exportado: 48K
[...] Cifrado y verificado: 890K
[...] Subida confirmada.
```

Si las cuatro líneas aparecen, las copias funcionan. Pon después
`COPIA_AL_ARRANCAR=false`.

Ya puedes entrar desde cualquier equipo del taller en **http://IP-DEL-MINIPC**.

### Lo primero al entrar: los datos del taller

Una instalación nueva viene **sin los datos de la empresa**, y a propósito: unos
datos fiscales de relleno acabarían impresos en una factura de verdad. Hasta que
se pongan, el programa avisa de que faltan y no deja abrir órdenes ni facturar,
porque la tarifa por hora y el emisor de las facturas salen de ahí.

Entra como administrador, ve a **Ajustes → Empresa y facturación**, rellena razón
social, NIF, dirección, tarifa por hora y horas de taller al día, y pulsa
*Guardar*. Es cosa de un minuto y solo hace falta una vez.

Cambiarlos más adelante no toca las facturas ya emitidas: cada una guarda dentro
una copia de cómo estaba el taller el día que se emitió.

## 7. Acceso desde fuera (el móvil)

1. Ten el dominio en Cloudflare (gratis).
2. En [one.dash.cloudflare.com](https://one.dash.cloudflare.com) →
   **Networks → Tunnels → Create a tunnel** → tipo *Cloudflared*.
3. Copia el token y ponlo en el `.env` como `CLOUDFLARE_TUNNEL_TOKEN`.
4. En **Public Hostname** del túnel:

   ```
   Subdomain:  taller
   Domain:     tudominio.com
   Service:    HTTP → web:80
   ```

5. Arranca el túnel:

   ```bash
   docker compose -f docker-compose.taller.yml --profile tunel up -d
   ```

Ya tienes `https://taller.tudominio.com` desde cualquier sitio, sin haber abierto
un solo puerto en el router.

### Ponle un login delante (hazlo)

En **Access → Applications → Add an application** → *Self-hosted* →
`taller.tudominio.com` → política *Allow* → *Emails* → los correos que puedan
entrar.

La aplicación ya pide usuario y contraseña por su cuenta, así que esto es una
segunda puerta, no la única. Aun así merece la pena: con Access, quien no esté en
la lista ni siquiera llega a ver la pantalla de entrada, y eso deja fuera a
cualquiera que ande probando URLs. Es gratis hasta 50 usuarios y se configura en
dos minutos.

---

# Mantenimiento

## Actualizar

```bash
cd MotorSport19
git pull
docker compose -f docker-compose.taller.yml up -d --build
```

Las migraciones de base de datos se aplican solas al arrancar.

## Probar que las copias sirven

**Hazlo cada tres meses.** Una copia que nunca se ha restaurado no es una copia,
es una carpeta con ficheros.

```bash
# Lista las copias que hay en el mini-PC
docker compose -f docker-compose.taller.yml exec respaldo ls -lh /copias

# Restaura sobre una base de usar y tirar (NO toca la de producción)
docker compose -f docker-compose.taller.yml exec respaldo \
  restaurar /copias/motorsport19_2026-08-05_0330.tar.enc
```

Al final comprueba solo la integridad de la facturación y te dice:

```
OK: la cadena de huellas de la facturacion esta integra tras restaurar.
```

Si eso sale, la copia sirve de verdad.

## Restaurar sobre la base real (el día malo)

```bash
docker compose -f docker-compose.taller.yml exec respaldo \
  restaurar /copias/LA_COPIA.tar.enc motorsport19
```

Da 10 segundos para cancelar antes de sobreescribir.

## Descifrar una copia desde tu Mac

No necesitas Docker ni este proyecto, solo `openssl`:

```bash
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 \
  -in motorsport19_2026-08-05_0330.tar.enc \
  -out copia.tar -pass pass:TU_CLAVE_CIFRADO
tar -xf copia.tar
```

Dentro está el volcado SQL y el libro de facturas en JSON.

## Ver qué está pasando

```bash
docker compose -f docker-compose.taller.yml ps          # estado
docker compose -f docker-compose.taller.yml logs -f api # logs de la aplicación
docker compose -f docker-compose.taller.yml logs respaldo | tail -30
```

---

# Lo que hay que vigilar

| Riesgo | Qué hacer |
|---|---|
| **Se pierde `CLAVE_CIFRADO`** | Las copias son ilegibles. Guárdala fuera del taller |
| **Se estropea el disco** | Las copias están en la nube. Mini-PC nuevo, `git clone`, `restaurar` |
| **Copias que fallan en silencio** | Mira los logs de `respaldo` una vez al mes |
| **Se va la luz** | Un SAI de 40 € evita corrupción por apagones bruscos |
| **Alguien adivina la URL** | Login propio + Cloudflare Access como segunda puerta |
| **Contraseñas de fábrica** | Cámbialas desde *Mi cuenta* antes de la entrega |

## Una tranquilidad extra sobre la facturación

La copia incluye, además del volcado SQL, el **libro de facturas en JSON con las
cadenas de huellas**. Ese fichero se verifica por sí solo con un SHA-256: aunque
un día no pudieras restaurar la base de datos, seguirías pudiendo demostrar qué
se facturó y que nadie lo alteró.
