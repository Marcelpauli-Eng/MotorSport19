import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Avisos } from './compartido/avisos';
import { SesionService } from './nucleo/servicios/sesion.service';

interface Enlace {
  ruta: string;
  texto: string;
  icono: string;
}

/**
 * Armazón de la aplicación: navegación lateral fija en escritorio y desplegable
 * en tablet, más la pila de avisos.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Avisos],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly sesion = inject(SesionService);
  protected readonly menuAbierto = signal(false);

  protected readonly enlaces: Enlace[] = [
    { ruta: '/panel', texto: 'Panel', icono: '▤' },
    { ruta: '/ordenes', texto: 'Órdenes de trabajo', icono: '🔧' },
    { ruta: '/facturas', texto: 'Facturas', icono: '📄' },
    { ruta: '/clientes', texto: 'Clientes', icono: '👤' },
    { ruta: '/motos', texto: 'Motos', icono: '🏍' },
    { ruta: '/inventario', texto: 'Inventario', icono: '📦' },
  ];

  protected alternarMenu(): void {
    this.menuAbierto.update((abierto) => !abierto);
  }

  protected cerrarMenu(): void {
    this.menuAbierto.set(false);
  }
}
