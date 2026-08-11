import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { ConfiguracionTaller, Usuario } from '../../nucleo/modelos/configuracion';
import { ConfiguracionService } from '../../nucleo/servicios/configuracion.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { UsuariosService } from '../../nucleo/servicios/usuarios.service';
import { FormularioUsuario } from './formulario-usuario';

type Pestana = 'empresa' | 'usuarios';

/**
 * Ajustes del taller: lo que hay que tener puesto antes de facturar.
 *
 * Los datos de la empresa se COPIAN dentro de cada factura al emitirla, así que
 * cambiarlos aquí no reescribe el histórico: una factura de hace dos años sigue
 * mostrando el domicilio que tenía el taller ese día. Por eso conviene dejarlos
 * bien puestos antes de emitir la primera.
 */
@Component({
  selector: 'app-ajustes',
  imports: [CommonModule, FormsModule, Cargando, Icono, FormularioUsuario],
  templateUrl: './ajustes.html',
})
export class Ajustes {
  private readonly configuracion = inject(ConfiguracionService);
  private readonly usuariosServicio = inject(UsuariosService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly sesion = inject(SesionService);

  /** Mostrador consulta los datos de la empresa; solo dirección los cambia. */
  protected readonly esAdmin = this.sesion.puede('ADMIN');

  protected readonly pestana = signal<Pestana>('empresa');
  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);

  protected readonly datos = signal<ConfiguracionTaller | null>(null);
  protected readonly usuarios = signal<Usuario[]>([]);
  protected readonly editando = signal<Usuario | null>(null);
  protected readonly creando = signal(false);

  /** Copia editable: el original se conserva para poder descartar los cambios. */
  protected readonly borrador = signal<ConfiguracionTaller | null>(null);

  protected readonly puedeGuardar = computed(() => {
    const b = this.borrador();
    return (
      !this.guardando() &&
      !!b &&
      !!b.razonSocial?.trim() &&
      !!b.nif?.trim() &&
      !!b.direccion?.trim() &&
      !!b.codigoPostal?.trim() &&
      !!b.ciudad?.trim() &&
      b.tarifaHoraDefecto > 0 &&
      b.capacidadDiariaHoras > 0
    );
  });

  constructor() {
    this.configuracion.obtener().subscribe({
      next: (c) => {
        this.datos.set(c);
        this.borrador.set({ ...c });
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  protected cambiarPestana(p: Pestana): void {
    this.pestana.set(p);
    if (p === 'usuarios' && !this.usuarios().length) this.cargarUsuarios();
  }

  /** Anota un cambio en el borrador sin tocar los datos guardados. */
  protected campo(nombre: keyof ConfiguracionTaller, valor: string | number): void {
    this.borrador.update((b) => (b ? { ...b, [nombre]: valor } : b));
  }

  protected descartar(): void {
    const original = this.datos();
    if (original) this.borrador.set({ ...original });
  }

  protected guardar(): void {
    const b = this.borrador();
    if (!b || !this.puedeGuardar()) return;

    this.guardando.set(true);
    this.configuracion
      .guardar({
        razonSocial: b.razonSocial.trim(),
        nif: b.nif.trim(),
        direccion: b.direccion.trim(),
        codigoPostal: b.codigoPostal.trim(),
        ciudad: b.ciudad.trim(),
        provincia: b.provincia,
        pais: b.pais,
        telefono: b.telefono,
        email: b.email,
        tarifaHoraDefecto: b.tarifaHoraDefecto,
        tipoIvaDefecto: b.tipoIvaDefecto,
        capacidadDiariaHoras: b.capacidadDiariaHoras,
      })
      .subscribe({
        next: (c) => {
          this.datos.set(c);
          this.borrador.set({ ...c });
          this.guardando.set(false);
          this.notificaciones.exito(
            'Datos del taller guardados. Las facturas ya emitidas no cambian.',
          );
        },
        error: () => this.guardando.set(false),
      });
  }

  // ----- Usuarios -----

  protected cargarUsuarios(): void {
    this.usuariosServicio.listar().subscribe((u) => this.usuarios.set(u));
  }

  protected cambiarEstado(usuario: Usuario): void {
    const peticion = usuario.activo
      ? this.usuariosServicio.darDeBaja(usuario.id)
      : this.usuariosServicio.reactivar(usuario.id);

    peticion.subscribe(() => {
      this.notificaciones.exito(
        usuario.activo
          ? `${usuario.nombreCompleto} ya no puede entrar en el programa.`
          : `${usuario.nombreCompleto} vuelve a tener acceso.`,
      );
      this.cargarUsuarios();
    });
  }

  protected trasGuardarUsuario(): void {
    this.creando.set(false);
    this.editando.set(null);
    this.cargarUsuarios();
  }
}
