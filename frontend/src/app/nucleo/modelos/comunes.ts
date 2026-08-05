/**
 * Tipos que espejan los DTO del backend.
 *
 * Se escriben a mano en vez de generarse: son el contrato entre las dos partes
 * y conviene que un cambio en el backend rompa la compilacion del frontend en
 * lugar de fallar en tiempo de ejecucion delante del cliente.
 */

/** Pagina de resultados, con la forma que devuelve `PaginaResponse` del backend. */
export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalItems: number;
  totalPaginas: number;
  primera: boolean;
  ultima: boolean;
}

/** Cuerpo de error uniforme de la API. */
export interface RespuestaError {
  momento: string;
  estado: number;
  error: string;
  mensaje: string;
  ruta: string;
  detalles: Record<string, string>;
}

export const PAGINA_VACIA: Pagina<never> = {
  contenido: [],
  pagina: 0,
  tamano: 20,
  totalItems: 0,
  totalPaginas: 0,
  primera: true,
  ultima: true,
};
