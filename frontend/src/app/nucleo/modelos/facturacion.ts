export type TipoFactura = 'ORDINARIA' | 'RECTIFICATIVA';
export type TipoRectificativa = 'POR_SUSTITUCION' | 'POR_DIFERENCIAS';

export type TipoEventoFactura =
  | 'EMISION'
  | 'RECTIFICACION'
  | 'GENERACION_PDF'
  | 'EXPORTACION'
  | 'VERIFICACION_CADENA'
  | 'CONSULTA'
  | 'INCIDENCIA';

export interface DatosFiscales {
  nombre: string;
  nif: string;
  direccion: string;
  codigoPostal: string;
  ciudad: string;
  provincia: string;
  pais: string;
}

export interface LineaFactura {
  numeroLinea: number;
  tipo: string;
  descripcion: string;
  piezaSku: string | null;
  cantidad: number;
  precioUnitario: number;
  descuentoPct: number;
  tipoIva: string;
  porcentajeIva: number;
  /** Lo que costaría a precio de tarifa, sin descuento. */
  importeBruto: number;
  /** Rebaja aplicada en esta línea, en euros. */
  importeDescuento: number;
  baseImponible: number;
  cuotaIva: number;
  total: number;
}

export interface DesgloseIva {
  tipoIva: string;
  porcentajeIva: number;
  baseImponible: number;
  cuotaIva: number;
}

export interface Factura {
  id: number;
  numeroCompleto: string;
  serieCodigo: string;
  ejercicio: number;
  numero: number;
  tipo: TipoFactura;
  tipoDescripcion: string;

  ordenTrabajoId: number | null;
  codigoOt: string | null;
  facturaRectificadaId: number | null;
  facturaRectificadaNumero: string | null;
  tipoRectificativa: TipoRectificativa | null;
  motivoRectificacion: string | null;

  fechaEmision: string;
  fechaOperacion: string;
  timestampEmision: string;

  emisor: DatosFiscales;
  receptorId: number | null;
  receptor: DatosFiscales;

  matricula: string | null;
  descripcionVehiculo: string | null;

  baseImponible: number;
  totalIva: number;
  total: number;

  numeroRegistro: number;
  huellaAnterior: string;
  huella: string;
  cadenaHuella: string;
  algoritmoHuella: string;
  /** Recalculada por el servidor al leer la factura. */
  huellaVerificada: boolean;
  qrContenido: string | null;

  softwareNombre: string;
  softwareVersion: string;

  lineas: LineaFactura[];
  desgloseIva: DesgloseIva[];
}

export interface FacturaResumen {
  id: number;
  numeroRegistro: number;
  numeroCompleto: string;
  tipo: TipoFactura;
  fechaEmision: string;
  receptorNombre: string;
  receptorNif: string;
  baseImponible: number;
  totalIva: number;
  total: number;
  codigoOt: string | null;
  matricula: string | null;
  rectificaA: string | null;
  huella: string;
}

export interface SerieFactura {
  id: number;
  codigo: string;
  ejercicio: number;
  descripcion: string;
  tipo: TipoFactura;
  ultimoNumero: number;
  proximoNumero: number;
  activa: boolean;
}

export interface AnomaliaCadena {
  numeroRegistro: number;
  numeroFactura: string;
  tipo: string;
  detalle: string;
}

export interface InformeVerificacion {
  momento: string;
  facturasVerificadas: number;
  anomalias: AnomaliaCadena[];
  primeraHuella: string | null;
  ultimaHuella: string | null;
  integra: boolean;
  resumen: string;
}

export interface EventoFactura {
  id: number;
  facturaId: number | null;
  numeroFactura: string | null;
  tipoEvento: TipoEventoFactura;
  tipoDescripcion: string;
  fecha: string;
  usuarioNombre: string | null;
  descripcion: string;
  detalle: string | null;
}
