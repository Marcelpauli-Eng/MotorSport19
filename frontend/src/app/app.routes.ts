import { Routes } from '@angular/router';

/**
 * Rutas de la aplicación.
 *
 * Todas usan carga diferida: el navegador solo descarga la pantalla que se abre.
 * En una tablet con conexión regular eso se nota.
 *
 * Cuando llegue la fase 5, los guards de autenticación y de rol se añaden aquí
 * con `canActivate` sin tocar ninguna pantalla.
 */
export const routes: Routes = [
  { path: '', redirectTo: 'panel', pathMatch: 'full' },

  {
    path: 'panel',
    title: 'Panel · MotorSport19',
    loadComponent: () => import('./funcionalidades/panel/panel').then((m) => m.Panel),
  },

  {
    path: 'ordenes',
    title: 'Órdenes de trabajo · MotorSport19',
    loadComponent: () =>
      import('./funcionalidades/ordenes/lista-ordenes').then((m) => m.ListaOrdenes),
  },
  {
    path: 'ordenes/:id',
    title: 'Orden de trabajo · MotorSport19',
    loadComponent: () =>
      import('./funcionalidades/ordenes/detalle-orden').then((m) => m.DetalleOrden),
  },

  {
    path: 'facturas',
    title: 'Facturas · MotorSport19',
    loadComponent: () =>
      import('./funcionalidades/facturas/lista-facturas').then((m) => m.ListaFacturas),
  },
  {
    path: 'facturas/:id',
    title: 'Factura · MotorSport19',
    loadComponent: () =>
      import('./funcionalidades/facturas/detalle-factura').then((m) => m.DetalleFactura),
  },

  {
    path: 'clientes',
    title: 'Clientes · MotorSport19',
    loadComponent: () =>
      import('./funcionalidades/clientes/lista-clientes').then((m) => m.ListaClientes),
  },
  {
    path: 'clientes/:id',
    title: 'Cliente · MotorSport19',
    loadComponent: () =>
      import('./funcionalidades/clientes/detalle-cliente').then((m) => m.DetalleCliente),
  },

  {
    path: 'motos',
    title: 'Motos · MotorSport19',
    loadComponent: () => import('./funcionalidades/motos/lista-motos').then((m) => m.ListaMotos),
  },
  {
    path: 'motos/:id',
    title: 'Moto · MotorSport19',
    loadComponent: () => import('./funcionalidades/motos/detalle-moto').then((m) => m.DetalleMoto),
  },

  {
    path: 'inventario',
    title: 'Inventario · MotorSport19',
    loadComponent: () =>
      import('./funcionalidades/inventario/inventario').then((m) => m.Inventario),
  },

  { path: '**', redirectTo: 'panel' },
];
