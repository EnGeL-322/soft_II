import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { EMPTY, Subject, of, throwError } from 'rxjs';
import { PERMISO } from '../../core/config/navegacion';
import {
  Acopiador,
  PlanRutaDia,
  Productor,
  Ruta,
  RutaPunto,
  TrazadoAcopiador,
  UbicacionGps,
} from '../../core/models/models';
import { AcopiadorRequest, AcopiadoresService } from '../../core/services/acopiadores.service';
import { ProductoresService } from '../../core/services/productores.service';
import { AuthService } from '../../core/services/auth.service';
import { ExportacionesService } from '../../core/services/exportaciones.service';
import { RecojoService } from '../../core/services/recojo.service';
import { RutasService } from '../../core/services/rutas.service';
import { PuntosEstablecidosService } from '../../core/services/puntos-establecidos.service';
import { TrazadosService } from '../../core/services/trazados.service';
import { UbicacionesTiempoRealService } from '../../core/services/ubicaciones-tiempo-real.service';
import { UbicacionesService } from '../../core/services/ubicaciones.service';
import { RutasComponent } from './rutas.component';

/**
 * Flujo territorial de "Rutas y mapa": zona -> acopiador -> trazado de su ruta. Lo que se prueba
 * aquí es el pegamento entre las tres cosas (qué compone una zona, qué se envía al backend y qué se
 * bloquea antes de enviarlo), no el dibujo en el mapa.
 */

const RUTAS: Ruta[] = [
  { id: 1, nombre: 'Ruta mañana', zona: 'COLLANA I/YASIN', descripcion: 'Temprano', activo: true },
  { id: 2, nombre: 'Ruta tarde', zona: 'COLLANA I/YASIN', activo: true },
  { id: 3, nombre: 'Ruta Pancha', zona: 'MORO VIEJO', activo: true },
];

const ACOPIADORES: Acopiador[] = [
  {
    id: 10,
    nombres: 'Juan',
    apellidos: 'Condori',
    documentoIdentidad: '40404040',
    telefono: '951000111',
    rutaId: 1,
    rutaNombre: 'Ruta mañana',
    zona: 'COLLANA I/YASIN',
    username: 'juan@huata.pe',
    activo: true,
  },
  {
    id: 11,
    nombres: 'Rosa',
    apellidos: 'Mamani',
    rutaId: 3,
    rutaNombre: 'Ruta Pancha',
    zona: 'MORO VIEJO',
    username: 'rosa@huata.pe',
    activo: true,
  },
];

function proveedor(id: number, nombres: string, rutaId?: number, ordenEnRuta?: number): Productor {
  return {
    id,
    codigo: `P-${id}`,
    nombres,
    apellidos: 'Prueba',
    rutaId,
    ordenEnRuta,
    estado: 'ACTIVO',
    excelencia: false,
    activo: true,
  };
}

/** Tres en COLLANA I/YASIN (rutas 1 y 2), uno en MORO VIEJO y uno sin ruta. */
const PROVEEDORES: Productor[] = [
  proveedor(20, 'Alexander', 1, 2),
  proveedor(21, 'Bea', 2, 1),
  proveedor(22, 'Carla', 1),
  proveedor(23, 'Dora', 3),
  proveedor(24, 'Eva'),
];

/**
 * Dos recorridos pintados: uno de la zona que se está mirando y otro de otra zona, para comprobar
 * que la tabla filtra por las rutas de la zona y no lo muestra todo junto.
 */
function trazado(
  over: Partial<TrazadoAcopiador> & { id: number; acopiadorId: number; rutaId: number },
): TrazadoAcopiador {
  return {
    acopiadorNombre: 'Juan Condori',
    rutaNombre: 'Ruta mañana',
    tipo: 'DIA',
    fecha: '2026-09-22',
    sigueRutaDesignada: false,
    puntos: [
      { secuencia: 0, latitud: -15.65, longitud: -69.98 },
      { secuencia: 1, latitud: -15.66, longitud: -69.99 },
    ],
    ...over,
  };
}

const PINTADOS: TrazadoAcopiador[] = [
  trazado({ id: 501, acopiadorId: 10, rutaId: 1 }),
  trazado({
    id: 502,
    acopiadorId: 11,
    rutaId: 3,
    acopiadorNombre: 'Rosa Mamani',
    rutaNombre: 'Ruta Pancha',
  }),
];

function pagina<T>(content: T[]) {
  return of({
    content,
    page: 0,
    size: content.length,
    totalElements: content.length,
    totalPages: 1,
  });
}

