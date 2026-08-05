/**
 * Configuracion de produccion.
 *
 * La API NO vive en Vercel: Vercel sirve estaticos y funciones serverless, y
 * Spring Boot necesita una JVM permanente. El backend se despliega aparte
 * (Render, Railway, Fly.io...) y aqui se apunta a su URL publica.
 *
 * El valor se sustituye en el build a partir de la variable de entorno
 * NG_APP_URL_API (ver scripts/configurar-entorno.mjs).
 */
export const environment = {
  produccion: true,
  urlApi: 'URL_API_PLACEHOLDER',
};
