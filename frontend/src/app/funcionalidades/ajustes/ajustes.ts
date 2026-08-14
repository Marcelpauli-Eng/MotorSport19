import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { ConfiguracionTaller, Usuario } from '../../nucleo/modelos/configuracion';
import { SerieFactura, TipoFactura } from '../../nucleo/modelos/facturacion';
import { ConfiguracionService } from '../../nucleo/servicios/configuracion.service';
import { FacturasService } from '../../nucleo/servicios/facturas.service';
import { GrupoPermisos, Rol, RolesService } from '../../nucleo/servicios/roles.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { UsuariosService } from '../../nucleo/servicios/usuarios.service';
import { FormularioUsuario } from './formulario-usuario';

type Pestana = 'empresa' | 'series' | 'roles' | 'usuarios';

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
  styles: [
    `
      /* Alta de serie en la propia tarjeta: son cuatro campos y no merece una
         modal, igual que el alta de líneas del presupuesto. */
      .alta-serie {
        display: flex;
        align-items: flex-end;
        gap: var(--e2);
        flex-wrap: wrap;
        margin-bottom: var(--e3);
        padding: var(--e3);
        background: var(--azul-suave);
        border: 1px solid var(--azul-borde);
        border-radius: var(--radio);
      }

      .alta-serie .campo { margin-bottom: 0; }
      .alta-serie .crece { flex: 1 1 220px; min-width: 0; }

      /* ---------- Permisos de un rol ----------
         En columnas por área. Con cuarenta y cinco casillas en una sola lista
         no se encuentra nada; agrupadas por bloque se leen como el menú. */
      .permisos {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
        gap: var(--e4);
        margin-top: var(--e4);
      }

      .permisos__grupo {
        padding: var(--e3);
        border: var(--borde);
        border-radius: var(--radio);
      }

      .permisos__cabecera {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--e2);
        margin-bottom: var(--e2);
        padding-bottom: var(--e2);
        border-bottom: var(--borde);
      }

      .permisos__cabecera h3 {
        margin: 0;
        font-size: 0.8125rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.02em;
        color: var(--gris-500);
      }

      .permiso {
        display: flex;
        align-items: flex-start;
        gap: var(--e2);
        padding: 5px 4px;
        border-radius: var(--radio-s);
        cursor: pointer;
      }

      .permiso:hover { background: var(--gris-50); }

      /* El estilo general pone los input al ancho del formulario, y una casilla
         de 324 px deja el texto en cero. Aquí manda su tamaño de verdad. */
      .permiso input[type='checkbox'] {
        width: 16px;
        min-width: 16px;
        height: 16px;
        margin: 2px 0 0;
        flex: none;
      }
      .permiso__texto { display: flex; flex-direction: column; gap: 1px; min-width: 0; }

      .permiso__nombre { font-size: 0.875rem; color: var(--gris-700); }
      .permiso--puesto .permiso__nombre { color: var(--gris-900); font-weight: 500; }

      .permiso__detalle {
        font-size: 0.75rem;
        line-height: 1.35;
        color: var(--gris-500);
      }
    `,
  ],
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

  /**
   * Taller recién instalado: nadie ha guardado todavía los datos de la empresa.
   *
   * Hasta que se guarden no se puede abrir una orden ni emitir una factura,
   * porque la tarifa por hora y los datos fiscales salen de aquí.
   */
  protected readonly sinConfigurar = signal(false);
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
        this.recibir(c);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  /**
   * Deja la respuesta lista para el formulario.
   *
   * El taller sin estrenar llega con los campos a null, y un input atado a null
   * enseña "null" en vez de quedarse vacío.
   */
  private recibir(c: ConfiguracionTaller): void {
    const limpia: ConfiguracionTaller = {
      ...c,
      razonSocial: c.razonSocial ?? '',
      nif: c.nif ?? '',
      direccion: c.direccion ?? '',
      codigoPostal: c.codigoPostal ?? '',
      ciudad: c.ciudad ?? '',
      tarifaHoraDefecto: c.tarifaHoraDefecto ?? 0,
      capacidadDiariaHoras: c.capacidadDiariaHoras ?? 0,
    };
    this.datos.set(limpia);
    this.borrador.set({ ...limpia });
    this.sinConfigurar.set(!c.configurado);
  }

  protected cambiarPestana(p: Pestana): void {
    this.pestana.set(p);
    if (p === 'usuarios' && !this.usuarios().length) this.cargarUsuarios();
    if (p === 'series' && !this.series().length) this.cargarSeries();
    if (p === 'roles' && !this.roles().length) this.cargarRoles();
  }

  // ==================================================================
  // Roles y permisos
  // ==================================================================

  private readonly rolesServicio = inject(RolesService);

  protected readonly roles = signal<Rol[]>([]);
  protected readonly catalogo = signal<GrupoPermisos[]>([]);
  protected readonly cargandoRoles = signal(false);

  private cargarRoles(): void {
    this.cargandoRoles.set(true);
    // El catálogo lo sirve el backend: si se repitiera aquí, la pantalla podría
    // ofrecer casillas que no protegen nada.
    this.rolesServicio.catalogo().subscribe((c) => this.catalogo.set(c));
    this.rolesServicio.listar().subscribe({
      next: (r) => {
        this.roles.set(r);
        this.cargandoRoles.set(false);
      },
      error: () => this.cargandoRoles.set(false),
    });
  }

  // ----- Edición -----

  /** Rol que se está componiendo, o null si no hay ninguno abierto. */
  protected readonly rolEnEdicion = signal<Rol | null>(null);
  protected readonly esRolNuevo = signal(false);
  protected readonly rolNombre = signal('');
  protected readonly rolDescripcion = signal('');
  protected readonly rolPermisos = signal<Set<string>>(new Set());

  protected nuevoRol(): void {
    this.rolEnEdicion.set(null);
    this.esRolNuevo.set(true);
    this.rolNombre.set('');
    this.rolDescripcion.set('');
    this.rolPermisos.set(new Set());
  }

  protected editarRol(rol: Rol): void {
    this.rolEnEdicion.set(rol);
    this.esRolNuevo.set(false);
    this.rolNombre.set(rol.nombre);
    this.rolDescripcion.set(rol.descripcion ?? '');
    this.rolPermisos.set(new Set(rol.permisos));
  }

  protected cerrarEditorRol(): void {
    this.rolEnEdicion.set(null);
    this.esRolNuevo.set(false);
  }

  protected readonly editandoRol = computed(
    () => this.esRolNuevo() || this.rolEnEdicion() !== null,
  );

  protected tienePermisoMarcado(clave: string): boolean {
    return this.rolPermisos().has(clave);
  }

  protected alternarPermiso(clave: string): void {
    this.rolPermisos.update((actuales) => {
      const copia = new Set(actuales);
      if (copia.has(clave)) {
        copia.delete(clave);
      } else {
        copia.add(clave);
      }
      return copia;
    });
  }

  /** Marca o desmarca un bloque entero: «todo lo de almacén», y listo. */
  protected alternarGrupo(grupo: GrupoPermisos): void {
    const claves = grupo.permisos.map((p) => p.clave);
    const todosPuestos = claves.every((c) => this.rolPermisos().has(c));

    this.rolPermisos.update((actuales) => {
      const copia = new Set(actuales);
      claves.forEach((c) => (todosPuestos ? copia.delete(c) : copia.add(c)));
      return copia;
    });
  }

  protected grupoCompleto(grupo: GrupoPermisos): boolean {
    return grupo.permisos.every((p) => this.rolPermisos().has(p.clave));
  }

  protected readonly puedeGuardarRol = computed(
    () => !this.guardando() && this.rolNombre().trim().length > 0 && this.rolPermisos().size > 0,
  );

  protected guardarRol(): void {
    if (!this.puedeGuardarRol()) return;

    const datos = {
      nombre: this.rolNombre().trim(),
      descripcion: this.rolDescripcion().trim() || null,
      permisos: [...this.rolPermisos()],
    };

    this.guardando.set(true);
    const existente = this.rolEnEdicion();
    const peticion = existente
      ? this.rolesServicio.actualizar(existente.id, datos)
      : this.rolesServicio.crear(datos);

    peticion.subscribe({
      next: (rol) => {
        this.roles.update((lista) =>
          existente ? lista.map((r) => (r.id === rol.id ? rol : r)) : [...lista, rol],
        );
        this.guardando.set(false);
        this.cerrarEditorRol();
        this.notificaciones.exito(
          existente
            ? `Rol «${rol.nombre}» actualizado. Quien lo lleve lo verá al volver a entrar.`
            : `Rol «${rol.nombre}» creado con ${rol.permisos.length} permisos.`,
        );
      },
      error: () => this.guardando.set(false),
    });
  }

  protected alternarRol(rol: Rol): void {
    this.guardando.set(true);
    this.rolesServicio.cambiarEstado(rol.id, !rol.activo).subscribe({
      next: (actualizado) => {
        this.roles.update((lista) => lista.map((r) => (r.id === actualizado.id ? actualizado : r)));
        this.guardando.set(false);
      },
      error: () => this.guardando.set(false),
    });
  }

  protected borrarRol(rol: Rol): void {
    if (!confirm(`Se borrará el rol «${rol.nombre}». ¿Continuar?`)) return;

    this.guardando.set(true);
    this.rolesServicio.borrar(rol.id).subscribe({
      next: () => {
        this.roles.update((lista) => lista.filter((r) => r.id !== rol.id));
        this.guardando.set(false);
        this.notificaciones.exito(`Rol «${rol.nombre}» borrado.`);
      },
      error: () => this.guardando.set(false),
    });
  }

  // ==================================================================
  // Series de facturación
  // ==================================================================

  private readonly facturas = inject(FacturasService);

  protected readonly series = signal<SerieFactura[]>([]);
  protected readonly cargandoSeries = signal(false);

  /**
   * Sin ninguna serie abierta no se puede emitir una sola factura.
   *
   * Merece un aviso en toda regla y no una lista vacía: el botón de «Emitir
   * factura» de la orden se queda muerto por esto y no hay forma de adivinarlo
   * desde allí.
   */
  protected readonly sinSerieOrdinaria = computed(
    () => !this.series().some((s) => s.tipo === 'ORDINARIA' && s.activa),
  );

  private cargarSeries(): void {
    this.cargandoSeries.set(true);
    // Con las cerradas incluidas: aquí se mantienen, no se usan.
    this.facturas.series(false).subscribe({
      next: (s) => {
        this.series.set(s);
        this.cargandoSeries.set(false);
      },
      error: () => this.cargandoSeries.set(false),
    });
  }

  // ----- Alta -----

  protected readonly creandoSerie = signal(false);
  protected readonly nuevaSerieCodigo = signal('');
  protected readonly nuevaSerieEjercicio = signal(new Date().getFullYear());
  protected readonly nuevaSerieDescripcion = signal('');
  protected readonly nuevaSerieTipo = signal<TipoFactura>('ORDINARIA');

  protected abrirAltaSerie(tipo: TipoFactura = 'ORDINARIA'): void {
    // Se proponen los códigos de siempre: A para las ordinarias, R para las
    // rectificativas. Es lo que usa cualquier gestoría y ahorra la duda.
    this.nuevaSerieCodigo.set(tipo === 'ORDINARIA' ? 'A' : 'R');
    this.nuevaSerieEjercicio.set(new Date().getFullYear());
    this.nuevaSerieDescripcion.set('');
    this.nuevaSerieTipo.set(tipo);
    this.creandoSerie.set(true);
  }

  protected readonly puedeCrearSerie = computed(
    () =>
      !this.guardando() &&
      this.nuevaSerieCodigo().trim().length > 0 &&
      this.nuevaSerieEjercicio() >= 2000,
  );

  protected crearSerie(): void {
    if (!this.puedeCrearSerie()) return;

    this.guardando.set(true);
    this.facturas
      .crearSerie({
        codigo: this.nuevaSerieCodigo().trim().toUpperCase(),
        ejercicio: this.nuevaSerieEjercicio(),
        descripcion: this.nuevaSerieDescripcion().trim() || null,
        tipo: this.nuevaSerieTipo(),
      })
      .subscribe({
        next: (s) => {
          this.series.update((lista) => [s, ...lista]);
          this.creandoSerie.set(false);
          this.guardando.set(false);
          this.notificaciones.exito(
            `Serie ${s.codigo}/${s.ejercicio} abierta. La primera factura será la número 1.`,
          );
        },
        error: () => this.guardando.set(false),
      });
  }

  /**
   * Abre o cierra una serie.
   *
   * Cerrar no borra nada: las facturas emitidas se quedan donde están con su
   * numeración. Es lo que se hace al terminar el ejercicio.
   */
  protected alternarSerie(serie: SerieFactura): void {
    this.guardando.set(true);
    this.facturas
      .actualizarSerie(serie.id, { descripcion: serie.descripcion, activa: !serie.activa })
      .subscribe({
        next: (actualizada) => {
          this.series.update((lista) =>
            lista.map((s) => (s.id === actualizada.id ? actualizada : s)),
          );
          this.guardando.set(false);
          this.notificaciones.exito(
            actualizada.activa
              ? `Serie ${actualizada.codigo}/${actualizada.ejercicio} abierta.`
              : `Serie ${actualizada.codigo}/${actualizada.ejercicio} cerrada para nuevas facturas.`,
          );
        },
        error: () => this.guardando.set(false),
      });
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
          const eraPrimeraVez = this.sinConfigurar();
          this.recibir(c);
          this.guardando.set(false);
          this.notificaciones.exito(
            eraPrimeraVez
              ? 'Taller configurado. Ya se pueden abrir órdenes de trabajo y facturar.'
              : 'Datos del taller guardados. Las facturas ya emitidas no cambian.',
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
