import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialogo } from '../../compartido/dialogo';
import { ClienteResumen, Moto } from '../../nucleo/modelos/taller';
import { ClientesService } from '../../nucleo/servicios/clientes.service';
import { MotosService } from '../../nucleo/servicios/motos.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';

/**
 * Alta y edición de una moto.
 *
 * <p>Al dar de alta hay que decir de quién es: una moto sin dueño no sirve para
 * abrir una orden ni para facturar. Si el cliente viene dado desde fuera —por
 * ejemplo, al añadir una moto desde su ficha— el selector no aparece.
 */
@Component({
  selector: 'app-formulario-moto',
  standalone: true,
  imports: [CommonModule, FormsModule, Dialogo],
  templateUrl: './formulario-moto.html',
})
export class FormularioMoto {
  private readonly servicio = inject(MotosService);
  private readonly clientes = inject(ClientesService);
  private readonly notificaciones = inject(NotificacionesService);

  readonly moto = input<Moto | null>(null);
  /** Cliente ya elegido: entonces no se pregunta de quién es. */
  readonly clienteFijado = input<number | null>(null);

  readonly cerrar = output<void>();
  readonly guardado = output<Moto>();

  protected readonly enviando = signal(false);
  protected readonly listaClientes = signal<ClienteResumen[]>([]);

  protected readonly clienteId = signal<number | null>(null);
  protected readonly matricula = signal('');
  protected readonly marca = signal('');
  protected readonly modelo = signal('');
  protected readonly anio = signal<number | null>(null);
  protected readonly cilindrada = signal<number | null>(null);
  protected readonly color = signal('');
  protected readonly numeroBastidor = signal('');
  protected readonly kmActual = signal<number | null>(null);
  protected readonly observaciones = signal('');

  protected readonly esEdicion = computed(() => this.moto() !== null);

  protected readonly puedeGuardar = computed(
    () =>
      !this.enviando() &&
      this.matricula().trim().length > 0 &&
      this.marca().trim().length > 0 &&
      this.modelo().trim().length > 0 &&
      (this.esEdicion() || this.clienteId() !== null),
  );

  constructor() {
    // Solo los clientes activos: no tiene sentido matricular una moto a nombre
    // de alguien dado de baja.
    this.clientes.buscar('', true, 0, 200).subscribe((p) => this.listaClientes.set(p.contenido));

    queueMicrotask(() => {
      const fijado = this.clienteFijado();
      if (fijado) this.clienteId.set(fijado);

      const m = this.moto();
      if (!m) return;
      this.clienteId.set(m.clienteId);
      this.matricula.set(m.matricula);
      this.marca.set(m.marca);
      this.modelo.set(m.modelo);
      this.anio.set(m.anio);
      this.cilindrada.set(m.cilindrada);
      this.color.set(m.color ?? '');
      this.numeroBastidor.set(m.numeroBastidor ?? '');
      this.kmActual.set(m.kmActual);
      this.observaciones.set(m.observaciones ?? '');
    });
  }

  protected guardar(): void {
    if (!this.puedeGuardar()) return;
    this.enviando.set(true);

    const datos = {
      // La matrícula en mayúsculas: es como se escribe y como la busca todo el
      // mundo, y así no se crean duplicados por la caja.
      matricula: this.matricula().trim().toUpperCase(),
      marca: this.marca().trim(),
      modelo: this.modelo().trim(),
      anio: this.anio(),
      cilindrada: this.cilindrada(),
      color: vacioANulo(this.color()),
      numeroBastidor: this.numeroBastidor().trim().toUpperCase() || null,
      kmActual: this.kmActual(),
      observaciones: vacioANulo(this.observaciones()),
    };

    const existente = this.moto();
    const peticion = existente
      ? // Al editar no se manda el kilometraje: tiene su propia operación, que
        // ademas comprueba que no baje.
        this.servicio.actualizar(existente.id, {
          matricula: datos.matricula,
          marca: datos.marca,
          modelo: datos.modelo,
          anio: datos.anio,
          cilindrada: datos.cilindrada,
          color: datos.color,
          numeroBastidor: datos.numeroBastidor,
          observaciones: datos.observaciones,
        })
      : this.servicio.crear({ ...datos, clienteId: this.clienteId()! });

    peticion.subscribe({
      next: (m) => {
        this.enviando.set(false);
        this.notificaciones.exito(
          existente ? 'Moto actualizada.' : `Moto ${m.matricula} dada de alta.`,
        );
        this.guardado.emit(m);
      },
      error: () => this.enviando.set(false),
    });
  }
}

function vacioANulo(valor: string): string | null {
  const limpio = valor.trim();
  return limpio.length ? limpio : null;
}
