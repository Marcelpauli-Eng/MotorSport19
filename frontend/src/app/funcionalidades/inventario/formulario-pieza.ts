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
/**
 * Lo que se puso en el alta anterior, para no volver a escribirlo.
 *
 * <p>Se guarda en el propio equipo y no en el servidor: es la ayuda de quien
 * está metiendo un albarán en esta mesa, no un dato del taller.
 */
const ULTIMA_ALTA = 'motorsport19.ultimaAlta';

interface UltimaAlta {
  familia?: string;
  ubicacion?: string;
  unidadMedida?: string;
  proveedorId?: number | null;
}

function leerUltimaAlta(): UltimaAlta {
  try {
    return JSON.parse(localStorage.getItem(ULTIMA_ALTA) ?? '{}') as UltimaAlta;
  } catch {
    // Un localStorage lleno de basura no puede impedir dar de alta una pieza.
    return {};
  }
}

function anotarUltimaAlta(datos: UltimaAlta): void {
  try {
    localStorage.setItem(ULTIMA_ALTA, JSON.stringify(datos));
  } catch {
    /* Modo privado o cuota llena: la ayuda se pierde, el alta no. */
  }
}

/**
 * Lo escrito, o lo del alta anterior si se dejó en gris.
 *
 * <p>Es toda la regla del recordatorio: el valor anterior se enseña de gris en
 * el hueco del campo y solo se usa si nadie escribe encima. Así no hay que ir
 * borrando lo que la pantalla haya dejado puesto.
 */
export function oUltimo(escrito: string, ultimo: string | undefined): string | null {
  return escrito.trim() || ultimo?.trim() || null;
}

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

  /** La pieza recién creada, para que quien abrió el formulario pueda usarla. */
  readonly guardado = output<Pieza | null>();

  /** Lo que se puso en el alta anterior. Se enseña de gris, no escrito. */
  protected readonly ultima: UltimaAlta = leerUltimaAlta();

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
      if (!p) {
        // El proveedor es un desplegable: ahí no hay nada que borrar, así que
        // se deja elegido el del alta anterior en vez de enseñarlo de gris.
        if (this.ultima.proveedorId !== undefined) this.proveedorId.set(this.ultima.proveedorId);
        return;
      }
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
      // Los precios llegan a nulo si quien pregunta es un técnico, pero este
      // formulario solo lo abre dirección: ahí vienen siempre.
      this.precioCoste.set(p.precioCoste ?? 0);
      this.precioVenta.set(p.precioVenta ?? 0);
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
    // Se deja, que una liquidación existe, pero no sin preguntar: casi siempre es
    // un cero de menos al teclear el precio.
    if (
      this.precioVenta() < this.precioCoste() &&
      !confirm(
        `El precio de venta (${this.precioVenta()} €) es menor que el coste (${this.precioCoste()} €): cada unidad se venderá con pérdidas. ¿Guardar igualmente?`,
      )
    )
      return;
    this.enviando.set(true);

    const existente = this.pieza();
    // Solo un alta hereda lo gris del alta anterior. En una edición, un campo
    // vacío es que se quiere vaciar, y rellenarlo solo sería cambiar la ficha
    // por detrás.
    const ultima: UltimaAlta = existente ? {} : this.ultima;

    const datos = {
      sku: this.sku().trim().toUpperCase(),
      descripcion: this.descripcion().trim(),
      marca: this.marca().trim() || null,
      familia: oUltimo(this.familia(), ultima.familia),
      ubicacion: oUltimo(this.ubicacion(), ultima.ubicacion),
      stockMinimo: this.stockMinimo(),
      tipoIva: this.tipoIva(),
      proveedorId: this.proveedorId(),
      unidadMedida: (oUltimo(this.unidadMedida(), ultima.unidadMedida) ?? 'UD').toUpperCase(),
      observaciones: this.observaciones().trim() || null,
    };
    if (!existente) {
      this.servicio
        .crearPieza({
          ...datos,
          precioCoste: this.precioCoste(),
          precioVenta: this.precioVenta(),
          stockInicial: this.stockInicial() || undefined,
        })
        .subscribe({
          next: (creada) => {
            anotarUltimaAlta({
              familia: datos.familia ?? undefined,
              ubicacion: datos.ubicacion ?? undefined,
              unidadMedida: datos.unidadMedida,
              proveedorId: datos.proveedorId,
            });
            this.terminar(`${creada.sku} dada de alta.`, creada);
          },
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

  private terminar(mensaje: string, creada: Pieza | null = null): void {
    this.enviando.set(false);
    this.notificaciones.exito(mensaje);
    this.guardado.emit(creada);
  }
}
