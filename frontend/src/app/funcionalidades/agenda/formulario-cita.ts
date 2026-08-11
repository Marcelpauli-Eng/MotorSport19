import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialogo } from '../../compartido/dialogo';
import { Cita } from '../../nucleo/modelos/agenda';
import { Tecnico } from '../../nucleo/modelos/configuracion';
import { MotoResumen } from '../../nucleo/modelos/taller';
import { CitasService, DatosCita } from '../../nucleo/servicios/citas.service';
import { MotosService } from '../../nucleo/servicios/motos.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { UsuariosService } from '../../nucleo/servicios/usuarios.service';

/** `2026-08-07T09:30` — lo que espera un input datetime-local, en hora local. */
function paraInput(iso: string): string {
  const f = new Date(iso);
  const dos = (n: number) => `${n}`.padStart(2, '0');
  return `${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())}T${dos(f.getHours())}:${dos(f.getMinutes())}`;
}

/**
 * Alta y edición de una cita.
 *
 * <p>La moto es opcional a propósito: media agenda de un taller se coge por
 * teléfono de gente que llama por primera vez. Si está en el sistema se elige y
 * manda ella; si no, se apunta lo justo para reconocerla cuando entre por la
 * puerta, y ya se dará de alta al atenderla.
 */
@Component({
  selector: 'app-formulario-cita',
  standalone: true,
  imports: [CommonModule, FormsModule, Dialogo],
  templateUrl: './formulario-cita.html',
})
export class FormularioCita {
  private readonly servicio = inject(CitasService);
  private readonly motos = inject(MotosService);
  private readonly usuarios = inject(UsuariosService);
  private readonly notificaciones = inject(NotificacionesService);

  /** Cita que se edita. Sin ella, se da una nueva. */
  readonly cita = input<Cita | null>(null);
  /** Día que venía propuesto al pulsar el «+» de una columna. */
  readonly diaPropuesto = input<string | null>(null);

  readonly cerrar = output<void>();
  readonly guardado = output<void>();

  protected readonly enviando = signal(false);
  protected readonly listaMotos = signal<MotoResumen[]>([]);
  protected readonly tecnicos = signal<Tecnico[]>([]);

  protected readonly fechaHora = signal('');
  protected readonly duracion = signal(1);
  protected readonly motoId = signal<number | null>(null);
  protected readonly contactoNombre = signal('');
  protected readonly contactoTelefono = signal('');
  protected readonly descripcionMoto = signal('');
  protected readonly motivo = signal('');
  protected readonly tecnicoId = signal<number | null>(null);
  protected readonly observaciones = signal('');

  protected readonly esAlta = computed(() => this.cita() === null);

  /** Sin moto del sistema hay que poder localizar a quien la trae. */
  protected readonly pideContacto = computed(() => this.motoId() === null);

  protected readonly puedeGuardar = computed(() => {
    if (this.enviando() || !this.fechaHora() || !this.motivo().trim() || this.duracion() <= 0) {
      return false;
    }
    if (this.pideContacto()) {
      return !!this.contactoNombre().trim() && !!this.contactoTelefono().trim();
    }
    return true;
  });

  constructor() {
    this.motos.buscar('', true, 0, 300).subscribe((p) => this.listaMotos.set(p.contenido));
    this.usuarios.tecnicos().subscribe((t) => this.tecnicos.set(t));

    // El valor de `input()` no está puesto todavía cuando corre el constructor.
    queueMicrotask(() => {
      const c = this.cita();
      if (c) {
        this.fechaHora.set(paraInput(c.fechaHora));
        this.duracion.set(c.duracionEstimada);
        this.motoId.set(c.motoId);
        this.contactoNombre.set(c.motoSinRegistrar ? (c.contactoNombre ?? '') : '');
        this.contactoTelefono.set(c.motoSinRegistrar ? (c.contactoTelefono ?? '') : '');
        this.descripcionMoto.set(c.motoSinRegistrar ? (c.moto ?? '') : '');
        this.motivo.set(c.motivo);
        this.tecnicoId.set(c.tecnicoId);
        this.observaciones.set(c.observaciones ?? '');
        return;
      }
      // Cita nueva: se propone el día que se pulsó, a las nueve de la mañana.
      const dia = this.diaPropuesto();
      this.fechaHora.set(dia ? `${dia}T09:00` : paraInput(new Date().toISOString()));
    });
  }

  protected guardar(): void {
    if (!this.puedeGuardar()) return;
    this.enviando.set(true);

    const sinMoto = this.pideContacto();
    const datos: DatosCita = {
      // El input da hora local; la API trabaja en instantes.
      fechaHora: new Date(this.fechaHora()).toISOString(),
      duracionEstimada: this.duracion(),
      motoId: this.motoId(),
      contactoNombre: sinMoto ? this.contactoNombre().trim() : null,
      contactoTelefono: sinMoto ? this.contactoTelefono().trim() : null,
      descripcionMoto: sinMoto ? this.descripcionMoto().trim() || null : null,
      motivo: this.motivo().trim(),
      tecnicoId: this.tecnicoId(),
      observaciones: this.observaciones().trim() || null,
    };

    const existente = this.cita();
    const peticion = existente
      ? this.servicio.actualizar(existente.id, datos)
      : this.servicio.agendar(datos);

    peticion.subscribe({
      next: () => {
        this.enviando.set(false);
        this.notificaciones.exito(existente ? 'Cita actualizada.' : 'Cita apuntada en la agenda.');
        this.guardado.emit();
      },
      // El interceptor ya enseña el motivo: por ejemplo, que esa moto ya tiene
      // una cita sin cerrar.
      error: () => this.enviando.set(false),
    });
  }
}
