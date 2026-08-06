import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface FilaRanking {
  nombre: string;
  importe: number;
  unidades: number;
}

/**
 * Ranking en barras horizontales.
 *
 * <p>Horizontal y no vertical porque lo que se compara son nombres largos
 * («Talleres y Flotas Delta S.L.»): en columnas habría que girar el texto o
 * cortarlo, y ninguna de las dos cosas se lee.
 *
 * <p>Cada fila lleva su importe escrito. Aquí sí van todos los valores —son
 * cinco filas, no doce— y así la barra sirve para comparar de un vistazo
 * mientras la cifra da el dato exacto.
 */
@Component({
  selector: 'app-grafica-ranking',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  styleUrl: './graficas.scss',
  template: `
    @if (!filas().length) {
      <p class="grafica__vacia">{{ vacio() }}</p>
    } @else {
      <div class="ranking">
        @for (f of filas(); track f.nombre) {
          <div class="ranking__fila">
            <span class="ranking__nombre" [title]="f.nombre">{{ f.nombre }}</span>
            <span class="ranking__valor">
              @if (mostrarUnidades()) {
                {{ f.unidades | number: '1.0-0' : 'es' }} ud
              } @else {
                {{ f.importe | number: '1.2-2' : 'es' }} €
              }
            </span>
            <span class="ranking__pista">
              <span class="ranking__barra" [style.width.%]="proporcion(f)"></span>
            </span>
          </div>
        }
      </div>
    }
  `,
})
export class GraficaRanking {
  readonly filas = input.required<FilaRanking[]>();
  /** Con `true` la barra y la cifra representan unidades en vez de importe. */
  readonly mostrarUnidades = input(false);
  readonly vacio = input('Todavía no hay datos.');

  private readonly mayor = computed(() =>
    Math.max(1, ...this.filas().map((f) => this.magnitud(f))),
  );

  protected proporcion(f: FilaRanking): number {
    // Mínimo visible: una fila con valor real no debe quedarse en una raya.
    return Math.max(3, (this.magnitud(f) / this.mayor()) * 100);
  }

  private magnitud(f: FilaRanking): number {
    return Math.abs(this.mostrarUnidades() ? f.unidades : f.importe);
  }
}
