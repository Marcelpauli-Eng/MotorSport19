import { alCambiarDatos } from '../../nucleo/servicios/tiempo-real.service';
import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { AlertaStock, MovimientoStock, Pieza } from '../../nucleo/modelos/taller';
import { InventarioService } from '../../nucleo/servicios/inventario.service';
import { Icono } from '../../compartido/icono';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { FormularioPieza } from './formulario-pieza';

type Pestana = 'catalogo' | 'alertas' | 'movimientos';

/**
 * Inventario: catálogo, alertas de reposición y libro de movimientos.
 *
 * No hay ningún campo para escribir el stock a mano, y es deliberado: las
 * existencias solo cambian registrando entradas, salidas o ajustes.
 */
@Component({
  selector: 'app-inventario',
  imports: [CommonModule, FormsModule, RouterLink, Cargando, Icono, FormularioPieza],
  templateUrl: './inventario.html',
  styleUrl: './inventario.scss',
})
export class Inventario {
  private readonly servicio = inject(InventarioService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly sesion = inject(SesionService);

  /** Entradas y ajustes de almacen los reserva la API para direccion. */
  protected readonly gestionaAlmacen = this.sesion.tienePermiso('ALMACEN_MOVER');

  /**
   * ¿Se le enseñan los precios del catálogo?
   *
   * A un técnico no: de poco serviría taparlos en la orden de trabajo si los
   * tiene a un clic aquí. El servidor tampoco se los manda —llegan a nulo—, así
   * que esto solo evita pintar dos columnas de guiones.
   */
  protected readonly vePrecios = this.sesion.tienePermiso('IMPORTES_VER');

  protected readonly pestana = signal<Pestana>('catalogo');
  protected readonly cargando = signal(true);

  protected readonly piezas = signal<Pieza[]>([]);
  protected readonly alertas = signal<AlertaStock[]>([]);
  protected readonly movimientos = signal<MovimientoStock[]>([]);
  protected readonly texto = signal('');
  protected readonly familia = signal('');
  protected readonly familias = signal<string[]>([]);

  protected readonly creando = signal(false);
  protected readonly editando = signal<Pieza | null>(null);

  constructor() {
    alCambiarDatos(() => this.cambiarPestana(this.pestana()));
    this.cargarCatalogo();
    this.servicio.alertas().subscribe((a) => this.alertas.set(a));
    this.servicio.familias().subscribe((f) => this.familias.set(f));
  }

  /** Filtra por grupo del almacén: es como se busca una pieza en la estantería. */
  protected filtrarPorFamilia(familia: string): void {
    this.familia.set(familia);
    this.cargarCatalogo();
  }

  protected trasGuardarPieza(): void {
    this.creando.set(false);
    this.editando.set(null);
    this.cargarCatalogo();
    this.servicio.familias().subscribe((f) => this.familias.set(f));
    this.servicio.alertas().subscribe((a) => this.alertas.set(a));
  }

  protected cambiarPestana(p: Pestana): void {
    this.pestana.set(p);
    if (p === 'catalogo') this.cargarCatalogo();
    if (p === 'alertas') this.servicio.alertas().subscribe((a) => this.alertas.set(a));
    if (p === 'movimientos') this.cargarMovimientos();
  }

  protected cargarCatalogo(): void {
    this.cargando.set(true);
    this.servicio.buscarPiezas(this.texto(), { familia: this.familia() || null, tamano: 100 }).subscribe({
      next: (p) => {
        this.piezas.set(p.contenido);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  private cargarMovimientos(): void {
    this.cargando.set(true);
    this.servicio.movimientos(undefined, 100).subscribe({
      next: (p) => {
        this.movimientos.set(p.contenido);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  /** Entrada de mercancía por compra a proveedor. */
  protected registrarEntrada(pieza: Pieza): void {
    const texto = prompt(`Unidades que entran de ${pieza.sku}:`, '1');
    if (texto === null) return;
    const cantidad = numeroTecleado(texto);
    if (!(cantidad > 0)) {
      this.notificaciones.error(`«${texto}» no es una cantidad válida. Escriba un número mayor que cero, por ejemplo 2 o 1,5.`);
      return;
    }

    const documento = prompt('Albarán o factura del proveedor (opcional):') ?? undefined;

    this.servicio.registrarEntrada(pieza.id, { cantidad, documentoProveedor: documento }).subscribe({
      next: (m) => {
        this.notificaciones.exito(
          `${pieza.sku}: ${m.stockAnterior} → ${m.stockResultante} unidades.`,
        );
        this.cargarCatalogo();
        this.servicio.alertas().subscribe((a) => this.alertas.set(a));
      },
    });
  }

  /** Ajuste tras inventario físico. La cantidad lleva signo y el motivo es obligatorio. */
  protected registrarAjuste(pieza: Pieza): void {
    const texto = prompt(`Ajuste para ${pieza.sku} (negativo si faltan unidades):`, '-1');
    if (texto === null) return;
    const cantidad = numeroTecleado(texto);
    if (!Number.isFinite(cantidad) || cantidad === 0) {
      this.notificaciones.error(`«${texto}» no es un ajuste válido. Escriba las unidades con su signo, por ejemplo -1 o 2,5.`);
      return;
    }

    const motivo = prompt('Motivo del ajuste (obligatorio):');
    if (motivo === null) return;
    if (!motivo.trim()) {
      this.notificaciones.error('El ajuste necesita un motivo: sin él no se registra.');
      return;
    }

    this.servicio.registrarAjuste(pieza.id, { cantidad, motivo }).subscribe({
      next: (m) => {
        this.notificaciones.exito(
          `${pieza.sku}: ${m.stockAnterior} → ${m.stockResultante} unidades.`,
        );
        this.cargarCatalogo();
        this.servicio.alertas().subscribe((a) => this.alertas.set(a));
      },
    });
  }
}

/** «1,5» es como se escribe aquí, y `Number` solo entiende «1.5»: lo daba por nada. */
export function numeroTecleado(texto: string): number {
  const limpio = texto.trim().replace(',', '.');
  return limpio === '' ? NaN : Number(limpio);
}
