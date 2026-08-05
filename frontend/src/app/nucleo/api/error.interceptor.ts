import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificacionesService } from '../servicios/notificaciones.service';
import { RespuestaError } from '../modelos/comunes';

/**
 * Traduce los errores de la API a avisos legibles.
 *
 * El backend ya devuelve mensajes redactados en espanol y pensados para el
 * usuario ("Stock insuficiente de la pieza ESP-RET-DER..."), asi que se muestran
 * tal cual en vez de sustituirlos por un generico. Solo se inventa un texto
 * cuando de verdad no hay ninguno, como cuando el servidor no responde.
 */
export const errorInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const notificaciones = inject(NotificacionesService);

  return siguiente(peticion).pipe(
    catchError((error: HttpErrorResponse) => {
      notificaciones.error(mensajeDe(error), detallesDe(error));
      return throwError(() => error);
    }),
  );
};

function mensajeDe(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'No se ha podido contactar con el servidor. Revise su conexión.';
  }
  const cuerpo = error.error as RespuestaError | undefined;
  if (cuerpo?.mensaje) {
    return cuerpo.mensaje;
  }
  if (error.status === 404) {
    return 'No se ha encontrado lo que buscaba.';
  }
  return 'Se ha producido un error inesperado.';
}

/** Errores por campo en los fallos de validación. */
function detallesDe(error: HttpErrorResponse): string[] {
  const cuerpo = error.error as RespuestaError | undefined;
  if (!cuerpo?.detalles) {
    return [];
  }
  return Object.values(cuerpo.detalles);
}
