import { Routes } from '@angular/router';
import { invitadoGuard, rolGuard, sesionGuard } from './nucleo/api/sesion.guard';

/**
 * Rutas de la aplicación.
 *
 * Todas usan carga diferida: el navegador solo descarga la pantalla que se abre.
 * En una tablet con conexión regular eso se nota.
 *
 * Salvo la de entrada, todas cuelgan de una ruta sin componente que aplica
 * `sesionGuard` a sus hijas, y las reservadas añaden `rolGuard`. Los guards solo
 * evitan pintar algo que la API va a rechazar de todos modos: el permiso de
 * verdad se comprueba en el backend en cada petición.
 */
export const routes: Routes = [
  {
    path: 'entrar',
    title: 'Entrar · MotorSport19',
    canActivate: [invitadoGuard],
    loadComponent: () => import('./funcionalidades/sesion/entrar').then((m) => m.Entrar),
  },

  {
    path: '',
    canActivateChild: [sesionGuard],
    children: [
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

      // Facturar no es tarea del taller: un técnico no entra aquí.
      {
        path: 'facturas',
        title: 'Facturas · MotorSport19',
        canActivate: [rolGuard('ADMIN', 'MOSTRADOR')],
        loadComponent: () =>
          import('./funcionalidades/facturas/lista-facturas').then((m) => m.ListaFacturas),
      },
      {
        path: 'facturas/:id',
        title: 'Factura · MotorSport19',
        canActivate: [rolGuard('ADMIN', 'MOSTRADOR')],
        loadComponent: () =>
          import('./funcionalidades/facturas/detalle-factura').then((m) => m.DetalleFactura),
      },

      // Un técnico si consulta clientes y motos: necesita saber de quién es la
      // moto que tiene en el elevador. Lo que no puede es crearlos ni editarlos,
      // y eso se controla en la propia pantalla, no en la ruta.
      // Datos económicos del taller: mismo perfil que la facturación.
      {
        path: 'informes',
        title: 'Informes · MotorSport19',
        canActivate: [rolGuard('ADMIN', 'MOSTRADOR')],
        loadComponent: () =>
          import('./funcionalidades/informes/facturacion-informe').then((m) => m.FacturacionInforme),
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
        loadComponent: () =>
          import('./funcionalidades/motos/lista-motos').then((m) => m.ListaMotos),
      },
      {
        path: 'motos/:id',
        title: 'Moto · MotorSport19',
        loadComponent: () =>
          import('./funcionalidades/motos/detalle-moto').then((m) => m.DetalleMoto),
      },

      {
        path: 'inventario',
        title: 'Inventario · MotorSport19',
        loadComponent: () =>
          import('./funcionalidades/inventario/inventario').then((m) => m.Inventario),
      },

      {
        path: 'mi-cuenta',
        title: 'Mi cuenta · MotorSport19',
        loadComponent: () => import('./funcionalidades/sesion/mi-cuenta').then((m) => m.MiCuenta),
      },

      { path: '**', redirectTo: 'panel' },
    ],
  },
];
