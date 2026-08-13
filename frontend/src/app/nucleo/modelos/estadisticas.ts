/** Un mes del informe de facturación, con los derivados ya calculados. */
export interface ResumenMes {
  mes: number;
  nombreMes: string;
  baseFacturada: number;
  ivaRepercutido: number;
  totalFacturado: number;
  numeroFacturas: number;
  ingresoManoDeObra: number;
  ingresoPiezas: number;
  comprasMaterial: number;
  ivaSoportado: number;
  /** Repercutido − soportado. En negativo, sale a devolver. */
  ivaALiquidar: number;
  costeMaterialVendido: number;
  margenBruto: number;
  margenPorcentaje: number;
  ordenesAbiertas: number;
  diasMediosEnTaller: number;
}

export interface TotalesEjercicio {
  ejercicio: number;
  baseFacturada: number;
  ivaRepercutido: number;
  totalFacturado: number;
  numeroFacturas: number;
  ingresoManoDeObra: number;
  ingresoPiezas: number;
  comprasMaterial: number;
  ivaSoportado: number;
  ivaALiquidar: number;
  costeMaterialVendido: number;
  margenBruto: number;
  margenPorcentaje: number;
  ticketMedio: number;
  /** Variación de la base frente al mismo tramo del año anterior, o null. */
  variacionBase: number | null;
  /** Meses que entran en el acumulado: en el año en curso, hasta el actual. */
  mesesComputados: number;
}

/** Un mes de una de las dos columnas del reparto por IVA. */
export interface MesIva {
  anio: number;
  mes: number;
  nombreMes: string;
  /** «Mar 2026»: el año va dentro porque un periodo puede cruzar ejercicios. */
  etiqueta: string;
  baseFacturada: number;
  ivaRepercutido: number;
  totalFacturado: number;
  numeroFacturas: number;
  ingresoManoDeObra: number;
  ingresoPiezas: number;
  /** Coste del material que se fue en los trabajos facturados ese mes. */
  gastoMaterial: number;
  margenBruto: number;
  margenPorcentaje: number;
}

/** Una de las dos columnas: sus meses y su acumulado del periodo. */
export interface ColumnaIva {
  conIva: boolean;
  titulo: string;
  meses: MesIva[];
  baseFacturada: number;
  ivaRepercutido: number;
  totalFacturado: number;
  numeroFacturas: number;
  ingresoManoDeObra: number;
  ingresoPiezas: number;
  gastoMaterial: number;
  margenBruto: number;
  margenPorcentaje: number;
  ticketMedio: number;
  /** Lo que pesa esta columna sobre el total facturado en el periodo. */
  pesoPorcentaje: number;
}

export interface InformeIva {
  desde: string;
  hasta: string;
  conIva: ColumnaIva;
  sinIva: ColumnaIva;
}

export interface FilaReparto {
  nombre: string;
  importe: number;
  unidades: number;
}

export interface InformeFacturacion {
  ejercicio: number;
  ejerciciosDisponibles: number[];
  totales: TotalesEjercicio;
  meses: ResumenMes[];
  mejoresClientes: FilaReparto[];
  piezasMasUsadas: FilaReparto[];
}
