import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { ColorEstadoPipe } from '../../compartido/estado-ot.pipe';
import { Icono } from '../../compartido/icono';
import { FormularioOrden } from './formulario-orden';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { OrdenTrabajo } from '../../nucleo/modelos/taller';
import { EstadoOT, OrdenTrabajoResumen } from '../../nucleo/modelos/taller';
import { OrdenesService } from '../../nucleo/servicios/ordenes.service';

@Component({
  selector: 'app-lista-ordenes',
  imports: [CommonModule, FormsModule, RouterLink, Cargando, ColorEstadoPipe, Icono, FormularioOrden],
  templateUrl: './lista-ordenes.html',
  styleUrl: './lista-ordenes.scss',
})
export class ListaOrdenes {
  private readonly servicio = inject(OrdenesService);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly filas = signal<OrdenTrabajoResumen[]>([]);
  protected readonly totalItems = signal(0);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);

  protected readonly estado = signal<string>('');
  protected readonly soloAbiertas = signal(true);
  protected readonly mostrarFormulario = signal(false);

  /** Abrir órdenes es cosa de mostrador: es quien recibe la moto. */
  protected readonly puedeAbrir = inject(SesionService).puede('ADMIN', 'MOSTRADOR');

  /** Recién abierta, se va directo a su ficha para empezar a trabajarla. */
  protected trasAbrir(orden: OrdenTrabajo): void {
    this.mostrarFormulario.set(false);
    void this.router.navigate(['/ordenes', orden.id]);
  }

  /**
   * El desplegable enseñaba el nombre del enum en crudo (`EN_DIAGNOSTICO`).
   * Aquí van los mismos textos que usa el resto de la interfaz.
   */
  protected readonly estados: { valor: EstadoOT; texto: string }[] = [
    { valor: 'RECIBIDA', texto: 'Recibida' },
    { valor: 'PREPARADA', texto: 'Preparada, pendiente de empezar' },
    { valor: 'EN_DIAGNOSTICO', texto: 'En diagnóstico' },
    { valor: 'PRESUPUESTADA', texto: 'Presupuestada' },
    { valor: 'APROBADA', texto: 'Aprobada por el cliente' },
    { valor: 'EN_REPARACION', texto: 'En reparación' },
    { valor: 'ESPERANDO_PIEZAS', texto: 'Esperando piezas' },
    { valor: 'LISTA', texto: 'Lista para entregar' },
    { valor: 'ENTREGADA', texto: 'Entregada' },
    { valor: 'RECHAZADA', texto: 'Presupuesto rechazado' },
  ];

  /** Toda la fila lleva al detalle, no solo el código. */
  protected abrir(id: number): void {
    void this.router.navigate(['/ordenes', id]);
  }

  constructor() {
    this.cargar();
  }

  protected cargar(pagina = 0): void {
    this.cargando.set(true);
    this.servicio
      .buscar({
        pagina,
        estado: (this.estado() || null) as EstadoOT | null,
        soloAbiertas: this.soloAbiertas(),
      })
      .subscribe({
        next: (p) => {
          this.filas.set(p.contenido);
          this.totalItems.set(p.totalItems);
          this.pagina.set(p.pagina);
          this.totalPaginas.set(p.totalPaginas);
          this.cargando.set(false);
        },
        error: () => this.cargando.set(false),
      });
  }
}
