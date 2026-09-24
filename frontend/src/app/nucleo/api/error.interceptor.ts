import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { NotificacionesService } from '../servicios/notificaciones.service';
import { SesionService } from '../servicios/sesion.service';
import { PENDIENTE, REINTENTO, noLlegoAlServidor } from '../servicios/sin-conexion.service';
import { RespuestaError } from '../modelos/comunes';

/**
 * Traduce los errores de la API a avisos legibles.
 *
 * El backend ya devuelve mensajes redactados en espanol y pensados para el
 * usuario ("Stock insuficiente de la pieza ESP-RET-DER..."), asi que se muestran
 * tal cual en vez de sustituirlos por un generico. Solo se inventa un texto
 * cuando de verdad no hay ninguno, como cuando el servidor no responde.
 *
 * Ademas cierra la sesion ante un 401. Es la unica forma fiable de detectar que
 * el token ha caducado: no se comprueba la fecha en el navegador, porque quien
 * decide si un token vale es el servidor.
 */
export const errorInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const notificaciones = inject(NotificacionesService);
  const sesion = inject(SesionService);
  const router = inject(Router);

  return siguiente(peticion).pipe(
    catchError((error: HttpErrorResponse) => {
      // Se ha quedado en cola sin conexión: ya lo ha dicho quien la encoló, y
      // aquí se diría justo lo contrario («NO se ha guardado»).
      if (error.error === PENDIENTE) {
        return throwError(() => error);
      }
      // Un reenvío de la cola que sigue sin llegar se queda en la cola: avisar
      // de cada intento llenaba la esquina de errores de algo que no ha fallado.
      if (peticion.context.get(REINTENTO) && (noLlegoAlServidor(error.status) || [423, 504].includes(error.status))) {
        return throwError(() => error);
      }

      // El propio login devuelve 401 con credenciales malas: ahi no hay sesion
      // que cerrar, y la pantalla de entrada ya ensena el mensaje.
      const esLogin = peticion.url.includes('/auth/login');

      if (error.status === 401 && !esLogin) {
        if (sesion.autenticado()) {
          notificaciones.error('Su sesion ha caducado. Vuelva a entrar.');
        }
        sesion.salir();
        void router.navigate(['/entrar'], { queryParams: { returnUrl: router.url } });
        return throwError(() => error);
      }

      // 423: no hay jornada empezada. No es un fallo, es que falta el primer
      // paso del día, y la pantalla que tapa el programa ya lo dice con un
      // botón. Un aviso rojo encima solo consigue que parezca que algo se ha
      // roto nada más entrar a trabajar.
      // Sin permiso para algo que se ha pulsado: ventana que hay que cerrar, para
      // que no pase desapercibido como un aviso de esquina. Al LEER no: son
      // cargas que la persona no ha pedido, y con un rol a medida abrir una
      // orden sacaba dos ventanas seguidas que había que ir cerrando.
      if (error.status === 403 && peticion.method !== 'GET') {
        alert(mensajeDe(error));
      } else if (!esLogin && error.status !== 423) {
        notificaciones.error(mensajeDe(error, peticion), detallesDe(error, peticion));
      }
      return throwError(() => error);
    }),
  );
};

export function mensajeDe(error: HttpErrorResponse, peticion?: HttpRequest<unknown>): string {
  if (error.status === 0) {
    // Lo que sí se puede hacer sin conexión nunca llega aquí: se guarda para
    // después. Si llega, es algo que exige servidor (crear, facturar, fichar)
    // y hay que decir claro que no se ha guardado.
    return peticion && peticion.method !== 'GET'
      ? 'Sin conexión con el servidor: esto necesita conexión y NO se ha guardado. Inténtelo cuando vuelva.'
      : 'No se ha podido contactar con el servidor. Revise su conexión.';
  }

  // Lo que diga el backend manda: sus mensajes están redactados para el
  // mostrador y explican el caso concreto.
  const cuerpo = error.error as RespuestaError | undefined;
  if (cuerpo?.mensaje) {
    return cuerpo.mensaje;
  }

  // Y si no dice nada, al menos que el aviso distinga entre «no puedes»,
  // «ya no está» y «se ha roto». Un único «error inesperado» para las tres
  // cosas obliga a adivinar, que es justo lo que no se puede hacer con un
  // cliente delante.
  switch (error.status) {
    case 400:
      return 'Hay algo mal en los datos enviados.';
    case 403:
      return 'No tiene permiso para hacer eso.';
    case 404:
      return 'No se ha encontrado lo que buscaba.';
    case 409:
      return 'Eso choca con algo que ya existe.';
    case 422:
      return 'Los datos no cumplen alguna regla.';
    case 502:
    case 503:
    case 504:
      return 'El servidor no está disponible ahora mismo. Inténtelo en un momento.';
    default:
      return error.status >= 500
        ? 'El servidor ha fallado procesando la petición.'
        : 'Se ha producido un error inesperado.';
  }
}

/**
 * Lo que se enseña debajo del mensaje.
 *
 * <p>Primero los errores por campo, si el backend los manda. Y siempre, al
 * final, una línea técnica con el código y la petición que ha fallado.
 *
 * <p>Esa línea no es para el usuario, es para poder arreglarlo: con tres
 * avisos idénticos en pantalla y ninguno diciendo de dónde salen, no hay forma
 * de saber si es la misma llamada repetida o tres pantallas distintas. Ocupa
 * un renglón y ahorra media hora de adivinanzas.
 */
function detallesDe(error: HttpErrorResponse, peticion: HttpRequest<unknown>): string[] {
  const cuerpo = error.error as RespuestaError | undefined;
  const porCampo = cuerpo?.detalles ? Object.values(cuerpo.detalles) : [];

  const ruta = peticion.url.replace(/^.*\/api/, '') || peticion.url;
  return [...porCampo, `${error.status} · ${peticion.method} ${ruta}`];
}
