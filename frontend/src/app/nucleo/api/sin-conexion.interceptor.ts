import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, of, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  PENDIENTE,
  REINTENTO,
  SinConexionService,
  noLlegoAlServidor,
} from '../servicios/sin-conexion.service';
import { TiempoRealService } from '../servicios/tiempo-real.service';

/**
 * Hace que el programa siga funcionando cuando el servidor no contesta.
 *
 * Va el último de la cadena, pegado a la red: así el interceptor de errores
 * solo se entera de una caída cuando de verdad no hay nada que enseñar.
 *
 * «No contesta» no es solo el estado 0 del navegador. En el taller la API va
 * detrás de nginx, y cuando la que se cae es la API —un reinicio, una
 * actualización— nginx sí responde, con 502 o 503. Sin contarlos, la pantalla
 * decía «se ven los últimos datos recibidos» y enseñaba una lista vacía.
 */
export const sinConexionInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  if (!peticion.url.startsWith(environment.urlApi)) {
    return siguiente(peticion);
  }

  const sinConexion = inject(SinConexionService);
  const esLectura = peticion.method === 'GET';

  const marcada = peticion.clone({
    setHeaders: {
      'X-Cliente': inject(TiempoRealService).idPestana,
      // Con el mismo identificador, el servidor no aplica dos veces un reenvío.
      ...(esLectura || peticion.headers.has('X-Id-Peticion')
        ? {}
        : { 'X-Id-Peticion': `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}` }),
    },
  });

  if (esLectura) {
    if (marcada.responseType !== 'json') return siguiente(marcada);
    const clave = sinConexion.claveDe(marcada);

    return siguiente(marcada).pipe(
      tap((evento) => {
        if (evento instanceof HttpResponse) sinConexion.guardarRespuesta(clave, evento.body);
      }),
      catchError((error: HttpErrorResponse) =>
        // Leer es inofensivo: también ante un 504 se enseña lo guardado.
        !noLlegoAlServidor(error.status) && error.status !== 504
          ? throwError(() => error)
          : from(sinConexion.leerRespuesta(clave)).pipe(
              switchMap((guardada) =>
                guardada === undefined
                  ? throwError(() => error)
                  : of(new HttpResponse({ status: 200, body: guardada, url: marcada.urlWithParams })),
              ),
            ),
      ),
    );
  }

  if (marcada.context.get(REINTENTO) || !sinConexion.sePuedeGuardar(marcada)) {
    return siguiente(marcada);
  }

  return siguiente(marcada).pipe(
    catchError((error: HttpErrorResponse) => {
      // Un 504 no se encola: nginx se cansó de esperar, pero la API pudo
      // terminarla. Mejor que lo compruebe una persona que repetirla a ciegas.
      if (!noLlegoAlServidor(error.status)) return throwError(() => error);
      sinConexion.encolar(marcada);
      return throwError(
        () =>
          new HttpErrorResponse({
            error: PENDIENTE,
            status: error.status,
            statusText: 'Pendiente de enviar',
            url: marcada.urlWithParams,
          }),
      );
    }),
  );
};
