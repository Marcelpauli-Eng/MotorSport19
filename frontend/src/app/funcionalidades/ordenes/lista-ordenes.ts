import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { ColorEstadoPipe } from '../../compartido/estado-ot.pipe';
import { EstadoOT, OrdenTrabajoResumen } from '../../nucleo/modelos/taller';
import { OrdenesService } from '../../nucleo/servicios/ordenes.service';

@Component({
  selector: 'app-lista-ordenes',
  imports: [CommonModule, FormsModule, RouterLink, Cargando, ColorEstadoPipe],
  templateUrl: './lista-ordenes.html',
})
export class ListaOrdenes {
  private readonly servicio = inject(OrdenesService);

  protected readonly cargando = signal(true);
  protected readonly filas = signal<OrdenTrabajoResumen[]>([]);
  protected readonly totalItems = signal(0);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);

  protected readonly estado = signal<string>('');
  protected readonly soloAbiertas = signal(true);

  protected readonly estados: EstadoOT[] = [
    'RECIBIDA',
    'EN_DIAGNOSTICO',
    'PRESUPUESTADA',
    'APROBADA',
    'EN_REPARACION',
    'ESPERANDO_PIEZAS',
    'LISTA',
    'ENTREGADA',
    'RECHAZADA',
  ];

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
