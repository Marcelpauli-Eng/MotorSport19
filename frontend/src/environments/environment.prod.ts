/**
 * Configuracion de produccion.
 *
 * La ruta es relativa a proposito. En Vercel, `vercel.json` reenvia todo lo que
 * cuelga de /api al backend de Render, asi que el navegador solo habla con el
 * dominio de Vercel: no hace falta configurar CORS ni publicar la URL del
 * backend en el codigo del frontend.
 *
 * Si algun dia se llama a la API directamente, aqui iria su URL completa y
 * habria que habilitar CORS en el backend.
 */
export const environment = {
  produccion: true,
  urlApi: '/api',
};
