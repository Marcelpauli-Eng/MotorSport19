import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialogo } from '../../compartido/dialogo';
import { Icono } from '../../compartido/icono';
import { CanalPresupuesto, PresupuestoWeb, SolicitudWeb } from '../../nucleo/modelos/solicitudes';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { PresupuestosWebService } from '../../nucleo/servicios/presupuestos-web.service';

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
 * Manda el presupuesto de una solicitud web por WhatsApp o por email.
 *
 * <p>No manda nada por su cuenta: abre WhatsApp o el correo de quien lo atiende
 * con el mensaje ya escrito en el idioma del cliente, con las líneas y el total.
 * Antes de abrirlo apunta en la solicitud por dónde se mandó y la deja
 * presupuestada, a la espera de que el cliente acepte o no.
 */
@Component({
  selector: 'app-enviar-presupuesto',
  standalone: true,
  imports: [FormsModule, Dialogo, Icono],
  templateUrl: './enviar-presupuesto.html',
})
export class EnviarPresupuesto {
  private readonly servicio = inject(PresupuestosWebService);
  private readonly notificaciones = inject(NotificacionesService);

  readonly presupuesto = input.required<PresupuestoWeb>();
  readonly cerrar = output<void>();
  readonly enviado = output<void>();

  protected readonly mensaje = signal('');
  /** En cuanto se retoca el mensaje a mano, deja de rehacerse solo. */
  protected readonly retocado = signal(false);
  protected readonly enviando = signal(false);

  protected readonly idioma = computed(() => IDIOMAS[this.presupuesto().idioma]);

  protected readonly asunto = computed(() =>
    PLANTILLAS[this.presupuesto().idioma].asunto.replace('{moto}', this.presupuesto().descripcionMoto),
  );

  protected readonly puedeEnviar = computed(() => !this.enviando() && !!this.mensaje().trim());

  constructor() {
    effect(() => {
      if (this.retocado()) return;
      const p = this.presupuesto();
      const euros = (n: number | null) =>
        new Intl.NumberFormat(LOCALE[p.idioma], { style: 'currency', currency: 'EUR' }).format(n ?? 0);
      const numero = (n: number) => new Intl.NumberFormat(LOCALE[p.idioma]).format(n);
      // Las tasas van al final, como en el PDF.
      const lineas = [...p.lineas]
        .sort((a, b) => Number(a.tipo === 'TASA') - Number(b.tipo === 'TASA'))
        .map((l) => `• ${l.descripcion}${l.cantidad !== 1 ? ` (x${numero(l.cantidad)})` : ''}: ${euros(l.total)}`)
        .join('\n');
      this.mensaje.set(
        PLANTILLAS[p.idioma].cuerpo
          .replace('{nombre}', p.clienteNombre.split(/\s+/)[0])
          .replaceAll('{moto}', p.descripcionMoto)
          .replace('{detalle}', lineas ? `${lineas}\n\n` : '')
          .replace('{importe}', euros(p.total)),
      );
    });
  }

  protected retocar(texto: string): void {
    this.mensaje.set(texto);
    this.retocado.set(true);
  }

  protected descargarPdf(): void {
    this.servicio.abrirPresupuestoPdf(this.presupuesto().id);
  }

  protected enviar(canal: CanalPresupuesto): void {
    if (!this.puedeEnviar()) return;
    const p = this.presupuesto();
    const texto = this.mensaje().trim();
    // La pestaña de WhatsApp se abre ya, dentro del clic: si se abriera al volver
    // la respuesta, el navegador la tomaría por una ventana emergente y la bloquearía.
    const pestana = canal === 'WHATSAPP' ? window.open('', '_blank') : null;
    this.enviando.set(true);

    this.servicio.enviar(p.id, canal).subscribe({
      next: () => {
        this.enviando.set(false);
        if (canal === 'WHATSAPP') {
          const url = enlaceWhatsapp(p.clienteTelefono ?? '', texto);
          if (pestana) pestana.location.href = url;
          else window.open(url, '_blank');
        } else {
          window.location.href =
            `mailto:${p.clienteEmail}?subject=${encodeURIComponent(this.asunto())}&body=${encodeURIComponent(texto)}`;
        }
        this.notificaciones.exito('Presupuesto enviado. Queda esperando respuesta en «Presupuestadas».');
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
