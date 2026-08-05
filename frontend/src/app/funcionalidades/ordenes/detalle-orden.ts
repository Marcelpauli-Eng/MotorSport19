import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { ColorEstadoPipe } from '../../compartido/estado-ot.pipe';
import { EstadoOT, OrdenTrabajo, ResultadoConsumo } from '../../nucleo/modelos/taller';
import { SerieFactura } from '../../nucleo/modelos/facturacion';
import { FacturasService } from '../../nucleo/servicios/facturas.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { OrdenesService } from '../../nucleo/servicios/ordenes.service';

/** Acción que puede lanzarse desde la ficha, según el estado actual. */
interface Accion {
  destino: EstadoOT;
  texto: string;
  principal: boolean;
}

/**
 * Ficha de una orden de trabajo.
 *
 * Los botones de estado salen de `estadosPosibles`, que envía el propio backend:
 * así el mostrador solo ve las transiciones que la máquina de estados permite,
 * en lugar de probar y recibir un error.
 */
@Component({
  selector: 'app-detalle-orden',
  imports: [CommonModule, RouterLink, Cargando, ColorEstadoPipe],
  templateUrl: './detalle-orden.html',
  styleUrl: './detalle-orden.scss',
})
export class DetalleOrden {
  private readonly servicio = inject(OrdenesService);
  private readonly facturas = inject(FacturasService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly router = inject(Router);

  readonly id = input.required<string>();

  protected readonly cargando = signal(true);
  protected readonly orden = signal<OrdenTrabajo | null>(null);
  protected readonly trabajando = signal(false);
  protected readonly resultadoConsumo = signal<ResultadoConsumo | null>(null);
  protected readonly serieOrdinaria = signal<SerieFactura | null>(null);

  private static readonly TEXTOS: Record<EstadoOT, string> = {
    RECIBIDA: 'Volver a recibida',
    EN_DIAGNOSTICO: 'Iniciar diagnóstico',
    PRESUPUESTADA: 'Pasar a presupuestada',
    APROBADA: 'El cliente aprueba',
    EN_REPARACION: 'Entrar en reparación',
    ESPERANDO_PIEZAS: 'Marcar en espera de piezas',
    LISTA: 'Marcar lista para entregar',
    ENTREGADA: 'Entregar al cliente',
    RECHAZADA: 'El cliente rechaza',
  };

  /** Botones que tienen sentido ahora mismo. */
  protected readonly acciones = computed<Accion[]>(() => {
    const o = this.orden();
    if (!o) return [];
    return o.estadosPosibles.map((destino) => ({
      destino,
      texto: DetalleOrden.TEXTOS[destino] ?? destino,
      principal: destino !== 'RECHAZADA' && destino !== 'ESPERANDO_PIEZAS',
    }));
  });

  constructor() {
    queueMicrotask(() => this.cargar());
    this.facturas.series().subscribe((series) => {
      this.serieOrdinaria.set(series.find((s) => s.tipo === 'ORDINARIA' && s.activa) ?? null);
    });
  }

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
