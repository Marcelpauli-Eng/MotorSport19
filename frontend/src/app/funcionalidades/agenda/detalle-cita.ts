import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Dialogo } from '../../compartido/dialogo';
import { Cita } from '../../nucleo/modelos/agenda';
import { MotoResumen } from '../../nucleo/modelos/taller';
import { CitasService } from '../../nucleo/servicios/citas.service';
import { MotosService } from '../../nucleo/servicios/motos.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';

/**
 * Ficha de una cita, con lo que se puede hacer con ella.
 *
 * <p>La acción que importa es «ha llegado»: abre la orden de trabajo y cierra la
 * cita enlazándolas. Es el puente entre la agenda y el taller, y por eso pide el
 * kilometraje, que es lo único que la orden necesita y la cita no sabía.
 */
@Component({
  selector: 'app-detalle-cita',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, Dialogo],
  templateUrl: './detalle-cita.html',
})
export class DetalleCita {
  private readonly servicio = inject(CitasService);
  private readonly motos = inject(MotosService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);

  readonly cita = input.required<Cita>();

  readonly cerrar = output<void>();
  readonly editar = output<void>();
  readonly cambiada = output<void>();

  protected readonly gestionaAgenda = this.sesion.puede('ADMIN', 'MOSTRADOR');
  protected readonly trabajando = signal(false);

  /** Formulario de entrada, que aparece al pulsar «Ha llegado». */
  protected readonly registrandoEntrada = signal(false);
  protected readonly kmEntrada = signal<number | null>(null);
  protected readonly motoParaLaOrden = signal<number | null>(null);
  protected readonly listaMotos = signal<MotoResumen[]>([]);

  /** «viernes 7 de agosto, 09:30». Se compone aquí porque el `date` de la
      plantilla no admite comillas dentro del formato. */
  protected readonly cuando = computed(() => {
    const f = new Date(this.cita().fechaHora);
    const dia = f.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
    const hora = f.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
    return `${dia}, ${hora}`;
  });

  protected readonly puedeAtender = computed(() =>
    this.cita().estadosPosibles.includes('ATENDIDA'),
  );
  protected readonly puedeConfirmar = computed(() =>
    this.cita().estadosPosibles.includes('CONFIRMADA'),
  );
  protected readonly estaCerrada = computed(() => this.cita().estadosPosibles.length === 0);

  /** Si la cita se cogió sin moto, hay que decir cuál es antes de abrir la orden. */
  protected readonly faltaMoto = computed(
    () => this.cita().motoSinRegistrar && this.motoParaLaOrden() === null,
  );

  protected readonly puedeAbrirOrden = computed(
    () => !this.trabajando() && this.kmEntrada() !== null && !this.faltaMoto(),
  );

  protected empezarEntrada(): void {
    this.kmEntrada.set(null);
    this.motoParaLaOrden.set(null);
    this.registrandoEntrada.set(true);

    if (this.cita().motoSinRegistrar && !this.listaMotos().length) {
      this.motos.buscar('', true, 0, 300).subscribe((p) => this.listaMotos.set(p.contenido));
    }
  }

  protected abrirOrden(): void {
    const km = this.kmEntrada();
    if (!this.puedeAbrirOrden() || km === null) return;

    this.trabajando.set(true);
    this.servicio
      .atender(this.cita().id, { motoId: this.motoParaLaOrden(), kmEntrada: km })
      .subscribe({
        next: (actualizada) => {
          this.trabajando.set(false);
          this.notificaciones.exito(`Abierta la orden ${actualizada.ordenCodigo}.`);
          this.cambiada.emit();
          if (actualizada.ordenTrabajoId) {
            void this.router.navigate(['/ordenes', actualizada.ordenTrabajoId]);
          }
        },
        error: () => this.trabajando.set(false),
      });
  }

  protected confirmar(): void {
    this.ejecutar(this.servicio.confirmar(this.cita().id), 'Cita confirmada.');
  }

  protected cancelar(): void {
    const motivo = prompt('¿Por qué se cancela la cita?');
    if (motivo === null) return;
    this.ejecutar(this.servicio.cancelar(this.cita().id, motivo), 'Cita cancelada.');
  }

  protected noSePresento(): void {
    const motivo = prompt('¿Algo que apuntar? (opcional)') ?? undefined;
    this.ejecutar(
      this.servicio.marcarNoPresentado(this.cita().id, motivo),
      'Anotado: no se presentó.',
    );
  }

  private ejecutar(peticion: ReturnType<CitasService['confirmar']>, mensaje: string): void {
    this.trabajando.set(true);
    peticion.subscribe({
      next: () => {
        this.trabajando.set(false);
        this.notificaciones.exito(mensaje);
        this.cambiada.emit();
      },
      error: () => this.trabajando.set(false),
    });
  }
}
