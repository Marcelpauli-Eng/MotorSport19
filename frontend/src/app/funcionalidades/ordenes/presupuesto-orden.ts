import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Observable, concat } from 'rxjs';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { ColorEstadoPipe } from '../../compartido/estado-ot.pipe';
import { ServicioTipo } from '../../nucleo/modelos/servicios';
import { LineaOT, OrdenTrabajo, Pieza } from '../../nucleo/modelos/taller';
import { InventarioService } from '../../nucleo/servicios/inventario.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { OrdenesService } from '../../nucleo/servicios/ordenes.service';
import { ServiciosTipoService } from '../../nucleo/servicios/servicios-tipo.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';

/** Las dos mitades del presupuesto: lo que se hace y lo que se monta. */
type Pestana = 'mano-obra' | 'materiales';

/**
 * Composición del presupuesto de una orden de trabajo.
 *
 * <p>Vive en su propia pantalla y no dentro de la ficha de la orden a propósito.
 * Montar un presupuesto es una tarea larga —se encadenan diez o quince
 * conceptos, se corrigen cantidades, se regatean descuentos— y necesita la
 * pantalla entera: la tabla, el buscador y los totales siempre a la vista. Como
 * un bloque más de la ficha quedaba estrujado entre la avería, las notas y el
 * historial, y había que hacer scroll para cada línea que se añadía.
 *
 * <p>El presupuesto se guarda solo: cada línea que se añade, se corrige o se
 * quita viaja al servidor en el momento. Por eso no hay botón de «Guardar», que
 * sería mentira, sino uno de volver a la orden.
 */
