import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialogo } from '../../compartido/dialogo';
import { Icono } from '../../compartido/icono';
import { EstadoSolicitud, SolicitudWeb } from '../../nucleo/modelos/solicitudes';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { SolicitudesService } from '../../nucleo/servicios/solicitudes.service';
import { alCambiarDatos } from '../../nucleo/servicios/tiempo-real.service';
import { BorradorCita, FormularioCita } from '../agenda/formulario-cita';

const IDIOMAS: Record<SolicitudWeb['idioma'], string> = {
  es: 'castellano',
  ca: 'catalán',
  en: 'inglés',
  fr: 'francés',
};

/**
 * Bandeja de lo que piden los clientes desde la web: citas y presupuestos.
 *
 * <p>Nada de lo que llega entra solo en la agenda ni en clientes. Desde aquí se
 * le da cita (con el formulario de la agenda ya relleno), se marca como
 * atendida —el presupuesto se dio por teléfono— o se descarta.
 */
@Component({
  selector: 'app-bandeja-solicitudes',
  standalone: true,
  imports: [CommonModule, FormsModule, Dialogo, Icono, FormularioCita],
  templateUrl: './bandeja-solicitudes.html',
  styleUrl: './bandeja-solicitudes.scss',
})
export class BandejaSolicitudes {
  private readonly servicio = inject(SolicitudesService);
  private readonly notificaciones = inject(NotificacionesService);

  /** Dar cita apunta en la agenda: sin ese permiso solo se puede cerrar o descartar. */
  protected readonly puedeDarCita = inject(SesionService).tienePermiso('AGENDA_GESTIONAR');

  protected readonly estado = signal<EstadoSolicitud>('PENDIENTE');
  protected readonly solicitudes = signal<SolicitudWeb[]>([]);
  protected readonly cargando = signal(true);

  /** Fotos ya bajadas, por solicitud: se piden al pulsar, no al listar. */
  protected readonly fotos = signal<Record<number, string[]>>({});

  protected readonly dandoCita = signal<SolicitudWeb | null>(null);
  protected readonly cerrando = signal<{ s: SolicitudWeb; accion: 'atender' | 'descartar' } | null>(null);
  protected readonly nota = signal('');
  protected readonly trabajando = signal(false);

  protected readonly borrador = computed<BorradorCita | null>(() => {
    const s = this.dandoCita();
    return s
      ? {
          contactoNombre: s.nombre,
          contactoTelefono: s.telefono,
          descripcionMoto: s.descripcionMoto,
          motivo: s.necesita,
          dia: s.fechaPreferida,
        }
      : null;
  });

  constructor() {
    this.cargar();
    alCambiarDatos(() => this.cargar());
    inject(DestroyRef).onDestroy(() =>
      Object.values(this.fotos())
        .flat()
        .forEach((url) => URL.revokeObjectURL(url)),
    );
  }

  protected cambiarEstado(estado: EstadoSolicitud): void {
    this.estado.set(estado);
    this.cargando.set(true);
    this.cargar();
  }

  private cargar(): void {
    this.servicio.bandeja(this.estado()).subscribe({
      next: (lista) => {
        this.solicitudes.set(lista);
        this.cargando.set(false);
        this.servicio.contarPendientes();
      },
      error: () => this.cargando.set(false),
    });
  }

  protected verFotos(s: SolicitudWeb): void {
    if (this.fotos()[s.id]) return;
    const urls: string[] = [];
    for (let orden = 1; orden <= s.fotos; orden++) {
      this.servicio.foto(s.id, orden).subscribe((blob) => {
        urls[orden - 1] = URL.createObjectURL(blob);
        this.fotos.update((f) => ({ ...f, [s.id]: [...urls] }));
      });
    }
  }

  protected idioma(s: SolicitudWeb): string {
    return IDIOMAS[s.idioma];
  }

  /**
   * Enlace de WhatsApp. Un número de nueve cifras sin prefijo es español; los
   * de Francia llegan con su +33 porque así los pide el formulario.
   */
  protected whatsapp(telefono: string): string {
    let cifras = telefono.replace(/\D/g, '');
    if (!telefono.trim().startsWith('+') && cifras.length === 9) cifras = '34' + cifras;
    return `https://wa.me/${cifras}`;
  }

  protected trasDarCita(): void {
    this.dandoCita.set(null);
    this.cargar();
  }

  protected abrirCierre(s: SolicitudWeb, accion: 'atender' | 'descartar'): void {
    this.nota.set('');
    this.cerrando.set({ s, accion });
  }

  protected confirmarCierre(): void {
    const c = this.cerrando();
    if (!c || this.trabajando()) return;
    this.trabajando.set(true);
    const nota = this.nota().trim() || null;
    const peticion =
      c.accion === 'atender'
        ? this.servicio.marcarAtendida(c.s.id, nota)
        : this.servicio.descartar(c.s.id, nota);

    peticion.subscribe({
      next: () => {
        this.trabajando.set(false);
        this.cerrando.set(null);
        this.notificaciones.exito(c.accion === 'atender' ? 'Solicitud atendida.' : 'Solicitud descartada.');
        this.cargar();
      },
      // El interceptor ya enseña el motivo: por ejemplo, que otro puesto la cerró antes.
      error: () => {
        this.trabajando.set(false);
        this.cerrando.set(null);
        this.cargar();
      },
    });
  }
}
