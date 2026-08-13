import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import {
  AgendaSemanal,
  CargaDiaria,
  Cita,
  CitaBreve,
  SeguimientoAusencias,
} from '../../nucleo/modelos/agenda';
import { CitasService } from '../../nucleo/servicios/citas.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { DetalleCita } from './detalle-cita';
import { FormularioCita } from './formulario-cita';

type Vista = 'semana' | 'dia' | 'tecnicos';

/** Un día del calendario, con lo que entra y cuánto ocupa. */
interface DiaAgenda {
  fecha: string;
  esHoy: boolean;
  citas: Cita[];
  carga: CargaDiaria | null;
}

/** Fecha en el formato que espera la API: 2026-08-07. */
function comoDia(fecha: Date): string {
  const mes = `${fecha.getMonth() + 1}`.padStart(2, '0');
  const dia = `${fecha.getDate()}`.padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

/** Lunes de la semana a la que pertenece la fecha. */
function lunesDe(fecha: Date): Date {
  const lunes = new Date(fecha);
  // getDay() devuelve 0 para domingo: aquí la semana empieza en lunes.
  const desplazamiento = (lunes.getDay() + 6) % 7;
  lunes.setDate(lunes.getDate() - desplazamiento);
  lunes.setHours(0, 0, 0, 0);
  return lunes;
}

/**
 * Calendario del taller.
 *
 * <p>Existe para responder a una pregunta que antes no se podía contestar sin
 * mirar papeles: ¿cuánto trabajo entra el jueves? Hasta ahora la orden de
 * trabajo nacía con la moto ya en el elevador, así que no había forma de ver la
 * carga por delante ni de decirle a un cliente «ese día lo tengo lleno».
 *
 * <p>La barra de cada día compara lo comprometido con la capacidad del taller,
 * que se configura en Ajustes. No impide nada: un taller siempre puede meter una
 * urgencia más, pero conviene que se vea que la está metiendo.
 */
@Component({
  selector: 'app-agenda',
  imports: [CommonModule, Cargando, Icono, FormularioCita, DetalleCita],
  templateUrl: './agenda.html',
  styleUrl: './agenda.scss',
})
export class Agenda {
  private readonly servicio = inject(CitasService);
  private readonly sesion = inject(SesionService);

  /** Dar y mover citas es trabajo de quien coge el teléfono. */
  protected readonly gestionaAgenda = this.sesion.puede('ADMIN', 'MOSTRADOR');

  protected readonly vista = signal<Vista>('semana');
  protected readonly cargando = signal(true);
  /** Día de referencia: la semana que se pinta es la suya. */
  protected readonly referencia = signal(new Date());

  protected readonly citas = signal<Cita[]>([]);
  protected readonly carga = signal<CargaDiaria[]>([]);

  /** Parrilla de la semana por técnico. Solo se pide en su vista. */
  protected readonly parrilla = signal<AgendaSemanal | null>(null);
  /** Plantones del periodo que se está mirando. */
  protected readonly ausencias = signal<SeguimientoAusencias | null>(null);

  protected readonly creando = signal(false);
  protected readonly editando = signal<Cita | null>(null);
  protected readonly abierta = signal<Cita | null>(null);
  /** Día que se propone al dar una cita nueva desde la cabecera de una columna. */
  protected readonly diaPropuesto = signal<string | null>(null);

  /** Días que se pintan: siete en la vista de semana, uno en la de día. */
  protected readonly dias = computed<DiaAgenda[]>(() => {
    const hoy = comoDia(new Date());
    const porDia = new Map<string, CargaDiaria>();
    for (const c of this.carga()) porDia.set(c.dia, c);

    return this.fechasDelRango().map((fecha) => ({
      fecha,
      esHoy: fecha === hoy,
      carga: porDia.get(fecha) ?? null,
      citas: this.citas().filter((c) => c.fechaHora.slice(0, 10) === fecha),
    }));
  });

  protected readonly titulo = computed(() => {
    const fechas = this.fechasDelRango();
    if (this.vista() === 'dia') {
      return new Date(`${fechas[0]}T12:00:00`).toLocaleDateString('es-ES', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
      });
    }
    const inicio = new Date(`${fechas[0]}T12:00:00`);
    const fin = new Date(`${fechas[fechas.length - 1]}T12:00:00`);
    const mismoMes = inicio.getMonth() === fin.getMonth();
    const formatoInicio: Intl.DateTimeFormatOptions = mismoMes
      ? { day: 'numeric' }
      : { day: 'numeric', month: 'short' };
    return `${inicio.toLocaleDateString('es-ES', formatoInicio)} – ${fin.toLocaleDateString('es-ES', { day: 'numeric', month: 'long' })}`;
  });

  /** Horas comprometidas en todo el rango que se está mirando. */
  protected readonly resumen = computed(() => {
    const carga = this.carga();
    return {
      citas: carga.reduce((total, c) => total + c.citas, 0),
      horas: carga.reduce((total, c) => total + c.horasComprometidas, 0),
      diasSaturados: carga.filter((c) => c.saturado).length,
    };
  });

  constructor() {
    this.cargar();
  }

  // ----- Navegación -----

  protected cambiarVista(vista: Vista): void {
    this.vista.set(vista);
    this.cargar();
  }

  protected mover(pasos: number): void {
    // Solo la vista de día avanza de uno en uno; las otras dos son semanales.
    const salto = this.vista() === 'dia' ? 1 : 7;
    const nueva = new Date(this.referencia());
    nueva.setDate(nueva.getDate() + pasos * salto);
    this.referencia.set(nueva);
    this.cargar();
  }

  protected irAHoy(): void {
    this.referencia.set(new Date());
    this.cargar();
  }

  // ----- Citas -----

  protected nuevaCita(dia?: string): void {
    this.diaPropuesto.set(dia ?? null);
    this.creando.set(true);
  }

  protected editar(cita: Cita): void {
    this.abierta.set(null);
    this.editando.set(cita);
  }

  protected trasCambiar(): void {
    this.creando.set(false);
    this.editando.set(null);
    this.abierta.set(null);
    this.diaPropuesto.set(null);
    this.cargar();
  }

  /** Color de la etiqueta según el estado, igual que en las órdenes. */
  protected colorDe(cita: Cita): string {
    switch (cita.estado) {
      case 'CONFIRMADA':
        return 'verde';
      case 'ATENDIDA':
        return 'azul';
      case 'CANCELADA':
      case 'NO_PRESENTADO':
        return 'rojo';
      default:
        return 'ambar';
    }
  }

  /**
   * Estado abreviado para la tarjeta.
   *
   * La columna de un día es estrecha y «Pendiente de confirmar» se come dos
   * líneas. El texto completo sigue estando en la ficha de la cita.
   */
  protected etiquetaDe(cita: Cita): string {
    switch (cita.estado) {
      case 'PENDIENTE':
        return 'Sin confirmar';
      case 'NO_PRESENTADO':
        return 'No vino';
      default:
        return cita.estadoDescripcion;
    }
  }

  protected hora(cita: Cita): string {
    return new Date(cita.fechaHora).toLocaleTimeString('es-ES', {
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  // ----- Carga de datos -----

  private fechasDelRango(): string[] {
    if (this.vista() === 'dia') {
      return [comoDia(this.referencia())];
    }
    const lunes = lunesDe(this.referencia());
    return Array.from({ length: 7 }, (_, i) => {
      const dia = new Date(lunes);
      dia.setDate(lunes.getDate() + i);
      return comoDia(dia);
    });
  }

  private cargar(): void {
    const fechas = this.fechasDelRango();
    const desde = fechas[0];
    const hasta = fechas[fechas.length - 1];

    this.cargando.set(true);

    if (this.vista() === 'tecnicos') {
      // La parrilla ya trae sus citas dentro: pedir además el listado del día
      // sería traerse lo mismo dos veces.
      this.servicio.semana(desde, hasta).subscribe({
        next: (p) => {
          this.parrilla.set(p);
          this.cargando.set(false);
        },
        error: () => this.cargando.set(false),
      });
      this.servicio.ausencias(desde, hasta).subscribe((a) => this.ausencias.set(a));
      return;
    }

    this.servicio.agenda(desde, hasta).subscribe({
      next: (citas) => {
        this.citas.set(citas);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
    this.servicio.carga(desde, hasta).subscribe((c) => this.carga.set(c));
  }

  // ----- Parrilla por técnico -----

  /** Un técnico está libre toda la semana: es a quien se le puede dar trabajo. */
  protected sinTrabajo(horas: number): boolean {
    return horas === 0;
  }

  protected esHoy(dia: string): boolean {
    return dia === comoDia(new Date());
  }

  protected horaBreve(cita: CitaBreve): string {
    return new Date(cita.fechaHora).toLocaleTimeString('es-ES', {
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  /** Abre la ficha completa de una cita pinchada en la parrilla. */
  protected abrirDeParrilla(cita: CitaBreve): void {
    this.servicio.obtener(cita.id).subscribe((c) => this.abierta.set(c));
  }
}