function crear(queryParams: Record<string, string> = {}) {
  const rutasService = {
    listar: vi.fn(() => pagina(RUTAS)),
    asignaciones: vi.fn(() => of([])),
    planOperativo: vi.fn(() => of([])),
    poligonosDeZona: vi.fn(() =>
      of([{ id: 7, zona: 'COLLANA I/YASIN', color: '#123456', puntos: [] }]),
    ),
    puntos: vi.fn(() => of([])),
    proveedores: vi.fn(() => of([])),
    crear: vi.fn((datos: Partial<Ruta>) => of({ id: 99, activo: true, ...datos } as Ruta)),
    actualizar: vi.fn(() => of(RUTAS[0])),
    inactivar: vi.fn(() => of(undefined)),
    renombrarZona: vi.fn(() => of(undefined)),
    eliminarZona: vi.fn(() => of(undefined)),
    historialDeLimite: vi.fn(() => of([])),
    restaurarLimite: vi.fn(),
    guardarPoligonoDeZona: vi.fn(),
    eliminarPoligonoDeZona: vi.fn(),
    // Devuelven observable porque el componente se suscribe: un vi.fn() pelado rompe el flujo real.
    guardarPuntos: vi.fn((_rutaId: number, puntos: unknown[]) => of(puntos)),
    eliminarPuntos: vi.fn(() => of(undefined)),
  };
  const productoresService = {
    registrarUbicacion: vi.fn(
      (id: number, datos: { latitud: number; longitud: number; tipo: string }) =>
        of({ id: 1, productorId: id, ...datos, fecha: '2026-09-21' }),
    ),
    listar: vi.fn(() => pagina(PROVEEDORES)),
    asignarRuta: vi.fn((id: number, rutaId: number, ordenEnRuta?: number | null) =>
      of({
        ...PROVEEDORES.find((p) => p.id === id)!,
        rutaId,
        ordenEnRuta: ordenEnRuta ?? undefined,
      }),
    ),
  };
  const acopiadoresService = {
    listar: vi.fn(() => pagina(ACOPIADORES)),
    crear: vi.fn(() => of(ACOPIADORES[0])),
    actualizar: vi.fn(() => of(ACOPIADORES[0])),
  };

  const trazadosService = {
    pintados: vi.fn(() => of(PINTADOS)),
    predeterminados: vi.fn(() => of([] as TrazadoAcopiador[])),
    predeterminar: vi.fn((id: number) => {
      const origen = PINTADOS.find((t) => t.id === id) as TrazadoAcopiador;
      return of({
        ...origen,
        id: 900,
        tipo: 'PREDETERMINADO' as const,
        fecha: undefined,
        origenTrazadoId: id,
      });
    }),
    quitarPredeterminado: vi.fn(() => of(undefined)),
  };

  const exportacionesService = { descargar: vi.fn(() => EMPTY) };

  const puntosEstablecidosService = {
    listar: vi.fn(() => of([])),
    crear: vi.fn((datos: unknown) => of({ id: 1, activo: true, ...(datos as object) })),
    actualizar: vi.fn((id: number, datos: unknown) =>
      of({ id, activo: true, ...(datos as object) }),
    ),
    desactivar: vi.fn(() => of(undefined)),
  };

  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [RutasComponent],
    providers: [
      { provide: RutasService, useValue: rutasService },
      { provide: TrazadosService, useValue: trazadosService },
      { provide: PuntosEstablecidosService, useValue: puntosEstablecidosService },
      { provide: AcopiadoresService, useValue: acopiadoresService },
      { provide: ProductoresService, useValue: productoresService },
      { provide: UbicacionesService, useValue: { ultimas: () => of([]), historial: () => of([]) } },
      {
        provide: UbicacionesTiempoRealService,
        useValue: { estado$: EMPTY, conectar: () => EMPTY },
      },
      { provide: RecojoService, useValue: { listar: () => of({ content: [] }) } },
      { provide: ExportacionesService, useValue: exportacionesService },
      // Las confirmaciones se aceptan: estas pruebas verifican lo que pasa después de confirmar.
      {
        provide: DialogoService,
        useValue: {
          confirmar: vi.fn(async () => true),
          pedirTexto: vi.fn(async (o: { placeholder?: string }) => o.placeholder ?? ''),
        },
      },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } },
      },
      {
        provide: AuthService,
        useValue: {
          puede: (permiso: string) => permiso === PERMISO.rutasWrite,
          nombreCompleto: () => 'Admin de prueba',
        },
      },
    ],
  });

  const fixture: ComponentFixture<RutasComponent> = TestBed.createComponent(RutasComponent);
  fixture.detectChanges();
  return {
    fixture,
    component: fixture.componentInstance,
    rutasService,
    acopiadoresService,
    productoresService,
    trazadosService,
    puntosEstablecidosService,
    exportacionesService,
  };
}

describe('RutasComponent · selección y seguimiento', () => {
  const plan: PlanRutaDia[] = [
    {
      rutaId: 1,
      rutaNombre: 'Ruta mañana',
      zona: 'COLLANA',
      acopiadorId: 10,
      acopiadorNombre: 'Juan Condori',
      estado: 'ASIGNADA',
      litros: 0,
      recepciones: 0,
    },
    {
      rutaId: 2,
      rutaNombre: 'Ruta tarde',
      zona: 'YASIN',
      acopiadorId: 11,
      acopiadorNombre: 'Rosa Mamani',
      estado: 'CON_REGISTROS',
      litros: 120,
      recepciones: 2,
    },
  ];

  it('el clic en una ruta muestra solo su GPS y permite volver a la vista general', async () => {
    const { component, fixture } = crear();
    component.plan.set(plan);
    component.ultimasUbicaciones.set(
      plan.map((p) => ({
        acopiadorId: p.acopiadorId!,
        acopiadorNombre: p.acopiadorNombre!,
        latitud: -15.65,
        longitud: -69.98,
        fecha: new Date().toISOString(),
      })),
    );
    fixture.detectChanges();
    const opcion = [...fixture.nativeElement.querySelectorAll('.route-option')].find((el: any) =>
      el.textContent.includes('Ruta mañana'),
    ) as HTMLButtonElement;
    opcion.click();
    // Si quedó una edición abierta, primero se pregunta si se descarta (el mock acepta).
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component.puntosMapa().map((p) => p.id)).toEqual([10]);
    expect(fixture.nativeElement.querySelector('.routes-map-heading h3').textContent).toContain(
      'Ruta mañana',
    );
    fixture.nativeElement.querySelector('.route-overview').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component.puntosMapa().map((p) => p.id)).toEqual([10, 11]);
    expect(fixture.nativeElement.querySelector('.routes-route-detail')).toBeNull();
  });

  it('ignora el trazado y GPS de una ruta anterior si su respuesta llega tarde', () => {
    const { component, rutasService } = crear();
    component.plan.set(plan);
    const trazadoAnterior = new Subject<RutaPunto[]>();
    const gpsAnterior = new Subject<UbicacionGps[]>();
    rutasService.puntos.mockReturnValueOnce(trazadoAnterior as any);
    vi.spyOn(TestBed.inject(UbicacionesService), 'historial').mockReturnValueOnce(gpsAnterior);
    component.cambiarRutaSeleccionada(1);
    component.cambiarRutaSeleccionada(2);
    trazadoAnterior.next([{ secuencia: 1, latitud: -15.6, longitud: -69.9 }]);
    gpsAnterior.next([
      {
        acopiadorId: 10,
        acopiadorNombre: 'Juan',
        latitud: -15.6,
        longitud: -69.9,
        fecha: new Date().toISOString(),
      },
    ]);
    expect(component.rutaSeleccionadaId()).toBe(2);
    expect(component.trazadoMapaSeguimiento()).toEqual([]);
    expect(component.eventosRecorrido()).toEqual([]);
  });

  it('muestra un error de consulta en lugar de presentar el plan como vacío', () => {
    const { component, rutasService, fixture } = crear();
    rutasService.planOperativo.mockReturnValueOnce(throwError(() => new Error('Sin conexión')));
    component.cargarTodo();
    fixture.detectChanges();
    expect(component.cargandoPlan()).toBe(false);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'No se pudo cargar el plan',
    );
  });
});

