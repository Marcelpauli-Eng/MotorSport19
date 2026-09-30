import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialogo } from '../../compartido/dialogo';
import { Icono } from '../../compartido/icono';
import { CanalPresupuesto, SolicitudWeb } from '../../nucleo/modelos/solicitudes';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { SolicitudesService } from '../../nucleo/servicios/solicitudes.service';

type Idioma = SolicitudWeb['idioma'];

/**
 * Enlace de WhatsApp a un teléfono. Un número de nueve cifras sin prefijo es
 * español; los de fuera llegan con su prefijo porque así los pide la web.
 */
export function enlaceWhatsapp(telefono: string, texto?: string): string {
  let cifras = telefono.replace(/\D/g, '');
  if (!telefono.trim().startsWith('+') && cifras.length === 9) cifras = '34' + cifras;
  return `https://wa.me/${cifras}${texto ? `?text=${encodeURIComponent(texto)}` : ''}`;
}

const LOCALE: Record<Idioma, string> = { es: 'es-ES', ca: 'ca-ES', en: 'en-GB', fr: 'fr-FR' };

/**
 * El mensaje al cliente, en el idioma en que escribió en la web. Es un
 * borrador: quien lo manda lo puede retocar antes de enviarlo.
 */
const PLANTILLAS: Record<Idioma, { asunto: string; cuerpo: string }> = {
  es: {
    asunto: 'Presupuesto para tu {moto}',
    cuerpo:
      'Hola, {nombre}:\n\nTe pasamos el presupuesto para tu {moto}.\n\n{detalle}Total: {importe} (IVA incluido).\n\n' +
      'Si te va bien, contéstanos y te damos cita.\n\n19 Racing Motorsport',
  },
  ca: {
    asunto: 'Pressupost per a la teva {moto}',
    cuerpo:
      'Hola, {nombre}:\n\nT’enviem el pressupost per a la teva {moto}.\n\n{detalle}Total: {importe} (IVA inclòs).\n\n' +
      'Si et va bé, respon-nos i et donem cita.\n\n19 Racing Motorsport',
  },
  en: {
    asunto: 'Quote for your {moto}',
    cuerpo:
      'Hi {nombre},\n\nHere is the quote for your {moto}.\n\n{detalle}Total: {importe} (VAT included).\n\n' +
      'If you’re happy with it, just reply and we’ll book you in.\n\n19 Racing Motorsport',
  },
  fr: {
    asunto: 'Devis pour votre {moto}',
    cuerpo:
      'Bonjour {nombre},\n\nVoici le devis pour votre {moto}.\n\n{detalle}Total : {importe} (TVA incluse).\n\n' +
      'Si cela vous convient, répondez-nous et nous vous proposerons un rendez-vous.\n\n19 Racing Motorsport',
  },
};

const IDIOMAS: Record<Idioma, string> = { es: 'castellano', ca: 'catalán', en: 'inglés', fr: 'francés' };

/**
 * Presupuesto para una solicitud de la web, enviado por WhatsApp o por email.
 *
 * <p>No manda nada por su cuenta: abre WhatsApp o el correo de quien lo atiende
 * con el mensaje ya escrito, que es desde donde el taller habla con sus clientes.
 * Antes de abrirlo apunta en la solicitud cuánto y por dónde, y la deja
 * presupuestada: si el cliente acepta, se le da cita desde la bandeja.
 */
@Component({
  selector: 'app-enviar-presupuesto',
  standalone: true,
  imports: [FormsModule, Dialogo, Icono],
  templateUrl: './enviar-presupuesto.html',
})
export class EnviarPresupuesto {
  private readonly servicio = inject(SolicitudesService);
  private readonly notificaciones = inject(NotificacionesService);

  readonly solicitud = input.required<SolicitudWeb>();
  readonly cerrar = output<void>();
  readonly enviado = output<void>();

  protected readonly detalle = signal('');
  protected readonly importe = signal<number | null>(null);
  protected readonly mensaje = signal('');
  /** En cuanto se retoca el mensaje a mano, deja de rehacerse solo. */
  protected readonly retocado = signal(false);
  protected readonly enviando = signal(false);

  protected readonly idioma = computed(() => IDIOMAS[this.solicitud().idioma]);
  protected readonly moto = computed(() => `${this.solicitud().marca} ${this.solicitud().modelo}`);

  protected readonly asunto = computed(() =>
    PLANTILLAS[this.solicitud().idioma].asunto.replace('{moto}', this.moto()),
  );

  protected readonly puedeEnviar = computed(
    () => !this.enviando() && (this.importe() ?? 0) > 0 && !!this.mensaje().trim(),
  );

  constructor() {
    // Si ya se le mandó uno, se parte de él para corregirlo.
    queueMicrotask(() => {
      const s = this.solicitud();
      this.detalle.set(s.presupuestoDetalle ?? '');
      this.importe.set(s.presupuestoImporte);
    });

    effect(() => {
      if (this.retocado()) return;
      const s = this.solicitud();
      const importe = this.importe();
      const detalle = this.detalle().trim();
      const cantidad =
        importe && importe > 0
          ? new Intl.NumberFormat(LOCALE[s.idioma], { style: 'currency', currency: 'EUR' }).format(importe)
          : '…';
      this.mensaje.set(
        PLANTILLAS[s.idioma].cuerpo
          .replace('{nombre}', s.nombre.split(/\s+/)[0])
          .replaceAll('{moto}', this.moto())
          .replace('{detalle}', detalle ? `${detalle}\n\n` : '')
          .replace('{importe}', cantidad),
      );
    });
  }

  protected retocar(texto: string): void {
    this.mensaje.set(texto);
    this.retocado.set(true);
  }

  protected enviar(canal: CanalPresupuesto): void {
    if (!this.puedeEnviar()) return;
    const s = this.solicitud();
    const texto = this.mensaje().trim();
    // La pestaña de WhatsApp se abre ya, dentro del clic: si se abriera al volver
    // la respuesta, el navegador la tomaría por una ventana emergente y la bloquearía.
    const pestana = canal === 'WHATSAPP' ? window.open('', '_blank') : null;
    this.enviando.set(true);

    this.servicio
      .enviarPresupuesto(s.id, { importe: this.importe()!, detalle: this.detalle().trim() || null, canal })
      .subscribe({
        next: () => {
          this.enviando.set(false);
          if (canal === 'WHATSAPP') {
            const url = enlaceWhatsapp(s.telefono, texto);
            if (pestana) pestana.location.href = url;
            else window.open(url, '_blank');
          } else {
            window.location.href =
              `mailto:${s.email}?subject=${encodeURIComponent(this.asunto())}&body=${encodeURIComponent(texto)}`;
          }
          this.notificaciones.exito('Presupuesto apuntado. Queda esperando respuesta en «Presupuestadas».');
          this.enviado.emit();
        },
        // El interceptor ya enseña el motivo: por ejemplo, que otro puesto la cerró antes.
        error: () => {
          this.enviando.set(false);
          pestana?.close();
        },
      });
  }
}
