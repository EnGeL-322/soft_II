import { TestBed } from '@angular/core/testing';
import { PaginadorComponent, numerosDePagina } from './paginador.component';
import { PaginacionLocal, paginaDisponible } from './paginacion-local';

describe('numerosDePagina', () => {
  it('muestra todas las páginas cuando son pocas', () => {
    expect(numerosDePagina(0, 5)).toEqual([0, 1, 2, 3, 4]);
  });

  it('resume con puntos suspensivos cuando son muchas', () => {
    expect(numerosDePagina(0, 25)).toEqual([0, 1, 2, 3, 4, 'hueco', 24]);
    expect(numerosDePagina(12, 25)).toEqual([0, 'hueco', 11, 12, 13, 'hueco', 24]);
    expect(numerosDePagina(24, 25)).toEqual([0, 'hueco', 20, 21, 22, 23, 24]);
  });
});

describe('PaginadorComponent', () => {
  function crear(page: number, size: number, total: number) {
    const fixture = TestBed.createComponent(PaginadorComponent);
    fixture.componentRef.setInput('page', page);
    fixture.componentRef.setInput('size', size);
    fixture.componentRef.setInput('total', total);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    return { fixture, c: fixture.componentInstance, el };
  }
  const boton = (el: HTMLElement, etiqueta: string) =>
    el.querySelector(`button[aria-label="${etiqueta}"]`) as HTMLButtonElement;

  it('indica qué registros se ven y del total', () => {
    const { el } = crear(0, 10, 245);
    expect(el.querySelector('.paginador-info')!.textContent).toContain('Mostrando 1–10 de 245 registros');
  });

  it('deshabilita Anterior en la primera página y Siguiente en la última', () => {
    let { el } = crear(0, 10, 30);
    expect(boton(el, 'Página anterior').disabled).toBe(true);
    expect(boton(el, 'Página siguiente').disabled).toBe(false);
    ({ el } = crear(2, 10, 30));
    expect(boton(el, 'Página siguiente').disabled).toBe(true);
  });

  it('resalta la página actual', () => {
    const { el } = crear(1, 10, 30);
    const actual = el.querySelector('button[aria-current="page"]')!;
    expect(actual.textContent!.trim()).toBe('2');
    expect(actual.classList).toContain('activa');
  });

  it('emite la página elegida y el nuevo tamaño', () => {
    const { el, c } = crear(0, 10, 245);
    const paginas: number[] = [];
    const tamanos: number[] = [];
    c.pageChange.subscribe((p) => paginas.push(p));
    c.sizeChange.subscribe((t) => tamanos.push(t));

    boton(el, 'Página siguiente').click();
    boton(el, 'Página 3').click();
    const select = el.querySelector('select')!;
    select.value = '50';
    select.dispatchEvent(new Event('change'));

    expect(paginas).toEqual([1, 2]);
    expect(tamanos).toEqual([50]);
  });

  it('sin registros no muestra nada: el mensaje de vacío lo da la tabla', () => {
    const { el } = crear(0, 10, 0);
    expect(el.querySelector('.paginador')).toBeNull();
  });

  it('con una sola página muestra el total pero no botones ni tamaño', () => {
    const { el } = crear(0, 10, 4);
    expect(el.querySelector('.paginador-info')!.textContent).toContain('Mostrando 1–4 de 4 registros');
    expect(el.querySelector('button')).toBeNull();
    expect(el.querySelector('select')).toBeNull();
  });

  it('si la página quedó fuera de rango muestra la última disponible', () => {
    const { c } = crear(5, 10, 21);
    expect(c.actual()).toBe(2);
    expect(c.desde()).toBe(21);
  });
});

describe('PaginacionLocal', () => {
  const lista = Array.from({ length: 25 }, (_, i) => i + 1);

  it('recorta la lista a la página actual con 10 por página', () => {
    const pag = new PaginacionLocal();
    expect(pag.ver(lista)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
    pag.irA(2);
    expect(pag.ver(lista)).toEqual([21, 22, 23, 24, 25]);
  });

  it('al cambiar el tamaño vuelve a la primera página', () => {
    const pag = new PaginacionLocal();
    pag.ver(lista);
    pag.irA(2);
    pag.cambiarTamano(20);
    expect(pag.pagina(lista.length)).toBe(0);
    expect(pag.ver(lista)).toHaveLength(20);
  });

  it('al cambiar los filtros vuelve a la primera página', () => {
    const pag = new PaginacionLocal();
    pag.ver(lista, 'texto=');
    pag.irA(1);
    expect(pag.pagina(lista.length, 'texto=')).toBe(1);
    expect(pag.ver(lista, 'texto=ana')[0]).toBe(1);
  });

  it('si se elimina el último registro de la página, retrocede a la anterior', () => {
    const pag = new PaginacionLocal();
    const once = lista.slice(0, 11);
    pag.ver(once);
    pag.irA(1);
    expect(pag.ver(once)).toEqual([11]);
    expect(pag.ver(once.slice(0, 10))).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
  });
});

describe('paginaDisponible', () => {
  it('pide la última página con datos si la actual quedó vacía', () => {
    expect(paginaDisponible({ page: 3, totalPages: 3, content: [] })).toBe(2);
    expect(paginaDisponible({ page: 0, totalPages: 0, content: [] })).toBeNull();
    expect(paginaDisponible({ page: 1, totalPages: 3, content: [1] })).toBeNull();
  });
});