describe('RutasComponent · editor unificado', () => {
  it('mantiene formulario y un solo mapa en la pestaña de zona y ruta', () => {
    const { component, fixture } = crear();
    component.tab.set('territorio');
    component.seleccionarZona('COLLANA I/YASIN', 3);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.unified-route-editor app-mapa')).toHaveLength(
      1,
    );
    expect(fixture.nativeElement.querySelector('.unified-points')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.routes-navigation')).toBeNull();
  });

  it('el primer clic pinta el punto inmediatamente y no permite guardar sin ubicación', async () => {
    const { component, puntosEstablecidosService } = crear();
    component.seleccionarRutaPuntos(1);
    component.activarModoAgregarPuntoEstablecido();
    component.formPuntoEstablecido.nombre = 'Nueva entrega';
    expect(component.puntoDraftMapa()).toEqual([]);
    await component.guardarPuntoEstablecido();
    expect(component.errorPuntoEstablecido()).toContain('Falta ubicar en el mapa');
    expect(puntosEstablecidosService.crear).not.toHaveBeenCalled();
    component.clicMapaPuntoEstablecido({ latitud: -15.65, longitud: -69.98 });
    expect(component.puntoDraftMapa()[0].lat).toBe(-15.65);
    component.clicMapaPuntoEstablecido({ latitud: -15.7, longitud: -70 });
    expect(component.puntoDraftMapa()[0].lat).toBe(-15.7);
  });

  it('ubica a un proveedor existente con un punto y nunca da de alta proveedores desde el mapa', async () => {
    const { component, puntosEstablecidosService, productoresService } = crear();
    vi.spyOn(TestBed.inject(AuthService), 'puede').mockReturnValue(true);
    component.seleccionarRutaPuntos(1);
    component.agregarProveedorConPunto();
    expect(component.modoAltaPunto()).toBe('existente');
    component.formPuntoEstablecido.proveedorId = 99;
    component.formPuntoEstablecido.nombre = 'Lucia Quispe';
    component.clicMapaPuntoEstablecido({ latitud: -15.65, longitud: -69.98 });
    await component.guardarPuntoEstablecido();
    expect(puntosEstablecidosService.crear).toHaveBeenCalledWith(
      expect.objectContaining({ proveedorId: 99, rutaId: 1, latitud: -15.65, longitud: -69.98 }),
    );
    expect(productoresService.registrarUbicacion).not.toHaveBeenCalled();
  });

  it('arrastrar un punto conserva su ID y guarda sus coordenadas para sincronizar', async () => {
    const { component, puntosEstablecidosService } = crear();
    vi.spyOn(TestBed.inject(AuthService), 'puede').mockReturnValue(true);
    component.seleccionarZona('COLLANA I/YASIN', 3);
    component.puntosEstablecidos.set([
      {
        id: 12,
        nombre: 'Casa Bea',
        rutaId: 1,
        rutaNombre: 'Ruta mañana',
        proveedorId: 20,
        latitud: -15.65,
        longitud: -69.98,
        activo: true,
      },
    ]);
    await component.moverMarcadorGestion({ id: 'pe-12', latitud: -15.7, longitud: -70 });
    expect(puntosEstablecidosService.actualizar).toHaveBeenCalledWith(
      12,
      expect.objectContaining({ proveedorId: 20, latitud: -15.7, longitud: -70 }),
    );
    expect(component.puntoSeleccionado()).toBe(12);
  });
});

