import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { ServicioTipo } from '../../nucleo/modelos/servicios';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { ServiciosTipoService } from '../../nucleo/servicios/servicios-tipo.service';
import { FormularioServicio } from './formulario-servicio';

/**
 * Servicios tipo: las plantillas que se vuelcan de golpe en una orden.
 *
 * <p>Una «revisión de 10.000 km» son siempre las mismas horas y el mismo kit
 * de piezas. Esta pantalla es donde se definen una vez para no volver a
 * teclearlas.
 */
@Component({
  selector: 'app-servicios',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, Cargando, Icono, FormularioServicio],
  templateUrl: './servicios.html',
  styleUrl: './servicios.scss',
})
export class Servicios {
  private readonly servicios = inject(ServiciosTipoService);
  private readonly avisos = inject(NotificacionesService);

  protected readonly filas = signal<ServicioTipo[]>([]);
  protected readonly cargando = signal(true);
  protected readonly incluirRetirados = signal(false);

  /** null = cerrado; undefined = alta; un servicio = edición. */
  protected readonly editando = signal<ServicioTipo | null | undefined>(null);
  protected readonly formularioAbierto = computed(() => this.editando() !== null);

  protected readonly activos = computed(() => this.filas().filter((s) => s.activo).length);

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.servicios.listar(!this.incluirRetirados()).subscribe({
      next: (lista) => {
        this.filas.set(lista);
        this.cargando.set(false);
      },
      error: () => {
        this.cargando.set(false);
        this.avisos.error('No se han podido cargar las plantillas.');
      },
    });
  }

  protected alternarRetirados(incluir: boolean): void {
    this.incluirRetirados.set(incluir);
    this.cargar();
  }

  protected nuevo(): void {
    this.editando.set(undefined);
  }

  protected editar(servicio: ServicioTipo): void {
    this.editando.set(servicio);
  }

  protected cerrarFormulario(): void {
    this.editando.set(null);
  }

  protected trasGuardar(servicio: ServicioTipo): void {
    this.editando.set(null);
    this.avisos.exito(`Servicio «${servicio.nombre}» guardado.`);
    this.cargar();
  }

  /**
   * Retirar no borra: la plantilla sigue explicando por qué una OT del año
   * pasado tiene esas líneas. Solo desaparece del desplegable de la orden.
   */
  protected cambiarActivo(servicio: ServicioTipo): void {
    const activar = !servicio.activo;
    this.servicios.cambiarActivo(servicio.id, activar).subscribe({
      next: () => {
        this.avisos.exito(
          activar
            ? `«${servicio.nombre}» vuelve a ofrecerse.`
            : `«${servicio.nombre}» retirado. Las órdenes que ya lo usaron no cambian.`,
        );
        this.cargar();
      },
      error: () => this.avisos.error('No se ha podido cambiar el estado de la plantilla.'),
    });
  }
}
