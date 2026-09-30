import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Dialogo } from '../../compartido/dialogo';
import { Icono } from '../../compartido/icono';
import { EstadoSolicitud, SolicitudWeb } from '../../nucleo/modelos/solicitudes';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { SolicitudesService } from '../../nucleo/servicios/solicitudes.service';
import { alCambiarDatos } from '../../nucleo/servicios/tiempo-real.service';
import { BorradorCita, FormularioCita } from '../agenda/formulario-cita';
import { FormularioOrden } from '../ordenes/formulario-orden';
import { PresupuestosWebService } from '../../nucleo/servicios/presupuestos-web.service';
import { enlaceWhatsapp } from './enviar-presupuesto';

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
 * le monta un presupuesto (como el de una orden, con mano de obra y material)
 * y se le manda, se le da cita (con el formulario de la agenda ya relleno), se
 * marca como atendida o se descarta. La presupuestada espera a que el cliente
 * conteste: si acepta se abre la orden, si no se rechaza o se reescribe.
 */
@Component({
  selector: 'app-bandeja-solicitudes',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, Dialogo, Icono, FormularioCita, FormularioOrden],
  templateUrl: './bandeja-solicitudes.html',
  styleUrl: './bandeja-solicitudes.scss',
})
export class BandejaSolicitudes {
  private readonly servicio = inject(SolicitudesService);
  private readonly presupuestos = inject(PresupuestosWebService);
  private readonly router = inject(Router);
  private readonly notificaciones = inject(NotificacionesService);

  /** Dar cita apunta en la agenda: sin ese permiso solo se puede cerrar o descartar. */
  protected readonly puedeDarCita = inject(SesionService).tienePermiso('AGENDA_GESTIONAR');
  /** Mandar un presupuesto es poner precio: lo hace quien ve importes. */
  protected readonly puedePresupuestar = inject(SesionService).tienePermiso('IMPORTES_VER');
  /** Aceptarlo abre la orden y la deja aprobada. */
  protected readonly puedeAceptar =
    this.puedePresupuestar &&
    inject(SesionService).tienePermiso('ORDENES_ABRIR') &&
    inject(SesionService).tienePermiso('ORDENES_APROBAR');

  protected readonly estado = signal<EstadoSolicitud>('PENDIENTE');
  protected readonly solicitudes = signal<SolicitudWeb[]>([]);
  protected readonly cargando = signal(true);

  /** Fotos ya bajadas, por solicitud: se piden al pulsar, no al listar. */
  protected readonly fotos = signal<Record<number, string[]>>({});

  protected readonly dandoCita = signal<SolicitudWeb | null>(null);
  protected readonly aceptando = signal<SolicitudWeb | null>(null);
  protected readonly cerrando = signal<{ s: SolicitudWeb; accion: 'atender' | 'descartar' | 'rechazar' } | null>(
    null,
  );
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

  protected whatsapp(telefono: string): string {
    return enlaceWhatsapp(telefono);
  }

  /** Vuelve a pendiente, con sus líneas, y abre el presupuesto para corregirlo. */
  protected reescribir(s: SolicitudWeb): void {
    if (this.trabajando()) return;
    this.trabajando.set(true);
    this.presupuestos.reescribir(s.id).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.router.navigate(['/solicitudes', s.id, 'presupuesto']);
      },
      error: () => {
        this.trabajando.set(false);
        this.cargar();
      },
    });
  }

  protected trasAceptar(orden: { id: number }): void {
    this.aceptando.set(null);
    this.router.navigate(['/ordenes', orden.id]);
  }

  protected trasDarCita(): void {
    this.dandoCita.set(null);
    this.cargar();
  }

  protected abrirCierre(s: SolicitudWeb, accion: 'atender' | 'descartar' | 'rechazar'): void {
    this.nota.set('');
    this.cerrando.set({ s, accion });
  }

  protected confirmarCierre(): void {
    const c = this.cerrando();
    if (!c || this.trabajando()) return;
    this.trabajando.set(true);
    const nota = this.nota().trim() || null;
    const peticion: Observable<unknown> =
      c.accion === 'atender'
        ? this.servicio.marcarAtendida(c.s.id, nota)
        : c.accion === 'rechazar'
          ? this.presupuestos.rechazar(c.s.id, nota)
          : this.servicio.descartar(c.s.id, nota);

    peticion.subscribe({
      next: () => {
        this.trabajando.set(false);
        this.cerrando.set(null);
        this.notificaciones.exito(
          c.accion === 'atender'
            ? 'Solicitud atendida.'
            : c.accion === 'rechazar'
              ? 'Presupuesto rechazado. La solicitud pasa a «Descartadas».'
              : 'Solicitud descartada.',
        );
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