describe('RutasComponent · flujo territorial', () => {
  it('abre el flujo territorial en la zona indicada por la URL', () => {
    const { component } = crear({ tab: 'territorio', zona: 'MORO VIEJO' });

    expect(component.tab()).toBe('territorio');
    expect(component.zonaPoligonoSeleccionada()).toBe('MORO VIEJO');
    expect(component.rutasDeZonaSeleccionada().map((r) => r.id)).toEqual([3]);
  });

  it('ignora una zona de la URL que ya no existe, sin romper la pantalla', () => {
    const { component } = crear({ tab: 'territorio', zona: 'ZONA BORRADA' });

    expect(component.tab()).toBe('territorio');
    expect(component.zonaPoligonoSeleccionada()).toBeUndefined();
  });

  it('agrupa aparte las rutas que se quedaron sin zona, para que no queden invisibles', () => {
    const { component, rutasService } = crear();
    rutasService.listar.mockReturnValueOnce(
      pagina([...RUTAS, { id: 9, nombre: 'Ruta heredada', zona: '  ', activo: true } as Ruta]),
    );
    component.seleccionarZona(undefined);
    // Fuerza una relectura como la que hace cualquier cambio territorial.
    component.abrirRenombrarZona();

    rutasService.listar().subscribe((pg) => component.rutas.set(pg.content));

    const sinZona = component.zonasResumen().find((z) => z.zona.includes('Sin zona'));
    expect(sinZona?.rutas.map((r) => r.id)).toEqual([9]);
  });

  it('no deja renombrar ni dibujar territorio sobre el grupo "Sin zona"', () => {
    const { component, rutasService } = crear();
    rutasService
      .listar()
      .subscribe((pg) =>
        component.rutas.set([
          ...pg.content,
          { id: 9, nombre: 'Ruta heredada', zona: '', activo: true } as Ruta,
        ]),
      );
    const sinZona = component.zonasResumen().find((z) => z.zona.includes('Sin zona'))!;

    component.seleccionarZona(sinZona.zona);

    expect(component.esSinZona()).toBe(true);
    expect(component.rutasDeZonaSeleccionada().map((r) => r.id)).toEqual([9]);
  });

  it('un clic en el mapa ubica al proveedor elegido en vez de añadir un punto al trazado', async () => {
    const { component, rutasService, productoresService, puntosEstablecidosService } = crear();
    vi.spyOn(TestBed.inject(AuthService), 'puede').mockReturnValue(true);
    component.seleccionarRutaPuntos(1);
    const proveedor = { id: 77, codigo: 'P-77', nombres: 'Ana', apellidos: 'Quispe' };
    component.proveedoresRuta.set([proveedor]);
    component.puntosCargados.set(true);

    component.ubicarProveedor(proveedor);
    expect(component.pintandoRuta()).toBe(false);
    await component.clicEnElMapa({ latitud: -15.61, longitud: -69.97 });

    expect(puntosEstablecidosService.crear).toHaveBeenCalledWith(expect.objectContaining({ proveedorId: 77,
      latitud: -15.61,
      longitud: -69.97,
      rutaId: 1,
    }));
    expect(productoresService.registrarUbicacion).not.toHaveBeenCalled();
    // No se tocó el trazado de la ruta.
    expect(component.puntosRuta()).toEqual([]);
    expect(rutasService.guardarPuntos).not.toHaveBeenCalled();
    expect(component.proveedorAUbicar()).toBeNull();
  });

  it('el proveedor ubicado queda en la tabla y en el mapa sin recargar la ruta', async () => {
    const { component, rutasService } = crear();
    vi.spyOn(TestBed.inject(AuthService), 'puede').mockReturnValue(true);
    component.seleccionarRutaPuntos(1);
    component.proveedoresRuta.set([
      { id: 77, codigo: 'P-77', nombres: 'Ana', apellidos: 'Quispe' },
    ]);
    expect(component.proveedoresEnMapa()).toEqual([]);
    rutasService.proveedores.mockReturnValue(of([{ id: 77, codigo: 'P-77', nombres: 'Ana', apellidos: 'Quispe', latitud: -15.61, longitud: -69.97 }] as any));

    component.ubicarProveedor(component.proveedoresRuta()[0]);
    await component.clicEnElMapa({ latitud: -15.61, longitud: -69.97 });

    expect(component.proveedoresRuta()[0].latitud).toBe(-15.61);
    expect(component.proveedoresEnMapa()).toEqual([
      { id: 'prov-77', lat: -15.61, lng: -69.97, etiqueta: 'P-77 · Ana Quispe' },
    ]);
  });

  it('sin proveedor seleccionado, el clic sigue dibujando el trazado', () => {
    const { component, productoresService } = crear();
    component.seleccionarZona('COLLANA I/YASIN', 3);
    component.puntosCargados.set(true);

    component.clicEnElMapa({ latitud: -15.61, longitud: -69.97 });

    expect(productoresService.registrarUbicacion).not.toHaveBeenCalled();
    expect(component.puntosRuta().length).toBe(1);
  });

  it('cambiar de ruta cancela la ubicación en curso', () => {
    const { component } = crear();
    component.proveedoresRuta.set([
      { id: 77, codigo: 'P-77', nombres: 'Ana', apellidos: 'Quispe' },
    ]);
    component.ubicarProveedor(component.proveedoresRuta()[0]);

    component.seleccionarRutaPuntos(2);

    expect(component.proveedorAUbicar()).toBeNull();
  });

  it('trata la zona como el texto completo de la ruta y no lo parte por "/"', () => {
    const { component } = crear();

    expect(component.zonasDisponibles()).toEqual(['COLLANA I/YASIN', 'MORO VIEJO']);
  });

  it('compone cada zona con sus rutas, sus acopiadores y su polígono', () => {
    const { component } = crear();

    const collana = component.zonasResumen().find((z) => z.zona === 'COLLANA I/YASIN');
    expect(collana?.rutas.map((r) => r.id)).toEqual([1, 2]);
    expect(collana?.acopiadores.map((a) => a.id)).toEqual([10]);
    expect(collana?.poligono?.id).toBe(7);

    const moro = component.zonasResumen().find((z) => z.zona === 'MORO VIEJO');
    expect(moro?.poligono).toBeUndefined();
  });

  it('al elegir una zona deja listos sus rutas, acopiadores y el trazado de su primera ruta', () => {
    const { component, rutasService } = crear();

    component.seleccionarZona('COLLANA I/YASIN');

    expect(component.rutasDeZonaSeleccionada().map((r) => r.id)).toEqual([1, 2]);
    expect(component.acopiadoresDeZonaSeleccionada().map((a) => a.id)).toEqual([10]);
    expect(component.acopiadoresFueraDeZona().map((a) => a.id)).toEqual([11]);
    expect(component.rutaPuntosId()).toBe(1);
    expect(rutasService.puntos).toHaveBeenCalledWith(1);
  });

  it('no deja crear una zona con un nombre que ya existe', () => {
    const { component, rutasService } = crear();

    component.nuevaZonaPoligono = ' collana i/yasin ';
    component.crearZona();

    expect(component.errorZona()).toContain('Ya existe una zona');
    expect(rutasService.crear).not.toHaveBeenCalled();
  });

  it('la zona nueva queda seleccionada y en modo dibujo, sin polígono heredado', () => {
    const { component } = crear();
    component.seleccionarZona('COLLANA I/YASIN');

    component.nuevaZonaPoligono = 'HUANINA ALTA';
    component.crearZona();

    expect(component.zonaPoligonoSeleccionada()).toBe('HUANINA ALTA');
    expect(component.poligonoPuntos()).toEqual([]);
    expect(component.pintandoPoligono()).toBe(true);
    // Sin pestaña "Límite": la zona nueva abre "Rutas y personas" con su límite desplegado.
    expect(component.seccion()).toBe('personas');
    expect(component.limiteAbierto()).toBe(true);
  });

  it('crea la ruta dentro de la zona seleccionada y pasa al paso de acopiadores', () => {
    const { component, rutasService } = crear();
    component.seleccionarZona('MORO VIEJO');

    component.formRutaZona = { nombre: '  Ruta nueva  ', descripcion: '' };
    component.crearRutaEnZona();

    expect(rutasService.crear).toHaveBeenCalledWith({
      nombre: 'Ruta nueva',
      zona: 'MORO VIEJO',
      descripcion: undefined,
    });
    expect(component.pasoTerritorio()).toBe(2);
  });

  it('asignar un acopiador existente a la zona solo le cambia la ruta y conserva su contraseña', async () => {
    const { component, acopiadoresService } = crear();
    component.seleccionarZona('COLLANA I/YASIN');

    component.abrirFormAcopiador();
    component.acopiadorExistenteId = 11;
    component.rutaAcopiadorZonaId = 2;
    await component.guardarAcopiadorZona();

    const esperado: AcopiadorRequest = {
      nombres: 'Rosa',
      apellidos: 'Mamani',
      documentoIdentidad: undefined,
      telefono: undefined,
      username: 'rosa@huata.pe',
      password: '',
      rutaId: 2,
    };
    expect(acopiadoresService.actualizar).toHaveBeenCalledWith(11, esperado);
  });

  it('exige elegir la ruta de la zona antes de guardar el acopiador', async () => {
    const { component, acopiadoresService } = crear();
    component.seleccionarZona('COLLANA I/YASIN');

    component.abrirFormAcopiador();
    component.acopiadorExistenteId = 11;
    component.rutaAcopiadorZonaId = undefined;
    await component.guardarAcopiadorZona();

    expect(component.errorZona()).toContain('ruta de esta zona');
    expect(acopiadoresService.actualizar).not.toHaveBeenCalled();
  });

  it('no da de baja una ruta mientras un acopiador la tenga como ruta habitual', async () => {
    const { component, rutasService } = crear();
    component.seleccionarZona('COLLANA I/YASIN');

    await component.darDeBajaRuta(RUTAS[0]);

    expect(rutasService.inactivar).not.toHaveBeenCalled();
    expect(component.errorZona()).toContain('Juan Condori');
  });

  it('da de baja la ruta que nadie tiene asignada', async () => {
    const { component, rutasService } = crear();
    component.seleccionarZona('COLLANA I/YASIN');

    await component.darDeBajaRuta(RUTAS[1]);

    expect(rutasService.inactivar).toHaveBeenCalledWith(2);
  });

  it('renombrar la zona la delega al backend en una sola llamada', async () => {
    const { component, rutasService } = crear();
    component.seleccionarZona('MORO VIEJO');

    component.abrirRenombrarZona();
    component.nombreZonaEditado = '  MORO VIEJO ALTO ';
    await component.renombrarZona();

    expect(rutasService.renombrarZona).toHaveBeenCalledWith('MORO VIEJO', 'MORO VIEJO ALTO');
    expect(component.renombrandoZona()).toBe(false);
  });

  it('tras renombrar vuelve a leer rutas, acopiadores y polígonos para no duplicar la zona', async () => {
    const { component, rutasService, acopiadoresService } = crear();
    component.seleccionarZona('COLLANA I/YASIN');
    const renombrada = RUTAS.map((r) =>
      r.zona === 'COLLANA I/YASIN' ? { ...r, zona: 'COLLANA ALTA' } : r,
    );
    rutasService.listar.mockReturnValueOnce(pagina(renombrada));
    rutasService.poligonosDeZona.mockReturnValueOnce(
      of([{ id: 7, zona: 'COLLANA ALTA', color: '#123456', puntos: [] }]),
    );

    component.abrirRenombrarZona();
    component.nombreZonaEditado = 'COLLANA ALTA';
    await component.renombrarZona();

    expect(acopiadoresService.listar).toHaveBeenCalledTimes(2);
    expect(component.zonasDisponibles()).toEqual(['COLLANA ALTA', 'MORO VIEJO']);
    expect(component.zonaPoligonoSeleccionada()).toBe('COLLANA ALTA');
  });

  it('renombrar sin cambiar el nombre no llama al backend', async () => {
    const { component, rutasService } = crear();
    component.seleccionarZona('MORO VIEJO');

    component.abrirRenombrarZona();
    await component.renombrarZona();

    expect(rutasService.renombrarZona).not.toHaveBeenCalled();
  });

  it('eliminar la zona la envía por nombre y deja la pantalla sin zona seleccionada', async () => {
    const { component, rutasService } = crear();
    component.seleccionarZona('MORO VIEJO');

    await component.eliminarZona();

    expect(rutasService.eliminarZona).toHaveBeenCalledWith('MORO VIEJO');
    expect(component.zonaPoligonoSeleccionada()).toBeUndefined();
  });

  it('muestra el mensaje del backend cuando la zona todavía tiene rutas', async () => {
    const { component, rutasService } = crear();
    component.seleccionarZona('MORO VIEJO');
    rutasService.eliminarZona.mockReturnValueOnce(
      new (class {
        subscribe(obs: { error: (e: unknown) => void }) {
          obs.error({
            error: { message: 'La zona MORO VIEJO todavia tiene 1 ruta(s) activa(s).' },
          });
        }
      })() as never,
    );

    await component.eliminarZona();

    expect(component.errorZona()).toContain('todavia tiene 1 ruta(s) activa(s)');
    expect(component.zonaPoligonoSeleccionada()).toBe('MORO VIEJO');
  });
});

