import { Rol } from '../servicios/sesion.service';

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
  softwareNombre: string;
  softwareVersion: string;
  tiposIva: TipoIva[];
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
