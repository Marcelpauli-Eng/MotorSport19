import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Avisos } from './compartido/avisos';
import { Icono, NombreIcono } from './compartido/icono';
import { EmpezarJornada } from './funcionalidades/fichajes/empezar-jornada';
import { FichajesService } from './nucleo/servicios/fichajes.service';
import { NotificacionesService } from './nucleo/servicios/notificaciones.service';
import { Permiso, SesionService } from './nucleo/servicios/sesion.service';
import { SinConexionService } from './nucleo/servicios/sin-conexion.service';
import { TiempoRealService, alCambiarDatos } from './nucleo/servicios/tiempo-real.service';

interface Enlace {
  ruta: string;
  texto: string;
  icono: NombreIcono;
  /**
   * Permisos que abren el enlace. Sin permisos, lo ve todo el mundo.
   *
   * Es el MISMO permiso que exige la ruta y que exige la API. Antes eran roles,
   * y por eso un rol a medida —un jefe de taller con FACTURAS_VER pero sin
   * IMPORTES_VER— no veía la entrada en el menú aunque el servidor le dejara
   * entrar: no encajaba en ninguno de los tres perfiles de siempre.
   */
  permisos?: Permiso[];
}

interface Grupo {
  titulo: string;
  enlaces: Enlace[];
}