/**
 * Recorridos que los acopiadores pintan en la app (V27) y su promoción a predeterminado.
 *
 * El predeterminado es **por acopiador**, no por ruta: lo que estas pruebas fijan es que la pantalla
 * no lo confunda con la geometría de la ruta y que no ofrezca predeterminar lo que ya lo es.
 */
describe('RutasComponent · proveedores de la zona', () => {
  it('la lista de zonas cuenta los proveedores de cada zona, igual que los acopiadores', () => {
    const { component } = crear();

    const collana = component.zonasResumen().find((z) => z.zona === 'COLLANA I/YASIN')!;
    const moro = component.zonasResumen().find((z) => z.zona === 'MORO VIEJO')!;
    expect(collana.proveedores.map((p) => p.id).sort()).toEqual([20, 21, 22]);
    expect(moro.proveedores.map((p) => p.id)).toEqual([23]);
  });

  it('muestra los de la zona en el orden en que los visita el acopiador, los sin orden al final', () => {
    const { component } = crear();
    component.seleccionarZona('COLLANA I/YASIN');

    // "Ruta mañana" (1) antes que "Ruta tarde" (2); dentro de la ruta, por orden.
    expect(component.proveedoresDeZonaSeleccionada().map((p) => p.id)).toEqual([20, 22, 21]);
  });

  it('para traer solo ofrece a los de otras zonas o sin ruta', () => {
    const { component } = crear();
    component.seleccionarZona('COLLANA I/YASIN');

    expect(component.opcProveedoresFuera().map((o) => o.valor)).toEqual([23, 24]);
  });

  it('traer un proveedor manda solo su ruta y su orden, no la ficha completa', async () => {
    const { component, productoresService } = crear();
    component.seleccionarZona('COLLANA I/YASIN');
    component.abrirFormProveedor();
    component.proveedorExistenteId = 23;
    component.rutaProveedorZonaId = 2;
    component.ordenProveedorZona = 3;

    await component.traerProveedorAZona();

    expect(productoresService.asignarRuta).toHaveBeenCalledWith(23, 2, 3);
    expect(component.formProveedorAbierto()).toBe(false);
    expect(component.mensajeZona()).toContain('Dora');
  });

  it('sin proveedor elegido no envía nada y lo dice', async () => {
    const { component, productoresService } = crear();
    component.seleccionarZona('COLLANA I/YASIN');
    component.abrirFormProveedor();

    await component.traerProveedorAZona();

    expect(productoresService.asignarRuta).not.toHaveBeenCalled();
    expect(component.errorZona()).toContain('Selecciona el proveedor');
  });

  it('al cambiarlo de ruta pierde el orden, que era el de la ruta anterior', async () => {
    const { component, productoresService } = crear();
    component.seleccionarZona('COLLANA I/YASIN');

    await component.moverProveedorDeRuta(PROVEEDORES[0], 2);

    expect(productoresService.asignarRuta).toHaveBeenCalledWith(20, 2, null);
  });

  it('el orden se valida antes de enviarlo, y vacío significa "sin orden"', async () => {
    const { component, productoresService } = crear();
    component.seleccionarZona('COLLANA I/YASIN');

    await component.cambiarOrdenProveedor(PROVEEDORES[0], '0');
    expect(productoresService.asignarRuta).not.toHaveBeenCalled();
    expect(component.errorZona()).toContain('desde 1');

    await component.cambiarOrdenProveedor(PROVEEDORES[0], '');
    expect(productoresService.asignarRuta).toHaveBeenCalledWith(20, 1, null);
  });
});

