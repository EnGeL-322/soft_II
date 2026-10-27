import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard.component';
import { AuthService } from '../../core/services/auth.service';
import { ResumenOperacion } from '../../core/services/operacion.service';

describe('Panel: datos reales, permisos y carga incompleta', () => {
  function crear(roles: string[] = [], permisos: string[] = []) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            puede: (p: string) => permisos.includes(p),
            nombreCompleto: () => 'Prueba',
            roles: () => roles,
          },
        },
      ],
    });
    return {
      c: TestBed.createComponent(DashboardComponent).componentInstance,
      http: TestBed.inject(HttpTestingController),
    };
  }
  const resumen = (fecha: string): ResumenOperacion => ({
    fecha,
    aceptados: 100,
    observados: 0,
    rechazados: 0,
    recepcionesAceptadas: 2,
    controlesObservados: 0,
    entregasRechazadas: 0,
    proveedores: 2,
    pendientesCalidad: 0,
    liquidacionesPendientes: 0,
    rutas: [],
  });
  // ---- El panel de cada rol ----

  it('cada rol entra a su propio panel, no al general con huecos', () => {
    expect(crear(['ADMINISTRADOR']).c.panel().titulo).toBe('Panel general');
    expect(crear(['CALIDAD']).c.panel().titulo).toBe('Panel de calidad');
    expect(crear(['SECRETARIA']).c.panel().titulo).toBe('Panel de pagos');
    expect(crear(['PRODUCTOR_QUESO']).c.panel().titulo).toBe('Panel de producción');
    expect(crear(['ACOPIADOR']).c.panel().titulo).toBe('Panel de acopio');
  });

  it('quien tiene varios roles ve el más completo', () => {
    expect(crear(['CALIDAD', 'ADMINISTRADOR']).c.panel().titulo).toBe('Panel general');
  });

  it('un bloque declarado por el rol no se muestra sin el permiso de sus datos', () => {
    const sinPermiso = crear(['CALIDAD'], []).c;
    expect(sinPermiso.panel().bloques).toContain('calidadDelDia');
    expect(sinPermiso.muestra('calidadDelDia')).toBe(false);

    const conPermiso = crear(['CALIDAD'], ['calidad.read']).c;
    expect(conPermiso.muestra('calidadDelDia')).toBe(true);
  });

  it('el permiso no basta: el bloque tiene que estar en el panel del rol', () => {
    // Producción de queso no declara el bloque de liquidaciones aunque tuviera el permiso.
    const c = crear(['PRODUCTOR_QUESO'], ['pagos.read']).c;
    expect(c.muestra('liquidacionesDelCiclo')).toBe(false);
  });

  it('consulta solo la fecha elegida y no pide módulos sin permiso', () => {
    const { c, http } = crear();
    c.cargar();
    const consultas = http.match(() => true);
    expect(consultas).toHaveLength(1);
    for (const r of consultas) {
      expect(r.request.url).toContain('/operacion/');
      r.flush(resumen(r.request.url.split('/').pop()!));
    }
    expect(c.data()?.aceptados).toBe(100);
    expect(c.data()?.fecha).toBe(c.fecha);
    expect(c.cargando()).toBe(false);
    http.verify();
  });
  it('conserva el día consultado si falla un módulo y explica qué falta', () => {
    const { c, http } = crear(['ACOPIADOR'], ['acopio.read']);
    c.cargar();
    const consultas = http.match(() => true);
    for (const r of consultas) {
      if (r.request.url.includes('/operacion/')) r.flush(resumen(c.fecha));
      else r.flush({}, { status: 500, statusText: 'Error' });
    }
    expect(c.data()?.aceptados).toBe(100);
    expect(c.conciliaciones()).toBeNull();
    expect(c.avisos().length).toBe(1);
    http.verify();
  });
  it('los accesos y pendientes respetan los permisos aunque cambie el rol', () => {
    const { c } = crear(['ADMINISTRADOR'], ['panel.read', 'rutas.read']);
    expect(c.accesosPrincipales().map((item) => item.ruta)).toEqual(['rutas']);
    c.data.set({ ...resumen(c.fecha), pendientesCalidad: 3, liquidacionesPendientes: 5 });
    expect(c.pendientes()).toEqual([]);
  });
  it('no presenta indicadores del día como cero si falla su respuesta', () => {
    const { c, http } = crear();
    c.cargar();
    const consultas = http.match(() => true);
    for (const r of consultas) {
      if (r.request.url.endsWith(c.fecha)) r.flush({}, { status: 500, statusText: 'Error' });
      else r.flush(resumen(r.request.url.split('/').pop()!));
    }
    expect(c.data()).toBeNull();
    expect(c.error()).toContain('No se pudo');
    expect(c.cargando()).toBe(false);
    http.verify();
  });
});
