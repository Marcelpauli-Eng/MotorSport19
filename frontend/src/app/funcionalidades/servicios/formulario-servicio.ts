import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialogo } from '../../compartido/dialogo';
import { Icono } from '../../compartido/icono';
import { GuardarServicioTipo, ServicioTipo } from '../../nucleo/modelos/servicios';
import { Pieza } from '../../nucleo/modelos/taller';
import { InventarioService } from '../../nucleo/servicios/inventario.service';
import { ServiciosTipoService } from '../../nucleo/servicios/servicios-tipo.service';

/** Línea mientras se edita: aún no ha pasado por el servidor. */
interface LineaBorrador {
  tipo: 'MANO_DE_OBRA' | 'PIEZA';
  descripcion: string;
  piezaId: number | null;
  cantidad: number | null;
}

/**
 * Alta y edición de un servicio tipo.
 *
 * <p>Las líneas se editan en bloque y se mandan de una vez. Es lo que encaja
 * con cómo se piensa una revisión: no se añade «una línea más» a algo que ya
 * está en marcha, se repasa la lista entera y se guarda.
 */
@Component({
  selector: 'app-formulario-servicio',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, Dialogo, Icono],
  templateUrl: './formulario-servicio.html',
  styleUrl: './formulario-servicio.scss',
})
export class FormularioServicio {
  private readonly servicios = inject(ServiciosTipoService);
  private readonly inventario = inject(InventarioService);

  /** Si viene, se edita; si no, es un alta. */
  readonly servicio = input<ServicioTipo | null>(null);

  readonly cerrar = output<void>();
  readonly guardado = output<ServicioTipo>();

  protected readonly nombre = signal('');
  protected readonly descripcion = signal('');
  protected readonly lineas = signal<LineaBorrador[]>([]);
  protected readonly piezas = signal<Pieza[]>([]);
  protected readonly guardando = signal(false);
  protected readonly error = signal('');

  /** El total de horas se ve mientras se monta, que es cuando se decide. */
  protected readonly horasTotales = computed(() =>
    this.lineas()
      .filter((l) => l.tipo === 'MANO_DE_OBRA')
      .reduce((suma, l) => suma + (l.cantidad ?? 0), 0),
  );

  protected readonly numeroDePiezas = computed(
    () => this.lineas().filter((l) => l.tipo === 'PIEZA').length,
  );

  protected readonly titulo = computed(() =>
    this.servicio() ? 'Editar servicio' : 'Nuevo servicio',
  );

  constructor() {
    // El catálogo entero de una vez: el desplegable de pieza lo necesita en
    // cada línea, y pedirlo por línea sería una petición por fila.
    this.inventario.buscarPiezas('', { soloActivas: true, tamano: 500 }).subscribe({
      next: (pagina) => this.piezas.set(pagina.contenido),
      error: () => this.error.set('No se ha podido cargar el catálogo de piezas.'),
    });
  }

  ngOnInit(): void {
    const actual = this.servicio();
    if (!actual) {
      // Un alta arranca con una línea de mano de obra: toda revisión lleva
      // horas, y empezar con la lista vacía obliga a un clic que sobra.
      this.lineas.set([{ tipo: 'MANO_DE_OBRA', descripcion: '', piezaId: null, cantidad: null }]);
      return;
    }
    this.nombre.set(actual.nombre);
    this.descripcion.set(actual.descripcion ?? '');
    this.lineas.set(
      actual.lineas.map((l) => ({
        tipo: l.tipo,
        descripcion: l.tipo === 'MANO_DE_OBRA' ? l.descripcion : '',
        piezaId: l.piezaId,
        cantidad: l.cantidad,
      })),
    );
  }

  protected anadirLinea(tipo: 'MANO_DE_OBRA' | 'PIEZA'): void {
    this.lineas.update((lista) => [
      ...lista,
      { tipo, descripcion: '', piezaId: null, cantidad: null },
    ]);
  }

  protected quitarLinea(indice: number): void {
    this.lineas.update((lista) => lista.filter((_, i) => i !== indice));
  }

  protected cambiarLinea(indice: number, cambios: Partial<LineaBorrador>): void {
    this.lineas.update((lista) =>
      lista.map((l, i) => (i === indice ? { ...l, ...cambios } : l)),
    );
  }

  protected guardar(): void {
    const problema = this.validar();
    if (problema) {
      this.error.set(problema);
      return;
    }

    const datos: GuardarServicioTipo = {
      nombre: this.nombre().trim(),
      descripcion: this.descripcion().trim() || null,
      lineas: this.lineas().map((l) => ({
        descripcion: l.tipo === 'MANO_DE_OBRA' ? l.descripcion.trim() : null,
        piezaId: l.tipo === 'PIEZA' ? l.piezaId : null,
        cantidad: l.cantidad!,
      })),
    };

    this.guardando.set(true);
    this.error.set('');

    const actual = this.servicio();
    const peticion = actual
      ? this.servicios.actualizar(actual.id, datos)
      : this.servicios.crear(datos);

    peticion.subscribe({
      next: (guardado) => {
        this.guardando.set(false);
        this.guardado.emit(guardado);
      },
      error: (fallo) => {
        this.guardando.set(false);
        this.error.set(fallo?.error?.mensaje ?? 'No se ha podido guardar el servicio.');
      },
    });
  }

  /**
   * Se valida aquí antes de mandar para que el mensaje diga qué línea falla.
   * El servidor lo vuelve a comprobar, que es donde manda de verdad.
   */
  private validar(): string {
    if (!this.nombre().trim()) {
      return 'Ponle un nombre al servicio.';
    }
    if (!this.lineas().length) {
      return 'Un servicio sin líneas no ahorra nada: añade al menos una.';
    }
    for (const [i, linea] of this.lineas().entries()) {
      const n = i + 1;
      if (!linea.cantidad || linea.cantidad <= 0) {
        return `La línea ${n} necesita una cantidad mayor que cero.`;
      }
      if (linea.tipo === 'MANO_DE_OBRA' && !linea.descripcion.trim()) {
        return `La línea ${n} es de mano de obra y necesita una descripción.`;
      }
      if (linea.tipo === 'PIEZA' && !linea.piezaId) {
        return `La línea ${n} es de pieza: elige una del catálogo.`;
      }
    }
    return '';
  }
}
