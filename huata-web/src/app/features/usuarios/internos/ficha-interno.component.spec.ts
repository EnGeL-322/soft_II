import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from '../../../core/config/api-config';
import { ModuloAcceso, RolInfo, Usuario } from '../../../core/models/models';
import { AuthService } from '../../../core/services/auth.service';
import { CatalogoAccesos } from '../compartido/catalogos';
import { FichaInternoComponent } from './ficha-interno.component';

function rol(nombre: string, permisos: string[], extra: Partial<RolInfo> = {}): RolInfo {
  return {
    id: nombre.length,
    nombre,
    etiqueta: nombre.charAt(0) + nombre.slice(1).toLowerCase(),
    sistema: true,
    gestionadoEnSuModulo: false,
    permisosBloqueados: false,
    usuarios: 0,
    permisos,
    ...extra,
  };
}

const ROLES: RolInfo[] = [
  rol('VENTAS', ['panel.read', 'quesos.read', 'ventas.registrar']),
  rol('GERENCIA', ['panel.read', 'quesos.read', 'reportes.read']),
  rol('ACOPIADOR', ['acopio.read'], { gestionadoEnSuModulo: true }),
  rol('PROVEEDOR', ['mensajeria.read'], { gestionadoEnSuModulo: true, permisosBloqueados: true }),
];

const CATALOGO: ModuloAcceso[] = [
  {
    codigo: 'VENTAS_PAGOS',
    nombre: 'Ventas y pagos',
    vistas: [
      {
        codigo: 'venta-queso',
        nombre: 'Ventas',
        ruta: 'venta-queso',
        permisoAcceso: 'quesos.read',
        acciones: [
          { permiso: 'quesos.read', etiqueta: 'Ver', compartidaCon: [] },
          { permiso: 'ventas.registrar', etiqueta: 'Registrar ventas', compartidaCon: [] },
        ],
      },
      {
        codigo: 'pagos',
        nombre: 'Pagos',
        ruta: 'pagos',
        permisoAcceso: 'pagos.read',
        acciones: [
          { permiso: 'pagos.read', etiqueta: 'Ver', compartidaCon: [] },
          { permiso: 'pagos.write', etiqueta: 'Pagar', compartidaCon: [] },
        ],
      },
    ],
  },
];

