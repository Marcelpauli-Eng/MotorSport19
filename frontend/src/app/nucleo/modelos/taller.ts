export type TipoDocumento = 'NIF' | 'CIF' | 'NIE' | 'PASAPORTE' | 'OTRO';

export interface Cliente {
  id: number;
  nombre: string;
  apellidos: string | null;
  nombreCompleto: string;
  tipoDocumento: TipoDocumento | null;
  documento: string | null;
  direccion: string | null;
  codigoPostal: string | null;
  ciudad: string | null;
  provincia: string | null;
  pais: string;
  email: string | null;
  telefono: string | null;
  observaciones: string | null;
  activo: boolean;
  fechaBaja: string | null;
  /** Indica si reune los datos fiscales que exige una factura. */
  facturable: boolean;
}

export interface ClienteResumen {
  id: number;
  nombreCompleto: string;
  documento: string | null;
  telefono: string | null;
  email: string | null;
  activo: boolean;
  facturable: boolean;
}

export interface Moto {
  id: number;
  clienteId: number;
  clienteNombre: string;
  matricula: string;
  marca: string;
  modelo: string;
  descripcion: string;
  anio: number | null;
  cilindrada: number | null;
  color: string | null;
  numeroBastidor: string | null;
  kmActual: number;
  observaciones: string | null;
  activo: boolean;
  fechaBaja: string | null;
}

export interface MotoResumen {
  id: number;
  matricula: string;
  descripcion: string;
  anio: number | null;
  kmActual: number;
  clienteId: number;
  clienteNombre: string;
  activo: boolean;
}

// ---------------------------------------------------------------------------
// Inventario
// ---------------------------------------------------------------------------

export type TipoMovimiento = 'ENTRADA' | 'SALIDA' | 'AJUSTE' | 'DEVOLUCION';

export interface Pieza {
  id: number;
  sku: string;
  descripcion: string;
  marca: string | null;
  ubicacion: string | null;
  /** Grupo del almacén: Frenos, Filtros, Transmisión… Texto libre. */
  familia: string | null;
  stockActual: number;
  stockMinimo: number;
  bajoMinimo: boolean;
  sinExistencias: boolean;
  /** Nulos para un técnico: el catálogo le dice qué hay, no lo que cuesta. */
  precioCoste: number | null;
  precioVenta: number | null;
  tipoIva: string;
  proveedorId: number | null;
  proveedorNombre: string | null;
  unidadMedida: string;
  observaciones: string | null;
  activo: boolean;
  fechaBaja: string | null;
}

export interface AlertaStock {
  piezaId: number;
  sku: string;
  descripcion: string;
  marca: string | null;
  ubicacion: string | null;
  stockActual: number;
  stockMinimo: number;
  unidadesAReponer: number;
  sinExistencias: boolean;
  proveedorId: number | null;
  proveedorNombre: string | null;
  precioCoste: number | null;
}

export interface MovimientoStock {
  id: number;
  piezaId: number;
  piezaSku: string;
  piezaDescripcion: string;
  tipo: TipoMovimiento;
  tipoDescripcion: string;
  cantidad: number;
  stockAnterior: number;
  stockResultante: number;
  fecha: string;
  usuarioId: number | null;
  usuarioNombre: string | null;
  ordenTrabajoId: number | null;
  /** Reparación y moto a las que se fue el material, si salió por una orden. */
  ordenCodigo: string | null;
  matricula: string | null;
  descripcionMoto: string | null;
  motivo: string | null;
  documentoProveedor: string | null;
  precioCosteUnitario: number | null;
}

export interface Proveedor {
  id: number;
  nombre: string;
  nif: string | null;
  direccion: string | null;
  codigoPostal: string | null;
  ciudad: string | null;
  provincia: string | null;
  telefono: string | null;
  email: string | null;
  observaciones: string | null;
  activo: boolean;
  fechaBaja: string | null;
}

// ---------------------------------------------------------------------------
// Ordenes de trabajo
// ---------------------------------------------------------------------------

