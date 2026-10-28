import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BuscadorComponent, OpcionBuscador } from './buscador.component';

/**
 * El buscador escribible que sustituye a los `<select>` con lista larga.
 *
 * <p>Lo que se fija aquí es que escribir filtre de verdad, que lo tecleado a medias NO cambie la
 * selección (el error clásico de un combobox: cierras y te ha elegido otra cosa) y que devuelva el
 * VALOR y no el texto, que es la razón por la que no se usó `<datalist>`.
 */
describe('BuscadorComponent', () => {
  let fixture: ComponentFixture<BuscadorComponent>;
  let c: BuscadorComponent;

  const OPCIONES: OpcionBuscador[] = [
    { valor: 1, etiqueta: 'DEMO-001 · Ana Quispe', detalle: 'COLLANA I' },
    { valor: 2, etiqueta: 'DEMO-002 · Luis Mamani', detalle: 'YASIN' },
    { valor: 3, etiqueta: 'DEMO-003 · Rosa Ramos', detalle: 'COLLANA I' },
  ];

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [BuscadorComponent] });
    fixture = TestBed.createComponent(BuscadorComponent);
    c = fixture.componentInstance;
    c.opciones = OPCIONES;
    fixture.detectChanges();
  });

  it('sin escribir nada ofrece todas las opciones', () => {
    c.abrir();
    expect(c.filtradas()).toHaveLength(3);
  });

  it('escribir filtra por la etiqueta', () => {
    c.alEscribir('mamani');
    expect(c.filtradas().map((o) => o.valor)).toEqual([2]);
  });

  it('escribir también filtra por el detalle: la zona sirve para encontrar al proveedor', () => {
    c.alEscribir('collana');
    expect(c.filtradas().map((o) => o.valor)).toEqual([1, 3]);
  });

  it('el filtro ignora mayúsculas y busca en cualquier parte del texto', () => {
    c.alEscribir('ROSA');
    expect(c.filtradas().map((o) => o.valor)).toEqual([3]);
  });

  it('elegir devuelve el valor, no el texto', () => {
    let recibido: unknown = 'sin-llamar';
    c.registerOnChange((v) => (recibido = v));

    c.elegir(OPCIONES[1]);

    expect(recibido).toBe(2);
    expect(c.texto()).toBe('DEMO-002 · Luis Mamani');
    expect(c.abierto()).toBe(false);
  });

  it('cerrar sin elegir restaura lo que había: escribir a medias no cambia la selección', () => {
    c.writeValue(1);
    let recibido: unknown = 'sin-llamar';
    c.registerOnChange((v) => (recibido = v));

    c.alEscribir('ros');
    c.cerrar();

    expect(c.valor()).toBe(1);
    expect(c.texto()).toBe('DEMO-001 · Ana Quispe');
    expect(recibido).toBe('sin-llamar');
  });

  it('el formulario puede fijar el valor y el campo muestra su etiqueta', () => {
    c.writeValue(3);
    expect(c.texto()).toBe('DEMO-003 · Rosa Ramos');
  });

  it('se navega y se elige con el teclado', () => {
    const evento = (key: string) => {
      const e = new KeyboardEvent('keydown', { key });
      c.alTeclear(e);
      return e;
    };
    let recibido: unknown;
    c.registerOnChange((v) => (recibido = v));

    evento('ArrowDown'); // abre
    evento('ArrowDown'); // baja a la segunda
    evento('Enter');

    expect(recibido).toBe(2);
  });

  it('Escape cierra sin elegir', () => {
    c.writeValue(1);
    c.abrir();
    c.alEscribir('luis');

    c.alTeclear(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(c.abierto()).toBe(false);
    expect(c.valor()).toBe(1);
  });

  it('deshabilitado no se abre', () => {
    c.setDisabledState(true);
    c.abrir();
    expect(c.abierto()).toBe(false);
  });

  it('avisa cuando lo tecleado no coincide con nada', () => {
    c.alEscribir('zzz');
    expect(c.filtradas()).toHaveLength(0);
  });

  // ---- Colocación de la lista (position: fixed) ----

  /** Simula dónde está el campo en la ventana; jsdom no calcula layout. */
  function campoEn(top: number, left: number, ancho: number, altoVentana = 800, anchoVentana = 1200) {
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: altoVentana });
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: anchoVentana });
    const buscador = fixture.nativeElement.querySelector('.buscador') as HTMLElement;
    buscador.getBoundingClientRect = () =>
      ({ top, bottom: top + 46, left, right: left + ancho, width: ancho, height: 46 }) as DOMRect;
  }

  it('con sitio abajo, la lista se abre bajo el campo', () => {
    campoEn(100, 50, 400);
    c.abrir();
    const p = c.posicion();
    expect(p.haciaArriba).toBe(false);
    expect(p.top).toBe(150);
    expect(p.bottom).toBeNull();
    expect(p.ancho).toBe(400);
  });

  it('cerca del borde inferior se abre hacia arriba, para no quedar cortada', () => {
    campoEn(700, 50, 400);
    c.abrir();
    const p = c.posicion();
    expect(p.haciaArriba).toBe(true);
    expect(p.top).toBeNull();
    expect(p.bottom).toBe(800 - 700 + 4);
    expect(p.altoMaximo).toBeLessThanOrEqual(700);
  });

  it('un campo angosto recibe una lista más ancha que no se sale por la derecha', () => {
    campoEn(100, 1100, 90);
    c.abrir();
    const p = c.posicion();
    expect(p.ancho).toBe(260);
    expect(p.left + p.ancho).toBeLessThanOrEqual(1200 - 8);
  });

  it('al cerrar deja de seguir el scroll del documento', () => {
    const quitar = vi.spyOn(document, 'removeEventListener');
    c.abrir();
    c.cerrar();
    expect(quitar).toHaveBeenCalledWith('scroll', expect.any(Function), true);
    quitar.mockRestore();
  });

  // ---- Regresión: «escribo sal y siempre pone cuajo» (Compras, 2026-10-28) ----
  describe('lo que reportó el cliente en Compras', () => {
    const INSUMOS: OpcionBuscador[] = [{ valor: 9, etiqueta: 'cuajo', detalle: 'Para yogurt' }];

    beforeEach(() => {
      c.opciones = INSUMOS;
    });

    it('abrir la lista y LUEGO escribir filtra: «sal» no muestra «cuajo»', () => {
      c.abrir(); // el foco abre la lista antes de teclear: así quedaba congelado el filtro
      expect(c.filtradas()).toHaveLength(1);
      c.alEscribir('sal');
      expect(c.filtradas()).toEqual([]);
    });

    it('Enter con lo escrito sin coincidencias no elige nada', () => {
      let recibido: unknown = 'sin-llamar';
      c.registerOnChange((v) => (recibido = v));
      c.abrir();
      c.alEscribir('zal');
      c.alTeclear(new KeyboardEvent('keydown', { key: 'Enter' }));
      expect(recibido).toBe('sin-llamar');
      expect(c.valor()).toBeUndefined();
    });

    it('la lista vacía puede explicar por qué no aparece', () => {
      c.sinCoincidencias = (t) => `«${t}» no lo lleva ninguna receta`;
      c.abrir();
      c.alEscribir('sal');
      fixture.detectChanges();
      const vacio = fixture.nativeElement.ownerDocument.querySelector('.buscador-vacio');
      expect(vacio?.textContent).toContain('«sal» no lo lleva ninguna receta');
    });
  });

  it('las opciones que llegan después se ven: en la lista y en el texto del valor ya puesto', () => {
    c.opciones = [];
    c.writeValue(2); // el formulario llega con el valor antes que las opciones
    c.abrir();
    expect(c.texto()).toBe('');
    c.opciones = OPCIONES;
    expect(c.texto()).toBe('DEMO-002 · Luis Mamani');
    expect(c.filtradas()).toHaveLength(3);
  });

  it('busca sin importar tildes', () => {
    c.opciones = [{ valor: 1, etiqueta: 'Lúcuma' }, { valor: 2, etiqueta: 'Sal' }];
    c.alEscribir('lucuma');
    expect(c.filtradas().map((o) => o.valor)).toEqual([1]);
  });

  it('salir habiendo escrito el nombre exacto lo elige; a medias, no', () => {
    let recibido: unknown = 'sin-llamar';
    c.registerOnChange((v) => (recibido = v));
    c.abrir();
    c.alEscribir('demo-003 · rosa ramos');
    c.alTeclear(new KeyboardEvent('keydown', { key: 'Tab' }));
    expect(recibido).toBe(3);
    expect(c.abierto()).toBe(false);

    recibido = 'sin-llamar';
    c.abrir();
    c.alEscribir('demo-00');
    c.alTeclear(new KeyboardEvent('keydown', { key: 'Tab' }));
    expect(recibido).toBe('sin-llamar');
    expect(c.valor()).toBe(3);
  });

  it('Escape cierra sin elegir aunque lo escrito sea exacto', () => {
    c.abrir();
    c.alEscribir('DEMO-001 · Ana Quispe');
    c.alTeclear(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(c.valor()).toBeUndefined();
    expect(c.texto()).toBe('');
  });
});
