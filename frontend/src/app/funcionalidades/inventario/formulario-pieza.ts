import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { switchMap } from 'rxjs';
import { Dialogo } from '../../compartido/dialogo';
import { TipoIva } from '../../nucleo/modelos/configuracion';
import { Pieza, Proveedor } from '../../nucleo/modelos/taller';
import { ConfiguracionService } from '../../nucleo/servicios/configuracion.service';
import { InventarioService } from '../../nucleo/servicios/inventario.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';

/** Redondeo a céntimos, para que lo escrito y lo guardado coincidan. */
function aCentimos(valor: number): number {
  return Math.round(valor * 100) / 100;
}

/**
 * Alta y edición de una pieza del catálogo.
 *
 * <p>El coste y la venta se pueden escribir con IVA o sin él, y el otro campo se
 * recalcula solo: la factura del proveedor viene con IVA y el precio que se le
 * dice al cliente también, pero lo que hay que guardar es la base imponible. Sin
 * esto habría que hacer la división a mano en cada alta.
 *
 * <p>El stock no se escribe: al dar de alta se puede indicar lo que hay en la
 * estantería y eso genera una entrada de almacén. Después solo cambia con
 * entradas, salidas y ajustes.
 */
@Component({
  selector: 'app-formulario-pieza',
  standalone: true,
  imports: [CommonModule, FormsModule, Dialogo],
  templateUrl: './formulario-pieza.html',
})
export class FormularioPieza {
  private readonly servicio = inject(InventarioService);
  private readonly configuracion = inject(ConfiguracionService);
  private readonly notificaciones = inject(NotificacionesService);

  /** Pieza que se edita. Sin ella, el formulario da de alta una nueva. */
  readonly pieza = input<Pieza | null>(null);

  readonly cerrar = output<void>();
  readonly guardado = output<void>();

  protected readonly enviando = signal(false);
  protected readonly familias = signal<string[]>([]);
  protected readonly proveedores = signal<Proveedor[]>([]);
  protected readonly tiposIva = signal<TipoIva[]>([]);

  protected readonly sku = signal('');
  protected readonly descripcion = signal('');
  protected readonly marca = signal('');
  protected readonly familia = signal('');
  protected readonly ubicacion = signal('');
  protected readonly unidadMedida = signal('UD');
  protected readonly stockMinimo = signal(0);
  protected readonly stockInicial = signal(0);
  protected readonly proveedorId = signal<number | null>(null);
  protected readonly tipoIva = signal('GENERAL');
  protected readonly observaciones = signal('');

  /** Ambos son base imponible: es lo que guarda el catálogo. */
  protected readonly precioCoste = signal(0);
  protected readonly precioVenta = signal(0);

  protected readonly esAlta = computed(() => this.pieza() === null);

  protected readonly porcentajeIva = computed(
    () => this.tiposIva().find((t) => t.codigo === this.tipoIva())?.porcentaje ?? 0,
  );

  protected readonly costeConIva = computed(() =>
    aCentimos(this.precioCoste() * (1 + this.porcentajeIva() / 100)),
  );

  protected readonly ventaConIva = computed(() =>
    aCentimos(this.precioVenta() * (1 + this.porcentajeIva() / 100)),
  );

  /** Lo que se gana con cada unidad, sobre el precio de compra. */
  protected readonly margen = computed(() => {
    const coste = this.precioCoste();
    if (coste <= 0) return null;
    return ((this.precioVenta() - coste) / coste) * 100;
  });

  protected readonly puedeGuardar = computed(
    () =>
      !this.enviando() &&
      !!this.sku().trim() &&
      !!this.descripcion().trim() &&
      this.precioCoste() >= 0 &&
      this.precioVenta() >= 0,
  );

  constructor() {
    this.servicio.familias().subscribe((f) => this.familias.set(f));
    this.servicio.proveedores().subscribe((p) => this.proveedores.set(p.contenido));
    this.configuracion.obtener().subscribe((c) => {
      this.tiposIva.set(c.tiposIva);
      if (this.esAlta()) this.tipoIva.set(c.tipoIvaDefecto);
    });

    // El valor de `input()` no está puesto todavía cuando corre el constructor.
    queueMicrotask(() => {
      const p = this.pieza();
      if (!p) return;
      this.sku.set(p.sku);
      this.descripcion.set(p.descripcion);
      this.marca.set(p.marca ?? '');
      this.familia.set(p.familia ?? '');
      this.ubicacion.set(p.ubicacion ?? '');
      this.unidadMedida.set(p.unidadMedida);
      this.stockMinimo.set(p.stockMinimo);
      this.proveedorId.set(p.proveedorId);
      this.tipoIva.set(p.tipoIva);
      this.observaciones.set(p.observaciones ?? '');
      this.precioCoste.set(p.precioCoste);
      this.precioVenta.set(p.precioVenta);
    });
  }

  /** Escriben el precio con IVA (el de la factura del proveedor): se quita. */
  protected fijarCosteConIva(conIva: number): void {
    this.precioCoste.set(aCentimos(conIva / (1 + this.porcentajeIva() / 100)));
  }

  protected fijarVentaConIva(conIva: number): void {
    this.precioVenta.set(aCentimos(conIva / (1 + this.porcentajeIva() / 100)));
  }

  /** Pone el PVP a partir del margen que quiere sacarse el taller. */
  protected aplicarMargen(porcentaje: number): void {
    this.precioVenta.set(aCentimos(this.precioCoste() * (1 + porcentaje / 100)));
  }

  protected guardar(): void {
    if (!this.puedeGuardar()) return;
    this.enviando.set(true);

    const datos = {
      sku: this.sku().trim().toUpperCase(),
      descripcion: this.descripcion().trim(),
      marca: this.marca().trim() || null,
      familia: this.familia().trim() || null,
      ubicacion: this.ubicacion().trim() || null,
      stockMinimo: this.stockMinimo(),
      tipoIva: this.tipoIva(),
      proveedorId: this.proveedorId(),
      unidadMedida: this.unidadMedida().trim().toUpperCase() || 'UD',
      observaciones: this.observaciones().trim() || null,
    };

    const existente = this.pieza();
    if (!existente) {
      this.servicio
        .crearPieza({
          ...datos,
          precioCoste: this.precioCoste(),
          precioVenta: this.precioVenta(),
          stockInicial: this.stockInicial() || undefined,
        })
        .subscribe({
          next: (creada) => this.terminar(`${creada.sku} dada de alta.`),
          error: () => this.enviando.set(false),
        });
      return;
    }

    // Los precios van en su propia petición: la API los separa porque cambiarlos
    // no toca las órdenes ya abiertas, que llevan el precio congelado.
    const preciosCambian =
      existente.precioCoste !== this.precioCoste() || existente.precioVenta !== this.precioVenta();

    const peticion = preciosCambian
      ? this.servicio.actualizarPieza(existente.id, datos).pipe(
          switchMap(() =>
            this.servicio.actualizarPrecios(existente.id, {
              precioCoste: this.precioCoste(),
              precioVenta: this.precioVenta(),
            }),
          ),
        )
      : this.servicio.actualizarPieza(existente.id, datos);

    peticion.subscribe({
      next: () => this.terminar(`${datos.sku} actualizada.`),
      error: () => this.enviando.set(false),
    });
  }

  private terminar(mensaje: string): void {
    this.enviando.set(false);
    this.notificaciones.exito(mensaje);
    this.guardado.emit();
  }
}
