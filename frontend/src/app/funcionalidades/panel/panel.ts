import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ColorEstadoPipe } from '../../compartido/estado-ot.pipe';
import { AlertaStock, EstadoOT, OrdenTrabajoResumen } from '../../nucleo/modelos/taller';
import { FacturaResumen } from '../../nucleo/modelos/facturacion';
import { FacturasService } from '../../nucleo/servicios/facturas.service';
import { InventarioService } from '../../nucleo/servicios/inventario.service';
import { OrdenesService } from '../../nucleo/servicios/ordenes.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';

/**
 * Pantalla de inicio: lo que hay que mirar nada más abrir el programa.
 *
 * Se prioriza lo que reclama acción — órdenes en marcha y piezas por reponer —
 * sobre los totales bonitos.
 */
@Component({
  selector: 'app-panel',
  imports: [CommonModule, RouterLink, ColorEstadoPipe],
  templateUrl: './panel.html',
  styleUrl: './panel.scss',
})
export class Panel {
  private readonly ordenes = inject(OrdenesService);
  private readonly inventario = inject(InventarioService);
  private readonly facturas = inject(FacturasService);
  protected readonly sesion = inject(SesionService);

  /** Facturar es cosa de mostrador y dirección: al técnico ni se le pide. */
  protected readonly veFacturacion = this.sesion.puede('ADMIN', 'MOSTRADOR');

  protected readonly ordenesAbiertas = signal<OrdenTrabajoResumen[]>([]);
  protected readonly alertas = signal<AlertaStock[]>([]);
  protected readonly ultimasFacturas = signal<FacturaResumen[]>([]);
  protected readonly totalAbiertas = signal(0);

  constructor() {
    this.ordenes.buscar({ soloAbiertas: true, tamano: 50 }).subscribe((p) => {
      this.ordenesAbiertas.set(p.contenido);
      this.totalAbiertas.set(p.totalItems);
    });
    this.inventario.alertas().subscribe((a) => this.alertas.set(a));
    // Sin esta comprobación el panel de un técnico pediría facturas, recibiría
    // un 403 y le saltaría un aviso de permisos cada vez que abre el programa.
    if (this.veFacturacion) {
      this.facturas.buscar({ tamano: 5 }).subscribe((p) => this.ultimasFacturas.set(p.contenido));
    }
  }

  /** Órdenes agrupadas por estado, para el tablero. */
  protected porEstado(estado: EstadoOT): OrdenTrabajoResumen[] {
    return this.ordenesAbiertas().filter((o) => o.estado === estado);
  }

  protected readonly columnas: { estado: EstadoOT; titulo: string }[] = [
    { estado: 'RECIBIDA', titulo: 'Recibidas' },
    { estado: 'EN_DIAGNOSTICO', titulo: 'En diagnóstico' },
    { estado: 'PRESUPUESTADA', titulo: 'Esperando respuesta' },
    { estado: 'APROBADA', titulo: 'Aprobadas' },
    { estado: 'EN_REPARACION', titulo: 'En reparación' },
    { estado: 'ESPERANDO_PIEZAS', titulo: 'Esperando piezas' },
    { estado: 'LISTA', titulo: 'Listas para entregar' },
  ];
}