/**
 * Armazón de la aplicación: barra lateral fija en escritorio y desplegable en
 * tablet, más la pila de avisos.
 *
 * Sin sesión iniciada no se pinta el armazón: la pantalla de entrada ocupa el
 * hueco entera, para que no haya menús a la vista antes de identificarse.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Avisos, Icono, EmpezarJornada],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly router = inject(Router);
  protected readonly sesion = inject(SesionService);
  protected readonly menuAbierto = signal(false);
  protected readonly menuColapsado = signal(false);

  /**
   * El menú va agrupado por para qué sirve cada cosa, no en una lista corrida.
   * Con seis entradas seguidas hay que leerlas todas; con dos grupos de tres, se
   * va directo al que toca.
   */
  private readonly todosLosGrupos: Grupo[] = [
    {
      titulo: 'Taller',
      enlaces: [
        { ruta: '/panel', texto: 'Panel', icono: 'panel' },
        { ruta: '/agenda', texto: 'Agenda', icono: 'agenda', permisos: ['AGENDA_VER'] },
        {
          ruta: '/ordenes',
          texto: 'Órdenes de trabajo',
          icono: 'ordenes',
          permisos: ['ORDENES_VER'],
        },
        {
          ruta: '/inventario',
          texto: 'Inventario',
          icono: 'inventario',
          permisos: ['ALMACEN_VER'],
        },
      ],
    },
    {
      titulo: 'Clientes',
      enlaces: [
        { ruta: '/clientes', texto: 'Clientes', icono: 'clientes', permisos: ['CLIENTES_VER'] },
        { ruta: '/motos', texto: 'Motos', icono: 'motos', permisos: ['MOTOS_VER'] },
      ],
    },
    {
      titulo: 'Administración',
      enlaces: [
        {
          ruta: '/adelantar-ot',
          texto: 'Adelantar OT',
          icono: 'ordenes',
          permisos: ['ORDENES_PREPARAR'],
        },
        {
          ruta: '/plantillas',
          texto: 'Plantillas',
          icono: 'ordenes',
          permisos: ['SERVICIOS_GESTIONAR'],
        },
        { ruta: '/facturas', texto: 'Facturas', icono: 'facturas', permisos: ['FACTURAS_VER'] },
        { ruta: '/informes', texto: 'Informes', icono: 'informes', permisos: ['INFORMES_VER'] },
        { ruta: '/horas', texto: 'Control de horas', icono: 'reloj', permisos: ['FICHAJES_VER'] },
        { ruta: '/ajustes', texto: 'Ajustes', icono: 'ajustes', permisos: ['AJUSTES_VER'] },
      ],
    },
  ];

  /** Solo los enlaces que el rol puede abrir, y sin grupos que queden vacíos. */
  protected readonly grupos = computed<Grupo[]>(() =>
    this.todosLosGrupos
      .map((g) => ({
        titulo: g.titulo,
        enlaces: g.enlaces.filter((e) => !e.permisos || this.sesion.tienePermiso(...e.permisos)),
      }))
      .filter((g) => g.enlaces.length > 0),
  );

  /**
   * El rol tal y como se llama.
   *
   * Ya no hay una tabla de tres nombres que traducir: el rol lo crea el
   * administrador y su nombre es el que él le puso, así que se enseña tal cual.
   */
  protected readonly rolLegible = computed(() => this.sesion.rol() ?? '');

  /** Iniciales para el avatar: «Javier Ortega Marín» → «JO». */
  protected readonly iniciales = computed(() => {
    const nombre = this.sesion.usuario()?.nombreCompleto ?? '';
    const partes = nombre.trim().split(/\s+/).filter(Boolean);
    if (partes.length === 0) return '?';
    return (partes[0][0] + (partes[1]?.[0] ?? '')).toUpperCase();
  });

  protected readonly fichajes = inject(FichajesService);
  private readonly avisos = inject(NotificacionesService);
  protected readonly cerrandoJornada = signal(false);

  protected readonly tiempoReal = inject(TiempoRealService);
  protected readonly sinConexion = inject(SinConexionService);

  /**
   * Si hay que taparle el programa entero con la pantalla de fichar.
   *
   * <p>Solo cuando ya se sabe: mientras la consulta está en vuelo la jornada es
   * nula y no se tapa nada. Tapar por defecto haría parpadear la pantalla de
   * fichar en cada recarga, también a quien ya había fichado.
   *
   * <p>Quien tiene el permiso de exención —la dirección— no ve nada de esto.
   * Aquí solo se decide qué se pinta; quien lo impide de verdad es el servidor.
   */
  protected readonly debeFicharYNoHaFichado = computed(() => {
    if (!this.sesion.autenticado() || this.sesion.tienePermiso('FICHAJE_EXENTO')) return false;
    const j = this.fichajes.jornada();
    return j !== null && !j.abierta;
  });

  /**
   * La hora de ahora, refrescada sola.
   *
   * <p>El servidor solo dice los minutos que llevabas cuando se le preguntó, y
   * eso fue al entrar. Sin este reloj el contador se queda clavado toda la
   * jornada y solo salta al fichar la salida.
   */
  private readonly ahora = signal(Date.now());

  /** «3:20:05» corriendo desde la hora de entrada, en hora del servidor. */
  protected readonly jornadaLegible = computed(() => {
    const j = this.fichajes.jornada();
    if (!j) return FichajesService.reloj(0);
    return FichajesService.reloj(
      this.fichajes.segundosDe(
        { inicio: j.inicio, abierta: j.abierta, segundos: j.segundosTrabajados },
        this.ahora(),
      ),
    );
  });

  constructor() {
    // Cada segundo: un contador que no se mueve parece averiado.
    const reloj = setInterval(() => this.ahora.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(reloj));

    // Si hay token guardado se confirma contra el servidor. Cuando ha caducado,
    // el interceptor recibe el 401, cierra la sesión y lleva a la entrada: sin
    // esto la interfaz quedaría montada y el usuario solo lo descubriría al
    // pulsar algo.
    if (this.sesion.autenticado() && !this.sesion.tienePermiso('FICHAJE_EXENTO')) {
      this.sesion.revalidar().subscribe({ error: () => {} });
      this.fichajes.consultar().subscribe({ error: () => {} });
    } else if (this.sesion.autenticado()) {
      this.sesion.revalidar().subscribe({ error: () => {} });
    }

    // Y luego cada 20 segundos, mientras haya sesión: si a alguien lo dan de
    // baja o le cambian el rol desde otro puesto, el servidor ya lo aplica en
    // el momento (FiltroSesionViva lo comprueba en cada petición), pero sin
    // este ping periódico nadie se lo pregunta hasta que el usuario navega o
    // recarga. Con esto un técnico bloqueado se queda fuera solo, sin que
    // nadie tenga que refrescarle la pantalla.
    const intervaloSesion = setInterval(() => {
      if (this.sesion.autenticado()) {
        this.sesion.revalidar().subscribe({ error: () => {} });
      }
    }, 20_000);
    inject(DestroyRef).onDestroy(() => clearInterval(intervaloSesion));

    // Al entrar o salir hay que volver a preguntar: el que acaba de
    // identificarse todavía no tiene jornada consultada, y el que sale no
    // puede dejar la suya visible para el siguiente.
    // Si dirección cambia el rol de alguien, sus menús y botones cambian al
    // momento; y la jornada, si se la cierran desde Control de horas.
    alCambiarDatos(() => {
      if (!this.sesion.autenticado()) return;
      this.sesion.revalidar().subscribe({ error: () => {} });
      if (!this.sesion.tienePermiso('FICHAJE_EXENTO'))
        this.fichajes.consultar().subscribe({ error: () => {} });
    });

    effect(() => {
      if (this.sesion.autenticado() && !this.sesion.tienePermiso('FICHAJE_EXENTO')) {
        this.fichajes.consultar().subscribe({ error: () => {} });
      } else {
        this.fichajes.jornada.set(null);
      }
    });
  }

  protected terminarJornada(): void {
    if (this.cerrandoJornada()) return;
    // Queda en el registro de jornada, y corregirlo es cosa de dirección: el
    // botón dice «Salir», y un clic de quien solo quería salir de la pantalla
    // cerraba el día.
    if (!confirm('¿Terminar la jornada? Se registrará ahora la hora de salida.')) return;
    this.cerrandoJornada.set(true);
    this.fichajes.terminar().subscribe({
      next: (f) => {
        this.cerrandoJornada.set(false);
        this.avisos.exito(
          `Jornada cerrada: ${Math.floor(f.minutos / 60)} h ${f.minutos % 60} min.`,
        );
      },
      error: () => this.cerrandoJornada.set(false),
    });
  }

  protected alternarMenu(): void {
    this.menuAbierto.update((abierto) => !abierto);
  }

  protected alternarMenuColapsado(): void {
    this.menuColapsado.update((colapsado) => !colapsado);
  }

  protected cerrarMenu(): void {
    this.menuAbierto.set(false);
  }

  protected salir(): void {
    this.sesion.salir();
    void this.router.navigate(['/entrar']);
  }
}