describe('RutasComponent · rutas pintadas por el acopiador', () => {
  it('solo lista los recorridos de las rutas de la zona que se está mirando', () => {
    const { component } = crear();
    component.seleccionarZona('COLLANA I/YASIN');

    expect(component.trazadosDeZona().map((t) => t.id)).toEqual([501]);
  });

  it('superpone el recorrido pintado en el mapa y salta a la ruta de ese trazado', () => {
    const { component } = crear();
    component.seleccionarZona('COLLANA I/YASIN');

    component.alternarTrazadoEnMapa(PINTADOS[0]);

    expect(component.trazadoVistoId()).toBe(501);
    expect(component.rutaPuntosId()).toBe(1);
    // Va como línea aparte del trazado de la ruta: las dos tienen que verse a la vez para comparar.
    expect(component.trazadoSecundarioMapa().length).toBe(2);
  });

  it('volver a pulsar deja de superponerlo', () => {
    const { component } = crear();
    component.alternarTrazadoEnMapa(PINTADOS[0]);

    component.alternarTrazadoEnMapa(PINTADOS[0]);

    expect(component.trazadoVistoId()).toBeNull();
    expect(component.trazadoSecundarioMapa()).toEqual([]);
  });

  it('asignar como predeterminada lo marca para ese acopiador y no para los demás', async () => {
    const { component, trazadosService } = crear();
    trazadosService.predeterminados.mockReturnValue(
      of([
        {
          ...PINTADOS[0],
          id: 900,
          tipo: 'PREDETERMINADO' as const,
          fecha: undefined,
          origenTrazadoId: 501,
        },
      ]),
    );

    await component.predeterminarTrazado(PINTADOS[0]);

    expect(trazadosService.predeterminar).toHaveBeenCalledWith(501);
    expect(component.esElTrazadoPredeterminado(PINTADOS[0])).toBe(true);
    expect(component.predeterminadoDe(11)).toBeUndefined();
    expect(component.mensajeTrazado()).toContain('predeterminado');
  });

  it('quitar el predeterminado devuelve al acopiador al trazado de su ruta', async () => {
    const { component, trazadosService } = crear();
    trazadosService.predeterminados.mockReturnValue(
      of([
        {
          ...PINTADOS[0],
          id: 900,
          tipo: 'PREDETERMINADO' as const,
          fecha: undefined,
          origenTrazadoId: 501,
        },
      ]),
    );
    await component.predeterminarTrazado(PINTADOS[0]);

    await component.quitarPredeterminado(PINTADOS[0]);

    expect(trazadosService.quitarPredeterminado).toHaveBeenCalledWith(10);
    expect(component.predeterminadoDe(10)).toBeUndefined();
    expect(component.mensajeTrazado()).toContain('trazado de su ruta');
  });

  it('cambiar de fecha deja de superponer un recorrido que ya no está en la lista', () => {
    const { component, trazadosService } = crear();
    component.alternarTrazadoEnMapa(PINTADOS[0]);
    trazadosService.pintados.mockReturnValue(of([]));

    component.fechaTrazados = '2026-09-01';
    component.cargarTrazados();

    expect(component.trazadoVistoId()).toBeNull();
    expect(trazadosService.pintados).toHaveBeenLastCalledWith('2026-09-01');
  });

  /**
   * El backend exige la fecha para no devolver el historial completo, y el <input type="date"> se
   * puede dejar en blanco. Sin esta red, limpiar el campo mandaba una peticion que responde 400.
   */
  it('si se limpia la fecha, vuelve a hoy en vez de pedir sin fecha', () => {
    const { component, trazadosService } = crear();

    component.fechaTrazados = '';
    component.cargarTrazados();

    expect(component.fechaTrazados).not.toBe('');
    expect(trazadosService.pintados).toHaveBeenLastCalledWith(component.fechaTrazados);
  });

  /** La jornada la corta el servidor en Lima; pedir el día del navegador dejaba la tabla vacía. */
  it('la fecha por defecto es el día operativo de Lima', () => {
    const { component } = crear();
    const hoyLima = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/Lima',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(new Date());

    expect(component.fechaTrazados).toBe(hoyLima);
    expect(component.fecha).toBe(hoyLima);
  });

  it('muestra el mensaje del backend si no se puede predeterminar', async () => {
    const { component, trazadosService } = crear();
    trazadosService.predeterminar.mockReturnValueOnce(
      new (class {
        subscribe(obs: { error: (e: unknown) => void }) {
          obs.error({ error: { message: 'El recorrido pintado no tiene puntos suficientes.' } });
        }
      })() as never,
    );

    await component.predeterminarTrazado(PINTADOS[0]);

    expect(component.errorTrazado()).toContain('puntos suficientes');
  });
});