export type EstadoOT =
  | 'RECIBIDA'
  /** Compuesta por dirección y esperando a que el técnico la empiece. */
  | 'PREPARADA'
  | 'EN_DIAGNOSTICO'
  | 'PRESUPUESTADA'
  | 'APROBADA'
  | 'EN_REPARACION'
  | 'ESPERANDO_PIEZAS'
  | 'LISTA'
  | 'ENTREGADA'
  | 'RECHAZADA';

export type TipoLinea = 'MANO_DE_OBRA' | 'PIEZA';

/**
 * Línea de una orden de trabajo.
 *
 * Todos los importes son opcionales porque a un técnico el servidor se los
 * envía a nulo: el taller puede decidir que quien monta la moto no vea a cuánto
 * se la cobra la casa al cliente. No es que la pantalla los oculte, es que no
 * llegan.
 */
export interface LineaOT {
  id: number;
  numeroLinea: number;
  tipo: TipoLinea;
  tipoDescripcion: string;
  descripcion: string;
  piezaId: number | null;
  piezaSku: string | null;
  cantidad: number;
  precioUnitario: number | null;
  descuentoPct: number | null;
  tipoIva: string | null;
  porcentajeIva: number | null;
  /** Lo que valdría sin descuento: cantidad × precio de tarifa. */
  importeBruto: number | null;
  /** Rebaja aplicada, en euros. Cero si no hay descuento. */
  importeDescuento: number | null;
  baseImponible: number | null;
  cuotaIva: number | null;
  total: number | null;
}

export interface CambioEstado {
  id: number;
  estadoAnterior: EstadoOT | null;
  estadoNuevo: EstadoOT;
  estadoNuevoDescripcion: string;
  fecha: string;
  usuarioId: number | null;
  usuarioNombre: string | null;
  motivo: string | null;
}

export interface OrdenTrabajo {
  id: number;
  codigo: string;
  ejercicio: number;
  numero: number;
  estado: EstadoOT;
  estadoDescripcion: string;
  /** A que estados puede saltar. El frontend pinta solo esos botones. */
  estadosPosibles: EstadoOT[];
  facturable: boolean;
  permiteEditarLineas: boolean;

  motoId: number;
  matricula: string;
  descripcionMoto: string;
  clienteId: number;
  clienteNombre: string;
  clienteTelefono: string | null;

  fechaEntrada: string;
  fechaEstimadaSalida: string | null;
  fechaRealSalida: string | null;
  kmEntrada: number;

  problemaReportado: string;
  diagnostico: string | null;
  tecnicoId: number | null;
  tecnicoNombre: string | null;

  /** Nulo para un técnico: el precio de la hora no es asunto del taller. */
  tarifaHora: number | null;
  fechaPresupuesto: string | null;
  fechaAprobacion: string | null;
  aprobadoPor: string | null;
  motivoRechazo: string | null;
  observaciones: string | null;

  /** Suma de las líneas a precio de tarifa, antes de descuentos. */
  importeBruto: number | null;
  /** Rebaja total aplicada al cliente. Cero si no hay descuentos. */
  totalDescuento: number | null;
  baseImponible: number | null;
  totalIva: number | null;
  total: number | null;
  /** Horas apuntadas. Son trabajo, no dinero: el técnico sí las ve. */
  horasManoDeObra: number;

  lineas: LineaOT[];
  historial: CambioEstado[];
}

export interface OrdenTrabajoResumen {
  id: number;
  codigo: string;
  estado: EstadoOT;
  estadoDescripcion: string;
  matricula: string;
  descripcionMoto: string;
  clienteNombre: string;
  tecnicoNombre: string | null;
  fechaEntrada: string;
  fechaEstimadaSalida: string | null;
  fechaRealSalida: string | null;
  problemaReportado: string;
}

export interface PiezaFaltante {
  piezaId: number;
  sku: string;
  descripcion: string;
  necesarias: number;
  disponibles: number;
  faltan: number;
}

export interface ResultadoConsumo {
  estado: EstadoOT;
  estadoDescripcion: string;
  completo: boolean;
  consumidas: number;
  faltantes: PiezaFaltante[];
  mensaje: string;
}
