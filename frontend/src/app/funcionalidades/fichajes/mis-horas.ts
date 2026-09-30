import { DatePipe } from '@angular/common';
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
import { Icono } from '../../compartido/icono';
import {
  CambioFichaje,
  Fichaje,
  FichajesService,
  ResumenFichajes,
} from '../../nucleo/servicios/fichajes.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { HistorialJornada } from './historial-jornada';

type Rango = 'semana' | 'mes' | 'personalizado';

/**
 * Las horas de uno mismo.
 *
 * <p>Consultar el propio registro de jornada es un derecho del trabajador (art.
 * 34.9 ET), no una concesión: por eso no pide permiso. Y si dirección cambió
 * una hora, aquí se ve qué había antes, quién la cambió y por qué: un cambio
 * que el afectado no puede ver no deja rastro para él.
 */
@Component({
  selector: 'app-mis-horas',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, FormsModule, Icono, Cargando, HistorialJornada],
  template: `
    <div class="pagina-cabecera">
      <div class="pagina-cabecera__texto">
        <h1>Mis horas</h1>
        <p class="pagina-cabecera__sub">
          Tu registro de jornada. Si dirección cambia alguna hora, aquí ves la de antes y por qué.
        </p>
      </div>
    </div>

    <section class="tarjeta">
      <div class="mandos">
        <div class="fila" style="gap: 4px">
          <button
            type="button"
            class="boton"
            [class.boton--principal]="rango() === 'semana'"
            (click)="elegirRango('semana')"
          >
            Esta semana
          </button>
          <button
            type="button"
            class="boton"
            [class.boton--principal]="rango() === 'mes'"
            (click)="elegirRango('mes')"
          >
            Este mes
          </button>
        </div>
        <div class="campo">
          <label for="desde">Desde</label>
          <input
            id="desde"
            type="date"
            [ngModel]="desde()"
            (ngModelChange)="desde.set($event); elegirRango('personalizado')"
          />
        </div>
        <div class="campo">
          <label for="hasta">Hasta</label>
          <input
            id="hasta"
            type="date"
            [ngModel]="hasta()"
            (ngModelChange)="hasta.set($event); elegirRango('personalizado')"
          />
        </div>
        <button
          type="button"
          class="boton"
          style="margin-left: auto"
          (click)="descargar()"
        >
          <app-icono nombre="descargar" [tamano]="15" />
          Descargar
        </button>
      </div>

      @if (cargando()) {
        <app-cargando mensaje="Cargando tus horas…" />
      } @else {
        <div class="resumen">
          <div class="dato">
            <div class="dato__valor">{{ total() }}</div>
            <div class="dato__pie">Total del periodo</div>
          </div>
          <div class="dato">
            <div class="dato__valor">{{ jornadas().length }}</div>
            <div class="dato__pie">Jornadas</div>
          </div>
        </div>

        @if (!jornadas().length) {
          <p class="silenciado" style="margin-top: var(--e3)">No fichaste ninguna jornada en esas fechas.</p>
        } @else {
          <div class="tabla-envoltorio" style="margin-top: var(--e3)">
            <table>
              <thead>
                <tr>
                  <th>Día</th>
                  <th>Horario</th>
                  <th class="num">Horas</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (f of jornadas(); track f.id) {
                  <tr [class.pulsable]="cambiada(f)" (click)="alternar(f)">
                    <td>{{ f.inicio | date: 'EEE d MMM' : undefined : 'es' }}</td>
                    <td>
                      <span class="tabular">{{ tramo(f.inicio, f.fin) }}</span>
                      @if (f.abierta) {
                        <span class="marca marca--abierta">Sin cerrar</span>
                      }
                      @if (f.cerradaPorOlvido) {
                        <span class="marca marca--olvido">Cerrada a mano</span>
                      }
                      @if (f.corregida) {
                        <span class="marca marca--corregida">Cambiada</span>
                      }
                    </td>
                    <td class="num horas">{{ reloj(f) }}</td>
                    <td style="text-align: right">
                      @if (cambiada(f)) {
                        <app-icono nombre="flecha-abajo" [tamano]="13" [class.girada]="abierta() === f.id" />
                      }
                    </td>
                  </tr>
                  @if (abierta() === f.id) {
                    <tr class="detalle">
                      <td></td>
                      <td colspan="3"><app-historial-jornada [cambios]="cambios()" /></td>
                    </tr>
                  }
                }
              </tbody>
            </table>
          </div>
        }
      }
    </section>
  `,
  styles: [
    `
      .mandos { display: flex; flex-wrap: wrap; align-items: flex-end; gap: var(--e3); margin-bottom: var(--e3); }
      .mandos .campo { margin-bottom: 0; }
      .resumen { display: grid; gap: var(--e2); grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); }
      .dato { padding: var(--e3); }
      .dato__valor { font-size: 1.6rem; font-weight: 700; font-variant-numeric: tabular-nums; }
      .dato__pie { font-size: 0.78rem; color: var(--gris-500, #6b7280); text-transform: uppercase; letter-spacing: 0.05em; }
      .horas { font-variant-numeric: tabular-nums; font-weight: 600; }
      .detalle td { background: var(--gris-50, #f9fafb); font-size: 0.85rem; }
      .marca { font-size: 0.72rem; padding: 2px 6px; border-radius: 4px; white-space: nowrap; margin-left: 4px; }
      .marca--abierta { background: #fdecea; color: #a32a20; }
      .marca--corregida { background: #fef6e0; color: #8a6a1f; }
      .marca--olvido { background: #eef0f3; color: #4b5563; }
      app-icono.girada { display: inline-block; transform: rotate(180deg); }
    `,
  ],
})
export class MisHoras {
  private readonly fichajes = inject(FichajesService);
  private readonly sesion = inject(SesionService);