/** El encuadre del mapa del paso 3: cuándo debe reencuadrar y cuándo no. */
describe('RutasComponent · encuadre del mapa al comparar trazados', () => {
  it('cambia la clave de encuadre al superponer un recorrido, para que el mapa lo enseñe', () => {
    const { component } = crear();
    component.seleccionarZona('COLLANA I/YASIN');
    const antes = component.claveEncuadreTrazado();

    component.alternarTrazadoEnMapa(PINTADOS[0]);

    expect(component.claveEncuadreTrazado()).not.toBe(antes);
    expect(component.claveEncuadreTrazado()).toContain('pintado:501');
  });

  it('la clave no depende de los puntos, así que dibujar no reencuadra', () => {
    const { component } = crear();
    component.seleccionarRutaPuntos(1);
    const antes = component.claveEncuadreTrazado();

    component.puntosRuta.set([
      { secuencia: 0, latitud: -15.6, longitud: -69.9 },
      { secuencia: 1, latitud: -15.7, longitud: -69.8 },
    ]);

    expect(component.claveEncuadreTrazado()).toBe(antes);
  });
});

/**
 * Pestaña "Puntos establecidos" (pedido del cliente, Opción B1): el CRUD en sí es del backend
 * (PuntoEstablecidoServiceTests), lo que se fija aquí es el pegamento de la pantalla — la
 * validación de rango antes de llamar al servicio y qué hoja descarga "Exportar Excel" según la
 * pestaña activa.
 */
describe('RutasComponent · puntos establecidos', () => {
  it('rechaza guardar sin nombre, sin tocar el backend', async () => {
    const { component, puntosEstablecidosService } = crear();
    component.activarModoAgregarPuntoEstablecido();
    component.formPuntoEstablecido.latitud = -15.65;
    component.formPuntoEstablecido.longitud = -69.98;
    component.formPuntoEstablecido.rutaId = 1;

    await component.guardarPuntoEstablecido();

    expect(component.errorPuntoEstablecido()).toContain('nombre');
    expect(puntosEstablecidosService.crear).not.toHaveBeenCalled();
  });

  it('rechaza una latitud fuera de rango, sin tocar el backend', async () => {
    const { component, puntosEstablecidosService } = crear();
    component.activarModoAgregarPuntoEstablecido();
    component.formPuntoEstablecido.nombre = 'Cruce principal';
    component.formPuntoEstablecido.rutaId = 1;
    component.formPuntoEstablecido.latitud = 95;
    component.formPuntoEstablecido.longitud = -69.98;

    await component.guardarPuntoEstablecido();

    expect(component.errorPuntoEstablecido()).toContain('válidas');
    expect(puntosEstablecidosService.crear).not.toHaveBeenCalled();
  });

  it('el clic en el mapa autocompleta latitud y longitud mientras se está agregando', () => {
    const { component } = crear();
    component.activarModoAgregarPuntoEstablecido();

    component.clicMapaPuntoEstablecido({ latitud: -15.7, longitud: -70.0 });

    expect(component.formPuntoEstablecido.latitud).toBe(-15.7);
    expect(component.formPuntoEstablecido.longitud).toBe(-70.0);
  });

  it('un clic en el mapa fuera del modo agregar no toca el formulario', () => {
    const { component } = crear();

    component.clicMapaPuntoEstablecido({ latitud: -15.7, longitud: -70.0 });

    expect(component.formPuntoEstablecido.latitud).toBeNull();
  });

  it('crea el punto con datos válidos y recarga la lista', async () => {
    const { component, puntosEstablecidosService } = crear();
    component.activarModoAgregarPuntoEstablecido();
    component.formPuntoEstablecido.nombre = 'Cruce principal';
    component.formPuntoEstablecido.rutaId = 1;
    component.formPuntoEstablecido.latitud = -15.65;
    component.formPuntoEstablecido.longitud = -69.98;

    await component.guardarPuntoEstablecido();

    expect(puntosEstablecidosService.crear).toHaveBeenCalledWith(
      expect.objectContaining({ nombre: 'Cruce principal', rutaId: 1 }),
    );
    expect(component.agregandoModoPE()).toBe(false);
  });

  it('editar precarga el formulario con los datos del punto', () => {
    const { component } = crear();

    component.editarPuntoEstablecido({
      id: 5,
      nombre: 'Punto X',
      latitud: -15.6,
      longitud: -69.9,
      rutaId: 1,
      rutaNombre: 'Ruta mañana',
      activo: true,
    });

    expect(component.editandoPuntoEstablecidoId()).toBe(5);
    expect(component.formPuntoEstablecido.nombre).toBe('Punto X');
    expect(component.agregandoModoPE()).toBe(true);
  });

  it('exportar descarga la hoja "PuntosEstablecidos" cuando esa es la pestaña activa', () => {
    const { component, exportacionesService } = crear();
    component.tab.set('puntos');

    component.exportar();

    expect(exportacionesService.descargar).toHaveBeenCalledWith('PuntosEstablecidos');
  });

  it('exportar sigue descargando "Rutas" en las demás pestañas', () => {
    const { component, exportacionesService } = crear();
    component.tab.set('gps');

    component.exportar();

    expect(exportacionesService.descargar).toHaveBeenCalledWith('Rutas');
  });
});

describe('RutasComponent · unificación y modos de mapa', () => {
  it('el mapa es editable de inmediato en modo Vivienda sin necesidad de alternar de modo', () => {
    const { component } = crear();
    component.setModoMapa('vivienda');

    expect(component.mapaEsEditable()).toBe(true);
  });

  it('un clic en el mapa con modo Vivienda activo registra las coordenadas de inmediato', () => {
    const { component } = crear();
    component.seleccionarZona('COLLANA I/YASIN', 3);
    component.setModoMapa('vivienda');

    component.clicEnElMapa({ latitud: -15.654, longitud: -69.987 });

    expect(component.agregandoModoPE()).toBe(true);
    expect(component.formPuntoEstablecido.latitud).toBe(-15.654);
    expect(component.formPuntoEstablecido.longitud).toBe(-69.987);
    expect(component.puntoDraftMapa().length).toBe(1);
    expect(component.puntoDraftMapa()[0].lat).toBe(-15.654);
  });

  it('un clic en el mapa con modo Acopio activo captura las coordenadas de acopio dinámico', () => {
    const { component } = crear();
    component.setModoMapa('acopio');

    component.clicEnElMapa({ latitud: -15.62, longitud: -69.95 });

    expect(component.nuevoPuntoLat).toBe(-15.62);
    expect(component.nuevoPuntoLng).toBe(-69.95);
  });

  it('al cambiar tipo de ubicación a CASA se activa el modo Vivienda', () => {
    const { component } = crear();
    component.setModoMapa('ruta');

    component.alCambiarTipoUbicacion('CASA');

    expect(component.modoMapa()).toBe('vivienda');
  });
});

