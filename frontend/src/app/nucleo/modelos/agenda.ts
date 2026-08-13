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

/** Lo justo para pintar una cita en la parrilla semanal, sin abrirla. */
export interface CitaBreve {
  id: number;
  fechaHora: string;
  duracionEstimada: number;
  estado: string;
  cliente: string | null;
  moto: string | null;
  motivo: string;
}

/** Un día de un técnico en la parrilla. */
export interface DiaTecnico {
  dia: string;
  citas: CitaBreve[];
  horasComprometidas: number;
  /** Lo que le queda de jornada. Nunca es negativo: para eso está `saturado`. */
  horasLibres: number;
  saturado: boolean;
}

export interface ColumnaTecnico {
  /** Nulo en la fila de las citas que aún no tienen técnico. */
  tecnicoId: number | null;
  nombre: string;
  dias: DiaTecnico[];
  horasComprometidas: number;
  horasLibres: number;
  citas: number;
}

/** La semana repartida por técnico. */
export interface AgendaSemanal {
  desde: string;
  hasta: string;
  capacidadDiaria: number;
  dias: string[];
  tecnicos: ColumnaTecnico[];
  horasComprometidas: number;
  horasLibres: number;
}

export interface Reincidente {
  nombre: string;
  telefono: string | null;
  faltas: number;
}

export interface Ausencia {
  citaId: number;
  dia: string;
  cliente: string | null;
  telefono: string | null;
  moto: string | null;
  motivo: string;
  horas: number;
  tecnico: string | null;
}

/** Los plantones de un periodo. */
export interface SeguimientoAusencias {
  desde: string;
  hasta: string;
  ausencias: number;
  /** Citas que llegaron a su día: atendidas más ausencias. */
  citasCerradas: number;
  porcentaje: number;
  horasPerdidas: number;
  reincidentes: Reincidente[];
  ultimas: Ausencia[];
}
