import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/config/api-config';
import { PERMISO } from '../../core/config/navegacion';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { AuthService } from '../../core/services/auth.service';
import { Productor } from '../../core/models/models';
import { UsuariosComponent } from './usuarios.component';

const PAGINA_VACIA = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe('Usuarios · internos, acopiadores y proveedores en una pantalla', () => {
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function () {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function () {
      this.removeAttribute('open');
      this.dispatchEvent(new Event('close'));
    };
  });

  function crear(permisos: string[], queryParams: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
        {
          provide: AuthService,
          useValue: { puede: (p: string) => permisos.includes(p), refrescarSesion: () => {} },
        },
      ],
    });
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(UsuariosComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    return { fixture, c: fixture.componentInstance, http, navegar };
  }

  /** Responde los catálogos que cada pestaña pide al abrirse. */
  function responderCatalogos(http: HttpTestingController) {
    http.match(`${API_BASE_URL}/roles`).forEach((r) => r.flush([]));
    http.match(`${API_BASE_URL}/roles/catalogo-vistas`).forEach((r) => r.flush([]));
    http.match((r) => r.url === `${API_BASE_URL}/rutas`).forEach((r) =>
      r.flush({ ...PAGINA_VACIA, content: [{ id: 1, nombre: 'Ruta Norte', zona: 'COLLANA', activo: true }] }),
    );
  }

  function pedidas(http: HttpTestingController, url: string): TestRequest[] {
    return http.match((r) => r.url === `${API_BASE_URL}${url}`);
  }

  it('muestra solo las pestañas que permiten los permisos de la sesión', () => {
    const { c, http } = crear([PERMISO.proveedoresRead]);
    responderCatalogos(http);
    expect(c.pestanas().map((p) => p.id)).toEqual(['proveedores']);
    expect(c.activa()).toBe('proveedores');
    // Sin proveedores.write no se ofrece el alta: el backend respondería 403.
    expect(c.puedeCrear()).toBe(false);
    // Sin usuarios.read no se piden roles ni catálogo de vistas.
    http.expectNone(`${API_BASE_URL}/roles`);
  });

  it('el administrador ve las cuatro pestañas y abre la que pide el enlace', () => {
    const todos = [
      PERMISO.usuariosRead, PERMISO.usuariosWrite, PERMISO.acopiadoresRead, PERMISO.acopiadoresWrite,
      PERMISO.proveedoresRead, PERMISO.proveedoresWrite,
    ];
    const { c, http } = crear(todos, { tipo: 'acopiadores' });
    responderCatalogos(http);
    expect(c.pestanas().map((p) => p.id)).toEqual(['internos', 'acopiadores', 'proveedores', 'roles']);
    expect(c.activa()).toBe('acopiadores');
    expect(c.def().nuevo).toBe('Nuevo acopiador');
    expect(c.puedeCrear()).toBe(true);
  });

  it('cambiar de pestaña la deja en la URL para recargar o compartir el enlace', () => {
    const { c, http, navegar } = crear([PERMISO.usuariosRead, PERMISO.proveedoresRead]);
    responderCatalogos(http);
    c.elegir('proveedores');
    expect(c.activa()).toBe('proveedores');
    expect(navegar).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { tipo: 'proveedores' } }));
  });

  it('la pestaña Internos pide solo cuentas internas: acopiadores y proveedores tienen la suya', () => {
    const { http } = crear([PERMISO.usuariosRead]);
    responderCatalogos(http);
    const [listado] = pedidas(http, '/usuarios');
    expect(listado.request.params.get('internos')).toBe('true');
    expect(listado.request.params.get('estado')).toBe('ACTIVOS');
  });

  it('los filtros de Proveedores viajan al servidor y solo los que tienen valor', () => {
    const { fixture, http } = crear([PERMISO.proveedoresRead], { tipo: 'proveedores' });
    responderCatalogos(http);
    pedidas(http, '/productores').forEach((r) => r.flush(PAGINA_VACIA));

    const lista = fixture.debugElement.query((d) => d.name === 'app-lista-proveedores').componentInstance;
    lista.zona = 'COLLANA';
    lista.estado = 'PENDIENTE_APROBACION';
    lista.lista.buscar();
    const [req] = pedidas(http, '/productores');
    expect(req.request.params.get('zona')).toBe('COLLANA');
    expect(req.request.params.get('estado')).toBe('PENDIENTE_APROBACION');
    expect(req.request.params.has('rutaId')).toBe(false);
    expect(req.request.params.has('acopiadorId')).toBe(false);
  });

  it('aprobar un alta incompleta abre su ficha diciendo qué falta, sin llamar al servidor', async () => {
    const { fixture, http } = crear([PERMISO.proveedoresRead, PERMISO.proveedoresWrite], { tipo: 'proveedores' });
    responderCatalogos(http);
    pedidas(http, '/productores').forEach((r) => r.flush(PAGINA_VACIA));
    const pedirTexto = vi.spyOn(TestBed.inject(DialogoService), 'pedirTexto');

    const lista = fixture.debugElement.query((d) => d.name === 'app-lista-proveedores').componentInstance;
    const incompleto = {
      id: 3, codigo: '123', documentoIdentidad: '123', nombres: 'Ana', apellidos: 'Quispe', estado: 'PENDIENTE_APROBACION',
      excelencia: false, activo: true,
    } as Productor;
    await lista.cambiarEstado(incompleto, 'ACTIVO');
    fixture.detectChanges();

    expect(pedirTexto).not.toHaveBeenCalled();
    http.expectNone((r) => r.method === 'PATCH');
    const aviso = fixture.nativeElement.querySelector('app-ficha-proveedor [role="status"]')?.textContent ?? '';
    expect(aviso).toContain('un DNI válido de 8 dígitos');
    expect(aviso).toContain('una zona y ruta de acopio');
  });

  it('los filtros de Acopiadores viajan al servidor', () => {
    const { fixture, http } = crear([PERMISO.acopiadoresRead], { tipo: 'acopiadores' });
    responderCatalogos(http);
    pedidas(http, '/acopiadores').forEach((r) => r.flush(PAGINA_VACIA));

    const lista = fixture.debugElement.query((d) => d.name === 'app-lista-acopiadores').componentInstance;
    lista.zona = 'COLLANA';
    lista.rutaId = 1;
    lista.lista.buscar();
    const [req] = pedidas(http, '/acopiadores');
    expect(req.request.params.get('zona')).toBe('COLLANA');
    expect(req.request.params.get('rutaId')).toBe('1');
  });
});
