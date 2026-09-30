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
import { HistorialJornada } from './historial-jornada';
import { Icono } from '../../compartido/icono';
import { RouterLink } from '@angular/router';
import {
  ApunteActividad,
  CambioFichaje,
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
  imports: [CommonModule, FormsModule, RouterLink, Icono, Cargando, Dialogo, HistorialJornada],
  templateUrl: './control-horas.html',
  styles: [
    `
      /* Los filtros apoyan en la misma linea de abajo: botones y campos a la par. */
      .mandos { display: flex; flex-wrap: wrap; align-items: flex-end; gap: var(--e3); }
      .mandos .campo { margin-bottom: 0; }
      .resumen { display: grid; gap: var(--e2); grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); }
      .dato { padding: var(--e3); border: 1px solid var(--borde, #e5e7eb); border-radius: var(--radio, 8px); }
      .dato__valor { font-size: 1.6rem; font-weight: 700; font-variant-numeric: tabular-nums; }
      .dato__pie { font-size: 0.78rem; color: var(--gris-500, #6b7280); text-transform: uppercase; letter-spacing: 0.05em; }
      .fila-trabajador { cursor: pointer; }
      .fila-trabajador:hover { background: var(--gris-50, #f9fafb); }
      /* Quien no ha fichado en el periodo sale igual, pero apagado. */
      .fila-trabajador--vacia { cursor: default; color: var(--gris-500, #6b7280); }
      .acciones { text-align: right; white-space: nowrap; }
      .detalle__titulo { font-weight: 600; margin: 0 0 4px; }
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
  protected readonly desde = signal(FichajesService.inicioDe('semana'));
  protected readonly hasta = signal(FichajesService.dia(new Date()));
  protected readonly usuarioId = signal<number | null>(null);

  /** Qué jornada tiene la actividad desplegada, y lo que se hizo en ella. */
  protected readonly jornadaAbierta = signal<number | null>(null);
  protected readonly actividad = signal<ApunteActividad[]>([]);
  protected readonly cargandoActividad = signal(false);
  /** Los cambios a mano de la jornada desplegada, con lo que había antes. */
  protected readonly cambios = signal<CambioFichaje[]>([]);

  /** Qué trabajador está desplegado. Solo uno a la vez: si no, es ilegible. */
  protected readonly desplegado = signal<number | null>(null);

  // --- cambio a mano de las horas: cerrar una olvidada o corregir una cerrada
  protected readonly editando = signal<Fichaje | null>(null);
  protected readonly inicioManual = signal('');
  protected readonly finManual = signal('');
  protected readonly motivoManual = signal('');
  protected readonly guardandoEdicion = signal(false);
  /** Lo que ponían los campos al abrir, para mandar solo lo que se ha tocado. */
  private inicioAlAbrir = '';
  private finAlAbrir = '';

  /**
   * El texto de ayuda del diálogo.
   *
   * <p>Se arma aquí y no en la plantilla porque lleva un formato de fecha con
   * comillas dentro, y anidarlas en un binding rompe el analizador de Angular.
   */
  protected readonly subtituloEdicion = computed(() => {
    const f = this.editando();
    if (!f) return '';
    if (!f.abierta) {
      return 'Lo que se fichó no se borra: queda en el historial de la jornada, con quién lo cambió y por qué.';
    }
    const cuando = new Date(f.inicio).toLocaleString('es-ES', {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
    return `Empezó el ${cuando}. Pon la hora a la que se fue de verdad: queda registrado que la cerraste tú y por qué.`;
  });

  protected sinCambios(): boolean {
    return (
      !this.editando()?.abierta &&
      this.inicioManual() === this.inicioAlAbrir &&
      this.finManual() === this.finAlAbrir
    );
  }

  /**
   * Todos los trabajadores, hayan fichado o no.
   *
   * <p>El servidor solo devuelve a quien tiene jornadas en el periodo. Pero lo
   * que se quiere ver es a cada persona del taller, y que alguien no haya
   * fichado en toda la semana es justo lo que tiene que saltar a la vista.
   */
  protected readonly filas = computed<PorTrabajador[]>(() => {
    const r = this.resumen();
    if (!r) return [];
    const conJornadas = new Set(r.trabajadores.map((t) => t.usuarioId));
    const elegido = this.usuarioId();
    const sinJornadas = this.usuarios()
      .filter((u) => u.activo && !conJornadas.has(u.id) && (elegido === null || u.id === elegido))
      .map((u) => ({
        usuarioId: u.id,
        usuarioNombre: u.nombreCompleto,
        minutos: 0,
        segundos: 0,
        horasLegibles: '',
        jornadas: 0,
        detalle: [],
      }));
    return [...r.trabajadores, ...sinJornadas].sort((a, b) =>
      a.usuarioNombre.localeCompare(b.usuarioNombre, 'es'),
    );
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

  protected esDeHoy(instante: string): boolean {
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
        // Mirando a una sola persona no hay nada que elegir: se abre sola.
        if (this.usuarioId()) this.desplegado.set(this.usuarioId());
      },
      error: () => this.cargando.set(false),
    });
    this.fichajes.abiertas().subscribe((a) => this.abiertas.set(a));
  }

  protected elegirRango(r: Rango): void {
    this.rango.set(r);
    if (r !== 'personalizado') {
      this.desde.set(FichajesService.inicioDe(r));
      this.hasta.set(FichajesService.dia(new Date()));
    }
    this.cargar();
  }

  /** Despliega lo que se hizo en esa jornada, pidiendolo solo la primera vez. */
  protected alternarActividad(f: Fichaje): void {
    if (this.jornadaAbierta() === f.id) {
      this.jornadaAbierta.set(null);
      return;
    }
    this.jornadaAbierta.set(f.id);
    this.actividad.set([]);
    this.cambios.set([]);
    if (f.corregida || f.cerradaPorOlvido) this.cargarCambios(f.id);
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

  private cargarCambios(fichajeId: number): void {
    this.fichajes.cambios(fichajeId).subscribe((c) => this.cambios.set(c));
  }

  protected readonly tramo = FichajesService.tramo;

  /**
   * El registro del periodo en una hoja de cálculo, con el historial de cada
   * jornada. Si hay un trabajador elegido, solo el suyo.
   */
  protected descargar(): void {
    const quien = this.usuarios().find((u) => u.id === this.usuarioId())?.nombreCompleto;
    const nombre = ['registro-jornada', quien, this.desde(), this.hasta()]
      .filter(Boolean)
      .join('_')
      .replace(/\s+/g, '-');
    this.fichajes.exportar(this.desde(), this.hasta(), this.usuarioId()).subscribe((csv) =>
      FichajesService.guardar(csv, `${nombre}.csv`),
    );
  }

  // --- cambio a mano de las horas ---------------------------------------

  /** Abierta: se cierra poniendo la salida. Cerrada: se corrigen entrada y salida. */
  protected abrirEdicion(f: Fichaje, evento?: Event): void {
    evento?.stopPropagation();
    this.editando.set(f);
    this.inicioAlAbrir = this.aTextoLocal(new Date(f.inicio));
    // Abierta, se propone la hora de ahora, pero se puede cambiar: lo normal
    // es que la salida real fuera ayer por la tarde.
    this.finAlAbrir = this.aTextoLocal(f.fin ? new Date(f.fin) : new Date());
    this.inicioManual.set(this.inicioAlAbrir);
    this.finManual.set(this.finAlAbrir);
    this.motivoManual.set('');
  }

  protected guardarEdicion(): void {
    const f = this.editando();
    if (!f || this.guardandoEdicion()) return;

    const iso = (texto: string) => new Date(texto).toISOString();
    // Lo que no se ha tocado va nulo y el servidor lo deja como estaba. Si se
    // mandara, la hora fichada perdería sus segundos al pasar por el campo.
    const peticion = f.abierta
      ? this.fichajes.cerrarPorOlvido(f.id, iso(this.finManual()), this.motivoManual())
      : this.fichajes.corregir(
          f.id,
          this.inicioManual() !== this.inicioAlAbrir ? iso(this.inicioManual()) : null,
          this.finManual() !== this.finAlAbrir ? iso(this.finManual()) : null,
          this.motivoManual(),
        );

    this.guardandoEdicion.set(true);
    peticion.subscribe({
      next: () => {
        this.guardandoEdicion.set(false);
        this.editando.set(null);
        this.avisos.exito(
          f.abierta ? `Jornada de ${f.usuarioNombre} cerrada.` : `Horas de ${f.usuarioNombre} cambiadas.`,
        );
        this.cargar();
        if (this.jornadaAbierta() === f.id) this.cargarCambios(f.id);
      },
      error: () => this.guardandoEdicion.set(false),
    });
  }

  protected cancelarEdicion(): void {
    this.editando.set(null);
  }

  // --- fechas ----------------------------------------------------------

  /** Para el input datetime-local, que no admite zona horaria. */
  private aTextoLocal(d: Date): string {
    return `${FichajesService.dia(d)}T${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }
}
