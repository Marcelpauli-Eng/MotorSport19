/**
 * Elemento <camara-video> del mosaico de camaras.
 *
 * El navegador no sabe reproducir RTSP, que es lo que hablan las camaras. El
 * servicio `camaras` (go2rtc) hace de traductor, y este fichero carga SU
 * reproductor: el mismo que usa su panel de control.
 *
 * Se carga desde el servidor de camaras en vez de copiarlo aqui para que
 * reproductor y servidor sean siempre de la misma version. Copiado, cada
 * actualizacion de la imagen de Docker seria una ocasion de que dejaran de
 * entenderse sin que nadie se enterase hasta ver una pantalla en negro.
 *
 * Por que un fichero suelto y no codigo de Angular: la ruta de la importacion
 * tiene que resolverse en el navegador, contra el servidor de camaras. Metida
 * en el codigo de la aplicacion, el compilador intentaria resolverla al
 * construir -cuando no hay ningun go2rtc a mano- y la compilacion fallaria.
 *
 * Reparte solo (WebRTC si puede, si no MSE, si no HLS o imagenes sueltas),
 * segun lo que aguante cada navegador. Por eso se ve igual en el PC del
 * mostrador que en un iPhone.
 */
import { VideoRTC } from '/camaras-api/video-rtc.js';

class CamaraVideo extends VideoRTC {
  constructor() {
    super();

    // Solo imagen. Grabar sonido en un centro de trabajo tiene bastantes mas
    // limites legales que grabar imagen, y para vigilar una nave no aporta.
    this.media = 'video';

    // Una camara que no se esta viendo deja de gastar. Con cuatro en pantalla y
    // el taller mirando desde casa, esto es la diferencia entre ir fino y
    // saturar la subida del local.
    this.visibilityThreshold = 0.2;
  }

  oninit() {
    super.oninit();

    // Los controles de video (play, volumen, barra de tiempo) no pintan nada en
    // una emision en directo, y en el mosaico tapaban la imagen al pasar el
    // raton. Los de la pantalla los pone la propia aplicacion.
    this.video.controls = false;
    this.video.muted = true;

    // La camara no tiene por que dar la misma forma que su hueco. `cover`
    // recorta un poco antes que dejar franjas negras alrededor.
    this.video.style.objectFit = 'cover';
  }
}

customElements.define('camara-video', CamaraVideo);
