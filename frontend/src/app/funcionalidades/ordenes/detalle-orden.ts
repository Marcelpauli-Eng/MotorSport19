import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { ColorEstadoPipe } from '../../compartido/estado-ot.pipe';
import { EstadoOT, OrdenTrabajo, ResultadoConsumo } from '../../nucleo/modelos/taller';
import { SerieFactura } from '../../nucleo/modelos/facturacion';
import { FacturasService } from '../../nucleo/servicios/facturas.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { OrdenesService } from '../../nucleo/servicios/ordenes.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { Tecnico } from '../../nucleo/modelos/configuracion';
import { UsuariosService } from '../../nucleo/servicios/usuarios.service';

/** Acción que puede lanzarse desde la ficha, según el estado actual. */
interface Accion {
  destino: EstadoOT;
  texto: string;
  principal: boolean;
}

/** Un hito del recorrido de la orden por el taller. */
interface Paso {
  titulo: string;
  detalle: string;
  estados: EstadoOT[];
  hecho: boolean;
  actual: boolean;
}

/**
 * Ficha de una orden de trabajo: por dónde va y qué toca hacer ahora.
 *
 * <p>La pantalla está montada alrededor del recorrido de la moto por el taller,
 * porque eso es lo que se viene a mirar: en qué punto está y cuál es el
 * siguiente paso. Antes esto convivía con la composición del presupuesto en la
 * misma página y quedaba todo apelmazado; el presupuesto se ha llevado a su
 * propia pantalla, que es una tarea larga y necesita sitio.
 *
 * <p>Los botones de estado salen de `estadosPosibles`, que envía el propio
 * backend: así el mostrador solo ve las transiciones que la máquina de estados
 * permite, en lugar de probar y recibir un error.
 */
