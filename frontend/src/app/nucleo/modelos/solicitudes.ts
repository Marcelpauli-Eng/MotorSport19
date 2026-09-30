export type TipoSolicitud = 'CITA' | 'PRESUPUESTO';
export type EstadoSolicitud = 'PENDIENTE' | 'PRESUPUESTADA' | 'ATENDIDA' | 'DESCARTADA';
export type CanalPresupuesto = 'WHATSAPP' | 'EMAIL';

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

  /** El último presupuesto que se le mandó. Nulo si no se ha mandado o si no se pueden ver importes. */
  presupuestoImporte: number | null;
  presupuestoDetalle: string | null;
  presupuestoCanal: CanalPresupuesto | null;
  presupuestadaEn: string | null;
  presupuestadaPor: string | null;

  citaId: number | null;
  citaFechaHora: string | null;
  nota: string | null;
  atendidaEn: string | null;
  atendidaPor: string | null;
}
