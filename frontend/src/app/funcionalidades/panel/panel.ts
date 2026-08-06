import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ColorEstadoPipe } from '../../compartido/estado-ot.pipe';
import { Icono } from '../../compartido/icono';
import { AlertaStock, EstadoOT, OrdenTrabajoResumen } from '../../nucleo/modelos/taller';
import { FacturaResumen } from '../../nucleo/modelos/facturacion';
import { FacturasService } from '../../nucleo/servicios/facturas.service';
import { InventarioService } from '../../nucleo/servicios/inventario.service';
import { OrdenesService } from '../../nucleo/servicios/ordenes.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { EstadisticasService } from '../../nucleo/servicios/estadisticas.service';
import { GraficaBarras, PuntoBarra } from '../../compartido/graficas/grafica-barras';
import { GraficaReparto } from '../../compartido/graficas/grafica-reparto';
import { InformeFacturacion } from '../../nucleo/modelos/estadisticas';

/**
 * Pantalla de inicio: lo que hay que mirar nada más abrir el programa.
 *
 * Se prioriza lo que reclama acción — órdenes en marcha y piezas por reponer —
 * sobre los totales bonitos.
 */
@Component({
  selector: 'app-panel',
  imports: [CommonModule, RouterLink, ColorEstadoPipe, Icono, GraficaBarras, GraficaReparto],
  templateUrl: './panel.html',
  styleUrl: './panel.scss',
})
export class Panel {
  private readonly ordenes = inject(OrdenesService);
  private readonly inventario = inject(InventarioService);
  private readonly facturas = inject(FacturasService);
  private readonly estadisticas = inject(EstadisticasService);
  protected readonly sesion = inject(SesionService);

  /** Facturar es cosa de mostrador y dirección: al técnico ni se le pide. */
  protected readonly veFacturacion = this.sesion.puede('ADMIN', 'MOSTRADOR');

  protected readonly ordenesAbiertas = signal<OrdenTrabajoResumen[]>([]);
  protected readonly alertas = signal<AlertaStock[]>([]);
  protected readonly ultimasFacturas = signal<FacturaResumen[]>([]);
  protected readonly totalAbiertas = signal(0);
  protected readonly informe = signal<InformeFacturacion | null>(null);

  constructor() {
    this.ordenes.buscar({ soloAbiertas: true, tamano: 50 }).subscribe((p) => {
      this.ordenesAbiertas.set(p.contenido);
      this.totalAbiertas.set(p.totalItems);
    });
    this.inventario.alertas().subscribe((a) => this.alertas.set(a));
    // Sin esta comprobación el panel de un técnico pediría facturas, recibiría
    // un 403 y le saltaría un aviso de permisos cada vez que abre el programa.
    if (this.veFacturacion) {
      this.facturas.buscar({ tamano: 5 }).subscribe((p) => this.ultimasFacturas.set(p.contenido));
      this.estadisticas.facturacion().subscribe((i) => this.informe.set(i));
    }
  }

  /** Fecha de hoy escrita como se lee: «miércoles, 6 de agosto». */
  protected readonly hoy = computed(() => {
    const texto = new Intl.DateTimeFormat('es-ES', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    }).format(new Date());
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  });

  /**
   * Las cuatro cifras de arriba.
   *
   * Se marcan como «atención» solo cuando hay algo que hacer: un cero en piezas
   * pendientes es una buena noticia y no debe pintarse de rojo.
   */
  protected readonly metricas = computed(() => [
    {
      valor: this.totalAbiertas(),
      texto: 'órdenes abiertas',
      ruta: '/ordenes',
      atencion: false,
    },
    {
      valor: this.porEstado('LISTA').length,
      texto: 'listas para entregar',
      ruta: '/ordenes',
      atencion: false,
    },
    {
      valor: this.porEstado('ESPERANDO_PIEZAS').length,
      texto: 'esperando piezas',
      ruta: '/ordenes',
      atencion: this.porEstado('ESPERANDO_PIEZAS').length > 0,
    },
    {
      valor: this.alertas().length,
      texto: 'piezas bajo mínimo',
      ruta: '/inventario',
      atencion: this.alertas().length > 0,
    },
  ]);

  /**
   * Facturación de los últimos seis meses.
   *
   * <p>Seis y no doce: en el panel la gráfica es pequeña y lo que interesa es
   * la tendencia reciente. El año entero está en Informes.
   */
  protected readonly serieFacturacion = computed<PuntoBarra[]>(() => {
    const meses = this.informe()?.meses ?? [];
    const hasta = this.informe()?.totales.mesesComputados ?? meses.length;
    return meses.slice(Math.max(0, hasta - 6), hasta).map((m) => ({
      etiqueta: m.nombreMes.slice(0, 3),
      valor: m.baseFacturada,
    }));
  });

  protected readonly serieOrdenes = computed<PuntoBarra[]>(() => {
    const meses = this.informe()?.meses ?? [];
    const hasta = this.informe()?.totales.mesesComputados ?? meses.length;
    return meses.slice(Math.max(0, hasta - 6), hasta).map((m) => ({
      etiqueta: m.nombreMes.slice(0, 3),
      valor: m.ordenesAbiertas,
    }));
  });

  /** «Nuria Sanz Belmonte» → «NS», para la ficha del tablero. */
  protected inicialesDe(nombre: string): string {
    const partes = nombre.trim().split(/\s+/).filter(Boolean);
    return (partes[0]?.[0] ?? '') + (partes[1]?.[0] ?? '');
  }

  /** Órdenes agrupadas por estado, para el tablero. */
  protected porEstado(estado: EstadoOT): OrdenTrabajoResumen[] {
    return this.ordenesAbiertas().filter((o) => o.estado === estado);
  }

  protected readonly columnas: { estado: EstadoOT; titulo: string }[] = [
    { estado: 'RECIBIDA', titulo: 'Recibidas' },
    { estado: 'EN_DIAGNOSTICO', titulo: 'En diagnóstico' },
    { estado: 'PRESUPUESTADA', titulo: 'Esperando respuesta' },
    { estado: 'APROBADA', titulo: 'Aprobadas' },
    { estado: 'EN_REPARACION', titulo: 'En reparación' },
    { estado: 'ESPERANDO_PIEZAS', titulo: 'Esperando piezas' },
    { estado: 'LISTA', titulo: 'Listas para entregar' },
  ];
}
