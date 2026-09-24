import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Permiso, SesionService } from '../servicios/sesion.service';
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
 * Exige al menos uno de los permisos indicados.
 *
 * Se usa en la ruta: `canActivate: [permisoGuard('FACTURAS_VER')]`.
 *
 * <p>Pregunta por el MISMO permiso que exige la API para esa pantalla. Antes
 * preguntaba por el perfil («¿eres ADMIN o MOSTRADOR?»), y eso hacía imposible
 * un rol a medida: un jefe de taller con FACTURAS_VER no era ninguno de los
 * tres perfiles, así que la ruta lo echaba aunque el servidor le hubiera
 * dejado pasar. Conceder un permiso solo se nota si lo que se pregunta aquí es
 * exactamente ese permiso.
 */
export function permisoGuard(...permisos: Permiso[]): CanActivateFn {
  return () => {
    const sesion = inject(SesionService);
    const notificaciones = inject(NotificacionesService);
    const router = inject(Router);

    if (sesion.tienePermiso(...permisos)) {
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
