import { Component, input } from '@angular/core';

/** Indicador de carga con un mensaje, para que se sepa qué se está esperando. */
@Component({
  selector: 'app-cargando',
  template: `
    <div class="cargando">
      <div class="cargando__giro" aria-hidden="true"></div>
      <span>{{ mensaje() }}</span>
    </div>
  `,
  styles: [
    `
      .cargando {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.75rem;
        padding: 2.5rem 1rem;
        color: var(--gris-500);
      }

      .cargando__giro {
        width: 20px;
        height: 20px;
        border: 2px solid var(--gris-300);
        border-top-color: var(--azul);
        border-radius: 50%;
        animation: girar 0.7s linear infinite;
      }

      @keyframes girar {
        to {
          transform: rotate(360deg);
        }
      }

      /* Respeta a quien tiene desactivadas las animaciones. */
      @media (prefers-reduced-motion: reduce) {
        .cargando__giro {
          animation: none;
        }
      }
    `,
  ],
})
export class Cargando {
  readonly mensaje = input('Cargando…');
}
