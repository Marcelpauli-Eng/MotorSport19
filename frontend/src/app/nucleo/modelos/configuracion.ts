
export interface TipoIva {
  codigo: string;
  descripcion: string;
  porcentaje: number;
}

/**
 * Datos del taller: los que salen impresos en cada factura.
 *
 * Cambiarlos no reescribe las facturas ya emitidas: cada una guarda dentro una
 * copia de cómo estaba el taller el día que se emitió.
 */
export interface ConfiguracionTaller {
  /** false en un taller recién instalado, hasta que se guardan los datos una vez. */
  configurado: boolean;
  razonSocial: string;
  nif: string;
  direccion: string;
  codigoPostal: string;
  ciudad: string;
  provincia: string | null;
  pais: string;
  telefono: string | null;
  email: string | null;
  tarifaHoraDefecto: number;
  tipoIvaDefecto: string;
  /** Horas de taller al día. La agenda avisa cuando un día las pasa. */
  capacidadDiariaHoras: number;
  /** Importe (IVA incluido) a partir del cual una factura ya no puede ser simplificada. */
  limiteFacturaSimplificada: number;
  softwareNombre: string;
  softwareVersion: string;
  tiposIva: TipoIva[];
}

/**
 * Tasa o plus que se aplica solo al añadir una pieza a una orden.
 *
 * Una TASA añade una línea aparte de `valor` euros por unidad; un PLUS pone un
 * descuento de `valor` % en la línea de la pieza. Si la regla es de una pieza
 * concreta, manda sobre la de su grupo.
 */
export interface ReglaCobro {
  id: number;
  tipo: 'TASA' | 'PLUS';
  /** El grupo de la regla o, si es de una pieza, el de la pieza. */
  familia: string | null;
  /** Nula si vale para todo el grupo. */
  piezaId: number | null;
  piezaNombre: string | null;
  concepto: string | null;
  valor: number;
  /** En qué va el valor: euros por unidad o tanto por ciento del precio de la pieza. */
  unidad: 'EUROS' | 'PORCENTAJE';
}

export interface Usuario {
  id: number;
  username: string;
  nombreCompleto: string;
  email: string | null;
  telefono: string | null;
  rolId: number;
  /** Nombre del rol tal y como lo llamó quien lo creó. */
  rol: string;
  rolDescripcion: string | null;
  activo: boolean;
  ultimoAcceso: string | null;
}

/** Lo mínimo para poner un nombre en el desplegable de técnicos. */
export interface Tecnico {
  id: number;
  nombreCompleto: string;
}
