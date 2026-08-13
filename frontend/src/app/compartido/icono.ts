import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { inject } from '@angular/core';

export type NombreIcono =
  | 'panel'
  | 'ordenes'
  | 'facturas'
  | 'clientes'
  | 'motos'
  | 'inventario'
  | 'salir'
  | 'menu'
  | 'buscar'
  | 'descargar'
  | 'sello'
  | 'alerta'
  | 'aviso'
  | 'correcto'
  | 'cerrar'
  | 'mas'
  | 'usuario'
  | 'reloj'
  | 'flecha-derecha'
  | 'flecha-izquierda'
  | 'documento'
  | 'llave'
  | 'caja'
  | 'informes'
  | 'ajustes'
  | 'whatsapp'
  | 'agenda'
  | 'lapiz'
  | 'papelera'
  | 'filtro';

/**
 * Trazos de cada icono, sin el `<svg>` que los envuelve.
 *
 * Dibujados sobre una caja de 24 con trazo de 1.75: a 18–20 px de tamaño real
 * quedan nítidos sin verse endebles. Se usan trazos y no rellenos para que
 * hereden el color del texto y encajen con cualquier estado.
 *
 * Sustituyen a los emoji que había antes. Un emoji lo dibuja el sistema
 * operativo, así que el mismo menú se veía distinto en el PC del mostrador y en
 * la tablet, con colores que no eran los de la aplicación y alineaciones que no
 * cuadraban con el texto.
 */
