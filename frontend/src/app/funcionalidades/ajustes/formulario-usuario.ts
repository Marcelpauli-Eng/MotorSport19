import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, switchMap } from 'rxjs';
import { Dialogo } from '../../compartido/dialogo';
import { Usuario } from '../../nucleo/modelos/configuracion';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { Rol } from '../../nucleo/servicios/sesion.service';
import { UsuariosService } from '../../nucleo/servicios/usuarios.service';

const MINIMO_PASSWORD = 8;

/**
 * Alta y edición de un usuario del taller.
 *
 * <p>El nombre de usuario no se cambia una vez creado: aparece firmando cada
 * movimiento de almacén y cada cambio de estado de una orden, y renombrarlo
 * dejaría ese historial hablando de alguien que ya no existe.
 *
 * <p>Al editar, la contraseña es opcional: se rellena solo cuando alguien la ha
 * perdido. Nadie, ni dirección, puede leer la anterior.
 */
@Component({
  selector: 'app-formulario-usuario',
  standalone: true,
  imports: [FormsModule, Dialogo],
  templateUrl: './formulario-usuario.html',
})
export class FormularioUsuario {
  private readonly servicio = inject(UsuariosService);
  private readonly notificaciones = inject(NotificacionesService);

  /** Usuario que se edita. Sin él, el formulario da de alta uno nuevo. */
  readonly usuario = input<Usuario | null>(null);

  readonly cerrar = output<void>();
  readonly guardado = output<void>();

  protected readonly enviando = signal(false);

  protected readonly nombreCompleto = signal('');
  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly email = signal('');
  protected readonly telefono = signal('');
  protected readonly rol = signal<Rol>('TECNICO');

  protected readonly esAlta = computed(() => this.usuario() === null);

  protected readonly problema = computed<string | null>(() => {
    const clave = this.password();
    if (clave && clave.length < MINIMO_PASSWORD) {
      return `La contraseña debe tener al menos ${MINIMO_PASSWORD} caracteres.`;
    }
    return null;
  });

  protected readonly puedeGuardar = computed(() => {
    if (this.enviando() || this.problema()) return false;
    if (!this.nombreCompleto().trim()) return false;
    if (this.esAlta()) {
      return !!this.username().trim() && this.password().length >= MINIMO_PASSWORD;
    }
    return true;
  });

  constructor() {
    // El valor de `input()` no está puesto todavía cuando corre el constructor.
    queueMicrotask(() => {
      const u = this.usuario();
      if (!u) return;
      this.nombreCompleto.set(u.nombreCompleto);
      this.username.set(u.username);
      this.email.set(u.email ?? '');
      this.telefono.set(u.telefono ?? '');
      this.rol.set(u.rol);
    });
  }

  protected guardar(): void {
    if (!this.puedeGuardar()) return;
    this.enviando.set(true);

    const datos = {
      nombreCompleto: this.nombreCompleto().trim(),
      email: this.email().trim() || null,
      telefono: this.telefono().trim() || null,
      rol: this.rol(),
    };

    const existente = this.usuario();
    if (!existente) {
      this.servicio
        .crear({ ...datos, username: this.username().trim(), password: this.password() })
        .subscribe({
          next: (creado) => this.terminar(`${creado.nombreCompleto} ya puede entrar.`),
          error: () => this.enviando.set(false),
        });
      return;
    }

    // La contraseña va en su propia petición: la API la separa a propósito para
    // que un cambio de nombre o de perfil no arrastre nunca credenciales.
    const clave = this.password();
    const peticion: Observable<unknown> = clave
      ? this.servicio
          .actualizar(existente.id, datos)
          .pipe(switchMap(() => this.servicio.restablecerPassword(existente.id, clave)))
      : this.servicio.actualizar(existente.id, datos);

    peticion.subscribe({
      next: () =>
        this.terminar(
          clave
            ? `Datos guardados y contraseña nueva para ${datos.nombreCompleto}.`
            : 'Datos guardados.',
        ),
      error: () => this.enviando.set(false),
    });
  }

  private terminar(mensaje: string): void {
    this.enviando.set(false);
    this.notificaciones.exito(mensaje);
    this.guardado.emit();
  }
}
