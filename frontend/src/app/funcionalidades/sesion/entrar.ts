import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { mensajeDe } from '../../nucleo/api/error.interceptor';
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
        void this.irA(destino);
      },
      error: (fallo: HttpErrorResponse) => {
        this.enviando.set(false);
        this.password.set('');
        // Con el servidor caído no hay mensaje del backend, y el genérico «no se
        // ha podido iniciar sesión» se leía como una contraseña mal puesta.
        this.error.set(mensajeDe(fallo));
      },
    });
  }

  /**
   * Lleva a la pantalla de destino, y se hace cargo de que no se llegue.
   *
   * <p>Antes esto era un `void this.router.navigateByUrl(destino)`: la
   * contraseña era correcta, la sesión quedaba abierta, y si la navegación no
   * salía adelante nadie se enteraba. El botón se quedaba en «Entrando…» para
   * siempre, con el menú lateral ya montado detrás mostrando el nombre del
   * usuario. Desde fuera parece que el programa se ha colgado al entrar.
   *
   * <p>No llegar es más corriente de lo que parece:
   *
   * <ul>
   *   <li>La pantalla se carga aparte, y ese trozo puede no descargarse: sin
   *       cobertura, con la sesión de trabajo caída o —en desarrollo— porque
   *       una recompilación cambió los ficheros que el navegador tenía en
   *       cache. Ahí `navigateByUrl` <b>lanza</b>.</li>
   *   <li>El destino venía de un enlace guardado y un guard lo desvía. Ahí no
   *       lanza: devuelve `false`, que era igual de silencioso.</li>
   * </ul>
   */
  private async irA(destino: string): Promise<void> {
    try {
      const llego = await this.router.navigateByUrl(destino);

      // Un guard puede desviar a otra pantalla: la navegación dice que no llegó
      // a donde se pedía, pero la aplicación ya está dentro, que es lo que
      // importa. Lo que hay que detectar es seguir plantado en la entrada.
      if (llego || !this.router.url.startsWith('/entrar')) {
        return;
      }

      // El destino no se pudo abrir; el panel es el sitio seguro.
      if (destino !== '/panel' && (await this.router.navigateByUrl('/panel'))) {
        return;
      }
      this.noSePudo('Ha entrado, pero no se ha podido abrir la pantalla. Vuelva a intentarlo.');
    } catch {
      this.noSePudo(
        'Ha entrado, pero la pantalla no ha terminado de cargar. ' +
          'Recargue la página y vuelva a intentarlo.',
      );
    }
  }

  private noSePudo(mensaje: string): void {
    this.enviando.set(false);
    this.error.set(mensaje);
  }
}
