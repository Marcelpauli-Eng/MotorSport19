import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { environment } from './environments/environment';

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));

// Para poder abrir el programa sin conexión. Solo en producción: en desarrollo
// la copia guardada estorbaría al recargar. El navegador solo lo admite con
// https o en localhost; en otro caso sigue funcionando igual, sin esa copia.
if (environment.produccion && 'serviceWorker' in navigator) {
  void navigator.serviceWorker.register('/sw.js').catch(() => {});
}
