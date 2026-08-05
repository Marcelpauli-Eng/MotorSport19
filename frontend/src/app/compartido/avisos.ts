import { Component, inject } from '@angular/core';
import { NotificacionesService } from '../nucleo/servicios/notificaciones.service';

/**
 * Pila de avisos en la esquina inferior derecha.
 *
 * Los errores se quedan hasta que alguien los cierra: en un mostrador, un aviso
 * que desaparece solo es un aviso que nadie ha leído.
 */
@Component({
  selector: 'app-avisos',
  template: `
    <div class="avisos" role="status" aria-live="polite">
      @for (aviso of notificaciones.avisos(); track aviso.id) {
        <div class="aviso aviso--{{ aviso.tipo }}">
          <div class="aviso__cuerpo">
            <p class="aviso__mensaje">{{ aviso.mensaje }}</p>
            @if (aviso.detalles.length) {
              <ul class="aviso__detalles">
                @for (detalle of aviso.detalles; track detalle) {
                  <li>{{ detalle }}</li>
                }
              </ul>
            }
          </div>
          <button
            type="button"
            class="aviso__cerrar"
            (click)="notificaciones.cerrar(aviso.id)"
            aria-label="Cerrar aviso"
          >
            ×
          </button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .avisos {
        position: fixed;
        right: 1rem;
        bottom: 1rem;
        z-index: 1000;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        max-width: min(420px, calc(100vw - 2rem));
      }

      .aviso {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 0.8rem 1rem;
        border-radius: var(--radio);
        box-shadow: var(--sombra-alta);
        background: var(--blanco);
        border-left: 4px solid var(--gris-300);
      }

      .aviso--exito { border-left-color: var(--verde); }
      .aviso--error { border-left-color: var(--rojo); }
      .aviso--info  { border-left-color: var(--azul); }

      .aviso__cuerpo { flex: 1; min-width: 0; }
      .aviso__mensaje { margin: 0; font-size: 0.9rem; }

      .aviso__detalles {
        margin: 0.4rem 0 0;
        padding-left: 1.1rem;
        font-size: 0.82rem;
        color: var(--gris-700);
      }

      .aviso__cerrar {
        flex-shrink: 0;
        width: 28px;
        height: 28px;
        font-size: 1.2rem;
        line-height: 1;
        color: var(--gris-500);
        background: none;
        border: none;
        border-radius: 4px;
        cursor: pointer;
      }

      .aviso__cerrar:hover { background: var(--gris-100); }
    `,
  ],
})
export class Avisos {
  protected readonly notificaciones = inject(NotificacionesService);
}
