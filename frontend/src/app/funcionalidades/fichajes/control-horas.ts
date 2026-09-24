import { alCambiarDatos } from '../../nucleo/servicios/tiempo-real.service';
import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Cargando } from '../../compartido/cargando';
import { Dialogo } from '../../compartido/dialogo';
import { Icono } from '../../compartido/icono';
import { RouterLink } from '@angular/router';
import {
  ApunteActividad,
  Fichaje,
  FichajesService,
  PorTrabajador,
  ResumenFichajes,
} from '../../nucleo/servicios/fichajes.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { UsuariosService } from '../../nucleo/servicios/usuarios.service';
import { Usuario } from '../../nucleo/modelos/configuracion';

/** Un día del mes, con lo que se pueda elegir en el filtro rápido. */
type Rango = 'semana' | 'mes' | 'personalizado';

/**
 * Control de horas del taller.
 *
 * <p>Lo primero que se ve son las jornadas <b>que se quedaron abiertas</b>, y
 * no el total del mes. No es un capricho de orden: una jornada sin cerrar
 * bloquea a esa persona al día siguiente —no puede empezar la nueva— y además
 * falsea sus horas, así que es lo único de esta pantalla que hay que resolver
 * hoy mismo.
 *
 * <p>Debajo, las horas por trabajador. Se despliega para ver día a día, porque
 * lo que hay que poder enseñar a una inspección son las horas concretas de cada
 * jornada, no el total del periodo.
 */
@Component({
  selector: 'app-control-horas',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink, Icono, Cargando, Dialogo],
  templateUrl: './control-horas.html',
  styles: [
    `
      .resumen { display: grid; gap: var(--e2); grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); }
      .dato { padding: var(--e3); border: 1px solid var(--borde, #e5e7eb); border-radius: var(--radio, 8px); }
      .dato__valor { font-size: 1.6rem; font-weight: 700; font-variant-numeric: tabular-nums; }
      .dato__pie { font-size: 0.78rem; color: var(--gris-500, #6b7280); text-transform: uppercase; letter-spacing: 0.05em; }
      .fila-trabajador { cursor: pointer; }
      .fila-trabajador:hover { background: var(--gris-50, #f9fafb); }
      .horas { font-variant-numeric: tabular-nums; font-weight: 600; }
      /* El que esta trabajando ahora mismo, con el contador corriendo. */
      .horas--vivo { color: var(--verde); }
      .horas--alerta { color: var(--rojo); }
      .fila-jornada { cursor: pointer; }
      .actividad { list-style: none; margin: 0; padding: var(--e1) 0; display: grid; gap: 4px; }
      .actividad li { display: flex; align-items: baseline; gap: var(--e2); }
      .actividad__hora { font-variant-numeric: tabular-nums; color: var(--gris-500, #6b7280); }
      .marca--tipo {
        background: var(--gris-100, #f3f4f6);
        color: var(--gris-600, #4b5563);
        min-width: 64px;
        text-align: center;
      }
      .horas--vivo::before {
        content: '';
        display: inline-block;
        width: 7px; height: 7px;
        margin-right: 6px;
        border-radius: 50%;
        background: var(--verde);
        animation: latido 2s ease-in-out infinite;
      }
      @keyframes latido { 50% { opacity: 0.25; } }
      @media (prefers-reduced-motion: reduce) {
        .horas--vivo::before { animation: none; }
      }
      .detalle td { background: var(--gris-50, #f9fafb); font-size: 0.85rem; }
      .marca { font-size: 0.72rem; padding: 2px 6px; border-radius: 4px; white-space: nowrap; }
      .marca--abierta { background: #fdecea; color: #a32a20; }
      .marca--corregida { background: #fef6e0; color: #8a6a1f; }
      .marca--olvido { background: #eef0f3; color: #4b5563; }
      /* La flecha apunta abajo cerrada y arriba desplegada: no hace falta otro icono. */
      app-icono.girada { display: inline-block; transform: rotate(180deg); }
    `,
  ],
})
export class ControlHoras {
  private readonly fichajes = inject(FichajesService);
  private readonly usuariosServicio = inject(UsuariosService);
  private readonly avisos = inject(NotificacionesService);

  protected readonly cargando = signal(true);
  protected readonly resumen = signal<ResumenFichajes | null>(null);
  protected readonly abiertas = signal<Fichaje[]>([]);
  protected readonly usuarios = signal<Usuario[]>([]);

