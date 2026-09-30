import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CambioFichaje, FichajesService } from '../../nucleo/servicios/fichajes.service';

/**
 * Cómo estaba una jornada antes de cada cambio a mano.
 *
 * <p>La misma lista la ve dirección en Control de horas y el trabajador en Mis
 * horas: lo que se fichó y, debajo, cada cambio con lo de antes tachado, quién
 * lo hizo y por qué.
 */
@Component({
  selector: 'app-historial-jornada',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
  template: `
    @if (cambios().length) {
      <p class="titulo">Historial de horas</p>
      <ol class="historial">
        <li>
          <span class="cuando">Fichado</span>
          <span class="tabular">{{ tramo(cambios()[0].inicioAnterior, cambios()[0].finAnterior) }}</span>
        </li>
        @for (c of cambios(); track $index) {
          <li>
            <span class="cuando">{{ c.fecha | date: 'd MMM HH:mm' : undefined : 'es' }}</span>
            {{ c.usuarioNombre ?? 'Alguien' }} cambió
            <span class="tabular antes">{{ tramo(c.inicioAnterior, c.finAnterior) }}</span>
            →
            <span class="tabular nuevo">{{ tramo(c.inicioNuevo, c.finNuevo) }}</span>
            <span class="silenciado">· {{ c.motivo }}</span>
          </li>
        }
      </ol>
    }
  `,
  styles: [
    `
      .titulo { font-weight: 600; margin: 0 0 4px; }
      .historial { list-style: none; margin: 0 0 var(--e2); padding: 0; display: grid; gap: 6px; }
      .historial li { display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px; }
      .cuando { font-variant-numeric: tabular-nums; color: var(--gris-500, #6b7280); }
      .antes { text-decoration: line-through; color: var(--gris-500, #6b7280); }
      .nuevo { font-weight: 600; }
    `,
  ],
})
export class HistorialJornada {
  readonly cambios = input.required<CambioFichaje[]>();
  protected readonly tramo = FichajesService.tramo;
}
