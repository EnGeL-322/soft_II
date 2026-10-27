import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/config/api-config';
import { PortalComponent } from './portal.component';

function crear() {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [PortalComponent],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
  const fixture = TestBed.createComponent(PortalComponent);
  const http = TestBed.inject(HttpTestingController);
  return { fixture, component: fixture.componentInstance, http };
}

describe('PortalComponent', () => {
  afterEach(() => {
    // Cada test dispara su propia carga al construir el componente; no dejar pedidos colgados.
  });

  it('carga el resumen del día al construirse y expone zonas e incidencias', () => {
    const { component, http } = crear();

    const req = http.expectOne(`${API_BASE_URL}/public/incidencias/hoy`);
    req.flush({
      fecha: '2026-09-16',
      zonas: [{ zona: 'COLLANA I', controles: 3, aceptadas: 2, observadas: 1, rechazadas: 0, estado: 'OBSERVADA' }],
      incidencias: [{ productorCodigo: 'DEMO-007', zona: 'Moro Viejo', tipo: 'Adulteración', fecha: '2026-09-16' }],
    });

    expect(component.zonas()).toHaveLength(1);
    expect(component.incidencias()).toHaveLength(1);
    expect(component.totalControles).toBe(3);
    expect(component.totalAceptadas).toBe(2);
    expect(component.zonasConProblema).toBe(1);
    expect(component.cargandoResumen()).toBe(false);
    expect(component.errorResumen()).toBe('');
    http.verify();
  });

  it('si falla la carga del resumen, muestra un mensaje de error en vez de dejarlo en blanco', () => {
    const { component, http } = crear();

    http.expectOne(`${API_BASE_URL}/public/incidencias/hoy`).flush('boom', { status: 500, statusText: 'Error' });

    expect(component.errorResumen()).not.toBe('');
    expect(component.zonas()).toHaveLength(0);
    http.verify();
  });

  it('no consulta si el código está vacío', () => {
    const { component, http } = crear();
    http.expectOne(`${API_BASE_URL}/public/incidencias/hoy`).flush({ fecha: '2026-09-16', zonas: [], incidencias: [] });

    component.codigo = '   ';
    component.buscar();

    expect(component.error()).toBe('Ingresa tu código de proveedor.');
    http.expectNone(`${API_BASE_URL}/public/productores/  /calidad`);
    http.verify();
  });

  it('un código inexistente muestra un mensaje claro de 404, no un error genérico', () => {
    const { component, http } = crear();
    http.expectOne(`${API_BASE_URL}/public/incidencias/hoy`).flush({ fecha: '2026-09-16', zonas: [], incidencias: [] });

    component.codigo = 'NOEXISTE';
    component.buscar();
    http.expectOne(`${API_BASE_URL}/public/productores/NOEXISTE/calidad`).flush('no encontrado', {
      status: 404,
      statusText: 'Not Found',
    });

    expect(component.error()).toBe('No se encontró ese código de proveedor.');
    expect(component.resultado()).toBeNull();
    http.verify();
  });

  it('un código válido carga el historial real del proveedor', () => {
    const { component, http } = crear();
    http.expectOne(`${API_BASE_URL}/public/incidencias/hoy`).flush({ fecha: '2026-09-16', zonas: [], incidencias: [] });

    component.codigo = 'demo-001';
    component.buscar();
    http.expectOne(`${API_BASE_URL}/public/productores/DEMO-001/calidad`).flush({
      codigo: 'DEMO-001',
      zona: 'COLLANA I',
      estado: 'ACTIVO',
      historial: [{ fecha: '2026-09-16T10:00:00Z', resultado: 'ACEPTADA', litros: 30 }],
    });

    expect(component.resultado()?.codigo).toBe('DEMO-001');
    expect(component.error()).toBe('');
    http.verify();
  });
});
