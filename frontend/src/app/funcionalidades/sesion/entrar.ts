import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { SesionService } from '../../nucleo/servicios/sesion.service';

/**
 * Pantalla de entrada.
 *
 * No muestra pistas sobre qué ha fallado: el backend responde siempre
 * «Credenciales incorrectas» tanto si el usuario no existe como si la
 * contraseña es errónea, y aquí se respeta ese mensaje. Decir «ese usuario no
 * existe» sería regalar la mitad del trabajo a quien esté probando.
 */
@Component({
  selector: 'app-entrar',
  imports: [FormsModule],
  templateUrl: './entrar.html',
  styleUrl: './entrar.scss',
})
export class Entrar {
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected entrar(): void {
    const usuario = this.username().trim();
    if (!usuario || !this.password()) {
      this.error.set('Escriba su usuario y su contraseña.');
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    this.sesion.entrar(usuario, this.password()).subscribe({
      next: () => {
        const destino = this.ruta.snapshot.queryParamMap.get('returnUrl') ?? '/panel';
        void this.router.navigateByUrl(destino);
      },
      error: (fallo: { error?: { mensaje?: string }; status?: number }) => {
        this.enviando.set(false);
        this.password.set('');
        this.error.set(fallo.error?.mensaje ?? 'No se ha podido iniciar sesión.');
      },
    });
  }
}