@Component({
  selector: 'app-detalle-orden',
  imports: [CommonModule, RouterLink, Cargando, ColorEstadoPipe, Icono, FormsModule],
  templateUrl: './detalle-orden.html',
  styleUrl: './detalle-orden.scss',
})
export class DetalleOrden {
  private readonly servicio = inject(OrdenesService);
  private readonly facturas = inject(FacturasService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly router = inject(Router);
  private readonly sesion = inject(SesionService);
  private readonly usuarios = inject(UsuariosService);

  readonly id = input.required<string>();

  protected readonly cargando = signal(true);
  protected readonly orden = signal<OrdenTrabajo | null>(null);
  protected readonly trabajando = signal(false);
  protected readonly resultadoConsumo = signal<ResultadoConsumo | null>(null);
  protected readonly serieOrdinaria = signal<SerieFactura | null>(null);

  private static readonly TEXTOS: Record<EstadoOT, string> = {
    RECIBIDA: 'Volver a recibida',
    PREPARADA: 'Preparar el trabajo',
    EN_DIAGNOSTICO: 'Iniciar diagnóstico',
    PRESUPUESTADA: 'Pasar a presupuestada',
    APROBADA: 'El cliente aprueba',
    EN_REPARACION: 'Entrar en reparación',
    ESPERANDO_PIEZAS: 'Marcar en espera de piezas',
    LISTA: 'Marcar lista para entregar',
    ENTREGADA: 'Entregar al cliente',
    RECHAZADA: 'El cliente rechaza',
  };

  /** Facturar y ver importes es cosa de mostrador y dirección. */
  protected readonly puedeFacturar = this.sesion.puede('ADMIN', 'MOSTRADOR');
  protected readonly vePrecios = this.puedeFacturar;

  /**
   * ¿Puede el usuario en curso trabajar esta orden?
   *
   * Un técnico solo la suya, o una que aún no sea de nadie: eso último es cómo
   * se hace cargo de un trabajo nuevo. Mostrador y dirección, todas. Es el mismo
   * criterio que aplica el backend, replicado aquí solo para no enseñar botones
   * que van a devolver 403.
   */
  protected readonly puedeTrabajarla = computed(() => {
    const o = this.orden();
    if (!o) return false;
    if (!this.sesion.puede('TECNICO')) return true;
    return o.tecnicoId === null || o.tecnicoId === this.sesion.usuario()?.id;
  });

  /**
   * Transiciones que el backend reserva a mostrador y dirección.
   *
   * Aprobar y rechazar los decide el cliente por teléfono o en el mostrador, y
   * entregar la moto implica cobrar: nada de eso pasa por el taller. Preparar
   * una orden es repartir trabajo, y el trabajo lo reparte quien lo ha vendido.
   */
  private static readonly SOLO_MOSTRADOR: EstadoOT[] = [
    'PREPARADA',
    'APROBADA',
    'RECHAZADA',
    'ENTREGADA',
  ];

  /**
   * La orden admite cambios, pero ninguno lo puede hacer este usuario.
   *
   * Distinguirlo importa: decirle a un técnico que la orden «ya no admite
   * cambios» cuando en realidad está esperando que el cliente conteste sería
   * mentirle, y acabaría preguntando por qué no le funciona el programa.
   */
  protected readonly esperaAMostrador = computed(() => {
    const o = this.orden();
    return !!o && this.puedeTrabajarla() && o.estadosPosibles.length > 0
      && this.acciones().length === 0;
  });

  /** Botones que tienen sentido ahora mismo para este usuario. */
  protected readonly acciones = computed<Accion[]>(() => {
    const o = this.orden();
    if (!o || !this.puedeTrabajarla()) return [];

    const esTecnico = this.sesion.puede('TECNICO');
    return o.estadosPosibles
      .filter((destino) => !esTecnico || !DetalleOrden.SOLO_MOSTRADOR.includes(destino))
      .map((destino) => ({
        destino,
        // Desde una orden preparada no se «entra en reparación»: se empieza el
        // trabajo que ya te han dejado hecho. Es lo mismo por dentro, pero al
        // técnico se le habla de lo que va a hacer.
        texto:
          destino === 'EN_REPARACION' && o.estado === 'PREPARADA'
            ? 'Empezar el trabajo'
            : (DetalleOrden.TEXTOS[destino] ?? destino),
        principal: destino !== 'RECHAZADA' && destino !== 'ESPERANDO_PIEZAS',
      }));
  });

  constructor() {
    queueMicrotask(() => this.cargar());
    // Un técnico no puede consultar las series ni el listado de técnicos:
    // pedirlas le provocaría un aviso de permisos nada más abrir cualquier orden.
    if (this.puedeFacturar) {
      this.facturas.series().subscribe((series) => {
        this.serieOrdinaria.set(series.find((s) => s.tipo === 'ORDINARIA' && s.activa) ?? null);
      });
      this.usuarios.tecnicos().subscribe((t) => this.tecnicos.set(t));
    }
  }

  // ==================================================================
  // El recorrido de la orden, paso a paso
  // ==================================================================

  /**
   * Estados por los que ya ha pasado esta orden.
   *
   * Sale del historial y no de comparar el estado actual contra una lista
   * ordenada: la máquina de estados tiene dos caminos y va y viene entre
   * reparación y espera de piezas, así que «posterior a» no significa nada.
   */
  private readonly visitados = computed<Set<EstadoOT>>(() => {
    const o = this.orden();
    if (!o) return new Set();
    return new Set(o.historial.map((h) => h.estadoNuevo));
  });

  /**
   * ¿Va por la vía corta?
   *
   * Una orden preparada por dirección no pasa por diagnóstico, presupuesto ni
   * aprobación: el trabajo ya venía cerrado con el cliente. Enseñarle esos tres
   * pasos como pendientes para siempre sería mentir sobre lo que falta.
   */
  protected readonly viaPreparada = computed(() => this.visitados().has('PREPARADA'));

  protected readonly pasos = computed<Paso[]>(() => {
    const o = this.orden();
    if (!o) return [];

    const definicion: { titulo: string; detalle: string; estados: EstadoOT[] }[] = this.viaPreparada()
      ? [
          { titulo: 'Recepción', detalle: 'La moto entra en el taller', estados: ['RECIBIDA'] },
          {
            titulo: 'Preparación',
            detalle: 'Dirección compone el trabajo y lo asigna',
            estados: ['PREPARADA'],
          },
          {
            titulo: 'Reparación',
            detalle: 'El técnico la trabaja y consume el material',
            estados: ['EN_REPARACION', 'ESPERANDO_PIEZAS'],
          },
          { titulo: 'Entrega', detalle: 'Lista y entregada al cliente', estados: ['LISTA', 'ENTREGADA'] },
        ]
      : [
          { titulo: 'Recepción', detalle: 'La moto entra en el taller', estados: ['RECIBIDA'] },
          {
            titulo: 'Diagnóstico',
            detalle: 'El técnico mira qué le pasa',
            estados: ['EN_DIAGNOSTICO'],
          },
          {
            titulo: 'Presupuesto',
            detalle: 'Se compone y se le pasa al cliente',
            estados: ['PRESUPUESTADA'],
          },
          {
            titulo: 'Aprobación',
            detalle: 'El cliente dice que sí o que no',
            estados: ['APROBADA', 'RECHAZADA'],
          },
          {
            titulo: 'Reparación',
            detalle: 'Se trabaja y se consume el material',
            estados: ['EN_REPARACION', 'ESPERANDO_PIEZAS'],
          },
          { titulo: 'Entrega', detalle: 'Lista y entregada al cliente', estados: ['LISTA', 'ENTREGADA'] },
        ];

    const visitados = this.visitados();
    return definicion.map((paso) => ({
      ...paso,
      actual: paso.estados.includes(o.estado),
      hecho: paso.estados.some((e) => visitados.has(e)) && !paso.estados.includes(o.estado),
    }));
  });

  /** El paso en el que está ahora mismo, para titular la tarjeta de acciones. */
  protected readonly pasoActual = computed(() => this.pasos().find((p) => p.actual) ?? null);

  // ==================================================================
  // Resumen del presupuesto
  // ==================================================================

  /**
   * Las líneas, o una lista vacía.
   *
   * El interrogante va también en `lineas` y no solo en `orden`: una respuesta a
   * medias —una sesión que caduca a mitad de carga— dejaba la pantalla en blanco
   * con un error en consola en vez de avisar de lo que pasaba.
   */
  private readonly lineas = computed(() => this.orden()?.lineas ?? []);

  protected readonly numManoDeObra = computed(
    () => this.lineas().filter((l) => l.tipo === 'MANO_DE_OBRA').length,
  );

  protected readonly numMateriales = computed(
    () => this.lineas().filter((l) => l.tipo === 'PIEZA').length,
  );

  protected readonly sinPresupuesto = computed(() => this.lineas().length === 0);

  // ==================================================================
  // Técnico asignado
  // ==================================================================

  protected readonly tecnicos = signal<Tecnico[]>([]);

  /**
   * Quién puede repartir el trabajo.
   *
   * Mostrador y dirección eligen a cualquiera. Un técnico no reasigna nada: lo
   * único que puede es cogerse una orden que todavía no es de nadie, y eso se
   * ofrece aparte.
   */
  protected readonly puedeReasignar = this.puedeFacturar;

  protected readonly puedeCogerLaOrden = computed(
    () => this.sesion.puede('TECNICO') && this.orden()?.tecnicoId === null,
  );

  protected asignarTecnico(tecnicoId: number | null): void {
    const o = this.orden();
    if (!o || tecnicoId === o.tecnicoId) return;

    this.trabajando.set(true);
    this.servicio.asignarTecnico(o.id, tecnicoId).subscribe({
      next: (actualizada) => {
        this.orden.set(actualizada);
        this.trabajando.set(false);
        this.notificaciones.exito(
          actualizada.tecnicoNombre
            ? `La orden pasa a ${actualizada.tecnicoNombre}.`
            : 'La orden queda sin asignar.',
        );
      },
      error: () => this.trabajando.set(false),
    });
  }

  protected cogerLaOrden(): void {
    const yo = this.sesion.usuario();
    if (yo) this.asignarTecnico(yo.id);
  }

  // ==================================================================
  // Diagnóstico y observaciones
  // ==================================================================

  protected readonly editandoDiagnostico = signal(false);
  protected readonly borradorDiagnostico = signal('');

  protected empezarDiagnostico(): void {
    this.borradorDiagnostico.set(this.orden()?.diagnostico ?? '');
    this.editandoDiagnostico.set(true);
  }

  protected guardarDiagnostico(): void {
    const o = this.orden();
    const texto = this.borradorDiagnostico().trim();
    if (!o || !texto) return;

    this.trabajando.set(true);
    this.servicio.registrarDiagnostico(o.id, texto).subscribe({
      next: (actualizada) => {
        this.orden.set(actualizada);
        this.editandoDiagnostico.set(false);
        this.trabajando.set(false);
        this.notificaciones.exito('Diagnóstico guardado.');
      },
      error: () => this.trabajando.set(false),
    });
  }

  protected readonly editandoObservaciones = signal(false);
  protected readonly borradorObservaciones = signal('');

  protected abrirObservaciones(): void {
    this.borradorObservaciones.set(this.orden()?.observaciones ?? '');
    this.editandoObservaciones.set(true);
  }

  protected guardarObservaciones(): void {
    const o = this.orden();
    if (!o) return;

    this.trabajando.set(true);
    this.servicio
      .actualizarDatos(o.id, {
        fechaEstimadaSalida: o.fechaEstimadaSalida,
        observaciones: this.borradorObservaciones().trim() || null,
      })
      .subscribe({
        next: (actualizada) => {
          this.orden.set(actualizada);
          this.editandoObservaciones.set(false);
          this.trabajando.set(false);
          this.notificaciones.exito('Observaciones guardadas.');
        },
        error: () => this.trabajando.set(false),
      });
  }

  // ==================================================================
  // Transiciones de estado
  // ==================================================================

  protected ejecutar(destino: EstadoOT): void {
    const o = this.orden();
    if (!o) return;

    this.trabajando.set(true);
    this.resultadoConsumo.set(null);

    const terminar = () => {
      this.trabajando.set(false);
      this.cargar();
    };
    const fallo = () => this.trabajando.set(false);

    switch (destino) {
      case 'EN_DIAGNOSTICO':
        this.servicio.iniciarDiagnostico(o.id, o.tecnicoId).subscribe({ next: terminar, error: fallo });
        break;

      case 'PREPARADA':
        // Se le pasa el técnico que ya tenga puesto para que el cambio de
        // estado y la asignación queden en el mismo apunte del historial.
        this.servicio.preparar(o.id, o.tecnicoId).subscribe({ next: terminar, error: fallo });
        break;

      case 'PRESUPUESTADA':
        this.servicio.presupuestar(o.id).subscribe({ next: terminar, error: fallo });
        break;

      case 'APROBADA': {
        const quien = prompt('¿Quién aprueba el presupuesto?', o.clienteNombre);
        if (quien === null) return this.trabajando.set(false);
        this.servicio.aprobar(o.id, quien).subscribe({ next: terminar, error: fallo });
        break;
      }

      case 'RECHAZADA': {
        const motivo = prompt('Motivo del rechazo del presupuesto:');
        if (!motivo) return this.trabajando.set(false);
        this.servicio.rechazar(o.id, motivo).subscribe({ next: terminar, error: fallo });
        break;
      }

      case 'EN_REPARACION': {
        // Aquí es donde se consume el almacén. Si falta material la petición no
        // falla: la orden queda en espera y el resultado dice qué hay que pedir.
        const peticion =
          o.estado === 'ESPERANDO_PIEZAS'
            ? this.servicio.reanudarReparacion(o.id)
            : this.servicio.iniciarReparacion(o.id);

        peticion.subscribe({
          next: (resultado) => {
            this.resultadoConsumo.set(resultado);
            if (resultado.completo) {
              this.notificaciones.exito(resultado.mensaje);
            } else {
              this.notificaciones.info(resultado.mensaje);
            }
            terminar();
          },
          error: fallo,
        });
        break;
      }

      case 'LISTA':
        this.servicio.marcarLista(o.id).subscribe({ next: terminar, error: fallo });
        break;

      case 'ENTREGADA':
        if (!confirm('Al entregar, la orden queda congelada y ya no admitirá cambios. ¿Continuar?')) {
          return this.trabajando.set(false);
        }
        this.servicio.entregar(o.id).subscribe({ next: terminar, error: fallo });
        break;

      default:
        this.trabajando.set(false);
    }
  }

  protected facturar(): void {
    const o = this.orden();
    const serie = this.serieOrdinaria();
    if (!o || !serie) return;

    if (!confirm(`Se emitirá una factura en la serie ${serie.codigo}. Una factura emitida no se puede modificar. ¿Continuar?`)) {
      return;
    }

    this.trabajando.set(true);
    this.facturas.emitir(o.id, serie.id).subscribe({
      next: (factura) => {
        this.notificaciones.exito(`Emitida la factura ${factura.numeroCompleto}.`);
        this.router.navigate(['/facturas', factura.id]);
      },
      error: () => this.trabajando.set(false),
    });
  }

  private cargar(): void {
    this.servicio.obtener(Number(this.id())).subscribe({
      next: (o) => {
        this.orden.set(o);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }
}
