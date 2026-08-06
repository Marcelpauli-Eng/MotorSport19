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
