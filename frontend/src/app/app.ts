import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Avisos } from './compartido/avisos';
import { Icono, NombreIcono } from './compartido/icono';
import { Rol, SesionService } from './nucleo/servicios/sesion.service';

interface Enlace {
  ruta: string;
  texto: string;
  icono: NombreIcono;
  /** Roles que ven el enlace. Sin roles, lo ve todo el mundo. */
  roles?: Rol[];
}

interface Grupo {
  titulo: string;
  enlaces: Enlace[];
}

const ROLES_LEGIBLES: Record<Rol, string> = {
  ADMIN: 'Dirección',
  MOSTRADOR: 'Mostrador',
  TECNICO: 'Taller',
};

/**
 * Armazón de la aplicación: barra lateral fija en escritorio y desplegable en
 * tablet, más la pila de avisos.
 *
 * Sin sesión iniciada no se pinta el armazón: la pantalla de entrada ocupa el
 * hueco entera, para que no haya menús a la vista antes de identificarse.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Avisos, Icono],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly router = inject(Router);
  protected readonly sesion = inject(SesionService);
  protected readonly menuAbierto = signal(false);

  /**
   * El menú va agrupado por para qué sirve cada cosa, no en una lista corrida.
   * Con seis entradas seguidas hay que leerlas todas; con dos grupos de tres, se
   * va directo al que toca.
   */
  private readonly todosLosGrupos: Grupo[] = [
    {
      titulo: 'Taller',
      enlaces: [
        { ruta: '/panel', texto: 'Panel', icono: 'panel' },
        { ruta: '/ordenes', texto: 'Órdenes de trabajo', icono: 'ordenes' },
        { ruta: '/inventario', texto: 'Inventario', icono: 'inventario' },
      ],
    },
    {
      titulo: 'Clientes',
      enlaces: [
        { ruta: '/clientes', texto: 'Clientes', icono: 'clientes' },
        { ruta: '/motos', texto: 'Motos', icono: 'motos' },
      ],
    },
    {
      titulo: 'Administración',
      enlaces: [
        { ruta: '/facturas', texto: 'Facturas', icono: 'facturas', roles: ['ADMIN', 'MOSTRADOR'] },
        { ruta: '/informes', texto: 'Informes', icono: 'informes', roles: ['ADMIN', 'MOSTRADOR'] },
      ],
    },
  ];

  /** Solo los enlaces que el rol puede abrir, y sin grupos que queden vacíos. */
  protected readonly grupos = computed<Grupo[]>(() =>
    this.todosLosGrupos
      .map((g) => ({
        titulo: g.titulo,
        enlaces: g.enlaces.filter((e) => !e.roles || this.sesion.puede(...e.roles)),
      }))
      .filter((g) => g.enlaces.length > 0),
  );

  protected readonly rolLegible = computed(() => {
    const rol = this.sesion.rol();
    return rol ? ROLES_LEGIBLES[rol] : '';
  });

  /** Iniciales para el avatar: «Javier Ortega Marín» → «JO». */
  protected readonly iniciales = computed(() => {
    const nombre = this.sesion.usuario()?.nombreCompleto ?? '';
    const partes = nombre.trim().split(/\s+/).filter(Boolean);
    if (partes.length === 0) return '?';
    return (partes[0][0] + (partes[1]?.[0] ?? '')).toUpperCase();
  });

  constructor() {
    // Si hay token guardado se confirma contra el servidor. Cuando ha caducado,
    // el interceptor recibe el 401, cierra la sesión y lleva a la entrada: sin
    // esto la interfaz quedaría montada y el usuario solo lo descubriría al
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
