import { CommonModule } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { EventoFactura, Factura, FacturaResumen } from '../../nucleo/modelos/facturacion';
import { FacturasService } from '../../nucleo/servicios/facturas.service';

/**
 * Ficha completa de una factura.
 *
 * Muestra la huella y el resultado de reverificarla: es la prueba visible de
 * que el documento no se ha tocado desde que se emitió.
 */
@Component({
  selector: 'app-detalle-factura',
  imports: [CommonModule, RouterLink, Cargando, Icono],
  templateUrl: './detalle-factura.html',
  styleUrl: './detalle-factura.scss',
})
export class DetalleFactura {
  private readonly servicio = inject(FacturasService);

  /** Llega de la ruta gracias a `withComponentInputBinding()`. */
  readonly id = input.required<string>();

  protected readonly cargando = signal(true);
  protected readonly factura = signal<Factura | null>(null);
  protected readonly rectificativas = signal<FacturaResumen[]>([]);
  protected readonly eventos = signal<EventoFactura[]>([]);

  constructor() {
    // El input de ruta ya está resuelto cuando se construye el componente.
    queueMicrotask(() => this.cargar());
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
  }
}
