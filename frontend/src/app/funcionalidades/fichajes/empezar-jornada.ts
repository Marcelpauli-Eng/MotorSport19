import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Icono } from '../../compartido/icono';
import { FichajesService } from '../../nucleo/servicios/fichajes.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { TiempoRealService } from '../../nucleo/servicios/tiempo-real.service';

/**
 * La puerta: sin fichar no se entra al programa.
 *
 * <p>Ocupa la pantalla entera a propósito. No es un aviso que se pueda ignorar
 * ni un botón más en una barra: es el primer gesto del día, y hasta que no se
 * hace no hay nada más que hacer aquí.
 *
 * <p>Quien no ficha —la dirección— no ve esto nunca.
 */
@Component({
  selector: 'app-empezar-jornada',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, Icono],
  template: `
    <div class="puerta">
      <div class="puerta__tarjeta">
        <app-icono class="puerta__icono" nombre="reloj" [tamano]="34" />

        <div class="puerta__texto">
          <h1>{{ saludo }}{{ nombre() ? ', ' + nombre() : '' }}</h1>
          <p>Para empezar a trabajar, ficha la entrada.</p>
        </div>

        @if (error(); as e) {
          <p class="puerta__error" role="alert">{{ e }}</p>
        }

        <button
          type="button"
          class="boton boton--principal puerta__boton"
          [disabled]="fichando()"
          (click)="empezar()"
        >
          {{ fichando() ? 'Fichando…' : 'Empezar jornada' }}
        </button>

        <p class="puerta__pie">
          Queda registrada la hora de ahora. Al terminar, ficha la salida desde el
          botón de arriba.
        </p>
      </div>

      <button type="button" class="puerta__salir" (click)="salir()">Cerrar sesión</button>
    </div>
  `,
  styles: [
    `
      .puerta {
        position: fixed;
        inset: 0;
        z-index: 60;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: var(--e4);
        padding: var(--e4);
        background: var(--fondo, #f4f5f7);
      }
      .puerta__tarjeta {
        width: 100%;
        max-width: 420px;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--e3);
        text-align: center;
        padding: var(--e5) var(--e4);
        background: var(--blanco, #fff);
        border-radius: var(--radio, 10px);
        box-shadow: 0 1px 2px rgb(0 0 0 / 6%), 0 18px 40px -22px rgb(0 0 0 / 30%);
      }
      .puerta__icono { color: var(--primario, #d81b60); }
      .puerta__texto { display: flex; flex-direction: column; gap: 4px; }
      .puerta__texto h1 { margin: 0; font-size: 1.35rem; }
      .puerta__texto p { margin: 0; color: var(--gris-500, #6b7280); }
      /* Grande de verdad: se pulsa de pie, a veces con guantes. */
      .puerta__boton { width: 100%; min-height: 56px; font-size: 1.05rem; }
      .puerta__pie { margin: 0; font-size: 0.82rem; color: var(--gris-500, #6b7280); }
      .puerta__error {
        margin: 0;
        width: 100%;
        padding: var(--e2);
        border-radius: 6px;
        background: #fdecea;
        color: #a32a20;
        font-size: 0.9rem;
      }
      .puerta__salir {
        border: 0;
        background: none;
        color: var(--gris-500, #6b7280);
        font-size: 0.85rem;
        cursor: pointer;
        text-decoration: underline;
      }
    `,
  ],
})
export class EmpezarJornada {
  private readonly fichajes = inject(FichajesService);
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  private readonly tiempoReal = inject(TiempoRealService);

  protected readonly fichando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** El turno de tarde ficha a las cuatro: «Buenos días» a esa hora suena a reloj parado. */
  protected readonly saludo = saludoDeLaHora(new Date().getHours());

  /** Solo el nombre de pila: «Buenos días, Javier» y no el nombre completo. */
  protected readonly nombre = () => this.sesion.usuario()?.nombreCompleto?.split(' ')[0] ?? '';

  protected empezar(): void {
    if (this.fichando()) return;
    this.fichando.set(true);
    this.error.set(null);

    this.fichajes.empezar().subscribe({
      next: () => {
        this.fichando.set(false);
        // La pantalla de debajo se cargó con la puerta cerrada y todo le devolvió
        // 423: sin recargarla, el técnico empezaba el día viendo el taller a cero.
        this.tiempoReal.cambios.next('*');
      },
      error: (fallo: { error?: { mensaje?: string } }) => {
        this.fichando.set(false);
        this.error.set(fallo.error?.mensaje ?? 'No se ha podido fichar. Inténtalo otra vez.');
      },
    });
  }

  protected salir(): void {
    this.sesion.salir();
    // Sin llevarlo a la entrada se quedaba en la pantalla de debajo, sin menú y
    // con todo a cero, como si el taller estuviera vacío.
    void this.router.navigate(['/entrar']);
  }
}

export function saludoDeLaHora(hora: number): string {
  if (hora >= 6 && hora < 14) return 'Buenos días';
  return hora >= 14 && hora < 21 ? 'Buenas tardes' : 'Buenas noches';
}
