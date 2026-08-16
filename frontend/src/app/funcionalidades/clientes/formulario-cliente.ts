import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { Dialogo } from '../../compartido/dialogo';
import { Cliente, TipoDocumento } from '../../nucleo/modelos/taller';
import { ClientesService } from '../../nucleo/servicios/clientes.service';
import { CodigosPostalesService } from '../../nucleo/servicios/codigos-postales.service';
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
  private readonly codigosPostales = inject(CodigosPostalesService);

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

  /** Poblaciones del código postal cuando hay más de una: se ofrecen, no se eligen. */
  protected readonly ciudadesSugeridas = signal<string[]>([]);

  /**
   * Lo último que escribió el código postal por su cuenta.
   *
   * <p>Es lo que permite corregir la sugerencia cuando cambia el código sin
   * borrar jamás lo que haya tecleado una persona: solo se sobrescribe un campo
   * si sigue conteniendo exactamente lo que se puso ahí automáticamente.
   */
  private sugerido = { ciudad: '', provincia: '' };

  private readonly codigoTecleado = new Subject<string>();

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
    // Se espera a que pare de teclear. Sin esto, escribir «08820» dispara cinco
    // consultas y llegan desordenadas: `switchMap` además cancela la anterior,
    // así que manda siempre la última tecla y no la que conteste primero.
    this.codigoTecleado
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((cp) => this.codigosPostales.consultar(cp)),
        takeUntilDestroyed(),
      )
      .subscribe((datos) => {
        this.ciudadesSugeridas.set(datos.ciudades.length > 1 ? datos.ciudades : []);
        this.sugerir('provincia', datos.provincia);
        this.sugerir('ciudad', datos.ciudad);
      });

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

  /** Cada tecla del código postal. Actualiza el campo y pide la consulta. */
  protected escribirCodigoPostal(valor: string): void {
    this.codigoPostal.set(valor);
    this.codigoTecleado.next(valor);
  }

  /**
   * Pone en un campo lo que dice el código postal, sin pisar a nadie.
   *
   * <p>Dos reglas, y las dos importan:
   *
   * <ul>
   *   <li><b>No se toca lo que ha escrito una persona</b>, ni lo que traía la
   *       ficha de un cliente que se está editando. Quien corrige a mano una
   *       ciudad porque el callejero no la acierta no puede encontrarse con que
   *       el programa se la cambia otra vez al retocar un dígito.</li>
   *   <li><b>Lo que puso el autocompletado sí se retira</b> cuando deja de
   *       valer. Sin esto, cambiar el código postal de Madrid a uno de
   *       Barcelona dejaba «Madrid» escrito en la ciudad: el propio programa
   *       metía un dato falso en una dirección fiscal.</li>
   * </ul>
   */
  private sugerir(campo: 'ciudad' | 'provincia', valor: string | null): void {
    const destino = campo === 'ciudad' ? this.ciudad : this.provincia;
    const actual = destino().trim();

    // Solo es «nuestro» si está vacío o si sigue siendo exactamente lo último
    // que escribimos ahí. Cualquier otra cosa la ha puesto alguien.
    const loPusimosNosotros = actual === '' || actual === this.sugerido[campo];
    if (!loPusimosNosotros) return;

    destino.set(valor ?? '');
    this.sugerido[campo] = valor ?? '';
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
