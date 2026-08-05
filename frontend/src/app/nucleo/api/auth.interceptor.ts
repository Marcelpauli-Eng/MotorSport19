import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SesionService } from '../servicios/sesion.service';

/**
 * Adjunta el token de sesión a cada petición.
 *
 * Se deja montado desde ya aunque la autenticación llegue en la fase 5: así
 * cuando exista el login no hay que tocar ni una pantalla, solo empezar a
 * guardar el token. Mientras no haya token, el interceptor no hace nada.
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
