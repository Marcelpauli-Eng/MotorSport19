import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { of, switchMap } from 'rxjs';
import { Dialogo } from '../../compartido/dialogo';
import { Cliente, ClienteResumen, Moto, MotoResumen, OrdenTrabajo } from '../../nucleo/modelos/taller';
import { ClientesService } from '../../nucleo/servicios/clientes.service';
import { MotosService } from '../../nucleo/servicios/motos.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { OrdenesService } from '../../nucleo/servicios/ordenes.service';
import { Tecnico } from '../../nucleo/modelos/configuracion';
import { UsuariosService } from '../../nucleo/servicios/usuarios.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { FormularioCliente } from '../clientes/formulario-cliente';
import { FormularioMoto } from '../motos/formulario-moto';
import { Icono } from '../../compartido/icono';

/**
 * Apertura de una orden de trabajo: la moto entra en el taller.
 *
 * <p>El flujo va por pasos: primero se elige (o crea) el cliente, después se
 * elige (o crea) una de sus motos, y por último se rellenan los datos de la
 * orden. Es el orden natural en el mostrador: «¿quién trae la moto?» → «¿qué
 * moto es?» → «¿qué le pasa?».
 *
 * <p>Si el cliente o la moto no están dados de alta, se crean ahí mismo sin
 * salir del formulario: abrir un alta aparte obliga a buscar después lo que
 * acabas de crear, y eso en el mostrador con cola es tiempo perdido.
 */
@Component({
  selector: 'app-formulario-orden',
  standalone: true,
  imports: [CommonModule, FormsModule, Dialogo, FormularioCliente, FormularioMoto, Icono],
  templateUrl: './formulario-orden.html',
  styles: [
    `
      /* Los dos caminos se eligen pulsando la tarjeta entera y no un radio
         suelto: la diferencia entre uno y otro está en la explicación, así que
         la explicación tiene que ser parte de lo que se pulsa. */
      .caminos {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: var(--e2);
      }

      .camino {
        display: flex;
        flex-direction: column;
        gap: 3px;
        padding: var(--e3);
        text-align: left;
        background: var(--blanco);
        border: 1px solid var(--gris-300);
        border-radius: var(--radio);
        cursor: pointer;
      }

      .camino:hover { border-color: var(--gris-400); background: var(--gris-50); }

      .camino--elegido {
        border-color: var(--azul);
        background: var(--azul-suave);
        box-shadow: inset 0 0 0 1px var(--azul);
      }

      .camino__titulo {
        font-size: 0.9375rem;
        font-weight: 600;
        color: var(--gris-900);
      }

      .camino__detalle {
        font-size: 0.8125rem;
        line-height: 1.4;
        color: var(--gris-600);
      }
    `,
  ],
})
export class FormularioOrden {
  private readonly servicio = inject(OrdenesService);
  private readonly clientes = inject(ClientesService);
  private readonly motos = inject(MotosService);
  private readonly usuarios = inject(UsuariosService);
  private readonly notificaciones = inject(NotificacionesService);

  /** Moto ya elegida: entonces no se pregunta cuál entra. */
  readonly motoFijada = input<number | null>(null);

  protected readonly reparteTrabajo = inject(SesionService).puede('ADMIN', 'MOSTRADOR');

  /**
   * Fija el camino y esconde el selector.
   *
   * <p>Lo usa la pantalla de «Adelantar OT», donde la decision ya esta tomada
   * por el hecho de haber entrado ahi: enseñar el selector seria ofrecer un
   * camino que esa pantalla no hace.
   */
  readonly caminoFijo = input<'revisar' | 'preparar' | null>(null);

  readonly cerrar = output<void>();
  readonly abierta = output<OrdenTrabajo>();

  protected readonly enviando = signal(false);
  protected readonly listaClientes = signal<ClienteResumen[]>([]);
  protected readonly motosDelCliente = signal<MotoResumen[]>([]);
  protected readonly tecnicos = signal<Tecnico[]>([]);
  protected readonly cargandoMotos = signal(false);

  // ----- Selección de cliente -----

  protected readonly clienteId = signal<number | null>(null);
  protected readonly clienteNombre = signal<string | null>(null);
  protected readonly creandoCliente = signal(false);

  // ----- Selección de moto -----

  protected readonly motoId = signal<number | null>(null);
  protected readonly creandoMoto = signal(false);

  // ----- Datos de la orden -----

  protected readonly tecnicoId = signal<number | null>(null);
  protected readonly kmEntrada = signal<number | null>(null);
  protected readonly problema = signal('');
  protected readonly fechaEstimada = signal('');
  protected readonly observaciones = signal('');

  /**
   * Los dos caminos que puede seguir una orden, elegidos aquí y no escondidos
   * en un botón de la ficha.
   *
   * - `revisar`: la moto entra con una avería por determinar. Se diagnostica,
   *   se presupuesta y el cliente decide. Es el caso normal del mostrador.
   * - `preparar`: el trabajo ya está hablado y cerrado con el cliente.
   *   Dirección compone la orden y se la asigna a un técnico, que la ejecuta
   *   sin ver un solo precio.
   */
  protected readonly camino = signal<'revisar' | 'preparar'>('revisar');

  ngOnInit(): void {
    const fijo = this.caminoFijo();
    if (fijo) {
      this.camino.set(fijo);
    }
  }

  protected elegirCamino(destino: 'revisar' | 'preparar'): void {
    this.camino.set(destino);
  }

  /** Preparar el trabajo es dárselo a alguien: sin técnico no hay a quién. */
  protected readonly faltaTecnico = computed(
    () => this.camino() === 'preparar' && this.tecnicoId() === null,
  );

