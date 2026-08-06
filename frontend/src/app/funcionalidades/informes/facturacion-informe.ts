import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Cargando } from '../../compartido/cargando';
import { GraficaBarras, PuntoBarra } from '../../compartido/graficas/grafica-barras';
import { GraficaRanking } from '../../compartido/graficas/grafica-ranking';
import { GraficaReparto } from '../../compartido/graficas/grafica-reparto';
import { Icono } from '../../compartido/icono';
import { InformeFacturacion } from '../../nucleo/modelos/estadisticas';
import { EstadisticasService } from '../../nucleo/servicios/estadisticas.service';

/**
 * Informe económico del ejercicio.
 *
 * <p>Responde a las cuatro preguntas que se hace quien lleva un taller:
 * cuánto he facturado, cuánto me llevo de verdad, cuánto IVA tengo que
 * ingresar, y de qué vivo —del taller o de vender piezas—.
 *
 * <p>Todas las cifras salen de las facturas emitidas y de los movimientos de
 * almacén. No hay ningún dato metido a mano ni ninguna tabla de resumen que
 * pueda quedarse desfasada.
 */
@Component({
  selector: 'app-facturacion-informe',
  imports: [CommonModule, FormsModule, Cargando, Icono, GraficaBarras, GraficaRanking, GraficaReparto],
  templateUrl: './facturacion-informe.html',
  styleUrl: './facturacion-informe.scss',
})
export class FacturacionInforme {
  private readonly servicio = inject(EstadisticasService);

  protected readonly cargando = signal(true);
  protected readonly informe = signal<InformeFacturacion | null>(null);
  protected readonly ejercicio = signal(new Date().getFullYear());

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.servicio.facturacion(this.ejercicio()).subscribe({
      next: (i) => {
        this.informe.set(i);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  protected cambiarEjercicio(anio: string | number): void {
    this.ejercicio.set(Number(anio));
    this.cargar();
  }

  /** Facturación mes a mes, para la gráfica principal. */
  protected readonly serieFacturacion = computed<PuntoBarra[]>(() =>
    (this.informe()?.meses ?? []).map((m) => ({
      etiqueta: m.nombreMes.slice(0, 3),
      valor: m.baseFacturada,
    })),
  );

  /** IVA repercutido frente a soportado: la diferencia es lo que se ingresa. */
  protected readonly serieIva = computed<PuntoBarra[]>(() =>
    (this.informe()?.meses ?? []).map((m) => ({
      etiqueta: m.nombreMes.slice(0, 3),
      valor: m.ivaRepercutido,
      valor2: m.ivaSoportado,
    })),
  );

  /** Lo que entra frente a lo que cuesta el material. */
  protected readonly serieMargen = computed<PuntoBarra[]>(() =>
    (this.informe()?.meses ?? []).map((m) => ({
      etiqueta: m.nombreMes.slice(0, 3),
      valor: m.margenBruto,
      valor2: m.costeMaterialVendido,
    })),
  );

  /** Órdenes que entran cada mes: la carga de trabajo del taller. */
  protected readonly serieOrdenes = computed<PuntoBarra[]>(() =>
    (this.informe()?.meses ?? []).map((m) => ({
      etiqueta: m.nombreMes.slice(0, 3),
      valor: m.ordenesAbiertas,
    })),
  );

  /** Solo los meses con algo que enseñar: la tabla no lista doce ceros. */
  protected readonly mesesConActividad = computed(() =>
    (this.informe()?.meses ?? []).filter(
      (m) => m.numeroFacturas > 0 || m.comprasMaterial !== 0 || m.ordenesAbiertas > 0,
    ),
  );

  /**
   * Los cuatro trimestres, que es como se presenta el IVA.
   *
   * <p>El taller no liquida por meses: presenta el modelo 303 cada trimestre.
   * Enseñarlo mes a mes obligaría a sumar de tres en tres mentalmente.
   */
  protected readonly trimestres = computed(() => {
    const meses = this.informe()?.meses ?? [];
    return [0, 1, 2, 3].map((t) => {
      const tramo = meses.slice(t * 3, t * 3 + 3);
      const repercutido = tramo.reduce((a, m) => a + m.ivaRepercutido, 0);
      const soportado = tramo.reduce((a, m) => a + m.ivaSoportado, 0);
      return {
        nombre: `${t + 1}T`,
        rango: `${tramo[0]?.nombreMes ?? ''} – ${tramo[2]?.nombreMes ?? ''}`,
        repercutido,
        soportado,
        aLiquidar: repercutido - soportado,
        facturas: tramo.reduce((a, m) => a + m.numeroFacturas, 0),
      };
    });
  });

  protected readonly ejercicioEnCurso = computed(
    () => this.informe()?.ejercicio === new Date().getFullYear(),
  );

  protected descargarCsv(): void {
    const inf = this.informe();
    if (!inf) return;

    const cabecera = [
      'Mes', 'Facturas', 'Base facturada', 'IVA repercutido', 'Total facturado',
      'Mano de obra', 'Piezas', 'Compras de material', 'IVA soportado',
      'IVA a liquidar', 'Coste material vendido', 'Margen bruto', 'Margen %',
      'Ordenes', 'Dias medios en taller',
    ];

    // Punto y coma y coma decimal: es lo que espera Excel en español.
    const numero = (n: number) => n.toFixed(2).replace('.', ',');
    const filas = inf.meses.map((m) => [
      m.nombreMes, m.numeroFacturas, numero(m.baseFacturada), numero(m.ivaRepercutido),
      numero(m.totalFacturado), numero(m.ingresoManoDeObra), numero(m.ingresoPiezas),
      numero(m.comprasMaterial), numero(m.ivaSoportado), numero(m.ivaALiquidar),
      numero(m.costeMaterialVendido), numero(m.margenBruto), numero(m.margenPorcentaje),
      m.ordenesAbiertas, numero(m.diasMediosEnTaller),
    ].join(';'));

    // BOM para que Excel reconozca el UTF-8 y no destroce las tildes.
    const csv = '﻿' + [cabecera.join(';'), ...filas].join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = `motorsport19-facturacion-${inf.ejercicio}.csv`;
    enlace.click();
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
  }
}
