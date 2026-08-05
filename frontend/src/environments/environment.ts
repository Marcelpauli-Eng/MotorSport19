/**
 * Configuracion de desarrollo.
 *
 * En local el `ng serve` hace de proxy hacia la API (ver proxy.conf.json), asi
 * que basta con la ruta relativa y no hay problemas de CORS.
 */
export const environment = {
  produccion: false,
  urlApi: '/api',
};
