import { Pipe, PipeTransform } from '@angular/core';
import { EstadoOT } from '../nucleo/modelos/taller';

/**
 * Color de la etiqueta según el estado de la orden.
 *
 * El código de color es el del taller: ámbar para "algo espera a alguien",
 * azul para "está en marcha", verde para "terminado", rojo para "no sigue".
 */
@Pipe({ name: 'colorEstado' })
export class ColorEstadoPipe implements PipeTransform {
  private static readonly COLORES: Record<EstadoOT, string> = {
    RECIBIDA: 'gris',
    // Mismo color que APROBADA: las dos significan «lista para el taller».
    PREPARADA: 'morado',
    EN_DIAGNOSTICO: 'azul',
    PRESUPUESTADA: 'ambar',
    APROBADA: 'morado',
    EN_REPARACION: 'azul',
    ESPERANDO_PIEZAS: 'ambar',
    LISTA: 'verde',
    ENTREGADA: 'gris',
    RECHAZADA: 'rojo',
  };

  transform(estado: EstadoOT | null | undefined): string {
    return estado ? (ColorEstadoPipe.COLORES[estado] ?? 'gris') : 'gris';
  }
}

/** Texto legible de un estado, por si el backend no lo envía. */
@Pipe({ name: 'textoEstado' })
export class TextoEstadoPipe implements PipeTransform {
  private static readonly TEXTOS: Record<EstadoOT, string> = {
    RECIBIDA: 'Recibida',
    PREPARADA: 'Preparada, pendiente de empezar',
    EN_DIAGNOSTICO: 'En diagnóstico',
    PRESUPUESTADA: 'Presupuestada',
    APROBADA: 'Aprobada',
    EN_REPARACION: 'En reparación',
    ESPERANDO_PIEZAS: 'Esperando piezas',
    LISTA: 'Lista para entregar',
    ENTREGADA: 'Entregada',
    RECHAZADA: 'Rechazada',
  };

  transform(estado: EstadoOT | null | undefined): string {
    return estado ? (TextoEstadoPipe.TEXTOS[estado] ?? estado) : '';
  }
}
