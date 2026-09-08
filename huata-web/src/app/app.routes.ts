import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards/auth.guard';
import { ShellComponent } from './shared/layout/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'portal',
    loadComponent: () => import('./features/portal/portal.component').then((m) => m.PortalComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'recojo',
        loadComponent: () => import('./features/recojo/recojo.component').then((m) => m.RecojoComponent),
      },
      {
        path: 'calidad',
        loadComponent: () => import('./features/calidad/calidad.component').then((m) => m.CalidadComponent),
      },
      {
        path: 'rutas',
        loadComponent: () => import('./features/rutas/rutas.component').then((m) => m.RutasComponent),
      },
      {
        path: 'productores',
        loadComponent: () =>
          import('./features/productores/productores.component').then((m) => m.ProductoresComponent),
      },
      {
        path: 'acopiadores',
        loadComponent: () =>
          import('./features/acopiadores/acopiadores.component').then((m) => m.AcopiadoresComponent),
      },
      {
        path: 'liquidaciones',
        loadComponent: () =>
          import('./features/liquidaciones/liquidaciones.component').then((m) => m.LiquidacionesComponent),
      },
      {
        path: 'quesos',
        loadComponent: () => import('./features/quesos/quesos.component').then((m) => m.QuesosComponent),
      },
      {
        path: 'zonas',
        loadComponent: () => import('./features/zonas/zonas.component').then((m) => m.ZonasComponent),
      },
      {
        path: 'trazabilidad',
        loadComponent: () =>
          import('./features/trazabilidad/trazabilidad.component').then((m) => m.TrazabilidadComponent),
      },
      {
        path: 'reportes',
        loadComponent: () => import('./features/reportes/reportes.component').then((m) => m.ReportesComponent),
      },
      {
        path: 'usuarios',
        canActivate: [roleGuard('ADMINISTRADOR')],
        loadComponent: () => import('./features/usuarios/usuarios.component').then((m) => m.UsuariosComponent),
      },
      {
        path: 'parametros',
        canActivate: [roleGuard('ADMINISTRADOR')],
        loadComponent: () =>
          import('./features/parametros/parametros.component').then((m) => m.ParametrosComponent),
      },
      {
        path: 'datos-respaldo',
        canActivate: [roleGuard('ADMINISTRADOR')],
        loadComponent: () =>
          import('./features/datos-respaldo/datos-respaldo.component').then((m) => m.DatosRespaldoComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
