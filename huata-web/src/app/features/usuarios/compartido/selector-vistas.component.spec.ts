import { TestBed } from '@angular/core/testing';
import { ModuloAcceso } from '../../../core/models/models';
import { CambioPermiso, SelectorVistasComponent } from './selector-vistas.component';

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

describe('Selector de vistas de un usuario', () => {
  function crear(base: string[], seleccion: string[] = base) {
    const fixture = TestBed.createComponent(SelectorVistasComponent);
    fixture.componentRef.setInput('catalogo', CATALOGO);
    fixture.componentRef.setInput('base', new Set(base));
    fixture.componentRef.setInput('seleccion', new Set(seleccion));
    fixture.detectChanges();
    const emitidos: CambioPermiso[][] = [];
    fixture.componentInstance.cambios.subscribe((c) => emitidos.push(c));
    return { fixture, c: fixture.componentInstance, emitidos };
  }

  it('separa las vistas que da el rol de las que se pueden dar aparte', () => {
    const { c } = crear(['quesos.read', 'ventas.registrar']);
    expect(c.delRol().flatMap((g) => g.vistas.map((v) => v.codigo))).toEqual(['venta-queso']);
    expect(c.otras().flatMap((g) => g.vistas.map((v) => v.codigo))).toEqual(['pagos']);
    expect(c.pantallas()).toBe(1);
  });

  it('una acción suelta del rol sin acceso a la vista no la vuelve "del rol" ni "quitada"', () => {
    const { c } = crear(['quesos.read', 'pagos.write']);
    expect(c.delRol().flatMap((g) => g.vistas.map((v) => v.codigo))).toEqual(['venta-queso']);
    expect(c.quitadas()).toBe(0);
  });

  it('sin rol no hay vistas del rol: se pide elegir uno', () => {
    const { c, fixture } = crear([]);
    expect(c.delRol()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('Elige un rol para ver sus vistas.');
  });

  it('apagar una vista del rol apaga también sus acciones y se marca como quitada', () => {
    const { c, emitidos } = crear(['quesos.read', 'ventas.registrar']);
    c.alternarVista(CATALOGO[0].vistas[0]);
    expect(emitidos[0]).toEqual([
      { permiso: 'quesos.read', activo: false },
      { permiso: 'ventas.registrar', activo: false },
    ]);
    const quitada = crear(['quesos.read'], []).c;
    expect(quitada.esQuitado('quesos.read')).toBe(true);
    expect(quitada.quitadas()).toBe(1);
  });

  it('dar una acción de otra vista enciende esa vista y cuenta como extra', () => {
    const { c, emitidos } = crear(['quesos.read']);
    c.alternarAccion(CATALOGO[0].vistas[1], 'pagos.write');
    expect(emitidos[0]).toEqual([
      { permiso: 'pagos.write', activo: true },
      { permiso: 'pagos.read', activo: true },
    ]);
    const conExtra = crear(['quesos.read'], ['quesos.read', 'pagos.read']).c;
    expect(conExtra.extrasActivas()).toBe(1);
    expect(conExtra.esExtra('pagos.read')).toBe(true);
  });

  it('al editar un rol (sin base) muestra todas las vistas en una lista, sin extras ni quitadas', () => {
    const fixture = TestBed.createComponent(SelectorVistasComponent);
    fixture.componentRef.setInput('catalogo', CATALOGO);
    fixture.componentRef.setInput('seleccion', new Set(['quesos.read']));
    fixture.detectChanges();
    const c = fixture.componentInstance;
    expect(c.modoRol()).toBe(true);
    expect(c.delRol().flatMap((g) => g.vistas.map((v) => v.codigo))).toEqual(['venta-queso', 'pagos']);
    expect(c.esQuitado('pagos.read')).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('Dar otras vistas');
  });

  it('resume cada vista en No · Ver · Todo, y "parcial" cuando solo tiene algunas acciones', () => {
    const ventas = { ...CATALOGO[0].vistas[0], acciones: [...CATALOGO[0].vistas[0].acciones,
      { permiso: 'ventas.anular', etiqueta: 'Anular ventas', compartidaCon: [] }] };
    const { c } = crear(['quesos.read', 'ventas.registrar']);
    expect(c.nivel(CATALOGO[0].vistas[1])).toBe('no');
    expect(c.nivel(ventas)).toBe('parcial');
    expect(c.detalleAbierto(ventas)).toBe(true);
    expect(crear(['quesos.read']).c.nivel(ventas)).toBe('ver');
    expect(crear(['quesos.read', 'ventas.registrar', 'ventas.anular']).c.nivel(ventas)).toBe('todo');
  });

  it('"Todo" enciende la vista y todas sus acciones; "Ver" deja solo mirar', () => {
    const { c, emitidos } = crear(['quesos.read']);
    c.fijarNivel(CATALOGO[0].vistas[1], 'todo');
    expect(emitidos[0]).toEqual([
      { permiso: 'pagos.read', activo: true },
      { permiso: 'pagos.write', activo: true },
    ]);
    const todo = crear(['quesos.read', 'ventas.registrar']);
    todo.c.fijarNivel(CATALOGO[0].vistas[0], 'ver');
    expect(todo.emitidos[0]).toEqual([{ permiso: 'ventas.registrar', activo: false }]);
  });

  it('marca "ajustada" la vista del rol a la que se le cambió una acción', () => {
    const { c } = crear(['quesos.read', 'ventas.registrar'], ['quesos.read']);
    expect(c.marca(CATALOGO[0].vistas[0])).toBe('ajustada');
  });

  it('en solo lectura no emite cambios', () => {
    const { c, fixture, emitidos } = crear(['quesos.read']);
    fixture.componentRef.setInput('soloLectura', true);
    c.alternarVista(CATALOGO[0].vistas[0]);
    expect(emitidos).toEqual([]);
  });
});