@Component({
  selector: 'app-presupuesto-orden',
  imports: [CommonModule, RouterLink, Cargando, ColorEstadoPipe, Icono, FormsModule],
  templateUrl: './presupuesto-orden.html',
  styleUrl: './presupuesto-orden.scss',
})
export class PresupuestoOrden {
  private readonly servicio = inject(OrdenesService);
  private readonly inventario = inject(InventarioService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly sesion = inject(SesionService);
  private readonly serviciosTipo = inject(ServiciosTipoService);

  readonly id = input.required<string>();

  protected readonly cargando = signal(true);
  protected readonly orden = signal<OrdenTrabajo | null>(null);
  protected readonly trabajando = signal(false);

  /**
   * ¿Se le enseña el dinero a este usuario?
   *
   * A un técnico no. Lo de verdad lo decide el backend, que le sirve la orden
   * con los importes a nulo; aquí solo se evita pintar columnas vacías. Para él
   * esta pantalla no es un presupuesto: es la lista de lo que tiene que hacer.
   */
  protected readonly vePrecios = this.sesion.puede('ADMIN', 'MOSTRADOR');

  constructor() {
    queueMicrotask(() => this.cargar());
  }

  private cargar(): void {
    // Las plantillas activas, para el desplegable de «volcar servicio». Si
    // falla no se avisa: es un atajo, y sin el la pantalla sigue sirviendo
    // para montar el presupuesto a mano.
    this.serviciosTipo.listar(true).subscribe({
      next: (lista) => this.servicios.set(lista),
      error: () => this.servicios.set([]),
    });

    this.servicio.obtener(Number(this.id())).subscribe({
      next: (o) => {
        this.orden.set(o);
        this.borradorDtoGeneral.set(this.dtoGeneral());
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  /**
   * Un técnico solo trabaja la suya, o una que aún no sea de nadie. Es el mismo
   * criterio que aplica el backend, replicado aquí para no enseñar botones que
   * van a devolver 403.
   */
  private readonly puedeTrabajarla = computed(() => {
    const o = this.orden();
    if (!o) return false;
    if (!this.sesion.puede('TECNICO')) return true;
    return o.tecnicoId === null || o.tecnicoId === this.sesion.usuario()?.id;
  });

  protected readonly puedeEditarLineas = computed(
    () => !!this.orden()?.permiteEditarLineas && this.puedeTrabajarla(),
  );

  // ==================================================================
  // Las dos mitades del presupuesto
  // ==================================================================

  protected readonly pestana = signal<Pestana>('mano-obra');

  protected cambiarPestana(destino: Pestana): void {
    this.pestana.set(destino);
    this.anadiendo.set(null);
    this.lineaEnEdicion.set(null);
    this.pagina.set(1);
  }

  /**
   * Las líneas, o una lista vacía.
   *
   * El interrogante va también en `lineas` y no solo en `orden`: una respuesta a
   * medias —una sesión que caduca a mitad de carga— dejaba la pantalla en blanco
   * con un error en consola en vez de avisar de lo que pasaba.
   */
  protected readonly todasLasLineas = computed(() => this.orden()?.lineas ?? []);

  private lineasDe(tipo: Pestana): LineaOT[] {
    return this.todasLasLineas().filter((l) =>
      tipo === 'mano-obra' ? l.tipo === 'MANO_DE_OBRA' : l.tipo === 'PIEZA',
    );
  }

  protected readonly numManoDeObra = computed(() => this.lineasDe('mano-obra').length);
  protected readonly numMateriales = computed(() => this.lineasDe('materiales').length);

  // ----- Buscador y paginación -----

  /** Ocho filas: las que caben sin que la tabla empuje los totales. */
  private static readonly POR_PAGINA = 8;

  protected readonly busqueda = signal('');
  protected readonly pagina = signal(1);

  /**
   * Las líneas de la pestaña abierta, filtradas por el buscador.
   *
   * Se filtra aquí y no en el servidor porque las líneas ya vienen todas en la
   * ficha: pedirle al backend que busque entre doce conceptos sería un viaje de
   * red para nada.
   */
  protected readonly lineasFiltradas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    const lineas = this.lineasDe(this.pestana());
    if (!texto) return lineas;

    return lineas.filter(
      (l) =>
        l.descripcion.toLowerCase().includes(texto) ||
        (l.piezaSku ?? '').toLowerCase().includes(texto),
    );
  });

  protected readonly totalPaginas = computed(() =>
    Math.max(1, Math.ceil(this.lineasFiltradas().length / PresupuestoOrden.POR_PAGINA)),
  );

  protected readonly paginaActual = computed(() => Math.min(this.pagina(), this.totalPaginas()));

  protected readonly lineasPagina = computed(() => {
    // La página se recorta al total: al borrar la última línea de la página 2 no
    // debe quedarse la tabla en blanco.
    const desde = (this.paginaActual() - 1) * PresupuestoOrden.POR_PAGINA;
    return this.lineasFiltradas().slice(desde, desde + PresupuestoOrden.POR_PAGINA);
  });

  protected readonly paginas = computed(() =>
    Array.from({ length: this.totalPaginas() }, (_, i) => i + 1),
  );

  protected irAPagina(n: number): void {
    this.pagina.set(Math.min(Math.max(1, n), this.totalPaginas()));
  }

  protected readonly hayFiltros = computed(() => this.busqueda().trim().length > 0);

  protected limpiarFiltros(): void {
    this.busqueda.set('');
    this.pagina.set(1);
  }

  /**
   * Precio por unidad ya con el descuento puesto.
   *
   * Se saca de la base imponible en vez de recalcular el porcentaje, para que
   * cuadre al céntimo con la columna de al lado: la base la calcula la base de
   * datos y es la que manda.
   */
  protected neto(l: LineaOT): number | null {
    if (l.baseImponible === null || !l.cantidad) return l.precioUnitario;
    return l.baseImponible / l.cantidad;
  }

  /** Referencia de almacén; la mano de obra no tiene, así que lleva la suya. */
  protected codigoDe(l: LineaOT): string {
    return l.piezaSku ?? 'MO';
  }

  // ==================================================================
  // Precio de la hora pactado para esta orden
  // ==================================================================

  protected readonly editandoTarifa = signal(false);
  protected readonly borradorTarifa = signal<number | null>(null);

  protected readonly puedeCambiarTarifa = computed(
    () => this.vePrecios && !!this.orden()?.permiteEditarLineas,
  );

  protected empezarTarifa(): void {
    this.borradorTarifa.set(this.orden()?.tarifaHora ?? null);
    this.editandoTarifa.set(true);
  }

  protected guardarTarifa(): void {
    const o = this.orden();
    const tarifa = this.borradorTarifa();
    if (!o || tarifa === null || tarifa <= 0) return;

    if (tarifa === o.tarifaHora) {
      this.editandoTarifa.set(false);
      return;
    }

    this.trabajando.set(true);
    this.servicio.cambiarTarifaHora(o.id, tarifa).subscribe({
      next: (actualizada) => {
        this.orden.set(actualizada);
        this.editandoTarifa.set(false);
        this.trabajando.set(false);
        this.notificaciones.exito(
          `Precio de la hora de esta orden: ${tarifa} €. Las horas ya apuntadas se han recalculado.`,
        );
      },
      error: () => this.trabajando.set(false),
    });
  }

  // ==================================================================
  // Descuento general
  // ==================================================================

  protected readonly borradorDtoGeneral = signal<number | null>(null);

  /**
   * El descuento que llevan TODAS las líneas, o nulo si cada una lleva el suyo.
   *
   * No es un campo de la orden: se deduce de las líneas, que es donde vive de
   * verdad. Así el pie del presupuesto nunca puede decir un descuento distinto
   * del que se va a facturar.
   */
  protected readonly dtoGeneral = computed<number | null>(() => {
    const lineas = this.todasLasLineas();
    if (!lineas.length) return 0;

    const primero = lineas[0].descuentoPct ?? 0;
    return lineas.every((l) => (l.descuentoPct ?? 0) === primero) ? primero : null;
  });

  protected readonly puedeDescontar = computed(
    () => this.vePrecios && !!this.orden()?.permiteEditarLineas && this.todasLasLineas().length > 0,
  );

  protected aplicarDtoGeneral(): void {
    const o = this.orden();
    const pct = this.borradorDtoGeneral();
    if (!o || pct === null || pct < 0 || pct > 100) return;
    if (pct === this.dtoGeneral()) return;

    // El campo se aplica al pulsar Intro y al salir de él, y pulsar Intro hace
    // las dos cosas: sin esto salen dos peticiones idénticas a la vez y la
    // segunda choca con la primera a medio escribir en la base de datos.
    if (this.trabajando()) return;

    // Pisa los descuentos pactados concepto a concepto, así que se avisa: quien
    // ha regateado una línea suelta no espera perderla al teclear aquí.
    if (
      this.dtoGeneral() === null &&
      !confirm('Hay líneas con descuentos distintos. Se sustituirán todos por este. ¿Continuar?')
    ) {
      this.borradorDtoGeneral.set(null);
      return;
    }

    this.trabajando.set(true);
    this.servicio.aplicarDescuentoGeneral(o.id, pct).subscribe({
      next: (actualizada) => {
        this.orden.set(actualizada);
        this.trabajando.set(false);
        this.notificaciones.exito(`Descuento del ${pct} % aplicado a todo el presupuesto.`);
      },
      error: () => {
        this.trabajando.set(false);
        this.borradorDtoGeneral.set(this.dtoGeneral());
      },
    });
  }

  // ==================================================================
  // Alta de líneas
  // ==================================================================

  protected readonly anadiendo = signal<'mano-obra' | 'pieza' | null>(null);
  protected readonly piezas = signal<Pieza[]>([]);
  protected readonly familias = signal<string[]>([]);
  protected readonly familia = signal<string>('');

  protected readonly descripcionTrabajo = signal('');
  protected readonly horas = signal<number | null>(null);
  protected readonly piezaId = signal<number | null>(null);
  protected readonly cantidad = signal<number>(1);
  protected readonly descuento = signal<number>(0);

  /** El «Agregar nuevo» de la barra: da de alta lo que toque según la pestaña. */
  protected agregarNuevo(): void {
    this.abrirAlta(this.pestana() === 'mano-obra' ? 'mano-obra' : 'pieza');
  }

  protected abrirAlta(tipo: 'mano-obra' | 'pieza'): void {
    this.descripcionTrabajo.set('');
    this.horas.set(null);
    this.piezaId.set(null);
    this.cantidad.set(1);
    this.descuento.set(0);
    this.lineaEnEdicion.set(null);
    this.anadiendo.set(tipo);
    this.pestana.set(tipo === 'mano-obra' ? 'mano-obra' : 'materiales');

    if (tipo === 'pieza') {
      if (!this.familias().length) {
        this.inventario.familias().subscribe((f) => this.familias.set(f));
      }
      this.cargarPiezas();
    }
  }

  /**
   * El almacén se elige en dos pasos: primero el grupo y después la pieza.
   *
   * Con un solo desplegable hay que recorrer cientos de referencias para llegar
   * a «pastillas delanteras». Con el grupo delante, la segunda lista se queda en
   * unas pocas, que es como está ordenado el almacén de verdad.
   */
  protected elegirFamilia(familia: string): void {
    this.familia.set(familia);
    this.piezaId.set(null);
    this.cargarPiezas();
  }

  private cargarPiezas(): void {
    this.inventario
      .buscarPiezas('', { familia: this.familia() || null, tamano: 300 })
      .subscribe((p) => this.piezas.set(p.contenido));
  }

  protected anadirManoDeObra(): void {
    const o = this.orden();
    const horas = this.horas();
    if (!o || !horas || !this.descripcionTrabajo().trim()) return;

    this.trabajando.set(true);
    this.servicio
      .anadirManoDeObra(o.id, {
        descripcion: this.descripcionTrabajo().trim(),
        horas,
        descuentoPct: this.descuento() || undefined,
      })
      .subscribe({
        next: () => this.trasCambiarLineas('Mano de obra añadida.'),
        error: () => this.trabajando.set(false),
      });
  }

  protected anadirPieza(): void {
    const o = this.orden();
    const pieza = this.piezaId();
    if (!o || !pieza || this.cantidad() <= 0) return;

    this.trabajando.set(true);
    this.servicio
      .anadirPieza(o.id, {
        piezaId: pieza,
        cantidad: this.cantidad(),
        descuentoPct: this.descuento() || undefined,
      })
      .subscribe({
        next: () => this.trasCambiarLineas('Pieza añadida al presupuesto.'),
        error: () => this.trabajando.set(false),
      });
  }

  // ==================================================================
  // Servicios tipo: volcar una plantilla entera
  // ==================================================================

  protected readonly servicios = signal<ServicioTipo[]>([]);
  protected readonly servicioElegido = signal<number | null>(null);

  /** Lo que se va a volcar, para poder decir «2,5 h y 4 piezas» antes de hacerlo. */
  protected readonly servicioSeleccionado = computed(
    () => this.servicios().find((s) => s.id === this.servicioElegido()) ?? null,
  );

  /**
   * Vuelca un servicio tipo entero: la revisión de 10.000 km con sus horas y su
   * kit de piezas, en vez de teclear siete líneas.
   *
   * <p>Lo que entra son líneas normales: a partir de aquí se corrigen, se
   * descuentan y se quitan como cualquier otra, y la plantilla no queda
   * enganchada de ninguna forma.
   */
  protected aplicarServicio(): void {
    const o = this.orden();
    const servicio = this.servicioSeleccionado();
    if (!o || !servicio) return;

    this.trabajando.set(true);
    this.serviciosTipo.aplicarAOrden(o.id, servicio.id).subscribe({
      next: (lineas) => {
        this.servicioElegido.set(null);
        this.trasCambiarLineas(
          `«${servicio.nombre}» añadido: ${lineas.length} ${lineas.length === 1 ? 'línea' : 'líneas'}.`,
        );
      },
      error: () => this.trabajando.set(false),
    });
  }

  // ==================================================================
  // Edición de una línea ya puesta
  // ==================================================================

  protected readonly lineaEnEdicion = signal<number | null>(null);
  protected readonly edCantidad = signal<number | null>(null);
  protected readonly edPrecio = signal<number | null>(null);
  protected readonly edDescuento = signal<number | null>(null);

  /**
   * Se edita en la propia fila y no en una modal: corregir una cantidad es lo
   * que más se hace en un presupuesto, y abrir una ventana para cambiar un «2»
   * por un «4» sobra.
   */
  protected empezarEdicion(l: LineaOT): void {
    this.anadiendo.set(null);
    this.edCantidad.set(l.cantidad);
    this.edPrecio.set(l.precioUnitario);
    this.edDescuento.set(l.descuentoPct ?? 0);
    this.lineaEnEdicion.set(l.id);
  }

  protected cancelarEdicion(): void {
    this.lineaEnEdicion.set(null);
  }

  /**
   * Guarda solo lo que haya cambiado, y en serie.
   *
   * En serie y no en paralelo a propósito: son tres escrituras sobre la misma
   * orden y la entidad lleva control de versión, así que lanzarlas a la vez se
   * arriesga a que una pise a otra y salte un error de concurrencia.
   */
  protected guardarEdicion(l: LineaOT): void {
    const o = this.orden();
    if (!o) return;

    const cantidad = this.edCantidad();
    const precio = this.edPrecio();
    const dto = this.edDescuento();

    const peticiones: Observable<unknown>[] = [];

    if (cantidad !== null && cantidad > 0 && cantidad !== l.cantidad) {
      peticiones.push(this.servicio.cambiarCantidadDeLinea(o.id, l.id, cantidad));
    }
    // El precio de una pieza viene del catálogo y se congeló al añadirla:
    // cambiarlo aquí dejaría el presupuesto diciendo una cosa y el almacén otra.
    if (
      this.vePrecios &&
      l.tipo === 'MANO_DE_OBRA' &&
      precio !== null &&
      precio >= 0 &&
      precio !== l.precioUnitario
    ) {
      peticiones.push(this.servicio.cambiarPrecioDeLinea(o.id, l.id, precio));
    }
    if (this.vePrecios && dto !== null && dto >= 0 && dto <= 100 && dto !== (l.descuentoPct ?? 0)) {
      peticiones.push(this.servicio.cambiarDescuentoDeLinea(o.id, l.id, dto));
    }

    if (!peticiones.length) {
      this.lineaEnEdicion.set(null);
      return;
    }

    this.trabajando.set(true);
    concat(...peticiones).subscribe({
      complete: () => {
        this.lineaEnEdicion.set(null);
        this.trasCambiarLineas('Línea actualizada.');
      },
      error: () => this.trabajando.set(false),
    });
  }

  protected quitarLinea(lineaId: number): void {
    const o = this.orden();
    if (!o) return;

    this.trabajando.set(true);
    this.servicio.quitarLinea(o.id, lineaId).subscribe({
      next: () => this.trasCambiarLineas('Línea retirada.'),
      error: () => this.trabajando.set(false),
    });
  }

  private trasCambiarLineas(mensaje: string): void {
    this.anadiendo.set(null);
    this.trabajando.set(false);
    this.notificaciones.exito(mensaje);
    // Se recarga entera: los totales los recalcula el servidor.
    this.cargar();
  }

  // ==================================================================
  // Mandárselo al cliente
  // ==================================================================

  protected abrirPresupuestoPdf(): void {
    const o = this.orden();
    if (!o) return;
    this.servicio.abrirPresupuestoPdf(o.id, o.codigo);
  }

  /**
   * Abre WhatsApp con el presupuesto escrito y el número del cliente puesto.
   *
   * No envía nada por su cuenta: deja el mensaje redactado y lo manda la
   * persona. Tampoco hace falta cuenta de empresa ni la API de WhatsApp.
   */
  protected enviarPorWhatsapp(): void {
    const o = this.orden();
    if (!o || o.total === null) return;

    const telefono = numeroParaWhatsapp(o.clienteTelefono);
    if (!telefono) {
      this.notificaciones.info(
        `${o.clienteNombre} no tiene un teléfono válido en su ficha. Añádalo y vuelva a intentarlo.`,
      );
      return;
    }

    const euros = (importe: number) =>
      importe.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    const mensaje = [
      `Presupuesto ${o.codigo}`,
      `${o.descripcionMoto} · ${o.matricula}`,
      '',
      ...this.todasLasLineas().map(
        (l) => `• ${l.descripcion} (x${l.cantidad}): ${euros(l.total ?? 0)} €`,
      ),
      '',
      `TOTAL (IVA incluido): ${euros(o.total)} €`,
      '',
      '¿Nos confirma si seguimos adelante?',
    ].join('\n');

    window.open(`https://wa.me/${telefono}?text=${encodeURIComponent(mensaje)}`, '_blank');
  }
}

/**
 * Deja el teléfono como lo quiere wa.me: solo dígitos y con prefijo de país.
 *
 * Los teléfonos del taller se apuntan como se dicen («656 12 34 56»), sin
 * prefijo. Se asume España cuando son nueve dígitos, que es el caso normal; si
 * ya viene con prefijo internacional se respeta.
 */
function numeroParaWhatsapp(telefono: string | null): string | null {
  if (!telefono) return null;

  const internacional = telefono.trim().startsWith('+') || telefono.trim().startsWith('00');
  const digitos = telefono.replace(/\D/g, '').replace(/^00/, '');

  if (internacional) return digitos.length >= 10 ? digitos : null;
  return digitos.length === 9 ? `34${digitos}` : null;
}
