# Poner MotorSport19 en funcionamiento

Tres piezas en tres sitios:

```
   Navegador
       │
       ▼
   ┌─────────────────┐   /api/*    ┌──────────────────┐   JDBC   ┌──────────┐
   │ Vercel          │ ──────────► │ Render           │ ───────► │ Supabase │
   │ Angular (SPA)   │             │ Spring Boot      │          │ Postgres │
   └─────────────────┘             └──────────────────┘          └──────────┘
```

Vercel reenvía todo lo que empieza por `/api` a Render, así que el navegador
solo habla con un dominio. Eso evita tener que configurar CORS y mantiene la URL
del backend fuera del código del frontend.

---

## ⚠️ Antes de empezar: esto todavía no tiene contraseña

La API **no tiene autenticación** (eso es la fase 5). Si la publicas tal cual,
cualquiera con la URL puede ver los datos de tus clientes —nombre, NIF,
dirección, teléfono— y modificarlos. Son datos personales bajo RGPD.

Tres opciones, de menos a más recomendable:

1. **Terminar la fase 5 primero.** Es la siguiente y es la que cierra esto.
2. **Publicar con acceso restringido.** Vercel permite proteger el despliegue con
   contraseña (Settings → Deployment Protection). Sirve para enseñarlo a
   alguien concreto sin dejarlo abierto.
3. **Publicar con datos de prueba.** Despliega con el perfil `demo` y sin datos
   reales de clientes hasta que exista el login.

---

## 1. Base de datos en Supabase

1. En [supabase.com](https://supabase.com) → **New project**. Región
   **eu-west-3 (París)** o similar: cuanto más cerca de Render, mejor.
2. Anota la contraseña de la base de datos; no se vuelve a mostrar.
3. Ve a **Project Settings → Database → Connection string → Session pooler**.

   Necesitas los datos de la fila **Session pooler**, no los de «Direct
   connection». La conexión directa es solo IPv6 y Render no la alcanza; el
   pooler en modo *transaction* (puerto 6543) rompe las migraciones de Flyway.

   ```
   Host:     aws-0-eu-west-3.pooler.supabase.com
   Puerto:   5432
   Usuario:  postgres.abcdefghijklmnop
   Base:     postgres
   ```

No hace falta crear ninguna tabla: Flyway las crea al arrancar la API, y la
migración `V8` cierra el acceso directo desde la API pública de Supabase.

---

## 2. Backend en Render

1. Sube el repositorio a GitHub si no lo está.
2. En [render.com](https://render.com) → **New → Blueprint** → conecta el
   repositorio. Render detecta el fichero `render.yaml` de la raíz.
3. Te pedirá las tres variables marcadas como `sync: false`:

   ```
   SPRING_DATASOURCE_URL       jdbc:postgresql://aws-0-eu-west-3.pooler.supabase.com:5432/postgres?sslmode=require
   SPRING_DATASOURCE_USERNAME  postgres.abcdefghijklmnop
   SPRING_DATASOURCE_PASSWORD  (la de Supabase)
   ```

   Fíjate en el prefijo `jdbc:postgresql://` y en el `?sslmode=require` del
   final: Supabase rechaza las conexiones sin cifrar.

4. El primer despliegue tarda unos 5 minutos (compila el backend con Maven).
5. Comprueba que responde:

   ```bash
   curl https://motorsport19-api.onrender.com/api/actuator/health
   ```

   Debe devolver `{"status":"UP"}`.

### Cargar los datos de demostración

Si quieres arrancar con datos de ejemplo, cambia en Render la variable
`SPRING_PROFILES_ACTIVE` a `supabase,demo` y vuelve a desplegar. **No lo dejes
así con datos reales**: el perfil `demo` crea usuarios con contraseñas conocidas.

### El plan gratuito se duerme

Render apaga los servicios gratuitos tras 15 minutos sin tráfico, y la siguiente
petición tarda unos 50 segundos en despertarlo. Para enseñarlo va bien; para el
día a día del taller, el plan más barato (7 $/mes) evita esa espera.

---

## 3. Frontend en Vercel

1. Antes de nada, edita `frontend/vercel.json` y pon el nombre real de tu
   servicio de Render:

   ```json
   "destination": "https://TU-SERVICIO.onrender.com/api/:ruta*"
   ```

2. En [vercel.com](https://vercel.com) → **Add New → Project** → importa el
   repositorio.
3. En la configuración del proyecto:

   ```
   Root Directory:     frontend
   Framework Preset:   Other
   Build Command:      npm run build
   Output Directory:   dist/frontend/browser
   ```

   Los tres últimos ya vienen en `vercel.json`; solo hay que fijar el **Root
   Directory** a `frontend`, que eso no puede ir en el fichero.

4. Deploy. En un minuto tienes la URL.

---

## 4. Comprobar que todo funciona

Abre la URL de Vercel. Si ves el panel con datos, las tres piezas están
conectadas.

Si algo falla, en este orden:

| Síntoma | Dónde mirar |
|---------|-------------|
| Pantalla en blanco | Consola del navegador (F12) |
| «No se ha podido contactar con el servidor» | ¿Está despierto Render? Prueba la URL `/api/actuator/health` |
| Error 500 al cargar | Logs de Render: casi siempre es la cadena de conexión |
| Migraciones fallando | Comprueba que usas el pooler en modo **session**, puerto 5432 |

### Errores frecuentes con Supabase

**`Connection refused` o timeout**: estás usando la conexión directa
(`db.xxx.supabase.co`), que es solo IPv6. Cambia al pooler.

**`Flyway could not acquire lock`**: estás en el pooler de modo *transaction*
(puerto 6543). Cambia al 5432.

**`password authentication failed`**: el usuario del pooler lleva el sufijo del
proyecto (`postgres.abcdefghijklmnop`), no es solo `postgres`.

---

## 5. Cada vez que hagas cambios

Los dos servicios despliegan solos al hacer `git push`:

- **Vercel** reconstruye el frontend en unos 40 segundos.
- **Render** reconstruye el backend en unos 4 minutos.

Las migraciones de base de datos nuevas se aplican solas al arrancar la API.
Flyway lleva la cuenta de cuáles ya se ejecutaron.

---

## Copia de seguridad

Supabase hace copias automáticas diarias en el plan de pago. En el gratuito **no
hay copias automáticas**, así que descarga una tú de vez en cuando:

```bash
pg_dump "postgresql://postgres.TUPROYECTO:CONTRASENA@aws-0-eu-west-3.pooler.supabase.com:5432/postgres" \
  --no-owner --no-privileges -f copia-$(date +%F).sql
```

Para las facturas hay además una vía independiente que no depende de Supabase:
desde la pantalla de **Facturas → Descargar JSON completo**. Ese fichero incluye
las cadenas de huellas, con lo que la integridad del libro se puede verificar
aunque no tengas la base de datos delante.