describe('Ficha de usuario interno', () => {
  beforeEach(() => {
    // JSDOM no implementa la capa superior nativa del <dialog>; el foco se verifica en navegador.
    HTMLDialogElement.prototype.showModal = function () {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function () {
      this.removeAttribute('open');
      this.dispatchEvent(new Event('close'));
    };
  });

  function crear() {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        CatalogoAccesos,
        { provide: AuthService, useValue: { puede: () => true, refrescarSesion: () => {} } },
      ],
    });
    const accesos = TestBed.inject(CatalogoAccesos);
    accesos.roles.set(ROLES);
    accesos.catalogo.set(CATALOGO);
    const fixture = TestBed.createComponent(FichaInternoComponent);
    fixture.detectChanges();
    const c = fixture.componentInstance;
    const dialog = fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;
    return { fixture, c, dialog, http: TestBed.inject(HttpTestingController) };
  }

  it('solo ofrece los roles que no nacen con su propia ficha (Acopiador y Proveedor)', () => {
    crear();
    expect(TestBed.inject(CatalogoAccesos).rolesAsignables().map((r) => r.nombre)).toEqual(['VENTAS', 'GERENCIA']);
  });

  it('los permisos efectivos son la suma de los roles, más extras y menos quitados', () => {
    const { c } = crear();
    c.nuevo();
    c.alternarRol('VENTAS');
    c.alternarRol('GERENCIA');
    expect([...c.efectivos()].sort()).toEqual(['panel.read', 'quesos.read', 'reportes.read', 'ventas.registrar']);

    c.aplicarCambios([
      { permiso: 'pagos.read', activo: true },
      { permiso: 'ventas.registrar', activo: false },
    ]);
    expect([...c.concedidos()]).toEqual(['pagos.read']);
    expect([...c.revocados()]).toEqual(['ventas.registrar']);
    expect(c.efectivos().has('ventas.registrar')).toBe(false);

    // Volver a encender lo que da el rol deja de ser excepción.
    c.aplicarCambios([{ permiso: 'ventas.registrar', activo: true }]);
    expect(c.revocados().size).toBe(0);
  });

  it('al quitar el rol que daba un permiso quitado, la excepción desaparece', () => {
    const { c } = crear();
    c.nuevo();
    c.alternarRol('GERENCIA');
    c.aplicarCambios([{ permiso: 'reportes.read', activo: false }]);
    expect(c.revocados().has('reportes.read')).toBe(true);
    c.alternarRol('GERENCIA');
    expect(c.revocados().size).toBe(0);
  });

  it('no deja pasar a vistas y permisos sin datos completos', () => {
    const { c } = crear();
    c.nuevo();
    c.irA('roles');
    expect(c.paso()).toBe('datos');
    expect(c.error()).toContain('Completa');
  });

  it('roles y vistas son pasos separados: a vistas solo se llega con al menos un rol', () => {
    const { c } = crear();
    c.nuevo();
    c.form.nombreCompleto = 'Persona de prueba';
    c.form.username = 'prueba@huata.pe';
    c.form.password = 'ClaveDePrueba';
    expect(c.siguiente()).toBe('roles');
    c.irA('roles');
    expect(c.paso()).toBe('roles');
    expect(c.anterior()).toBe('datos');
    expect(c.pasoHecho('datos')).toBe(true);

    c.irA('vistas');
    expect(c.paso()).toBe('roles');
    expect(c.error()).toContain('rol');

    c.alternarRol('GERENCIA');
    c.irA('vistas');
    expect(c.paso()).toBe('vistas');
    expect(c.siguiente()).toBeNull();
    expect(c.error()).toBe('');
  });

  it('desde la lista se abre la ficha directo en los roles o en las vistas de la persona', () => {
    const { c, dialog } = crear();
    const u = { id: 4, nombreCompleto: 'Vendedora', username: 'v@huata.pe', roles: ['VENTAS'], activo: true } as Usuario;
    c.editar(u, 'roles');
    expect(dialog.open).toBe(true);
    expect(c.paso()).toBe('roles');
    c.editar(u, 'vistas');
    expect(c.paso()).toBe('vistas');
  });

  it('no guarda sin rol, envía las excepciones y conserva el formulario si el servidor rechaza', () => {
    const { c, http, dialog } = crear();
    c.nuevo();
    c.form.nombreCompleto = 'Persona de prueba';
    c.form.username = 'prueba@huata.pe';
    c.form.password = 'ClaveDePrueba';
    c.guardar();
    expect(c.error()).toContain('rol');
    // Sin rol vuelve al paso Roles.
    expect(c.paso()).toBe('roles');
    http.expectNone((r) => r.method === 'POST');

    c.alternarRol('VENTAS');
    c.aplicarCambios([{ permiso: 'pagos.read', activo: true }]);
    c.guardar();
    const req = http.expectOne(`${API_BASE_URL}/usuarios`);
    expect(req.request.body.roles).toEqual(['VENTAS']);
    expect(req.request.body.permisosConcedidos).toEqual(['pagos.read']);
    expect(req.request.body.permisosRevocados).toEqual([]);
    req.flush({ message: 'Ya existe un usuario' }, { status: 400, statusText: 'Bad Request' });
    expect(dialog.open).toBe(true);
    expect(c.error()).toBe('Ya existe un usuario');
    expect(c.form.nombreCompleto).toBe('Persona de prueba');
    expect(c.guardando()).toBe(false);
    http.verify();
  });

  it('al guardar cierra la ficha y avisa a la lista', () => {
    const { fixture, c, http, dialog } = crear();
    const avisos: string[] = [];
    c.guardado.subscribe((m) => avisos.push(m));
    c.editar({ id: 4, nombreCompleto: 'Vendedora', username: 'v@huata.pe', roles: ['VENTAS'], activo: true } as Usuario);
    c.guardar();
    // Como en el navegador: mientras espera, la ventana ya se marcó ocupada; aun así debe cerrarse.
    fixture.detectChanges();
    http.expectOne(`${API_BASE_URL}/usuarios/4`).flush({});
    expect(dialog.open).toBe(false);
    expect(avisos).toEqual(['Usuario actualizado.']);
  });

  it('al editar carga roles y excepciones de la persona', () => {
    const { c, dialog } = crear();
    c.editar({
      id: 4,
      nombreCompleto: 'Vendedora',
      username: 'vende@huata.pe',
      roles: ['VENTAS'],
      activo: true,
      permisosConcedidos: ['pagos.read'],
      permisosRevocados: ['ventas.registrar'],
    } as Usuario);
    expect(dialog.open).toBe(true);
    expect(c.rolesSel()).toEqual(['VENTAS']);
    expect(c.personalizado()).toBe(true);
    c.restablecerARoles();
    expect(c.personalizado()).toBe(false);
  });

  it('una cuenta de acopiador o proveedor no se edita como interna', () => {
    const { c, dialog } = crear();
    c.editar({ id: 9, nombreCompleto: 'Acopiador', username: 'a@huata.pe', roles: ['ACOPIADOR'], activo: true,
      gestionadoEnSuModulo: true } as Usuario);
    expect(dialog.open).toBe(false);
  });

  it('a un acopiador se le ajustan solo los permisos, sin tocar su rol ni sus datos', () => {
    const { c, http } = crear();
    c.abrirPermisos({
      id: 6, nombreCompleto: 'Acopiador Demo', username: 'acopiador.demo@huata.pe', activo: true,
      roles: ['ACOPIADOR'], gestionadoEnSuModulo: true, permisosConcedidos: [], permisosRevocados: [],
    } as Usuario);
    expect(c.paso()).toBe('vistas');
    expect(c.pasos()).toEqual(['vistas']);
    c.aplicarCambios([{ permiso: 'pagos.read', activo: true }]);
    c.guardar();

    const put = http.expectOne(`${API_BASE_URL}/usuarios/6/permisos`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ permisosConcedidos: ['pagos.read'], permisosRevocados: [] });
  });

  it('cuenta como una sola pantalla las vistas que comparten ruta (pestañas de Usuarios)', () => {
    crear();
    const accesos = TestBed.inject(CatalogoAccesos);
    accesos.catalogo.set([
      {
        codigo: 'PERSONAS_RUTAS',
        nombre: 'Personas y rutas',
        vistas: [
          { codigo: 'usuarios', nombre: 'Usuarios', ruta: 'usuarios', permisoAcceso: 'usuarios.read', acciones: [] },
          { codigo: 'productores', nombre: 'Proveedores', ruta: 'usuarios', permisoAcceso: 'proveedores.read', acciones: [] },
          { codigo: 'rutas', nombre: 'Rutas', ruta: 'rutas', permisoAcceso: 'rutas.read', acciones: [] },
        ],
      },
    ]);
    expect(accesos.pantallas(['usuarios.read', 'proveedores.read', 'rutas.read'])).toBe(2);
    expect(accesos.pantallas(['proveedores.read'])).toBe(1);
  });
});
