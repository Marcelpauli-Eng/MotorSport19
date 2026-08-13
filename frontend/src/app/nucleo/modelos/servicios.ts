/**
 * Servicios tipo: las plantillas de orden de trabajo.
 *
 * Una plantilla guarda QUÉ se hace y CUÁNTO (dos horas y media, un filtro,
 * tres litros de aceite), nunca a cuánto. El precio sale del catálogo y de la
 * tarifa de la OT en el momento de volcarla, así que aquí no hay ni un euro.
 */

export type TipoLineaServicio = 'MANO_DE_OBRA' | 'PIEZA';

export interface LineaServicioTipo {
  id: number;
  numeroLinea: number;
  tipo: TipoLineaServicio;
  /** Texto ya resuelto: el propio en mano de obra, el del catálogo en piezas. */
  descripcion: string;
  piezaId: number | null;
  piezaSku: string | null;
  cantidad: number;
}

export interface ServicioTipo {
  id: number;
  nombre: string;
  descripcion: string | null;
  activo: boolean;
  /** Suma de las horas de mano de obra. Lo calcula el servidor. */
  horasTotales: number;
  numeroDePiezas: number;
  lineas: LineaServicioTipo[];
}

/** Lo que se manda al guardar. Cada línea es o una pieza o mano de obra. */
export interface GuardarServicioTipo {
  nombre: string;
  descripcion: string | null;
  lineas: {
    descripcion: string | null;
    piezaId: number | null;
    cantidad: number;
  }[];
}
