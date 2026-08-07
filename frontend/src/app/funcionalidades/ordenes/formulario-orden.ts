import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialogo } from '../../compartido/dialogo';
import { MotoResumen, OrdenTrabajo } from '../../nucleo/modelos/taller';
import { MotosService } from '../../nucleo/servicios/motos.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { OrdenesService } from '../../nucleo/servicios/ordenes.service';
import { Tecnico } from '../../nucleo/modelos/configuracion';
import { UsuariosService } from '../../nucleo/servicios/usuarios.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';

/**
 * Apertura de una orden de trabajo: la moto entra en el taller.
 *
 * <p>Lo que se apunta aquí es «lo que cuenta el cliente», con sus palabras. El
 * diagnóstico lo escribe después el técnico, y son dos cosas distintas a
 * propósito: una cosa es «hace un ruido raro al frenar» y otra «pastillas
 * delanteras al límite».
 *
 * <p>El kilometraje sirve para dos cosas: queda en la orden y actualiza el de
 * la moto, de modo que la próxima vez ya sale el último conocido.
 */
@Component({
  selector: 'app-formulario-orden',
  standalone: true,
  imports: [CommonModule, FormsModule, Dialogo],
  templateUrl: './formulario-orden.html',
})
export class FormularioOrden {
  private readonly servicio = inject(OrdenesService);
  private readonly motos = inject(MotosService);
  private readonly usuarios = inject(UsuariosService);
  private readonly notificaciones = inject(NotificacionesService);

  /** Moto ya elegida: entonces no se pregunta cuál entra. */
  readonly motoFijada = input<number | null>(null);

  protected readonly reparteTrabajo = inject(SesionService).puede('ADMIN', 'MOSTRADOR');

  readonly cerrar = output<void>();
  readonly abierta = output<OrdenTrabajo>();

  protected readonly enviando = signal(false);
  protected readonly listaMotos = signal<MotoResumen[]>([]);
  protected readonly tecnicos = signal<Tecnico[]>([]);

  protected readonly motoId = signal<number | null>(null);
  protected readonly tecnicoId = signal<number | null>(null);
  protected readonly kmEntrada = signal<number | null>(null);
  protected readonly problema = signal('');
  protected readonly fechaEstimada = signal('');
  protected readonly observaciones = signal('');

  protected readonly puedeGuardar = computed(
    () =>
      !this.enviando() &&
      this.motoId() !== null &&
      this.problema().trim().length > 0 &&
      this.kmEntrada() !== null,
  );

  /** Al elegir moto se propone su último kilometraje conocido. */
  protected elegirMoto(id: number | null): void {
    this.motoId.set(id);
    const moto = this.listaMotos().find((m) => m.id === id);
    if (moto && this.kmEntrada() === null) {
      this.kmEntrada.set(moto.kmActual);
    }
  }

  constructor() {
    this.motos.buscar('', true, 0, 300).subscribe((p) => {
      this.listaMotos.set(p.contenido);
      const fijada = this.motoFijada();
      if (fijada) this.elegirMoto(fijada);
    });
    // El listado de técnicos lo reserva la API a mostrador y dirección: a un
    // técnico que abra una orden no se le pregunta a quién asignarla.
    if (this.reparteTrabajo) {
      this.usuarios.tecnicos().subscribe((t) => this.tecnicos.set(t));
    }
  }

  protected guardar(): void {
    if (!this.puedeGuardar()) return;
    this.enviando.set(true);

    this.servicio
      .abrir({
        motoId: this.motoId()!,
        kmEntrada: this.kmEntrada()!,
        problemaReportado: this.problema().trim(),
        tecnicoId: this.tecnicoId(),
        fechaEstimadaSalida: this.fechaEstimada() || null,
        observaciones: this.observaciones().trim() || null,
      })
      .subscribe({
        next: (o) => {
          this.enviando.set(false);
          this.notificaciones.exito(`Orden ${o.codigo} abierta.`);
          this.abierta.emit(o);
        },
        // El interceptor ya enseña el motivo: por ejemplo, que esa moto ya
        // tiene una orden sin cerrar.
        error: () => this.enviando.set(false),
      });
  }
}
