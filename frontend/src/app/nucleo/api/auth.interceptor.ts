import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SesionService } from '../servicios/sesion.service';

/**
 * Adjunta el token de sesión a cada petición.
 *
 * Mientras no haya token no hace nada, que es lo que corresponde en la pantalla
 * de entrada. El resto de la aplicación no sabe que existe: ningún servicio se
 * ocupa de la autenticación.
 */
export const authInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const sesion = inject(SesionService);
  const token = sesion.token();

  if (!token) {
    return siguiente(peticion);
  }

  return siguiente(
    peticion.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    }),
  );
};
