import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { ColorEstadoPipe } from '../../compartido/estado-ot.pipe';
import { EstadoOT, LineaOT, OrdenTrabajo, ResultadoConsumo } from '../../nucleo/modelos/taller';
import { SerieFactura } from '../../nucleo/modelos/facturacion';
import { FacturasService } from '../../nucleo/servicios/facturas.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { OrdenesService } from '../../nucleo/servicios/ordenes.service';
import { InventarioService } from '../../nucleo/servicios/inventario.service';
import { Pieza } from '../../nucleo/modelos/taller';
import { FormsModule } from '@angular/forms';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { Tecnico } from '../../nucleo/modelos/configuracion';
import { UsuariosService } from '../../nucleo/servicios/usuarios.service';

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
  imports: [CommonModule, RouterLink, Cargando, ColorEstadoPipe, Icono, FormsModule],
  templateUrl: './detalle-orden.html',
  styleUrl: './detalle-orden.scss',
})
export class DetalleOrden {
  private readonly servicio = inject(OrdenesService);
  private readonly facturas = inject(FacturasService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly router = inject(Router);
  private readonly sesion = inject(SesionService);
  private readonly inventario = inject(InventarioService);
  private readonly usuarios = inject(UsuariosService);

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

  /** Facturar es cosa de mostrador y dirección. */
  protected readonly puedeFacturar = this.sesion.puede('ADMIN', 'MOSTRADOR');

  /**
   * ¿Puede el usuario en curso trabajar esta orden?
   *
   * Un técnico solo la suya, o una que aún no sea de nadie: eso último es cómo
   * se hace cargo de un trabajo nuevo. Mostrador y dirección, todas. Es el mismo
   * criterio que aplica el backend, replicado aquí solo para no enseñar botones
   * que van a devolver 403.
   */
  protected readonly puedeTrabajarla = computed(() => {
    const o = this.orden();
    if (!o) return false;
    if (!this.sesion.puede('TECNICO')) return true;
    return o.tecnicoId === null || o.tecnicoId === this.sesion.usuario()?.id;
  });

  /**
   * Transiciones que el backend reserva a mostrador y dirección.
   *
   * Aprobar y rechazar los decide el cliente por teléfono o en el mostrador, y
   * entregar la moto implica cobrar: nada de eso pasa por el taller.
   */
  private static readonly SOLO_MOSTRADOR: EstadoOT[] = ['APROBADA', 'RECHAZADA', 'ENTREGADA'];

  /**
   * La orden admite cambios, pero ninguno lo puede hacer este usuario.
   *
   * Distinguirlo importa: decirle a un técnico que la orden «ya no admite
   * cambios» cuando en realidad está esperando que el cliente conteste seria
   * mentirle, y acabaria preguntando por que no le funciona el programa.
   */
  protected readonly esperaAMostrador = computed(() => {
    const o = this.orden();
    return !!o && this.puedeTrabajarla() && o.estadosPosibles.length > 0
      && this.acciones().length === 0;
  });

  /** Botones que tienen sentido ahora mismo para este usuario. */
  protected readonly acciones = computed<Accion[]>(() => {
    const o = this.orden();
    if (!o || !this.puedeTrabajarla()) return [];

    const esTecnico = this.sesion.puede('TECNICO');
    return o.estadosPosibles
      .filter((destino) => !esTecnico || !DetalleOrden.SOLO_MOSTRADOR.includes(destino))
      .map((destino) => ({
        destino,
        texto: DetalleOrden.TEXTOS[destino] ?? destino,
        principal: destino !== 'RECHAZADA' && destino !== 'ESPERANDO_PIEZAS',
      }));
  });

  constructor() {
    queueMicrotask(() => this.cargar());
    // Un técnico no puede consultar las series ni el listado de técnicos:
    // pedirlas le provocaría un aviso de permisos nada más abrir cualquier orden.
    if (this.puedeFacturar) {
      this.facturas.series().subscribe((series) => {
        this.serieOrdinaria.set(series.find((s) => s.tipo === 'ORDINARIA' && s.activa) ?? null);
      });
      this.usuarios.tecnicos().subscribe((t) => this.tecnicos.set(t));
    }
  }

  // ----- Técnico asignado -----

  protected readonly tecnicos = signal<Tecnico[]>([]);

  /**
   * Quién puede repartir el trabajo.
   *
   * Mostrador y dirección eligen a cualquiera. Un técnico no reasigna nada: lo
   * único que puede es cogerse una orden que todavía no es de nadie, y eso se
   * ofrece aparte.
   */
  protected readonly puedeReasignar = this.puedeFacturar;

  protected readonly puedeCogerLaOrden = computed(
    () => this.sesion.puede('TECNICO') && this.orden()?.tecnicoId === null,
  );

  protected asignarTecnico(tecnicoId: number | null): void {
    const o = this.orden();
    if (!o || tecnicoId === o.tecnicoId) return;

    this.trabajando.set(true);
    this.servicio.asignarTecnico(o.id, tecnicoId).subscribe({
      next: (actualizada) => {
        this.orden.set(actualizada);
        this.trabajando.set(false);
        this.notificaciones.exito(
          actualizada.tecnicoNombre
            ? `La orden pasa a ${actualizada.tecnicoNombre}.`
            : 'La orden queda sin asignar.',
        );
      },
      error: () => this.trabajando.set(false),
    });
  }

  protected cogerLaOrden(): void {
    const yo = this.sesion.usuario();
    if (yo) this.asignarTecnico(yo.id);
  }

  // ----- Precio de la hora pactado para esta orden -----

  protected readonly editandoTarifa = signal(false);
  protected readonly borradorTarifa = signal<number | null>(null);

  /**
   * El precio de la hora se negocia con el cliente, y eso pasa en el mostrador.
   * Solo se puede tocar mientras la orden admita cambios en el presupuesto:
   * después ya se ha cobrado a ese precio.
   */
  protected readonly puedeCambiarTarifa = computed(
    () => this.puedeFacturar && !!this.orden()?.permiteEditarLineas,
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

  // ----- Diagnóstico -----

  protected readonly editandoDiagnostico = signal(false);
  protected readonly borradorDiagnostico = signal('');

  protected empezarDiagnostico(): void {
    this.borradorDiagnostico.set(this.orden()?.diagnostico ?? '');
    this.editandoDiagnostico.set(true);
  }

  protected guardarDiagnostico(): void {
    const o = this.orden();
    const texto = this.borradorDiagnostico().trim();
    if (!o || !texto) return;

    this.trabajando.set(true);
    this.servicio.registrarDiagnostico(o.id, texto).subscribe({
      next: (actualizada) => {
        this.orden.set(actualizada);
        this.editandoDiagnostico.set(false);
        this.trabajando.set(false);
        this.notificaciones.exito('Diagnóstico guardado.');
      },
      error: () => this.trabajando.set(false),
    });
  }

  // ----- Líneas del presupuesto -----

  protected readonly anadiendo = signal<'mano-obra' | 'pieza' | null>(null);
  protected readonly piezas = signal<Pieza[]>([]);
  protected readonly familias = signal<string[]>([]);
  protected readonly familia = signal<string>('');

  protected readonly descripcionTrabajo = signal('');
  protected readonly horas = signal<number | null>(null);
  protected readonly piezaId = signal<number | null>(null);
  protected readonly cantidad = signal<number>(1);
  /** Descuento de la línea, en porcentaje. Se pacta con el cliente concepto a concepto. */
  protected readonly descuento = signal<number>(0);

  /**
   * Solo se pueden tocar las líneas mientras la orden lo permita, y solo si es
   * tuya. `permiteEditarLineas` lo decide el backend según el estado: una vez
   * entregada, ni la aplicación ni nadie puede cambiarlas.
   */
  protected readonly puedeEditarLineas = computed(
    () => !!this.orden()?.permiteEditarLineas && this.puedeTrabajarla(),
  );

  protected abrirAlta(tipo: 'mano-obra' | 'pieza'): void {
    this.descripcionTrabajo.set('');
    this.horas.set(null);
    this.piezaId.set(null);
    this.cantidad.set(1);
    this.descuento.set(0);
    this.anadiendo.set(tipo);

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

  // ----- Precio cerrado de una línea de mano de obra -----

  protected readonly lineaEnPrecio = signal<number | null>(null);
  protected readonly borradorPrecio = signal<number | null>(null);

  /**
   * Solo se retoca el precio de la mano de obra. El de una pieza viene del
   * catálogo y se congeló al añadirla: cambiarlo aquí dejaría el presupuesto
   * diciendo una cosa y el almacén otra.
   */
  protected empezarPrecio(linea: LineaOT): void {
    if (linea.tipo !== 'MANO_DE_OBRA') return;
    this.borradorPrecio.set(linea.precioUnitario);
    this.lineaEnPrecio.set(linea.id);
  }

  protected guardarPrecio(lineaId: number): void {
    const o = this.orden();
    const precio = this.borradorPrecio();
    if (!o || precio === null || precio < 0) return;

    this.trabajando.set(true);
    this.servicio.cambiarPrecioDeLinea(o.id, lineaId, precio).subscribe({
      next: () => {
        this.lineaEnPrecio.set(null);
        this.notificaciones.exito('Precio actualizado.');
        // Se recarga entera: los totales los recalcula el servidor.
        this.cargar();
        this.trabajando.set(false);
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

  // ----- Enviar el presupuesto al cliente -----

  /**
   * Abre WhatsApp con el presupuesto escrito y el número del cliente puesto.
   *
   * No envía nada por su cuenta: deja el mensaje redactado en WhatsApp y lo
   * manda la persona, que es quien decide si además le cuenta algo. Tampoco hace
   * falta ninguna cuenta de empresa ni integración con la API de WhatsApp.
   */
  protected enviarPorWhatsapp(): void {
    const o = this.orden();
    if (!o) return;

    const telefono = numeroParaWhatsapp(o.clienteTelefono);
    if (!telefono) {
      this.notificaciones.info(
        `${o.clienteNombre} no tiene un teléfono válido en su ficha. Añádalo y vuelva a intentarlo.`,
      );
      return;
    }

    const euros = (importe: number) =>
      importe.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    const lineas = o.lineas.map(
      (l) => `• ${l.descripcion} (x${l.cantidad}): ${euros(l.total)} €`,
    );

    const mensaje = [
      `Presupuesto ${o.codigo}`,
      `${o.descripcionMoto} · ${o.matricula}`,
      '',
      ...lineas,
      '',
      `TOTAL (IVA incluido): ${euros(o.total)} €`,
      '',
      '¿Nos confirma si seguimos adelante?',
    ].join('\n');

    window.open(`https://wa.me/${telefono}?text=${encodeURIComponent(mensaje)}`, '_blank');
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
