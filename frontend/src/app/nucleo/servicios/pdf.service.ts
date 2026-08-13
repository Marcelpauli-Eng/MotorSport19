import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

/**
 * Abre en otra pestaña un PDF que sirve la API.
 *
 * No se puede usar un enlace normal: el token viaja en una cabecera que pone
 * el interceptor, y un `<a href>` no la lleva, de modo que el servidor
 * respondería 401. Se descarga por HttpClient y se muestra desde memoria.
 */
@Injectable({ providedIn: 'root' })
export class PdfService {
  private readonly http = inject(HttpClient);

  /**
   * @param url        de dónde se baja el PDF.
   * @param nombre     con el que se guarda si el navegador bloquea la pestaña.
   */
  abrir(url: string, nombre: string): void {
    // La pestaña se abre antes de pedir el PDF, todavía dentro del clic: si se
    // abriera al recibir la respuesta, el navegador la tomaría por una ventana
    // emergente y la bloquearía.
    const pestana = window.open('', '_blank');

    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (pdf) => {
        const objeto = URL.createObjectURL(pdf);
        if (pestana) {
          pestana.location.href = objeto;
        } else {
          // Emergentes bloqueadas: se descarga, que siempre funciona.
          const enlace = document.createElement('a');
          enlace.href = objeto;
          enlace.download = nombre;
          enlace.click();
        }
        // Se libera con holgura: revocarla de inmediato dejaría la pestaña en blanco.
        setTimeout(() => URL.revokeObjectURL(objeto), 60_000);
      },
      error: () => pestana?.close(),
    });
  }
}
