import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Icono } from './icono';

/**
 * Ventana modal para los formularios de alta y edición.
 *
 * <p>Se usa una modal y no una pantalla aparte porque todos estos formularios
 * son cortos y salen de un contexto: «nuevo cliente» desde el listado de
 * clientes, «abrir orden» desde la ficha de una moto. Con una pantalla nueva se
 * pierde el sitio donde estabas y hay que volver a buscarlo.
 *
 * <p>Se cierra con Escape y pulsando fuera, pero nunca al pulsar dentro: en una
 * tablet es fácil rozar el fondo con la mano mientras se escribe, y perder un
 * formulario a medias por eso enfada con razón.
 */
@Component({
  selector: 'app-dialogo',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icono],
  template: `
    <div class="velo-dialogo" (click)="cerrar.emit()">
      <div
        class="dialogo"
        role="dialog"
        aria-modal="true"
        (click)="$event.stopPropagation()"
        (keydown.escape)="cerrar.emit()"
      >
        <header class="dialogo__cabecera">
          <div>
            <h2>{{ titulo() }}</h2>
            @if (subtitulo()) {
              <p class="dialogo__sub">{{ subtitulo() }}</p>
            }
          </div>
          <button type="button" class="dialogo__cerrar" (click)="cerrar.emit()" aria-label="Cerrar">
            <app-icono nombre="cerrar" [tamano]="18" />
          </button>
        </header>

        <div class="dialogo__cuerpo">
          <ng-content />
        </div>

        <footer class="dialogo__pie">
          <ng-content select="[pie]" />
        </footer>
      </div>
    </div>
  `,
  styleUrl: './dialogo.scss',
})
export class Dialogo {
  readonly titulo = input.required<string>();
  readonly subtitulo = input('');
  readonly cerrar = output<void>();
}
