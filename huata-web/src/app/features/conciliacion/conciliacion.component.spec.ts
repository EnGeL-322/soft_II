import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EMPTY, Subject, of } from 'rxjs';
import { ConciliacionRuta, EventoAcopio } from '../../core/models/models';
import { AcopioTiempoRealService } from '../../core/services/acopio-tiempo-real.service';
import { AuthService } from '../../core/services/auth.service';
import { ConciliacionService } from '../../core/services/conciliacion.service';
import { RutasService } from '../../core/services/rutas.service';
import { OperacionService } from '../../core/services/operacion.service';
import { ConciliacionComponent } from './conciliacion.component';

/** La fecha operativa de Huata (Lima), que es la que el componente considera "en vivo". */
function hoyLima(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}

function fila(parcial: Partial<ConciliacionRuta> & { rutaId: number; rutaNombre: string }): ConciliacionRuta {
  return {
    totalRecolectado: 0,
    litrosEnRuta: 0,
    litrosEntregadosEnPlanta: 0,
    proveedoresAcopiados: 0,
    proveedoresNoEntregaron: 0,
    detalle: [],
    ...parcial,
  };
}

const RUTA_NORTE = fila({
  rutaId: 1,
  rutaNombre: 'Ruta COLLANA I',
  acopiadorNombre: 'Juan Condori',
  totalRecolectado: 40,
  litrosEnRuta: 40,
  proveedoresAcopiados: 2,
  ultimoAcopio: '2035-07-10T10:40:00Z',
  detalle: [
    {
      recojoId: 100,
      productorCodigo: 'P-31',
      productorNombre: 'QUISPE HUMPIRI, LEONARDA',
      fechaHora: '2035-07-10T10:10:00Z',
      litros: 15,
      fuente: 'RUTA',
      estadoVisita: 'ACOPIADO',
    },
    {
      recojoId: 101,
      productorCodigo: 'P-32',
      productorNombre: 'RAMOS CALSIN, ELSA',
      fechaHora: '2035-07-10T10:40:00Z',
      litros: 25,
      fuente: 'RUTA',
      estadoVisita: 'ACOPIADO',
    },
  ],
});

