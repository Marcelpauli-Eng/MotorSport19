import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialogo } from '../../compartido/dialogo';
import { Cliente, TipoDocumento } from '../../nucleo/modelos/taller';
import { ClientesService } from '../../nucleo/servicios/clientes.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';

/**
 * Alta y edición de un cliente.
 *
 * <p>Los datos fiscales van aparte y no son obligatorios. Es a propósito: en el
 * mostrador se apunta el teléfono de quien trae la moto y poco más, y pedir el
 * NIF antes de poder abrir la orden entorpecería la entrada. El sistema solo
 * los exige cuando llega el momento de facturar, y hasta entonces la ficha
 * avisa de que faltan.
 */
@Component({
  selector: 'app-formulario-cliente',
  standalone: true,
  imports: [CommonModule, FormsModule, Dialogo],
  templateUrl: './formulario-cliente.html',
})
export class FormularioCliente {
  private readonly servicio = inject(ClientesService);
  private readonly notificaciones = inject(NotificacionesService);

  /** Si viene un cliente, se edita; si no, se da de alta uno nuevo. */
  readonly cliente = input<Cliente | null>(null);

  readonly cerrar = output<void>();
  readonly guardado = output<Cliente>();

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly nombre = signal('');
  protected readonly apellidos = signal('');
  protected readonly telefono = signal('');
  protected readonly email = signal('');
  protected readonly tipoDocumento = signal<TipoDocumento | ''>('');
  protected readonly documento = signal('');
  protected readonly direccion = signal('');
  protected readonly codigoPostal = signal('');
  protected readonly ciudad = signal('');
  protected readonly provincia = signal('');
  protected readonly observaciones = signal('');

  protected readonly esEdicion = computed(() => this.cliente() !== null);
  protected readonly puedeGuardar = computed(
    () => !this.enviando() && this.nombre().trim().length > 0,
  );

  protected readonly tiposDocumento: { valor: TipoDocumento; texto: string }[] = [
    { valor: 'NIF', texto: 'NIF' },
    { valor: 'CIF', texto: 'CIF' },
    { valor: 'NIE', texto: 'NIE' },
    { valor: 'PASAPORTE', texto: 'Pasaporte' },
    { valor: 'OTRO', texto: 'Otro' },
  ];

  constructor() {
    // `input()` ya está resuelto al construirse el componente.
    queueMicrotask(() => {
      const c = this.cliente();
      if (!c) return;
      this.nombre.set(c.nombre);
      this.apellidos.set(c.apellidos ?? '');
      this.telefono.set(c.telefono ?? '');
      this.email.set(c.email ?? '');
      this.tipoDocumento.set(c.tipoDocumento ?? '');
      this.documento.set(c.documento ?? '');
      this.direccion.set(c.direccion ?? '');
      this.codigoPostal.set(c.codigoPostal ?? '');
      this.ciudad.set(c.ciudad ?? '');
      this.provincia.set(c.provincia ?? '');
      this.observaciones.set(c.observaciones ?? '');
    });
  }

  protected guardar(): void {
    if (!this.puedeGuardar()) return;
    this.enviando.set(true);
    this.error.set(null);

    const datos = {
      nombre: this.nombre().trim(),
      apellidos: vacioANulo(this.apellidos()),
      telefono: vacioANulo(this.telefono()),
      email: vacioANulo(this.email()),
      tipoDocumento: (this.tipoDocumento() || null) as TipoDocumento | null,
      documento: vacioANulo(this.documento()),
      direccion: vacioANulo(this.direccion()),
      codigoPostal: vacioANulo(this.codigoPostal()),
      ciudad: vacioANulo(this.ciudad()),
      provincia: vacioANulo(this.provincia()),
      observaciones: vacioANulo(this.observaciones()),
    };

    const existente = this.cliente();
    const peticion = existente
      ? this.servicio.actualizarContacto(existente.id, datos)
      : this.servicio.crear(datos);

    peticion.subscribe({
      next: (c) => {
        // Al editar, el contacto y los datos fiscales son dos llamadas: el
        // backend los separa porque cambiar un NIF no es lo mismo que cambiar
        // un teléfono.
        if (existente && this.hayDatosFiscales()) {
          this.servicio
            .actualizarDatosFiscales(existente.id, {
              tipoDocumento: datos.tipoDocumento,
              documento: datos.documento ?? '',
              direccion: datos.direccion ?? '',
              codigoPostal: datos.codigoPostal ?? '',
              ciudad: datos.ciudad ?? '',
              provincia: datos.provincia ?? '',
            })
            .subscribe({
              next: (actualizado) => this.terminar(actualizado),
              error: () => this.enviando.set(false),
            });
        } else {
          this.terminar(c);
        }
      },
      // El interceptor ya enseña el motivo que manda el servidor.
      error: () => this.enviando.set(false),
    });
  }

  private hayDatosFiscales(): boolean {
    return this.documento().trim().length > 0;
  }

  private terminar(c: Cliente): void {
    this.enviando.set(false);
    this.notificaciones.exito(
      this.esEdicion() ? 'Cliente actualizado.' : `Cliente «${c.nombreCompleto}» dado de alta.`,
    );
    this.guardado.emit(c);
  }
}

function vacioANulo(valor: string): string | null {
  const limpio = valor.trim();
  return limpio.length ? limpio : null;
}
