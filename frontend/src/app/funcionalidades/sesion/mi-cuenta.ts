import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';

/**
 * Ficha del usuario en curso, con el cambio de contraseña.
 *
 * Existe porque las contraseñas iniciales las reparte quien instala el
 * programa: sin esta pantalla, todo el taller se quedaría para siempre con las
 * que vinieron de fábrica.
 */
@Component({
  selector: 'app-mi-cuenta',
  imports: [FormsModule],
  templateUrl: './mi-cuenta.html',
  styleUrl: './mi-cuenta.scss',
})
export class MiCuenta {
  private readonly sesion = inject(SesionService);
  private readonly notificaciones = inject(NotificacionesService);

  protected readonly usuario = this.sesion.usuario;

  protected readonly actual = signal('');
  protected readonly nueva = signal('');
  protected readonly repetida = signal('');
  protected readonly enviando = signal(false);

  /** Mismo mínimo que exige el backend. */
  private static readonly MINIMO = 8;

  protected readonly problema = computed<string | null>(() => {
    if (this.nueva() && this.nueva().length < MiCuenta.MINIMO) {
      return `La contraseña nueva debe tener al menos ${MiCuenta.MINIMO} caracteres.`;
    }
    if (this.repetida() && this.nueva() !== this.repetida()) {
      return 'Las dos contraseñas nuevas no coinciden.';
    }
    return null;
  });

  protected readonly puedeEnviar = computed(
    () =>
      !this.enviando() &&
      !!this.actual() &&
      this.nueva().length >= MiCuenta.MINIMO &&
      this.nueva() === this.repetida(),
  );

  protected cambiar(): void {
    if (!this.puedeEnviar()) {
      return;
    }
    this.enviando.set(true);

    this.sesion.cambiarPassword(this.actual(), this.nueva()).subscribe({
      next: () => {
        this.enviando.set(false);
        this.actual.set('');
        this.nueva.set('');
        this.repetida.set('');
        this.notificaciones.exito('Contraseña cambiada.');
      },
      // El interceptor ya muestra el motivo que envía el servidor.
      error: () => this.enviando.set(false),
    });
  }
}
