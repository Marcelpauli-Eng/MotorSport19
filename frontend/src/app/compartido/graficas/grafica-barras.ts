import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

/** Un punto de la serie. `valor2` solo se usa en las gráficas de dos series. */
export interface PuntoBarra {
  etiqueta: string;
  valor: number;
  valor2?: number;
}

interface BarraPintada {
  x: number;
  ancho: number;
  etiqueta: string;
  valor: number;
  valor2: number;
  y1: number;
  alto1: number;
  y2: number;
  alto2: number;
  centro: number;
}

/**
 * Gráfica de columnas, con una o dos series.
 *
 * <p>SVG dibujado a mano y sin librería. No es por ahorrarse una dependencia:
 * es que una librería genérica trae su propia tipografía, sus colores y sus
 * tooltips, y habría que pelearse con ella para que se pareciese al resto del
 * programa. Aquí son cien líneas y encaja exactamente.
 *
 * <p>Decisiones que vienen de cómo se leen las gráficas, no del gusto:
 *
 * <ul>
 *   <li><b>Un solo eje.</b> Dos escalas en una gráfica es la forma más rápida de
 *       que alguien lea una relación que no existe. Si hay dos magnitudes
 *       distintas, van en dos gráficas.</li>
 *   <li><b>Las columnas no llenan su hueco</b> (24 px como máximo): el aire
 *       entre ellas es lo que deja contarlas de un vistazo.</li>
 *   <li><b>No se etiqueta cada valor.</b> Solo el mayor. Un número sobre cada
 *       columna es ruido que nadie lee; el resto lo dicen el eje y el tooltip.</li>
 * </ul>
 */
@Component({
  selector: 'app-grafica-barras',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './grafica-barras.html',
  styleUrl: './graficas.scss',
})
export class GraficaBarras {
  readonly datos = input.required<PuntoBarra[]>();
  /** Nombre de la primera serie. Con dos series se pinta la leyenda. */
  readonly serie1 = input('');
  readonly serie2 = input('');
  readonly unidad = input('€');
  /** Alto del área de dibujo, sin contar el eje de abajo. */
  readonly alto = input(180);

  protected readonly activa = signal<number | null>(null);

  private readonly ANCHO = 720;
  private readonly MARGEN_IZQ = 4;
  private readonly ALTO_EJE = 22;
  private readonly GROSOR_MAX = 24;
  /** Separación en color de fondo entre las dos columnas de un mismo mes. */
  private readonly HUECO = 2;

  protected readonly dosSeries = computed(() => this.serie2() !== '');

  protected get anchoTotal(): number {
    return this.ANCHO;
  }

  protected get altoTotal(): number {
    return this.alto() + this.ALTO_EJE;
  }

  /** Techo del eje, redondeado hacia arriba a una cifra limpia. */
  protected readonly techo = computed(() => {
    const maximo = Math.max(
      0,
      ...this.datos().map((d) => Math.max(d.valor, d.valor2 ?? 0)),
    );
    if (maximo === 0) return 1;

    const magnitud = Math.pow(10, Math.floor(Math.log10(maximo)));
    const escalones = [1, 2, 2.5, 5, 10];
    for (const e of escalones) {
      if (maximo <= magnitud * e) return magnitud * e;
    }
    return magnitud * 10;
  });

  /** Tres líneas de referencia: base, mitad y techo. Más serían reja. */
  protected readonly guias = computed(() =>
    [0, 0.5, 1].map((f) => ({
      y: this.alto() - f * this.alto(),
      valor: this.techo() * f,
    })),
  );

  protected readonly barras = computed<BarraPintada[]>(() => {
    const datos = this.datos();
    if (!datos.length) return [];

    const banda = (this.ANCHO - this.MARGEN_IZQ) / datos.length;
    const dos = this.dosSeries();
    const grosor = Math.min(this.GROSOR_MAX, banda * (dos ? 0.34 : 0.56));
    const anchoGrupo = dos ? grosor * 2 + this.HUECO : grosor;

    return datos.map((d, i) => {
      const inicio = this.MARGEN_IZQ + banda * i + (banda - anchoGrupo) / 2;
      const alto1 = this.aAlto(d.valor);
      const alto2 = this.aAlto(d.valor2 ?? 0);

      return {
        x: inicio,
        ancho: grosor,
        etiqueta: d.etiqueta,
        valor: d.valor,
        valor2: d.valor2 ?? 0,
        y1: this.alto() - alto1,
        alto1,
        y2: this.alto() - alto2,
        alto2,
        centro: this.MARGEN_IZQ + banda * i + banda / 2,
      };
    });
  });

  /** Índice del valor más alto: es el único que lleva número encima. */
  protected readonly indiceMaximo = computed(() => {
    const datos = this.datos();
    let mejor = -1;
    let mayor = 0;
    datos.forEach((d, i) => {
      const v = Math.max(d.valor, d.valor2 ?? 0);
      if (v > mayor) {
        mayor = v;
        mejor = i;
      }
    });
    return mayor > 0 ? mejor : -1;
  });

  protected readonly hayDatos = computed(() =>
    this.datos().some((d) => d.valor !== 0 || (d.valor2 ?? 0) !== 0),
  );

  protected anchoGrupo(): number {
    const banda = (this.ANCHO - this.MARGEN_IZQ) / Math.max(1, this.datos().length);
    return banda;
  }

  protected inicioBanda(i: number): number {
    return this.MARGEN_IZQ + this.anchoGrupo() * i;
  }

  private aAlto(valor: number): number {
    if (valor <= 0) return 0;
    // Mínimo de 2 px: un importe pequeño pero real no debe desaparecer.
    return Math.max(2, (valor / this.techo()) * this.alto());
  }
}
