import { alCambiarDatos } from '../../nucleo/servicios/tiempo-real.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Dialogo } from '../../compartido/dialogo';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { SerieFactura } from '../../nucleo/modelos/facturacion';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import {
  EventoFactura,
  Factura,
  FacturaResumen,
  LineaFactura,
  LineaRectificativa,
} from '../../nucleo/modelos/facturacion';
import { Pieza } from '../../nucleo/modelos/taller';
import { FacturasService } from '../../nucleo/servicios/facturas.service';
import { InventarioService, porGrupo } from '../../nucleo/servicios/inventario.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';

/**
 * Ficha completa de una factura.
 *
 * Muestra la huella y el resultado de reverificarla: es la prueba visible de
 * que el documento no se ha tocado desde que se emitió.
 */
@Component({
  selector: 'app-detalle-factura',
  imports: [CommonModule, FormsModule, RouterLink, Cargando, Icono, Dialogo],
  templateUrl: './detalle-factura.html',
  styleUrl: './detalle-factura.scss',
})
export class DetalleFactura {
  private readonly servicio = inject(FacturasService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly router = inject(Router);
  private readonly sesion = inject(SesionService);

  /** Llega de la ruta gracias a `withComponentInputBinding()`. */
  readonly id = input.required<string>();

  protected readonly cargando = signal(true);
  protected readonly factura = signal<Factura | null>(null);
  protected readonly rectificativas = signal<FacturaResumen[]>([]);
  protected readonly eventos = signal<EventoFactura[]>([]);

  // ----- Rectificación -----
  protected readonly series = signal<SerieFactura[]>([]);
  protected readonly rectificando = signal(false);
  protected readonly emitiendo = signal(false);
  protected readonly motivoRect = signal('');

  /** La serie donde irá la corrección. Sin ella no se puede rectificar. */
  protected readonly serieRectificativa = computed(
    () => this.series().find((s) => s.tipo === 'RECTIFICATIVA' && s.activa) ?? null,
  );

  /**
   * Una rectificativa no se rectifica.
   *
   * <p>Lo que se corrige es la factura original: encadenar correcciones de
   * correcciones deja un libro que no hay quien siga.
   */
  protected readonly puedeRectificar = computed(() => {
    const f = this.factura();
    return !!f && f.tipo !== 'RECTIFICATIVA' && this.sesion.tienePermiso('FACTURAS_RECTIFICAR');
  });

  /** `null` mientras llegan del servidor. */
  protected readonly filas = signal<FilaCorreccion[] | null>(null);
  protected readonly anularEntera = signal(false);

  protected readonly lineasCorreccion = computed(() => lineasDeLaCorreccion(this.filas() ?? []));
  protected readonly valeHoy = computed(() =>
    suma((this.filas() ?? []).flatMap((f) => (f.original ? [f.original] : []))),
  );
  protected readonly importeCorreccion = computed(() => suma(this.lineasCorreccion()));

  /** Lo que impide emitir la corrección, dicho para quien la está componiendo. */
  protected readonly problema = computed(() => {
    const lineas = (this.filas() ?? []).filter((f) => !f.quitada).map((f) => f.linea);
    if (lineas.some((l) => !l.descripcion.trim())) return 'Falta la descripción de algún concepto.';
    if (lineas.some((l) => !Number.isFinite(l.cantidad) || l.cantidad === 0)) {
      return 'Alguna cantidad está vacía o a cero. Para quitar un concepto, pulse «Quitar».';
    }
    if (lineas.some((l) => !(l.precioUnitario >= 0)))
      return 'Algún precio está vacío o es negativo.';
    if (lineas.some((l) => !(l.descuentoPct >= 0 && l.descuentoPct <= 100))) {
      return 'El descuento va de 0 a 100.';
    }
    // Sale con precio 0: sin esto se emitía una rectificativa de 0,00 € que ya no se borra.
    if ((this.filas() ?? []).some((f) => !f.original && !f.quitada && !totalDe(f.linea))) {
      return 'Ponga el precio del concepto nuevo.';
    }
    if (this.valeHoy() + this.importeCorreccion() < 0) {
      return 'La factura no puede quedar por debajo de cero.';
    }
    return null;
  });

  protected abrirRectificar(): void {
    const f = this.factura();
    if (!f) return;
    this.motivoRect.set('');
    this.anularEntera.set(false);
    this.filas.set(null);
    this.rectificando.set(true);
    this.servicio.lineasVigentes(f.id).subscribe({
      next: (lineas) =>
        this.filas.set(lineas.map((l) => ({ original: l, linea: { ...l }, quitada: false }))),
      error: () => this.rectificando.set(false),
    });
  }

  protected cambiar(i: number, cambio: Partial<LineaRectificativa>): void {
    this.filas.update((fs) =>
      fs!.map((f, j) => (j === i ? { ...f, linea: { ...f.linea, ...cambio } } : f)),
    );
  }

  protected alternarQuitada(i: number): void {
    this.filas.update((fs) =>
      // Un concepto que no estaba en la factura no se tacha: se va sin más.
      fs![i].original
        ? fs!.map((f, j) => (j === i ? { ...f, quitada: !f.quitada } : f))
        : fs!.filter((_, j) => j !== i),
    );
  }

  /**
   * Algo que se quedó sin cobrar.
   *
   * ponytail: la mano de obra lleva el IVA de la primera línea de la factura; un
   * selector de tipos de IVA si algún día un taller mezcla tipos en la mano de obra.
   */
  protected anadirConcepto(): void {
    const primera = this.factura()!.lineas[0];
    this.filas.update((fs) => [
      ...fs!,
      {
        original: null,
        quitada: false,
        linea: {
          tipo: 'MANO_DE_OBRA',
          descripcion: '',
          piezaSku: null,
          cantidad: 1,
          precioUnitario: 0,
          descuentoPct: 0,
          tipoIva: primera.tipoIva,
          porcentajeIva: primera.porcentajeIva,
        },
      },
    ]);
  }

  private readonly inventario = inject(InventarioService);
  protected readonly veAlmacen = this.sesion.tienePermiso('ALMACEN_VER');

  /** El almacén, para elegir la pieza que no se cobró. Se pide la primera vez que hace falta. */
  protected readonly piezas = signal<Pieza[] | null>(null);
  protected readonly piezasPorGrupo = computed(() => porGrupo(this.piezas() ?? []));

  protected cambiarTipo(i: number, tipo: string): void {
    // Si pasa a mano de obra, la referencia de la pieza que se eligió ya no vale.
    this.cambiar(i, tipo === 'PIEZA' ? { tipo } : { tipo, piezaSku: null });
    if (tipo === 'PIEZA' && this.veAlmacen && !this.piezas()) {
      this.inventario
        .buscarPiezas('', { tamano: 300 })
        .subscribe((p) => this.piezas.set(p.contenido));
    }
  }

  protected elegirPieza(i: number, sku: string | null): void {
    const pieza = this.piezas()?.find((p) => p.sku === sku);
    this.cambiar(i, pieza ? datosDePieza(pieza, this.factura()!.lineas) : { piezaSku: null });
  }

  protected numero(valor: unknown): number {
    return valor === '' || valor === null ? NaN : Number(valor);
  }

  protected totalDe(l: LineaRectificativa): number {
    return totalDe(l);
  }

  /** Emite la corrección y lleva a verla: es el documento que vale a partir de ahora. */
  protected rectificar(): void {
    const f = this.factura();
    const serie = this.serieRectificativa();
    const motivo = this.motivoRect().trim();
    if (!f || !serie || !motivo || this.emitiendo()) return;

    const anular = this.anularEntera();
    const lineas = anular ? [] : this.lineasCorreccion();
    if (!anular && (!lineas.length || this.problema())) return;
    const importe = anular ? -this.valeHoy() : this.importeCorreccion();
    // Igual que al emitir: lo que entra en el libro de facturas no se borra.
    const pregunta = anular
      ? `Se anulará entera la factura ${f.numeroCompleto}.`
      : `Se emitirá una rectificativa de ${euros(importe)} sobre la factura ${f.numeroCompleto}, que pasará a valer ${euros(this.valeHoy() + importe)}.`;
    if (!confirm(`${pregunta} No se puede deshacer. ¿Continuar?`)) return;

    this.emitiendo.set(true);
    // Sin líneas, por diferencias: el servidor anula lo que la factura vale hoy.
    this.servicio.rectificar(f.id, serie.id, 'POR_DIFERENCIAS', motivo, lineas).subscribe({
      next: (nueva) => {
        this.emitiendo.set(false);
        this.rectificando.set(false);
        this.notificaciones.exito(`Emitida la rectificativa ${nueva.numeroCompleto}.`);
        this.router.navigate(['/facturas', nueva.id]);
      },
      error: () => this.emitiendo.set(false),
    });
  }

  /**
   * Descuento total de la factura, sumando el de cada línea.
   *
   * <p>Devuelve 0 cuando no hay ninguno, y la plantilla se apoya en eso con
   * `@if` para no enseñar una fila de «Descuento 0,00 €» en cada factura.
   */
  protected readonly totalDescuento = computed(() =>
    (this.factura()?.lineas ?? []).reduce((a, l) => a + l.importeDescuento, 0),
  );

  /** Lo que sumarían las líneas a precio de tarifa, antes del descuento. */
  protected readonly importeBruto = computed(() =>
    (this.factura()?.lineas ?? []).reduce((a, l) => a + l.importeBruto, 0),
  );

  constructor() {
    alCambiarDatos(() => this.cargar());
    // Con otro id en la misma ruta Angular reutiliza la pantalla: cargando solo al
    // construirla, tras emitir la rectificativa se seguía viendo la original.
    effect(() => {
      this.id();
      untracked(() => {
        this.factura.set(null);
        this.cargando.set(true);
        this.cargar();
      });
    });
  }

  protected verPdf(): void {
    const f = this.factura();
    if (f) this.servicio.abrirPdf(f.id);
  }

  private cargar(): void {
    const id = Number(this.id());
    this.servicio.obtener(id).subscribe({
      next: (f) => {
        this.factura.set(f);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
    this.servicio.rectificativasDe(id).subscribe((r) => this.rectificativas.set(r));
    this.servicio.eventos(id).subscribe((e) => this.eventos.set(e));
    this.servicio.series().subscribe((ss) => this.series.set(ss));
  }
}

/** Una línea en el editor de la corrección. Sin `original`, es un concepto que no estaba. */
export interface FilaCorreccion {
  original: LineaRectificativa | null;
  linea: LineaRectificativa;
  quitada: boolean;
}

/**
 * Las líneas de la rectificativa por diferencias que deja la factura como dicen las filas.
 *
 * Un concepto cambiado sale dos veces: en negativo tal como estaba y en positivo como
 * debe quedar. Restar solo la diferencia de cantidad redondea distinto, y la factura
 * dejaría de cuadrar al céntimo con sus correcciones.
 */
export function lineasDeLaCorreccion(filas: FilaCorreccion[]): LineaRectificativa[] {
  return filas.flatMap(({ original, linea, quitada }) => {
    if (!original) return quitada ? [] : [linea];
    const negada = { ...original, cantidad: -original.cantidad };
    if (quitada) return [negada];
    const igual =
      linea.descripcion === original.descripcion &&
      linea.cantidad === original.cantidad &&
      linea.precioUnitario === original.precioUnitario &&
      linea.descuentoPct === original.descuentoPct;
    return igual ? [] : [negada, { ...linea, descripcion: linea.descripcion.trim() }];
  });
}

/**
 * Lo que se copia del almacén a la línea: descripción, referencia, precio de venta e IVA.
 *
 * El IVA de la pieza si la factura ya lleva ese tipo; si no, el de la factura, que
 * es el que mandaba cuando la orden tenía un IVA para todo.
 */
export function datosDePieza(pieza: Pieza, lineas: LineaFactura[]): Partial<LineaRectificativa> {
  const iva = lineas.find((l) => l.tipoIva === pieza.tipoIva) ?? lineas[0];
  return {
    descripcion: pieza.descripcion,
    piezaSku: pieza.sku,
    precioUnitario: pieza.precioVenta ?? 0,
    tipoIva: iva.tipoIva,
    porcentajeIva: iva.porcentajeIva,
  };
}

/** Como en el servidor: a dos decimales y la mitad hacia fuera del cero, también en negativo. */
function redondear(x: number): number {
  return (Math.sign(x) * Math.round(Math.abs(x) * 100 + 1e-7)) / 100;
}

export function totalDe(l: LineaRectificativa): number {
  const base = redondear(l.cantidad * l.precioUnitario * (1 - l.descuentoPct / 100));
  return redondear(base + redondear((base * l.porcentajeIva) / 100));
}

function suma(lineas: LineaRectificativa[]): number {
  return redondear(lineas.reduce((a, l) => a + totalDe(l), 0));
}

function euros(importe: number): string {
  return importe.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });
}