describe('ConciliacionComponent', () => {
  let fixture: ComponentFixture<ConciliacionComponent>;
  let component: ConciliacionComponent;
  let canal: Subject<EventoAcopio>;

  beforeEach(async () => {
    canal = new Subject<EventoAcopio>();

    await TestBed.configureTestingModule({
      imports: [ConciliacionComponent],
      providers: [
        { provide: ConciliacionService, useValue: { deFecha: () => of([RUTA_NORTE]), registrar: () => EMPTY } },
        { provide: RutasService, useValue: { listar: () => of({ content: [] }) } },
        { provide: AuthService, useValue: { puede: () => false } },
        {
          provide: AcopioTiempoRealService,
          useValue: { estado$: of('conectado'), conectar: () => canal.asObservable() },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConciliacionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('separa lo acopiado en el camino de lo que el proveedor entregó en planta', () => {
    component.filas.set([
      fila({ rutaId: 1, rutaNombre: 'Ruta A', totalRecolectado: 50, litrosEnRuta: 30, litrosEntregadosEnPlanta: 20 }),
    ]);

    expect(component.totalEnRuta()).toBe(30);
  });

  it('reemplaza la fila que llega por el canal en vez de duplicarla', () => {
    const conMasLeche = { ...RUTA_NORTE, litrosEnRuta: 58, totalRecolectado: 58, proveedoresAcopiados: 3 };

    canal.next({ tipo: 'RECOJO', conciliacion: conMasLeche });

    expect(component.filas().length).toBe(1);
    expect(component.filas()[0].litrosEnRuta).toBe(58);
  });

  /** Una entrega directa ya está en planta: no debe aparecer una ruta "por recibir" por ella. */
  it('no agrega una ruta que solo tiene entregas directas, y quita la que ya no tiene nada que recibir', () => {
    canal.next({
      tipo: 'RECOJO',
      conciliacion: fila({ rutaId: 2, rutaNombre: 'Ruta AAA', litrosEntregadosEnPlanta: 25, requiereRecepcion: false }),
    });
    expect(component.filas().map((f) => f.rutaId)).toEqual([1]);

    canal.next({ tipo: 'RECOJO', conciliacion: { ...RUTA_NORTE, litrosEnRuta: 0, detalle: [], requiereRecepcion: false } });
    expect(component.filas()).toEqual([]);
  });

  it('inserta en orden una ruta que todavía no había registrado nada', () => {
    canal.next({ tipo: 'RECOJO', conciliacion: fila({ rutaId: 2, rutaNombre: 'Ruta AAA' }) });

    expect(component.filas().map((f) => f.rutaNombre)).toEqual(['Ruta AAA', 'Ruta COLLANA I']);
  });

  /**
   * El total nunca se suma en el navegador: la fila llega recalculada. Si se sumara, un mensaje
   * perdido dejaría la pantalla con un número que no existe en la base y nadie lo notaría.
   */
  it('toma el total del mensaje y no lo acumula sobre el que ya tenía', () => {
    canal.next({ tipo: 'RECOJO', conciliacion: { ...RUTA_NORTE, litrosEnRuta: 55, totalRecolectado: 55 } });
    canal.next({ tipo: 'RECOJO', conciliacion: { ...RUTA_NORTE, litrosEnRuta: 55, totalRecolectado: 55 } });

    expect(component.totalEnRuta()).toBe(55);
  });

  it('ignora los mensajes de hoy cuando el usuario está consultando otra fecha', () => {
    component.fecha = '2035-01-01';

    canal.next({ tipo: 'RECOJO', conciliacion: { ...RUTA_NORTE, litrosEnRuta: 999 } });

    expect(component.enVivo()).toBe(false);
    expect(component.filas()[0].litrosEnRuta).toBe(40);
  });

  it('considera en vivo la fecha operativa de Lima, no la del reloj del navegador', () => {
    expect(component.fecha).toBe(hoyLima());
    expect(component.enVivo()).toBe(true);
  });

  it('resalta la ruta que acaba de moverse y no las demás', () => {
    canal.next({ tipo: 'RECOJO', conciliacion: RUTA_NORTE });

    expect(component.resaltada(1)).toBe(true);
    expect(component.resaltada(2)).toBe(false);
  });

  it('despliega el detalle de paradas de una ruta a la vez', () => {
    expect(component.detalleVisible(1)).toBe(false);

    component.alternarDetalle(1);
    fixture.detectChanges();

    expect(component.detalleVisible(1)).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('RAMOS CALSIN, ELSA');

    component.alternarDetalle(1);
    expect(component.detalleVisible(1)).toBe(false);
  });

  it('suma la diferencia que informa el backend y no la recalcula', () => {
    component.filas.set([
      // totalRecolectado incluye 20 L que el proveedor llevó él mismo: restarlos daría -20.
      fila({
        rutaId: 1,
        rutaNombre: 'Ruta A',
        totalRecolectado: 50,
        litrosEnRuta: 30,
        litrosEntregadosEnPlanta: 20,
        litrosRecibidosPlanta: 30,
        diferencia: 0,
      }),
    ]);

    expect(component.totalDiferencia()).toBe(0);
    expect(component.rutasSinCerrar()).toBe(0);
  });

  it('cuenta las rutas que aún no tienen total de planta', () => {
    expect(component.rutasSinCerrar()).toBe(1);
  });

  it('el estado de cada ruta se lee en palabras: por recibir, cuadra o con diferencia', () => {
    expect(component.estado(RUTA_NORTE)).toBe('por-recibir');
    expect(component.estado({ ...RUTA_NORTE, litrosRecibidosPlanta: 40, diferencia: 0 })).toBe('cuadra');
    expect(component.estado({ ...RUTA_NORTE, litrosRecibidosPlanta: 38, diferencia: 2 })).toBe('diferencia');
    expect(component.porcentaje({ ...RUTA_NORTE, litrosRecibidosPlanta: 38, diferencia: 2 })).toBe(5);
  });
});

describe('Recepción de rutas · registrar la llegada', () => {
  it('solo envía litros y observación: quién recibe lo pone el backend con la cuenta de la sesión', () => {
    const registrar = vi.fn(() => of({ ...RUTA_NORTE, litrosRecibidosPlanta: 39, diferencia: 1 }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ConciliacionComponent],
      providers: [
        { provide: ConciliacionService, useValue: { deFecha: () => of([RUTA_NORTE]), registrar } },
        { provide: AuthService, useValue: { puede: () => true, nombreCompleto: () => 'Responsable Calidad Demo' } },
        {
          provide: OperacionService,
          useValue: { totales: () => EMPTY, resumen: () => EMPTY },
        },
        { provide: AcopioTiempoRealService, useValue: { estado$: of('conectado'), conectar: () => EMPTY } },
      ],
    });
    const f = TestBed.createComponent(ConciliacionComponent);
    f.detectChanges();
    const c = f.componentInstance;

    c.abrirRegistro(RUTA_NORTE);
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Recibe: Responsable Calidad Demo');
    c.litros = 39;
    c.observacion = '  ';
    c.registrar(RUTA_NORTE);

    expect(registrar).toHaveBeenCalledWith({ fecha: c.fecha, rutaId: 1, litrosRecibidosPlanta: 39, observacion: undefined });
    expect(c.registrando()).toBeNull();
    expect(c.filas()[0].litrosRecibidosPlanta).toBe(39);
  });
});
