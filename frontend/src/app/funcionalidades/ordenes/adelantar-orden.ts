import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Icono } from '../../compartido/icono';
import { OrdenTrabajo } from '../../nucleo/modelos/taller';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { FormularioOrden } from './formulario-orden';

/**
 * Adelantar una orden de trabajo.
 *
 * <p>El caso: el trabajo ya está hablado y cerrado con el cliente, y lo único
 * que falta es que alguien lo haga. Dirección abre la orden, la compone entera
 * y se la asigna a un técnico, que la ve entre las suyas y la ejecuta sin ver
 * un solo importe.
 *
 * <p><b>Por qué es una pantalla del menú y no una opción del alta normal.</b>
 * Quien entra aquí ya sabe lo que está haciendo: lo dice el hecho de haber
 * elegido esta entrada. Metido como una casilla dentro del formulario genérico
 * obligaba a todo el mundo —mostrador incluido— a decidir sobre algo que casi
 * nunca les toca, y encima antes de tener delante el trabajo. Aquí la decisión
 * está tomada de antemano y el formulario va derecho al grano.
 *
 * <p>Al guardar no se vuelve al listado sino al presupuesto de la orden recién
 * creada, que es lo siguiente que hay que hacer: componer el trabajo. Ahí está
 * también el volcado de plantillas, que es lo que hace esto rápido de verdad.
 */
@Component({
  selector: 'app-adelantar-orden',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormularioOrden, Icono],
  templateUrl: './adelantar-orden.html',
  styleUrl: './adelantar-orden.scss',
})
export class AdelantarOrden {
  private readonly router = inject(Router);
  private readonly avisos = inject(NotificacionesService);

  /** El formulario se abre solo: es lo único que hay en esta pantalla. */
  protected readonly formularioAbierto = signal(true);

  protected trasAbrir(orden: OrdenTrabajo): void {
    this.formularioAbierto.set(false);
    this.avisos.exito(
      `Orden ${orden.codigo} adelantada para ${orden.tecnicoNombre}. Ahora compón el trabajo.`,
    );
    void this.router.navigate(['/ordenes', orden.id, 'presupuesto']);
  }

  /** Cerrar sin guardar deja la pantalla vacía: se vuelve al listado. */
  protected cancelar(): void {
    this.formularioAbierto.set(false);
    void this.router.navigate(['/ordenes']);
  }

  protected reabrir(): void {
    this.formularioAbierto.set(true);
  }
}
