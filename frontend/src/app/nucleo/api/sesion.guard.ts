import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Rol, SesionService } from '../servicios/sesion.service';
import { NotificacionesService } from '../servicios/notificaciones.service';

/**
 * Exige sesión iniciada.
 *
 * Guarda la ruta a la que se iba en `returnUrl`, de modo que tras entrar se
 * llegue donde se quería y no siempre al panel. Esto importa con los enlaces
 * directos a una factura o a una orden.
 *
 * Un guard no protege nada por sí solo: lo único que hace es evitar que se
 * pinte una pantalla que la API va a rechazar de todas formas.
 */
export const sesionGuard: CanActivateFn = (_ruta, estado) => {
  const sesion = inject(SesionService);
  const router = inject(Router);

  if (sesion.autenticado()) {
    return true;
  }
  return router.createUrlTree(['/entrar'], { queryParams: { returnUrl: estado.url } });
};

/**
 * Exige uno de los roles indicados.
 *
 * Se usa en la ruta: `canActivate: [sesionGuard, rolGuard('ADMIN')]`.
 */
export function rolGuard(...roles: Rol[]): CanActivateFn {
  return () => {
    const sesion = inject(SesionService);
    const notificaciones = inject(NotificacionesService);
    const router = inject(Router);

    if (sesion.puede(...roles)) {
      return true;
    }
    notificaciones.error('No tiene permiso para acceder a esa pantalla.');
    return router.createUrlTree(['/panel']);
  };
}

/** Impide volver al login estando ya dentro. */
export const invitadoGuard: CanActivateFn = () => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  return sesion.autenticado() ? router.createUrlTree(['/panel']) : true;
};
