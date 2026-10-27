import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AcopiadoresService } from '../../core/services/acopiadores.service';
import { ExportacionesService } from '../../core/services/exportaciones.service';
import { ProductoresService } from '../../core/services/productores.service';
import { RecojoService } from '../../core/services/recojo.service';
import { RutasService } from '../../core/services/rutas.service';
import { RecojoComponent } from './recojo.component';

/**
 * Los botones de Recojos dependen de permisos que pueden cambiar con la pantalla abierta (se
 * refrescan al volver a la pestaña). Antes se calculaban una sola vez al crear el componente y el
 * botón no aparecía hasta salir y volver.
 */
describe('RecojoComponent · permisos', () => {
  function crear() {
    const permisos = signal<string[]>([]);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [RecojoComponent],
      providers: [
        { provide: AuthService, useValue: { puede: (p: string) => permisos().includes(p) } },
        { provide: RecojoService, useValue: {} },
        { provide: ProductoresService, useValue: {} },
        { provide: RutasService, useValue: {} },
        { provide: AcopiadoresService, useValue: {} },
        { provide: ExportacionesService, useValue: {} },
        { provide: ActivatedRoute, useValue: { snapshot: { data: {}, queryParamMap: convertToParamMap({}) } } },
      ],
    });
    // Sin detectChanges: solo se mide la regla de permisos, no la carga de datos de ngOnInit.
    const c = TestBed.createComponent(RecojoComponent).componentInstance;
    return { c, permisos };
  }

  it('el botón de cancelar aparece en cuanto llega acopio.write, sin recrear la pantalla', () => {
    const { c, permisos } = crear();
    expect(c.puedeCancelar()).toBe(false);

    permisos.set(['acopio.write']);

    expect(c.puedeCancelar()).toBe(true);
  });

  it('registrar entrega directa sigue a planta.recepcion', () => {
    const { c, permisos } = crear();
    permisos.set(['planta.recepcion']);
    expect(c.puedeRegistrarEntrega()).toBe(true);
    permisos.set([]);
    expect(c.puedeRegistrarEntrega()).toBe(false);
  });
});
