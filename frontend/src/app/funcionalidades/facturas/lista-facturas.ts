import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { FacturaResumen, InformeVerificacion } from '../../nucleo/modelos/facturacion';
import { FacturasService } from '../../nucleo/servicios/facturas.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';

/**
 * Libro de facturas, con la exportación para la gestoría como acción principal.
 *
 * Es la pantalla que más se va a usar al cierre de cada trimestre, así que la
 * exportación está arriba y a la vista, no escondida en un menú.
 */
@Component({
  selector: 'app-lista-facturas',
  imports: [CommonModule, FormsModule, RouterLink, Cargando, Icono],
  templateUrl: './lista-facturas.html',
  styleUrl: './lista-facturas.scss',
})
export class ListaFacturas {
  private readonly facturas = inject(FacturasService);
  private readonly notificaciones = inject(NotificacionesService);

  protected readonly cargando = signal(true);
  protected readonly filas = signal<FacturaResumen[]>([]);
  protected readonly totalItems = signal(0);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);

  protected readonly desde = signal<string>('');
  protected readonly hasta = signal<string>('');
  protected readonly tipo = signal<string>('');

  protected readonly verificando = signal(false);
  protected readonly informe = signal<InformeVerificacion | null>(null);
  protected readonly exportando = signal<'csv' | 'json' | null>(null);

  /** Suma de lo facturado en el periodo mostrado. */
  protected readonly totalPeriodo = computed(() =>
    this.filas().reduce((suma, f) => suma + f.total, 0),
  );

  constructor() {
    this.cargar();
  }

  protected cargar(pagina = 0): void {
    this.cargando.set(true);
    this.facturas
      .buscar({
        pagina,
        desde: this.desde() || null,
        hasta: this.hasta() || null,
        tipo: this.tipo() || null,
      })
      .subscribe({
        next: (p) => {
          this.filas.set(p.contenido);
          this.totalItems.set(p.totalItems);
          this.pagina.set(p.pagina);
          this.totalPaginas.set(p.totalPaginas);
          this.cargando.set(false);
        },
        error: () => this.cargando.set(false),
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
        this.informe.set(informe);
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
