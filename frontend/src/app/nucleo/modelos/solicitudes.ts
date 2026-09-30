export type TipoSolicitud = 'CITA' | 'PRESUPUESTO';
export type EstadoSolicitud = 'PENDIENTE' | 'ATENDIDA' | 'DESCARTADA';

/** Cita o presupuesto pedido desde la web pública. */
export interface SolicitudWeb {
  id: number;
  referencia: string;
  tipo: TipoSolicitud;
  tipoDescripcion: string;
  estado: EstadoSolicitud;
  estadoDescripcion: string;
  recibidaEn: string;
  /** Idioma en que se rellenó: en el que habrá que contestar. */
  idioma: 'es' | 'ca' | 'en' | 'fr';

  nombre: string;
  telefono: string;
  email: string | null;
  marca: string;
  modelo: string;
  matricula: string | null;
  /** Como se apunta en una cita sin ficha: «Yamaha R6 · 1234ABC». */
  descripcionMoto: string;
  necesita: string;
  fechaPreferida: string | null;
  /** Cuántas fotos hay en /fotos/1..n. */
  fotos: number;

  citaId: number | null;
  citaFechaHora: string | null;
  nota: string | null;
  atendidaEn: string | null;
  atendidaPor: string | null;
}
