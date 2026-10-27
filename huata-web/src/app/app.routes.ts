import { inject } from '@angular/core';
import { Params, Router, Routes } from '@angular/router';
import { authGuard, permisoGuard } from './core/guards/auth.guard';
import { PERMISO } from './core/config/navegacion';
import { ShellComponent } from './shared/layout/shell.component';

function irAUsuarios(tipo: string, queryParams: Params) {
  return inject(Router).createUrlTree(['/usuarios'], { queryParams: { ...queryParams, tipo } });
}

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'portal' },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'portal',
    loadComponent: () =>
      import('./features/portal/portal.component').then((m) => m.PortalComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      // El permiso de cada ruta es el mismo que declara NAVEGACION para su item de menu: el sidebar
      // y el guard leen la misma constante, de modo que no puede existir un item visible que la
      // ruta rechace, ni una ruta accesible que no aparezca en el menu.
      {
        path: 'dashboard',
        canActivate: [permisoGuard(PERMISO.panelRead)],
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'calidad',
        canActivate: [permisoGuard(PERMISO.calidadRead)],
        loadComponent: () =>
          import('./features/calidad/calidad-resumen.component').then(
            (m) => m.CalidadResumenComponent,
          ),
      },
      // Un solo componente, dos vistas: lo recogido en ruta (solo consulta) y lo entregado
      // directamente en planta (aquí sí se registra). Ver RecojoComponent.modo.
      {
        path: 'recojo',
        canActivate: [permisoGuard(PERMISO.acopioRead)],
        data: { modo: 'ruta' },
        loadComponent: () =>
          import('./features/recojo/recojo.component').then((m) => m.RecojoComponent),
      },
      {
        path: 'entregas-planta',
        canActivate: [permisoGuard(PERMISO.acopioRead)],
        data: { modo: 'planta' },
        loadComponent: () =>
          import('./features/recojo/recojo.component').then((m) => m.RecojoComponent),
      },
      {
        path: 'conciliacion',
        canActivate: [permisoGuard(PERMISO.acopioRead)],
        loadComponent: () =>
          import('./features/conciliacion/conciliacion.component').then(
            (m) => m.ConciliacionComponent,
          ),
      },
      {
        path: 'rutas',
        canActivate: [permisoGuard(PERMISO.rutasRead)],
        loadComponent: () =>
          import('./features/rutas/rutas.component').then((m) => m.RutasComponent),
      },
      {
        path: 'zonas',
        canActivate: [permisoGuard(PERMISO.zonasRead)],
        loadComponent: () =>
          import('./features/zonas/zonas.component').then((m) => m.ZonasComponent),
      },
      // Desde V55 es una pestaña de Usuarios; la ruta vieja sigue funcionando para enlaces guardados.
      { path: 'productores', redirectTo: ({ queryParams }) => irAUsuarios('proveedores', queryParams) },
      {
        path: 'solicitudes-zona',
        canActivate: [permisoGuard(PERMISO.proveedoresRead)],
        loadComponent: () =>
          import('./features/solicitudes-zona/solicitudes-zona.component').then(
            (m) => m.SolicitudesZonaComponent,
          ),
      },
      // Desde V55 es una pestaña de Usuarios; la ruta vieja sigue funcionando para enlaces guardados.
      { path: 'acopiadores', redirectTo: ({ queryParams }) => irAUsuarios('acopiadores', queryParams) },
      {
        path: 'reglas-leche',
        canActivate: [permisoGuard(PERMISO.parametrosRead)],
        loadComponent: () =>
          import('./features/reglas-leche/reglas-leche.component').then((m) => m.ReglasLecheComponent),
      },
      {
        path: 'recetas',
        canActivate: [permisoGuard(PERMISO.recetasRead)],
        loadComponent: () =>
          import('./features/recetas/recetas.component').then((m) => m.RecetasComponent),
      },
      {
        path: 'produccion-queso',
        canActivate: [permisoGuard(PERMISO.produccionRead, PERMISO.quesosRead)],
        loadComponent: () =>
          import('./features/produccion/produccion-queso.component').then(
            (m) => m.ProduccionQuesoComponent,
          ),
      },
      {
        path: 'compras',
        canActivate: [permisoGuard(PERMISO.comprasRead)],
        loadComponent: () =>
          import('./features/compras/compras.component').then((m) => m.ComprasComponent),
      },
      {
        path: 'inventario',
        canActivate: [permisoGuard(PERMISO.inventarioRead)],
        loadComponent: () =>
          import('./features/inventario/inventario.component').then((m) => m.InventarioComponent),
      },
      {
        path: 'venta-queso',
        canActivate: [permisoGuard(PERMISO.quesosRead)],
        loadComponent: () =>
          import('./features/ventas/venta-queso.component').then((m) => m.VentaQuesoComponent),
      },
      {
        path: 'pagos',
        canActivate: [permisoGuard(PERMISO.pagosRead)],
        loadComponent: () => import('./features/pagos/pagos.component').then((m) => m.PagosComponent),
      },
      {
        path: 'contabilidad',
        canActivate: [permisoGuard(PERMISO.contabilidadRead)],
        loadComponent: () =>
          import('./features/contabilidad/contabilidad.component').then(
            (m) => m.ContabilidadComponent,
          ),
      },
      {
        // La propia cuenta: basta con haber iniciado sesión (el backend usa siempre la del token).
        path: 'mi-cuenta',
        loadComponent: () =>
          import('./features/cuenta/mi-cuenta.component').then((m) => m.MiCuentaComponent),
      },
      {
        // Cada pestaña se ve con su permiso: basta con uno de los tres para entrar.
        path: 'usuarios',
        canActivate: [permisoGuard(PERMISO.usuariosRead, PERMISO.acopiadoresRead, PERMISO.proveedoresRead)],
        loadComponent: () =>
          import('./features/usuarios/usuarios.component').then((m) => m.UsuariosComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
