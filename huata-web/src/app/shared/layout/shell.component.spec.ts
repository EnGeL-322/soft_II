import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { MensajeriaFlotanteComponent } from '../mensajeria/mensajeria-flotante.component';
import { ShellComponent } from './shell.component';

@Component({ selector: 'app-mensajeria-flotante', template: '' })
class MensajeriaDePrueba {}
@Component({ template: '' })
class PantallaDePrueba {}

describe('Menú de tareas', () => {
  async function crear() {
    TestBed.resetTestingModule();
    const permisos = ['panel.read', 'rutas.read', 'proveedores.read', 'inventario.read'];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'dashboard', component: PantallaDePrueba },
          { path: 'rutas', component: PantallaDePrueba },
          { path: 'inventario', component: PantallaDePrueba },
        ]),
        {
          provide: AuthService,
          useValue: {
            puede: (permiso: string) => permisos.includes(permiso),
            roles: () => ['ADMINISTRADOR'],
            nombreCompleto: () => 'Usuario Prueba',
            logout: () => {},
          },
        },
      ],
    });
    TestBed.overrideComponent(ShellComponent, {
      remove: { imports: [MensajeriaFlotanteComponent] },
      add: { imports: [MensajeriaDePrueba] },
    });
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/dashboard');
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, c: fixture.componentInstance, router };
  }

  it('muestra todas las secciones permitidas a la vista y marca la actual', async () => {
    const { fixture, c, router } = await crear();
    expect(fixture.nativeElement.querySelector('a[href="/inventario"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/rutas"]')).not.toBeNull();
    await router.navigateByUrl('/inventario');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(c.seccionActual()?.ruta).toBe('inventario');
    expect(
      fixture.nativeElement.querySelector('a[href="/inventario"]').classList.contains('selected'),
    ).toBe(true);
    fixture.destroy();
  });

  it('busca sin distinguir acentos y no muestra módulos sin permiso', async () => {
    const { fixture, c } = await crear();
    c.busquedaMenu.set('PRODUCCION');
    fixture.detectChanges();
    expect(c.resultados().map((item) => item.ruta)).toEqual(['inventario']);
    expect(fixture.nativeElement.querySelector('a[href="/compras"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/inventario"]')).not.toBeNull();
    c.busquedaMenu.set('no existe');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.menu-empty').textContent).toContain(
      'No encontramos',
    );
    fixture.destroy();
  });
});
