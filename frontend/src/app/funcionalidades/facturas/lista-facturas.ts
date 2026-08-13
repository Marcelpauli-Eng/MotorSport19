import { CommonModule } from '@angular/common';
import { Component, WritableSignal, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { InformeIva } from '../../nucleo/modelos/estadisticas';
import { FacturaResumen, InformeVerificacion } from '../../nucleo/modelos/facturacion';
import { EstadisticasService } from '../../nucleo/servicios/estadisticas.service';
import { FacturasService } from '../../nucleo/servicios/facturas.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { ColumnaIvaComponente } from './columna-iva';

/** Listado y paginación de una de las dos columnas. */
interface EstadoColumna {
  filas: WritableSignal<FacturaResumen[]>;
  cargando: WritableSignal<boolean>;
  pagina: WritableSignal<number>;
  totalPaginas: WritableSignal<number>;
  totalItems: WritableSignal<number>;
}

function estadoColumna(): EstadoColumna {
  return {
    filas: signal<FacturaResumen[]>([]),
    cargando: signal(true),
    pagina: signal(0),
    totalPaginas: signal(0),
    totalItems: signal(0),
  };
}

/**
 * Libro de facturas, partido en dos por el IVA que llevan.
 *
 * <p>La separación no es un capricho de presentación: el trabajo que se factura
 * al 0 % y el que lleva IVA son dos negocios distintos a efectos de cuentas, y
 * mezclados en una sola tabla no hay forma de ver cuánto pesa cada uno ni qué
 * margen deja. Cada columna trae sus propias cifras del periodo, su mes a mes y
 * sus facturas.
 *
 * <p>Un mismo filtro de fechas manda sobre las dos columnas y sobre las
 * estadísticas. Si cada bloque tuviera su periodo, dos cifras de la misma
 * pantalla estarían hablando de meses distintos sin decirlo.
 *
 * <p>La exportación para la gestoría sigue arriba y sobre el periodo completo:
 * el libro que se entrega no se parte por nada.
 */
@Component({
  selector: 'app-lista-facturas',
  imports: [CommonModule, FormsModule, Cargando, Icono, ColumnaIvaComponente],
  templateUrl: './lista-facturas.html',
  styleUrl: './lista-facturas.scss',
})
export class ListaFacturas {
  private readonly facturas = inject(FacturasService);
  private readonly estadisticas = inject(EstadisticasService);
  private readonly notificaciones = inject(NotificacionesService);

  protected readonly desde = signal<string>('');
  protected readonly hasta = signal<string>('');
  protected readonly tipo = signal<string>('');

  protected readonly informe = signal<InformeIva | null>(null);
  protected readonly cargandoInforme = signal(true);

  protected readonly conIva = estadoColumna();
  protected readonly sinIva = estadoColumna();

  /**
   * Facturas marcadas para la descarga agrupada.
   *
   * <p>Vive aquí y no dentro de cada columna a propósito: así se pueden mezclar
   * facturas de los dos regímenes en el mismo ZIP, y la selección aguanta el
   * cambio de página, que es justo cuando se pierde en otros programas.
   */
  protected readonly seleccion = signal<ReadonlySet<number>>(new Set());
  protected readonly descargandoZip = signal(false);

  protected readonly verificando = signal(false);
  protected readonly informeVerificacion = signal<InformeVerificacion | null>(null);
  protected readonly exportando = signal<'csv' | 'json' | null>(null);

  /** Lo facturado en el periodo, las dos columnas juntas. */
  protected readonly totalPeriodo = computed(() => {
    const i = this.informe();
    return (i?.conIva?.totalFacturado ?? 0) + (i?.sinIva?.totalFacturado ?? 0);
  });

  protected readonly seleccionadas = computed(() => this.seleccion().size);

  protected readonly totalFacturas = computed(() => {
    const i = this.informe();
    return (i?.conIva?.numeroFacturas ?? 0) + (i?.sinIva?.numeroFacturas ?? 0);
  });

  constructor() {
    this.cargar();
  }

  /** Recarga las estadísticas y las dos columnas con el filtro actual. */
  protected cargar(): void {
    // Al cambiar de periodo la selección se vacía: quedarían marcadas facturas
    // que ya no están en pantalla y el ZIP traería cosas que no se ven.
    this.seleccion.set(new Set());
    this.cargarInforme();
    this.cargarColumna(true, 0);
    this.cargarColumna(false, 0);
  }

  protected alternarSeleccion(id: number): void {
    this.seleccion.update((actual) => {
      const nueva = new Set(actual);
      if (!nueva.delete(id)) nueva.add(id);
      return nueva;
    });
  }

  /** Marca o desmarca de golpe las facturas visibles en una de las columnas. */
  protected alternarPagina(conIva: boolean, marcar: boolean): void {
    const visibles = (conIva ? this.conIva : this.sinIva).filas();
    this.seleccion.update((actual) => {
      const nueva = new Set(actual);
      for (const f of visibles) {
        if (marcar) nueva.add(f.id);
        else nueva.delete(f.id);
      }
      return nueva;
    });
  }

  protected limpiarSeleccion(): void {
    this.seleccion.set(new Set());
  }

  /** Descarga en un ZIP los PDF de las facturas marcadas. */
  protected descargarSeleccion(): void {
    const ids = [...this.seleccion()];
    if (!ids.length || this.descargandoZip()) return;

    this.descargandoZip.set(true);
    this.facturas.descargarPdfsEnZip(ids).subscribe({
      next: (blob) => {
        this.descargar(blob, `facturas-${ids.length}.zip`);
        this.notificaciones.exito(
          ids.length === 1
            ? 'Factura descargada.'
            : `${ids.length} facturas descargadas en un ZIP.`,
        );
        this.descargandoZip.set(false);
      },
      error: () => this.descargandoZip.set(false),
    });
  }

  private cargarInforme(): void {
    this.cargandoInforme.set(true);
    this.estadisticas.porIva(this.desde() || null, this.hasta() || null).subscribe({
      next: (i) => {
        this.informe.set(i);
        this.cargandoInforme.set(false);
      },
      error: () => this.cargandoInforme.set(false),
    });
  }

  protected cargarColumna(conIva: boolean, pagina: number): void {
    const estado = conIva ? this.conIva : this.sinIva;
    estado.cargando.set(true);

    this.facturas
      .buscar({
        pagina,
        tamano: 10,
        desde: this.desde() || null,
        hasta: this.hasta() || null,
        tipo: this.tipo() || null,
        conIva,
      })
      .subscribe({
        next: (p) => {
          estado.filas.set(p.contenido);
          estado.pagina.set(p.pagina);
          estado.totalPaginas.set(p.totalPaginas);
          estado.totalItems.set(p.totalItems);
          estado.cargando.set(false);
        },
        error: () => estado.cargando.set(false),
      });
  }

  protected limpiarFiltros(): void {
    this.desde.set('');
    this.hasta.set('');
    this.tipo.set('');
    this.cargar();
  }

  /** Atajo habitual: el trimestre natural que se acaba de cerrar. */
  protected trimestreAnterior(): void {
    const hoy = new Date();
    const trimestreActual = Math.floor(hoy.getMonth() / 3);
    const anio = trimestreActual === 0 ? hoy.getFullYear() - 1 : hoy.getFullYear();
    const trimestre = trimestreActual === 0 ? 3 : trimestreActual - 1;

    const inicio = new Date(anio, trimestre * 3, 1);
    const fin = new Date(anio, trimestre * 3 + 3, 0);

    this.desde.set(this.aIso(inicio));
    this.hasta.set(this.aIso(fin));
    this.cargar();
  }

  protected anioActual(): void {
    const anio = new Date().getFullYear();
    this.desde.set(`${anio}-01-01`);
    this.hasta.set(`${anio}-12-31`);
    this.cargar();
  }

  /**
   * Descarga el libro registro.
   *
   * El JSON incluye las cadenas canónicas de las huellas, así que la gestoría
   * puede comprobar la integridad del libro sin este programa.
   */
  protected exportar(formato: 'csv' | 'json'): void {
    this.exportando.set(formato);
    this.facturas.exportar(formato, this.desde() || null, this.hasta() || null).subscribe({
      next: (blob) => {
        this.descargar(blob, this.nombreFichero(formato));
        this.notificaciones.exito(`Libro de facturas exportado en ${formato.toUpperCase()}.`);
        this.exportando.set(null);
      },
      error: () => this.exportando.set(null),
    });
  }

  protected verificarCadena(): void {
    this.verificando.set(true);
    this.facturas.verificarCadena().subscribe({
      next: (informe) => {
        this.informeVerificacion.set(informe);
        this.verificando.set(false);
        if (informe.integra) {
          this.notificaciones.exito(informe.resumen);
        } else {
          this.notificaciones.error(informe.resumen);
        }
      },
      error: () => this.verificando.set(false),
    });
  }

  private nombreFichero(formato: string): string {
    const rango = this.desde() || this.hasta() ? `-${this.desde() || 'inicio'}_${this.hasta() || 'hoy'}` : '';
    return `libro-facturas${rango}.${formato}`;
  }

  private descargar(blob: Blob, nombre: string): void {
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre;
    enlace.click();
    URL.revokeObjectURL(url);
  }

  private aIso(fecha: Date): string {
    return fecha.toISOString().slice(0, 10);
  }

  /** Abre el PDF de una factura. Va por HttpClient para que lleve el token. */
  protected verPdf(id: number): void {
    this.facturas.abrirPdf(id);
  }
}