const TRAZOS: Record<NombreIcono, string> = {
  panel:
    '<rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/>' +
    '<rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>',
  ordenes:
    '<path d="M14.7 6.3a4 4 0 0 0 5 5l-9.4 9.4a2.1 2.1 0 0 1-3-3l9.4-9.4a4 4 0 0 0-5-5"/>' +
    '<path d="m14.7 6.3 3 3"/>',
  facturas:
    '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/>' +
    '<path d="M9 13h6"/><path d="M9 17h4"/>',
  clientes:
    '<path d="M16 20v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 18.5V20"/>' +
    '<circle cx="10" cy="8" r="3.5"/><path d="M20 20v-1.5a3.5 3.5 0 0 0-2.6-3.4"/>' +
    '<path d="M15.5 4.8a3.5 3.5 0 0 1 0 6.4"/>',
  motos:
    '<circle cx="5.5" cy="16.5" r="3.5"/><circle cx="18.5" cy="16.5" r="3.5"/>' +
    '<path d="M5.5 16.5h5l4-6h3"/><path d="M12 10.5 9.5 7H7"/><path d="m15 10.5 3.5 6"/>' +
    '<path d="M16 7h3"/>',
  inventario:
    '<path d="m12 2.8 8 4.2v10L12 21.2 4 17V7z"/><path d="m4 7 8 4.2L20 7"/><path d="M12 11.2V21"/>',
  caja: '<path d="m12 2.8 8 4.2v10L12 21.2 4 17V7z"/><path d="m4 7 8 4.2L20 7"/><path d="M12 11.2V21"/>',
  salir:
    '<path d="M9 21H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3"/><path d="m15.5 16.5 4.5-4.5-4.5-4.5"/>' +
    '<path d="M20 12H9"/>',
  menu: '<path d="M4 7h16"/><path d="M4 12h16"/><path d="M4 17h16"/>',
  buscar: '<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.4-4.4"/>',
  descargar: '<path d="M12 3v12"/><path d="m7.5 10.5 4.5 4.5 4.5-4.5"/><path d="M4 20h16"/>',
  sello:
    '<path d="M12 2.8 20 6v6c0 4.5-3.2 8.3-8 9.2C7.2 20.3 4 16.5 4 12V6z"/>' +
    '<path d="m9 12 2.2 2.2L15.5 10"/>',
  alerta:
    '<path d="M10.3 4.3 2.8 17.2A1.9 1.9 0 0 0 4.4 20h15.2a1.9 1.9 0 0 0 1.6-2.8L13.7 4.3a1.9 1.9 0 0 0-3.4 0z"/>' +
    '<path d="M12 9.5v4"/><path d="M12 17h.01"/>',
  aviso: '<circle cx="12" cy="12" r="9"/><path d="M12 8h.01"/><path d="M11 12h1v4h1"/>',
  correcto: '<circle cx="12" cy="12" r="9"/><path d="m8.5 12.2 2.4 2.4 4.6-4.9"/>',
  cerrar: '<path d="m6.5 6.5 11 11"/><path d="m17.5 6.5-11 11"/>',
  mas: '<path d="M12 5v14"/><path d="M5 12h14"/>',
  usuario: '<circle cx="12" cy="8.5" r="3.8"/><path d="M19 20.5v-1a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v1"/>',
  reloj: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5.2l3.2 2"/>',
  'flecha-derecha': '<path d="M4 12h15"/><path d="m13 6 6 6-6 6"/>',
  'flecha-izquierda': '<path d="M20 12H5"/><path d="m11 6-6 6 6 6"/>',
  documento:
    '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/>',
  informes:
    '<path d="M4 20V10"/><path d="M10 20V4"/><path d="M16 20v-7"/><path d="M22 20H2"/>',
  llave:
    '<path d="M15.5 8.5a4 4 0 1 0-4.3 4L9 15h-2v2H5v2H2.5v-2.6l6.2-6.2a4 4 0 0 0 6.8-1.7z"/>' +
    '<path d="M16.5 6.5h.01"/>',
  ajustes:
    '<circle cx="12" cy="12" r="3"/>' +
    '<path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.1A1.6 1.6 0 0 0 8 19.4a1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H2a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 3.6 8a1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 2.7-1.1V2a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7H22a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1z"/>',
  agenda:
    '<rect x="3.5" y="5" width="17" height="15.5" rx="2"/><path d="M3.5 9.5h17"/>' +
    '<path d="M8 3.5v3"/><path d="M16 3.5v3"/><path d="M7.5 13h3"/><path d="M7.5 16.5h6"/>',
  whatsapp:
    '<path d="M20.5 11.6a8.5 8.5 0 0 1-12.6 7.4L3.5 20.5l1.6-4.3A8.5 8.5 0 1 1 20.5 11.6z"/>' +
    '<path d="M9 9.2c.3-.7.6-.7 1-.7h.5c.2 0 .4 0 .6.5l.7 1.6c.1.2 0 .4-.1.5l-.5.6c-.2.2-.2.3 0 .6a6 6 0 0 0 2.8 2.4c.3.1.4 0 .6-.1l.6-.7c.2-.2.3-.1.5 0l1.5.8c.2.1.3.2.3.4 0 .3-.2 1-.5 1.3-.3.3-.9.6-1.5.6-2.5 0-6.6-3.4-6.6-6 0-.7.3-1.4.6-1.8z"/>',
  lapiz:
    '<path d="M16.5 3.9a2.1 2.1 0 0 1 3 3L8.2 18.2l-4 1 1-4z"/><path d="m14.6 5.8 3 3"/>',
  papelera:
    '<path d="M4.5 6.5h15"/><path d="M9.5 6.5V4.8c0-.7.6-1.3 1.3-1.3h2.4c.7 0 1.3.6 1.3 1.3v1.7"/>' +
    '<path d="M6.5 6.5v12.2c0 .9.7 1.6 1.6 1.6h7.8c.9 0 1.6-.7 1.6-1.6V6.5"/>' +
    '<path d="M10 10.5v6"/><path d="M14 10.5v6"/>',
  filtro: '<path d="M3.5 5.5h17l-6.8 8v5.5l-3.4 1.7V13.5z"/>',
};

/**
 * Icono en línea.
 *
 * Hereda el color del texto (`currentColor`) y se alinea con la línea base, de
 * forma que dentro de un botón o de una etiqueta encaja sin ajustes.
 */
@Component({
  selector: 'app-icono',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg
    [attr.width]="tamano()"
    [attr.height]="tamano()"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    [attr.stroke-width]="grosor()"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
    [innerHTML]="trazos()"
  ></svg>`,
  styles: [
    `
      :host {
        display: inline-flex;
        flex: none;
        align-items: center;
        justify-content: center;
      }
    `,
  ],
})
export class Icono {
  private readonly sanitizador = inject(DomSanitizer);

  readonly nombre = input.required<NombreIcono>();
  readonly tamano = input(18);
  /** A tamaños grandes el trazo se afina para que no engorde. */
  readonly grosor = input(1.75);

  protected readonly trazos = computed<SafeHtml>(() =>
    // Los trazos son constantes de este fichero, no entran datos de fuera.
    this.sanitizador.bypassSecurityTrustHtml(TRAZOS[this.nombre()]),
  );
}
