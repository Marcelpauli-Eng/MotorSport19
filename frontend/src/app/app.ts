import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Avisos } from './compartido/avisos';
import { Rol, SesionService } from './nucleo/servicios/sesion.service';

interface Enlace {
  ruta: string;
  texto: string;
  icono: string;
  /** Roles que ven el enlace. Sin roles, lo ve todo el mundo. */
  roles?: Rol[];
}

/**
 * Armazón de la aplicación: navegación lateral fija en escritorio y desplegable
 * en tablet, más la pila de avisos.
 *
 * Sin sesión iniciada no se pinta el armazón: la pantalla de entrada ocupa el
 * hueco entera, para que no haya menús a la vista antes de identificarse.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Avisos],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly router = inject(Router);
  protected readonly sesion = inject(SesionService);
  protected readonly menuAbierto = signal(false);

  private readonly todosLosEnlaces: Enlace[] = [
    { ruta: '/panel', texto: 'Panel', icono: '▤' },
    { ruta: '/ordenes', texto: 'Órdenes de trabajo', icono: '🔧' },
    { ruta: '/facturas', texto: 'Facturas', icono: '📄', roles: ['ADMIN', 'MOSTRADOR'] },
    { ruta: '/clientes', texto: 'Clientes', icono: '👤' },
    { ruta: '/motos', texto: 'Motos', icono: '🏍' },
    { ruta: '/inventario', texto: 'Inventario', icono: '📦' },
  ];

  /** Solo los enlaces que el rol puede abrir: el resto ni se enseñan. */
  protected readonly enlaces = computed(() =>
    this.todosLosEnlaces.filter((e) => !e.roles || this.sesion.puede(...e.roles)),
  );

  constructor() {
    // Si hay token guardado se confirma contra el servidor. Cuando ha caducado,
    // el interceptor recibe el 401, cierra la sesion y lleva a la entrada: sin
    // esto la interfaz quedaria montada y el usuario solo lo descubriria al
    // pulsar algo.
    if (this.sesion.autenticado()) {
      this.sesion.revalidar().subscribe({ error: () => {} });
    }
  }

  protected alternarMenu(): void {
    this.menuAbierto.update((abierto) => !abierto);
  }

  protected cerrarMenu(): void {
    this.menuAbierto.set(false);
  }

  protected salir(): void {
    this.sesion.salir();
    void this.router.navigate(['/entrar']);
  }
}
