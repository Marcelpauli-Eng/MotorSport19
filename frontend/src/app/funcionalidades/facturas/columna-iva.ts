import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { ColumnaIva } from '../../nucleo/modelos/estadisticas';
import { FacturaResumen } from '../../nucleo/modelos/facturacion';

/**
 * Una de las dos mitades del libro de facturas: las que llevan IVA o las
 * emitidas al 0 %.
 *
 * <p>Es un componente y no dos bloques repetidos en la página porque las dos
 * columnas tienen que enseñar exactamente las mismas cifras en el mismo orden.
 * En cuanto se duplica el HTML, una de las dos se queda atrás en el primer
 * cambio y dejan de poder compararse de un vistazo, que es para lo único que
 * sirve ponerlas al lado.
 *
 * <p>El mes a mes se pinta entero, incluidos los meses a cero: un mes que
 * desaparece de una columna y no de la otra desalinea la comparación.
 */
@Component({
  selector: 'app-columna-iva',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, Cargando, Icono],
  templateUrl: './columna-iva.html',
  styleUrl: './columna-iva.scss',
})
export class ColumnaIvaComponente {
  readonly resumen = input<ColumnaIva | null>(null);
  readonly facturas = input<FacturaResumen[]>([]);
  readonly cargando = input(false);
  readonly pagina = input(0);
  readonly totalPaginas = input(0);
  readonly totalItems = input(0);

  /** Facturas marcadas para la descarga agrupada, de las dos columnas. */
  readonly seleccionadas = input<ReadonlySet<number>>(new Set());

  readonly irAPagina = output<number>();
  readonly abrirPdf = output<number>();
  readonly alternarSeleccion = output<number>();
  /** Marca o desmarca de golpe las facturas visibles en esta página. */
  readonly alternarPagina = output<boolean>();

  /** Con IVA a la izquierda, 0 % a la derecha: cambia el color y el texto. */
  protected readonly conIva = computed(() => this.resumen()?.conIva ?? true);

  protected readonly explicacion = computed(() =>
    this.conIva()
      ? 'Facturas con cuota de IVA repercutido.'
      : 'Facturas emitidas al 0 %: no llevan cuota de IVA.',
  );

  /** Meses del periodo, del más reciente al más antiguo. */
  protected readonly meses = computed(() => [...(this.resumen()?.meses ?? [])].reverse());

  protected readonly hayMovimiento = computed(() => (this.resumen()?.numeroFacturas ?? 0) > 0);

  protected estaMarcada(id: number): boolean {
    return this.seleccionadas().has(id);
  }

  /** La casilla de la cabecera solo sale marcada si lo están todas las de la página. */
  protected readonly paginaEntera = computed(() => {
    const filas = this.facturas();
    return filas.length > 0 && filas.every((f) => this.seleccionadas().has(f.id));
  });
}