  protected readonly rango = signal<Rango>('semana');
  protected readonly desde = signal(this.lunesDeEstaSemana());
  protected readonly hasta = signal(this.hoy());
  protected readonly usuarioId = signal<number | null>(null);

  /** Qué jornada tiene la actividad desplegada, y lo que se hizo en ella. */
  protected readonly jornadaAbierta = signal<number | null>(null);
  protected readonly actividad = signal<ApunteActividad[]>([]);
  protected readonly cargandoActividad = signal(false);

  /** Qué trabajador está desplegado. Solo uno a la vez: si no, es ilegible. */
  protected readonly desplegado = signal<number | null>(null);

  // --- cierre a mano de una jornada olvidada
  protected readonly cerrando = signal<Fichaje | null>(null);
  protected readonly finManual = signal('');
  protected readonly motivoManual = signal('');
  protected readonly guardandoCierre = signal(false);

  /**
   * El texto de ayuda del diálogo de cierre.
   *
   * <p>Se arma aquí y no en la plantilla porque lleva un formato de fecha con
   * comillas dentro, y anidarlas en un binding rompe el analizador de Angular.
   */
  protected readonly subtituloCierre = computed(() => {
    const f = this.cerrando();
    if (!f) return '';
    const cuando = new Date(f.inicio).toLocaleString('es-ES', {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
    return `Empezó el ${cuando}. Pon la hora a la que se fue de verdad: queda registrado que la cerraste tú y por qué.`;
  });

  /**
   * La hora de ahora, refrescada sola.
   *
   * <p>Los minutos de una jornada abierta los calcula el servidor cuando se le
   * pregunta, o sea una sola vez. Sin este reloj, «lleva 2 h» seguiria diciendo
   * 2 h a media tarde, que es justo lo que la direccion no puede fiarse.
   */
  private readonly ahora = signal(Date.now());

  /**
   * Quien esta trabajando ahora mismo: jornadas abiertas que empezaron hoy.
   *
   * <p>Se separan de las olvidadas por la fecha y no por las horas que lleve
   * abierta. Un turno de noche que entra a las 22:00 lleva diez horas a las
   * ocho de la manana y sigue siendo alguien trabajando, no un despiste.
   */
  protected readonly trabajandoAhora = computed(() =>
    this.abiertas().filter((f) => this.esDeHoy(f.inicio)),
  );

  /** Las que se quedaron abiertas de otro dia. Esto si hay que resolverlo. */
  protected readonly olvidadas = computed(() =>
    this.abiertas().filter((f) => !this.esDeHoy(f.inicio)),
  );

  private esDeHoy(instante: string): boolean {
    const d = new Date(instante);
    const hoy = new Date(this.ahora());
    return (
      d.getFullYear() === hoy.getFullYear() &&
      d.getMonth() === hoy.getMonth() &&
      d.getDate() === hoy.getDate()
    );
  }

  /**
   * Lo que ha durado una jornada, «1:23:45».
   *
   * <p>Si sigue abierta, hasta ahora mismo, y entonces corre sola. Se cuenta
   * aqui y no se usan los minutos del servidor porque redondeados a minutos una
   * jornada corta sale «0 h», que no dice nada, y porque una abierta se quedaria
   * clavada en el momento en que se cargo la pantalla.
   */
  protected transcurrido(f: Fichaje): string {
    return FichajesService.reloj(this.segundosDe(f));
  }

  /** Lo que suma un trabajador en el periodo, con el mismo detalle. */
  protected totalDe(t: PorTrabajador): string {
    return FichajesService.reloj(t.detalle.reduce((suma, f) => suma + this.segundosDe(f), 0));
  }

  private segundosDe(f: Fichaje): number {
    return this.fichajes.segundosDe(f, this.ahora());
  }

  protected readonly totalLegible = computed(() => {
    const seg = (this.resumen()?.trabajadores ?? [])
      .flatMap((t) => t.detalle)
      .reduce((suma, f) => suma + this.segundosDe(f), 0);
    return FichajesService.reloj(seg);
  });

  constructor() {
    alCambiarDatos(() => this.cargar());
    this.usuariosServicio.listar().subscribe((u) => this.usuarios.set(u));
    this.cargar();

    // El contador de las jornadas abiertas corre solo, cada segundo.
    const reloj = setInterval(() => this.ahora.set(Date.now()), 1000);

    // Y cada minuto se vuelve a preguntar al servidor, para que aparezca solo
    // quien acaba de fichar y desaparezca quien acaba de irse. Sin esto habria
    // que recargar la pagina para enterarse de quien esta en el taller.
    const refresco = setInterval(() => this.refrescarAbiertas(), 60_000);

    inject(DestroyRef).onDestroy(() => {
      clearInterval(reloj);
      clearInterval(refresco);
    });
  }

  /** Solo la lista de abiertas: es lo unico que cambia sin tocar los filtros. */
  private refrescarAbiertas(): void {
    this.fichajes.abiertas().subscribe({
      next: (a) => this.abiertas.set(a),
      error: () => {},
    });
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.fichajes.periodo(this.desde(), this.hasta(), this.usuarioId()).subscribe({
      next: (r) => {
        this.resumen.set(r);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
    this.fichajes.abiertas().subscribe((a) => this.abiertas.set(a));
  }

  protected elegirRango(r: Rango): void {
    this.rango.set(r);
    if (r === 'semana') {
      this.desde.set(this.lunesDeEstaSemana());
      this.hasta.set(this.hoy());
    } else if (r === 'mes') {
      const h = new Date();
      this.desde.set(this.aTexto(new Date(h.getFullYear(), h.getMonth(), 1)));
      this.hasta.set(this.hoy());
    }
    if (r !== 'personalizado') this.cargar();
  }

  /** Despliega lo que se hizo en esa jornada, pidiendolo solo la primera vez. */
  protected alternarActividad(f: Fichaje): void {
    if (this.jornadaAbierta() === f.id) {
      this.jornadaAbierta.set(null);
      return;
    }
    this.jornadaAbierta.set(f.id);
    this.actividad.set([]);
    this.cargandoActividad.set(true);
    this.fichajes.actividad(f.id).subscribe({
      next: (a) => {
        this.actividad.set(a);
        this.cargandoActividad.set(false);
      },
      error: () => this.cargandoActividad.set(false),
    });
  }

  /** A donde lleva un apunte al pulsarlo. */
  protected rutaDe(a: ApunteActividad): unknown[] {
    const seccion: Record<string, string> = {
      orden: '/ordenes',
      factura: '/facturas',
      cliente: '/clientes',
      moto: '/motos',
      inventario: '/inventario',
    };
    // La agenda no tiene pagina por cita: es un calendario. Se entra por la
    // ruta normal y se le dice cual abrir, y ella se coloca en ese dia.
    if (a.enlaceTipo === 'agenda') return ['/agenda'];
    return [seccion[a.enlaceTipo ?? ''] ?? '/panel', a.enlaceId];
  }

  /** Lo que va detras del interrogante, si hace falta algo. */
  protected parametrosDe(a: ApunteActividad): Record<string, unknown> {
    return a.enlaceTipo === 'agenda' ? { cita: a.enlaceId } : {};
  }

  protected alternar(usuarioId: number): void {
    this.desplegado.update((actual) => (actual === usuarioId ? null : usuarioId));
  }

  // --- cierre manual ---------------------------------------------------

  protected abrirCierre(f: Fichaje): void {
    this.cerrando.set(f);
    // Se propone la hora de ahora, pero se puede cambiar: lo normal es que la
    // salida real fuera ayer por la tarde.
    this.finManual.set(this.aTextoLocal(new Date()));
    this.motivoManual.set('');
  }

  protected confirmarCierre(): void {
    const f = this.cerrando();
    if (!f || this.guardandoCierre()) return;

    this.guardandoCierre.set(true);
    this.fichajes
      .cerrarPorOlvido(f.id, new Date(this.finManual()).toISOString(), this.motivoManual())
      .subscribe({
        next: () => {
          this.guardandoCierre.set(false);
          this.cerrando.set(null);
          this.avisos.exito(`Jornada de ${f.usuarioNombre} cerrada.`);
          this.cargar();
        },
        error: () => this.guardandoCierre.set(false),
      });
  }

  protected cancelarCierre(): void {
    this.cerrando.set(null);
  }

  // --- fechas ----------------------------------------------------------

  private hoy(): string {
    return this.aTexto(new Date());
  }

  private lunesDeEstaSemana(): string {
    const d = new Date();
    // getDay() da 0 el domingo; se quiere el lunes como primer día.
    const diasDesdeLunes = (d.getDay() + 6) % 7;
    d.setDate(d.getDate() - diasDesdeLunes);
    return this.aTexto(d);
  }

  private aTexto(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  /** Para el input datetime-local, que no admite zona horaria. */
  private aTextoLocal(d: Date): string {
    return `${this.aTexto(d)}T${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }
}
