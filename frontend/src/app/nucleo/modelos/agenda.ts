export type EstadoCita = 'PENDIENTE' | 'CONFIRMADA' | 'ATENDIDA' | 'CANCELADA' | 'NO_PRESENTADO';

/**
 * Cita de entrada al taller.
 *
 * `contacto` y `moto` vienen ya resueltos del servidor: cuando la moto está en
 * el sistema mandan sus datos, y si no, los que se apuntaron a mano al coger la
 * cita por teléfono.
 */
export interface Cita {
  id: number;
  fechaHora: string;
  duracionEstimada: number;
  estado: EstadoCita;
  estadoDescripcion: string;
  estadosPosibles: EstadoCita[];

  motoId: number | null;
  matricula: string | null;
  moto: string | null;
  clienteId: number | null;
  contactoNombre: string | null;
  contactoTelefono: string | null;
  /** La moto todavía no está dada de alta: se apuntó a mano. */
  motoSinRegistrar: boolean;

  motivo: string;
  tecnicoId: number | null;
  tecnicoNombre: string | null;
  observaciones: string | null;

  ordenTrabajoId: number | null;
  ordenCodigo: string | null;
  motivoCancelacion: string | null;
}

/** Trabajo comprometido un día concreto. */
export interface CargaDiaria {
  dia: string;
  citas: number;
  horasComprometidas: number;
  capacidad: number;
  porcentaje: number;
  /** Lo comprometido pasa de la capacidad del taller. */
  saturado: boolean;
}