  /** Kilómetros que ya tiene registrados la moto elegida, si es una del parque. */
  protected readonly kmMinimos = computed<number | null>(() => {
    const moto = this.motosDelCliente().find((m) => m.id === this.motoId());
    return moto ? moto.kmActual : null;
  });

  /**
   * El cuentakilómetros no retrocede.
   *
   * <p>Una lectura menor que la registrada o es un error de tecleo o alguien ha
   * tocado el cuadro; en los dos casos hay que mirarlo, no guardarlo en
   * silencio. El servidor lo rechaza igual, pero avisar aquí evita perder el
   * formulario entero por un dígito.
   */
  protected readonly kmRetrocede = computed(() => {
    const minimo = this.kmMinimos();
    const km = this.kmEntrada();
    return minimo !== null && km !== null && km < minimo;
  });

  protected readonly puedeGuardar = computed(
    () =>
      !this.enviando() &&
      this.motoId() !== null &&
      this.problema().trim().length > 0 &&
      this.kmEntrada() !== null &&
      !this.kmRetrocede() &&
      !this.faltaTecnico(),
  );

  constructor() {
    this.clientes.buscar('', true, 0, 300).subscribe((p) => this.listaClientes.set(p.contenido));

    if (this.reparteTrabajo) {
      this.usuarios.tecnicos().subscribe((t) => this.tecnicos.set(t));
    }

    // Si viene con moto fijada, hay que resolver el cliente automáticamente.
    queueMicrotask(() => {
      const fijada = this.motoFijada();
      if (fijada) {
        this.motos.obtener(fijada).subscribe((m) => {
          this.clienteId.set(m.clienteId);
          this.clienteNombre.set(m.clienteNombre);
          this.motoId.set(m.id);
          this.kmEntrada.set(m.kmActual);
          this.cargarMotosDelCliente(m.clienteId);
        });
      }
    });
  }

  // ----- Cliente -----

  protected elegirCliente(id: number | null): void {
    this.clienteId.set(id);
    // Limpiar la moto elegida: es de otro cliente.
    this.motoId.set(null);
    this.kmEntrada.set(null);
    this.motosDelCliente.set([]);

    if (id) {
      const cliente = this.listaClientes().find((c) => c.id === id);
      this.clienteNombre.set(cliente?.nombreCompleto ?? null);
      this.cargarMotosDelCliente(id);
    } else {
      this.clienteNombre.set(null);
    }
  }

  protected trasCrearCliente(cliente: Cliente): void {
    this.creandoCliente.set(false);
    // Añadirlo a la lista para que aparezca en el selector.
    this.listaClientes.update((lista) => [
      { id: cliente.id, nombreCompleto: cliente.nombreCompleto, documento: cliente.documento, telefono: cliente.telefono, email: cliente.email, activo: true, facturable: cliente.facturable },
      ...lista,
    ]);
    // Seleccionarlo automáticamente.
    this.clienteId.set(cliente.id);
    this.clienteNombre.set(cliente.nombreCompleto);
    this.cargarMotosDelCliente(cliente.id);
  }

  // ----- Moto -----

  private cargarMotosDelCliente(clienteId: number): void {
    this.cargandoMotos.set(true);
    this.clientes.motosDe(clienteId, true).subscribe({
      next: (motos) => {
        this.motosDelCliente.set(motos);
        this.cargandoMotos.set(false);
        // Si solo tiene una moto, seleccionarla directamente.
        if (motos.length === 1 && !this.motoId()) {
          this.elegirMoto(motos[0].id);
        }
      },
      error: () => this.cargandoMotos.set(false),
    });
  }

  /** Al elegir moto se propone su último kilometraje conocido. */
  protected elegirMoto(id: number | null): void {
    this.motoId.set(id);
    const moto = this.motosDelCliente().find((m) => m.id === id);
    if (moto) {
      this.kmEntrada.set(moto.kmActual);
    }
  }

  protected trasCrearMoto(moto: Moto): void {
    this.creandoMoto.set(false);
    // Recargar la lista y seleccionar la nueva.
    this.cargarMotosDelCliente(this.clienteId()!);
    this.motoId.set(moto.id);
    this.kmEntrada.set(moto.kmActual);
  }

  // ----- Guardar -----

  protected guardar(): void {
    if (!this.puedeGuardar()) return;
    this.enviando.set(true);

    this.servicio
      .abrir({
        motoId: this.motoId()!,
        kmEntrada: this.kmEntrada()!,
        problemaReportado: this.problema().trim(),
        tecnicoId: this.tecnicoId(),
        fechaEstimadaSalida: this.fechaEstimada() || null,
        observaciones: this.observaciones().trim() || null,
      })
      .pipe(
        // Por la vía corta la orden nace igual y acto seguido se prepara. Son
        // dos pasos y no uno porque el historial tiene que contar las dos
        // cosas: que la moto entró y que el trabajo se asignó.
        switchMap((o) =>
          this.camino() === 'preparar'
            ? this.servicio.preparar(o.id, this.tecnicoId())
            : of(o),
        ),
      )
      .subscribe({
        next: (o) => {
          this.enviando.set(false);
          this.notificaciones.exito(
            this.camino() === 'preparar'
              ? `Orden ${o.codigo} preparada para ${o.tecnicoNombre}.`
              : `Orden ${o.codigo} abierta.`,
          );
          this.abierta.emit(o);
        },
        // El interceptor ya enseña el motivo: por ejemplo, que esa moto ya
        // tiene una orden sin cerrar.
        error: () => this.enviando.set(false),
      });
  }
}
