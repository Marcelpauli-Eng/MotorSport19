import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Reparto de un total en dos partes.
 *
 * <p>Una barra apilada de una sola fila, no un quesito. Con dos partes el
 * quesito no aporta nada y obliga a comparar ángulos, que es justo lo que peor
 * se le da a la vista; una barra se lee como una proporción de longitud.
 *
 * <p>Los dos tramos se separan con 2 px del color del fondo, no con un borde:
 * un borde sería tinta que no es dato.
 */
@Component({
  selector: 'app-grafica-reparto',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  styleUrl: './graficas.scss',
  template: `
    @if (total() === 0) {
      <p class="grafica__vacia">Sin facturación en este periodo.</p>
    } @else {
      <div class="reparto__pista">
        <span class="reparto__tramo reparto__tramo--s1" [style.width.%]="porcentaje1()"></span>
        <span class="reparto__tramo reparto__tramo--s2" [style.width.%]="100 - porcentaje1()"></span>
      </div>
      <div class="reparto__leyenda">
        <span class="reparto__parte">
          <span class="reparto__etiqueta">
            <span class="leyenda__marca leyenda__marca--s1"></span>
            {{ etiqueta1() }} · {{ porcentaje1() | number: '1.0-0' : 'es' }} %
          </span>
          <span class="reparto__importe">{{ valor1() | number: '1.2-2' : 'es' }} €</span>
        </span>
        <span class="reparto__parte" style="text-align: right">
          <span class="reparto__etiqueta" style="justify-content: flex-end">
            <span class="leyenda__marca leyenda__marca--s2"></span>
            {{ etiqueta2() }} · {{ 100 - porcentaje1() | number: '1.0-0' : 'es' }} %
          </span>
          <span class="reparto__importe">{{ valor2() | number: '1.2-2' : 'es' }} €</span>
        </span>
      </div>
    }
  `,
})
export class GraficaReparto {
  readonly etiqueta1 = input.required<string>();
  readonly valor1 = input.required<number>();
  readonly etiqueta2 = input.required<string>();
  readonly valor2 = input.required<number>();

  protected readonly total = computed(() => this.valor1() + this.valor2());

  protected readonly porcentaje1 = computed(() =>
    this.total() === 0 ? 0 : (this.valor1() / this.total()) * 100,
  );
}