  protected readonly cargando = signal(true);
  protected readonly resumen = signal<ResumenFichajes | null>(null);
  protected readonly rango = signal<Rango>('semana');
  protected readonly desde = signal(FichajesService.inicioDe('semana'));
  protected readonly hasta = signal(FichajesService.dia(new Date()));

  /** Qué jornada tiene el historial desplegado, y ese historial. */
  protected readonly abierta = signal<number | null>(null);
  protected readonly cambios = signal<CambioFichaje[]>([]);

  /** Para que la jornada de hoy, si sigue abierta, vaya contando. */
  private readonly ahora = signal(Date.now());

  protected readonly tramo = FichajesService.tramo;

  /** Mías solo hay de una persona: la lista es el detalle de esa fila. */
  protected readonly jornadas = computed(() => this.resumen()?.trabajadores[0]?.detalle ?? []);

  protected readonly total = computed(() =>
    FichajesService.reloj(
      this.jornadas().reduce((suma, f) => suma + this.fichajes.segundosDe(f, this.ahora()), 0),
    ),
  );

  constructor() {
    this.cargar();
    const reloj = setInterval(() => this.ahora.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(reloj));
  }

  protected reloj(f: Fichaje): string {
    return FichajesService.reloj(this.fichajes.segundosDe(f, this.ahora()));
  }

  protected cambiada(f: Fichaje): boolean {
    return f.corregida || f.cerradaPorOlvido;
  }

  protected elegirRango(r: Rango): void {
    this.rango.set(r);
    if (r !== 'personalizado') {
      this.desde.set(FichajesService.inicioDe(r));
      this.hasta.set(FichajesService.dia(new Date()));
    }
    this.cargar();
  }

  protected alternar(f: Fichaje): void {
    if (!this.cambiada(f)) return;
    if (this.abierta() === f.id) {
      this.abierta.set(null);
      return;
    }
    this.abierta.set(f.id);
    this.cambios.set([]);
    this.fichajes.misCambios(f.id).subscribe((c) => this.cambios.set(c));
  }

  protected descargar(): void {
    const quien = this.sesion.usuario()?.nombreCompleto ?? '';
    const nombre = `registro-jornada_${quien}_${this.desde()}_${this.hasta()}`.replace(/\s+/g, '-');
    this.fichajes
      .exportarMias(this.desde(), this.hasta())
      .subscribe((csv) => FichajesService.guardar(csv, `${nombre}.csv`));
  }

  private cargar(): void {
    this.cargando.set(true);
    this.fichajes.mias(this.desde(), this.hasta()).subscribe({
      next: (r) => {
        this.resumen.set(r);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }
}
