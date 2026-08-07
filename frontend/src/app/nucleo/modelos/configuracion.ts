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
  rol: Rol;
  rolDescripcion: string;
  activo: boolean;
  ultimoAcceso: string | null;
}

/** Lo mínimo para poner un nombre en el desplegable de técnicos. */
export interface Tecnico {
  id: number;
  nombreCompleto: string;
}
