import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from '../../../core/config/api-config';
import { PERMISO } from '../../../core/config/navegacion';
import { AuthService } from '../../../core/services/auth.service';
import { BuscadorGeneralComponent } from './buscador-general.component';

const pagina = (content: unknown[], total = content.length) => ({
  content,
  page: 0,
  size: 4,
  totalElements: total,
  totalPages: 1,
});

describe('Buscador general de Usuarios', () => {
  afterEach(() => vi.useRealTimers());

  function crear(permisos: string[]) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { puede: (p: string) => permisos.includes(p) } },
      ],
    });
    const fixture = TestBed.createComponent(BuscadorGeneralComponent);
    fixture.detectChanges();
    return { fixture, c: fixture.componentInstance, http: TestBed.inject(HttpTestingController) };
  }

  it('espera a que se deje de escribir y busca solo en lo que la sesión puede ver', () => {
    vi.useFakeTimers();
    const { c, http } = crear([PERMISO.acopiadoresRead, PERMISO.proveedoresRead]);
    c.escribir('ana');
    c.escribir('ana qui');
    http.expectNone(() => true);
    vi.advanceTimersByTime(300);

    http.expectNone((r) => r.url === `${API_BASE_URL}/usuarios`);
    const acop = http.expectOne((r) => r.url === `${API_BASE_URL}/acopiadores`);
    const prov = http.expectOne((r) => r.url === `${API_BASE_URL}/productores`);
    expect(prov.request.params.get('q')).toBe('ana qui');
    acop.flush(pagina([]));
    prov.flush(pagina([{ id: 1, codigo: 'PRV-00001', nombres: 'Ana', apellidos: 'Quispe', zonaPrincipal: 'COLLANA' }], 7));

    // Sin coincidencias no se muestra el grupo; el total es el del servidor, no el de la muestra.
    expect(c.grupos().map((g) => [g.tipo, g.total])).toEqual([['proveedores', 7]]);
    expect(c.grupos()[0].filas[0]).toEqual({ titulo: 'Ana Quispe', detalle: 'PRV-00001 · COLLANA' });
  });

  it('elegir un resultado abre su pestaña con ese texto', () => {
    const { c } = crear([]);
    const elegidos: unknown[] = [];
    c.elegido.subscribe((e) => elegidos.push(e));
    c.elegir('proveedores', 'Ana Quispe');
    expect(elegidos).toEqual([{ tipo: 'proveedores', q: 'Ana Quispe' }]);
  });
});