describe('RutasComponent · protección contra accidentes', () => {
  // Pedido del cliente (2026-10-25): borró un límite sin querer. Ahora borrar exige escribir el
  // nombre, todo cambio pide aceptar y cancelar no toca nada.
  const LIMITE = {
    id: 9,
    zona: 'MORO VIEJO',
    color: '#437862',
    puntos: [
      { secuencia: 1, latitud: -15.6, longitud: -70.0 },
      { secuencia: 2, latitud: -15.7, longitud: -70.1 },
      { secuencia: 3, latitud: -15.8, longitud: -70.0 },
    ],
  };

  it('borrar el límite no hace nada si el nombre escrito no coincide', async () => {
    const { component, rutasService } = crear();
    rutasService.eliminarPoligonoDeZona.mockReturnValue(of(undefined));
    component.zonaPoligonos.set([LIMITE]);
    component.seleccionarZona('MORO VIEJO');
    const dialogo = TestBed.inject(DialogoService) as unknown as {
      pedirTexto: ReturnType<typeof vi.fn>;
    };

    dialogo.pedirTexto.mockResolvedValueOnce('MORO');
    await component.eliminarPoligonoActual();
    expect(rutasService.eliminarPoligonoDeZona).not.toHaveBeenCalled();

    dialogo.pedirTexto.mockResolvedValueOnce(null);
    await component.eliminarPoligonoActual();
    expect(rutasService.eliminarPoligonoDeZona).not.toHaveBeenCalled();

    dialogo.pedirTexto.mockResolvedValueOnce(' moro viejo ');
    await component.eliminarPoligonoActual();
    expect(rutasService.eliminarPoligonoDeZona).toHaveBeenCalledWith(9);
  });

  it('si no se acepta el cambio de ruta de un proveedor, no se envía nada', async () => {
    const { component, productoresService } = crear();
    component.seleccionarZona('COLLANA I/YASIN');
    const dialogo = TestBed.inject(DialogoService) as unknown as {
      confirmar: ReturnType<typeof vi.fn>;
    };
    dialogo.confirmar.mockResolvedValueOnce(false);

    await component.moverProveedorDeRuta(PROVEEDORES[0], 2);

    expect(productoresService.asignarRuta).not.toHaveBeenCalled();
  });

  it('guardar el límite pide aceptar y, si se cancela, no lo envía', async () => {
    const { component, rutasService } = crear();
    component.zonaPoligonos.set([LIMITE]);
    component.seleccionarZona('MORO VIEJO');
    const dialogo = TestBed.inject(DialogoService) as unknown as {
      confirmar: ReturnType<typeof vi.fn>;
    };
    dialogo.confirmar.mockResolvedValueOnce(false);

    await component.guardarPoligono();

    expect(rutasService.guardarPoligonoDeZona).not.toHaveBeenCalled();
    expect(dialogo.confirmar).toHaveBeenCalledWith(
      expect.objectContaining({ titulo: '¿Guardar el límite de «MORO VIEJO»?' }),
    );
  });

  it('trazar el recorrido ya no guarda en cada clic: se guarda una vez al confirmar', async () => {
    const { component, rutasService } = crear();
    component.seleccionarZona('COLLANA I/YASIN', 3);
    component.puntosCargados.set(true);
    component.pintandoRuta.set(true);

    await component.clicMapaGestion({ latitud: -15.61, longitud: -69.97 });
    await component.clicMapaGestion({ latitud: -15.62, longitud: -69.98 });
    expect(component.puntosRuta()).toHaveLength(2);
    expect(rutasService.guardarPuntos).not.toHaveBeenCalled();

    await component.guardarPuntosRuta();
    expect(rutasService.guardarPuntos).toHaveBeenCalledTimes(1);
  });

  it('cambiar de ruta con un dibujo a medio hacer pregunta antes y, si no se acepta, se queda', async () => {
    const { component } = crear();
    component.seleccionarZona('COLLANA I/YASIN', 1);
    component.pintandoPoligono.set(true);
    const dialogo = TestBed.inject(DialogoService) as unknown as {
      confirmar: ReturnType<typeof vi.fn>;
    };
    dialogo.confirmar.mockResolvedValueOnce(false);

    component.elegirRuta(RUTAS[2]);
    await Promise.resolve();
    await Promise.resolve();

    expect(component.zonaPoligonoSeleccionada()).toBe('COLLANA I/YASIN');
    expect(component.pintandoPoligono()).toBe(true);
  });
});

describe('RutasComponent · recorrido desde "Rutas y personas"', () => {
  it('el icono de una ruta abre su recorrido y cambiar de zona lo cierra', () => {
    const { component } = crear();
    component.tab.set('territorio');
    component.seleccionarZona('COLLANA I/YASIN', 2);

    component.abrirRecorrido(RUTAS[1]);
    expect(component.recorridoAbiertoId()).toBe(2);
    expect(component.rutaPuntosId()).toBe(2);
    expect(component.seccion()).toBe('personas');

    component.seleccionarZona('MORO VIEJO', 2);
    expect(component.recorridoAbiertoId()).toBeNull();
  });
});

describe('RutasComponent · límite desde "Rutas y personas"', () => {
  it('el icono de la zona abre su límite ahí mismo y los clics dibujan el contorno', async () => {
    const { component } = crear();
    component.tab.set('territorio');
    component.seleccionarZona('COLLANA I/YASIN', 2);
    component.cancelarFormPuntoEstablecido();

    component.abrirLimite();
    expect(component.seccion()).toBe('personas');
    expect(component.limiteAbierto()).toBe(true);

    const antes = component.poligonoPuntos().length;
    component.pintandoPoligono.set(true);
    await component.clicMapaGestion({ latitud: -15.61, longitud: -69.97 });
    expect(component.poligonoPuntos()).toHaveLength(antes + 1);

    // Abrir el recorrido de una ruta cierra el límite (un panel a la vez).
    component.pintandoPoligono.set(false);
    component.abrirRecorrido(RUTAS[0]);
    expect(component.limiteAbierto()).toBe(false);
  });
});
