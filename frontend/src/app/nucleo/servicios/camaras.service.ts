import { Injectable } from '@angular/core';

/** Una camara tal y como se pinta en el mosaico. */
export interface Camara {
  /** Clave en `go2rtc.yaml`. Es lo que se manda al servidor para pedir el video. */
  nombre: string;
  /** El mismo nombre, presentable: `patio-trasero` -> `Patio trasero`. */
  titulo: string;
  /** URL de negociacion del reproductor. */
  origen: string;
}

/** Base del servicio de camaras, que el nginx del contenedor `web` redirige. */
const BASE = '/camaras-api';

/**
 * Camaras del taller.
 *
 * La lista no esta escrita en el codigo: se pregunta al servidor de camaras,
 * que responde con lo que haya en `infra/camaras/go2rtc.yaml`. Anadir una
 * camara es editar ese fichero y reiniciar el servicio; aqui no se toca nada.
 *
 * Se usa `fetch` y no `HttpClient` a proposito. Los interceptores estan hechos
 * para nuestra API: uno adjunta el token de sesion, que aqui no pinta nada
 * porque detras no hay un Spring sino un go2rtc, y el otro cierra la sesion
 * ante un 401 y avisa por pantalla de cualquier fallo. Con el servicio de
 * camaras apagado -que es lo normal en una instalacion sin camaras- eso llenaria
 * la pantalla de avisos de error. Aqui el fallo se trata donde toca: pintando
 * un mensaje en la propia pantalla de camaras.
 */
@Injectable({ providedIn: 'root' })
export class CamarasService {
  /** Carga del reproductor, en marcha o ya resuelta. Se hace una sola vez. */
  private reproductor?: Promise<void>;

  async listar(): Promise<Camara[]> {
    const respuesta = await fetch(`${BASE}/api/streams`);
    if (!respuesta.ok) {
      throw new Error(`El servidor de cámaras respondió ${respuesta.status}`);
    }

    // Responde un objeto con una entrada por camara: `{ "patio": {...}, ... }`.
    // Solo interesan las claves; lo de dentro son detalles del flujo.
    const flujos = (await respuesta.json()) as Record<string, unknown>;

    return Object.keys(flujos)
      .sort((a, b) => a.localeCompare(b, 'es'))
      .map((nombre) => ({
        nombre,
        titulo: titular(nombre),
        origen: `${BASE}/api/ws?src=${encodeURIComponent(nombre)}`,
      }));
  }

  /** URL de la imagen fija de una camara, para la vista previa impresa o de respaldo. */
  fotograma(nombre: string): string {
    return `${BASE}/api/frame.jpeg?src=${encodeURIComponent(nombre)}`;
  }

  /**
   * Registra el elemento `<camara-video>`.
   *
   * Hay que esperar a que termine antes de pintar ninguna camara. Un elemento
   * a medio registrar se queda como una etiqueta vacia: si se le pone el origen
   * antes de tiempo, la propiedad se escribe sobre el elemento corriente y el
   * reproductor, cuando por fin se registra, no llega a verla. Se queda todo en
   * negro y sin ningun error que lo explique.
   *
   * Solo se carga una vez aunque se entre y se salga de la pantalla.
   */
  cargarReproductor(): Promise<void> {
    this.reproductor ??= new Promise<void>((resolver, rechazar) => {
      const script = document.createElement('script');
      script.type = 'module';
      script.src = '/camara-video.js';
      script.onload = () => customElements.whenDefined('camara-video').then(() => resolver());
      script.onerror = () =>
        rechazar(new Error('No se ha podido cargar el reproductor de cámaras'));
      document.head.appendChild(script);
    });

    return this.reproductor;
  }
}

/** `patio-trasero` -> `Patio trasero`. */
function titular(nombre: string): string {
  const limpio = nombre.replace(/[-_]+/g, ' ').trim();
  return limpio.charAt(0).toUpperCase() + limpio.slice(1);
}
