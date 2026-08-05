import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeEs from '@angular/common/locales/es';
import {
  ApplicationConfig,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './nucleo/api/auth.interceptor';
import { errorInterceptor } from './nucleo/api/error.interceptor';

// Fechas, decimales y moneda en formato español en toda la aplicación.
registerLocaleData(localeEs, 'es');

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),

    provideRouter(
      routes,
      // Los parámetros de ruta llegan como `input()` a los componentes.
      withComponentInputBinding(),
      // Al navegar se vuelve arriba; al usar «atrás» se recupera la posición.
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled' }),
    ),

    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),

    { provide: LOCALE_ID, useValue: 'es' },
  ],
};
