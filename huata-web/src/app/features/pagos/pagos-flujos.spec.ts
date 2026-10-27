import { provideRouter } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from '../../core/config/api-config';
import { Liquidacion } from '../../core/models/models';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { AuthService } from '../../core/services/auth.service';
import { FilaPlanilla } from '../../core/services/liquidaciones.service';
import { PagosComponent } from './pagos.component';

const URL = API_BASE_URL + '/liquidaciones';

function fila(cambios: Partial<FilaPlanilla> = {}): FilaPlanilla {
  return {
    proveedorId: 1, proveedorCodigo: 'PR-001', proveedorNombre: 'Ana Apaza', proveedorApellidos: 'Apaza',
    zona: 'FAON', excelencia: false, litrosEntregados: 50, litrosRechazados: 0, litrosPagados: 0,
    litrosPendientes: 50, tarifaAplicada: 2.4, importePendiente: 120, liquidado: false,
    liquidacionId: null, tieneLiquidacionCerrada: false, ...cambios,
  };
}

function liquidacion(cambios: Partial<Liquidacion> = {}): Liquidacion {
  return {
    id: 10, productorId: 1, productorNombre: 'Ana Apaza', periodoInicio: '2025-04-10', periodoFin: '2025-04-16',
    litrosTotales: 50, montoTotal: 120, ajustesTotal: 0, montoNeto: 120, estado: 'CALCULATED',
    detalle: [{ recojoId: 1, fechaHora: '2025-04-10T12:00:00Z', litros: 50, precioAplicado: 2.4, subtotal: 120 }],
    ajustes: [], ...cambios,
  };
}

describe('Pagos: confirmación, respuestas y comprobantes', () => {
  let fixture: ComponentFixture<PagosComponent>;
  let c: PagosComponent;
  let http: HttpTestingController;
  let dialogo: DialogoService;
  let avisos: Pick<NotificacionesService, 'exito' | 'info' | 'error'>;
  const decodeOriginal = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, 'decode');

  beforeEach(() => {
    avisos = { exito: vi.fn(), info: vi.fn(), error: vi.fn() };
    TestBed.configureTestingModule({
      imports: [PagosComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: AuthService, useValue: { puede: () => true } },
        { provide: NotificacionesService, useValue: avisos },
      ],
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.spyOn(window, 'alert').mockImplementation(() => {});
    vi.spyOn(window, 'print').mockImplementation(() => {});
    Object.defineProperty(HTMLImageElement.prototype, 'decode', {
      configurable: true, value: vi.fn().mockResolvedValue(undefined),
    });
    fixture = TestBed.createComponent(PagosComponent);
    c = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    dialogo = TestBed.inject(DialogoService);
    fixture.detectChanges();
    flushPlanilla();
    flushProveedores();
    fixture.detectChanges();
  });

  afterEach(() => {
    if (!fixture.componentRef.hostView.destroyed) fixture.destroy();
    http.verify();
    vi.restoreAllMocks();
    if (decodeOriginal) Object.defineProperty(HTMLImageElement.prototype, 'decode', decodeOriginal);
    else delete (HTMLImageElement.prototype as Partial<HTMLImageElement>).decode;
    document.body.classList.remove('print-worker', 'print-worker-lote');
    document.getElementById('boleta-print-page')?.remove();
  });

  function flushPlanilla(filas: FilaPlanilla[] = []): void {
    http.expectOne((r) => r.method === 'GET' && r.url === URL + '/planilla').flush(filas);
  }

  /** El buscador de proveedores carga el padrón completo al abrir la pantalla. */
  function flushProveedores(): void {
    http.expectOne((r) => r.method === 'GET' && r.url === API_BASE_URL + '/productores')
      .flush({ content: [], page: 0, size: 0, totalElements: 0, totalPages: 0 });
  }

  async function aceptarDialogo(): Promise<void> {
    dialogo.responder(true);
    // confirmar transforma la respuesta en una promesa antes de que continúe la acción.
    await Promise.resolve();
    await Promise.resolve();
  }

  async function esperarImpresion(): Promise<void> {
    await new Promise<void>((resolver) => setTimeout(resolver, 0));
  }

  function comprobarLimpieza(): void {
    expect(document.body.classList.contains('print-worker')).toBe(false);
    expect(document.body.classList.contains('print-worker-lote')).toBe(false);
    expect(document.getElementById('boleta-print-page')).toBeNull();
    expect(c.boletasParaImprimir()).toEqual([]);
    expect(c.imprimiendo()).toBe(false);
    expect(c.imprimiendoTodo()).toBe(false);
  }

  it('permite cancelar el diálogo de pago sin PATCH ni ventanas nativas', async () => {
    c.liquidacionSeleccionada.set(liquidacion());
    const cierre = c.cerrarSeleccionada();

    expect(dialogo.actual()?.opciones.titulo).toBe('Confirmar pago al proveedor');
    expect(c.pagandoLiquidacion()).toBe(true);
    http.expectNone((r) => r.method === 'PATCH');
    dialogo.responder(false);
    await cierre;

    http.expectNone((r) => r.method === 'PATCH');
    expect(c.pagandoLiquidacion()).toBe(false);
    expect(c.liquidacionSeleccionada()?.estado).toBe('CALCULATED');
    expect(avisos.exito).not.toHaveBeenCalled();
    expect(window.confirm).not.toHaveBeenCalled();
    expect(window.alert).not.toHaveBeenCalled();
  });

  it('un doble clic abre un solo diálogo y registra un solo pago', async () => {
    c.liquidacionSeleccionada.set(liquidacion());
    const confirmar = vi.spyOn(dialogo, 'confirmar');
    const primero = c.cerrarSeleccionada();
    const segundo = c.cerrarSeleccionada();
    expect(confirmar).toHaveBeenCalledTimes(1);

    await aceptarDialogo();
    const pago = http.expectOne(URL + '/10/estado');
    expect(pago.request.method).toBe('PATCH');
    expect(pago.request.body).toEqual({ estado: 'CLOSED' });
    pago.flush(liquidacion({ estado: 'CLOSED' }));
    await Promise.all([primero, segundo]);
    flushPlanilla([fila({ liquidado: true, tieneLiquidacionCerrada: true, liquidacionId: 10 })]);

    http.expectNone((r) => r.method === 'PATCH');
    expect(confirmar).toHaveBeenCalledTimes(1);
    expect(c.liquidacionSeleccionada()?.estado).toBe('CLOSED');
    expect(c.pagandoLiquidacion()).toBe(false);
    expect(avisos.exito).toHaveBeenCalledTimes(1);
  });

  it('un fallo 500 conserva la liquidación, libera el botón y oculta la URL técnica', async () => {
    c.liquidacionSeleccionada.set(liquidacion());
    const cierre = c.cerrarSeleccionada();
    await aceptarDialogo();
    http.expectOne(URL + '/10/estado').flush(
      { message: 'PATCH http://localhost:8080/api/v1/liquidaciones/10/estado: SQLException' },
      { status: 500, statusText: 'Internal Server Error' },
    );
    await cierre;
    fixture.detectChanges();

    expect(c.pagandoLiquidacion()).toBe(false);
    expect(c.liquidacionSeleccionada()?.estado).toBe('CALCULATED');
    expect(c.error()).toBe('No se pudo registrar el pago. Inténtalo de nuevo.');
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).not.toContain('localhost');
    expect(avisos.exito).not.toHaveBeenCalled();
    expect(window.alert).not.toHaveBeenCalled();
  });

  it('al seleccionar otro proveedor borra el código y descarta la liquidación anterior atrasada', () => {
    const primera = fila({ liquidado: true, tieneLiquidacionCerrada: true, liquidacionId: 10 });
    const segunda = fila({ proveedorId: 2, liquidado: true, tieneLiquidacionCerrada: true, liquidacionId: 11 });
    c.codigoEnviadoA.set('sec***@huata.pe');
    c.codigoReapertura = '123456';
    c.seleccionar(primera);
    const atrasada = http.expectOne(URL + '/10');
    expect(c.codigoEnviadoA()).toBeNull();
    expect(c.codigoReapertura).toBe('');

    c.seleccionar(segunda);
    const vigente = liquidacion({ id: 11, productorId: 2, estado: 'CLOSED' });
    http.expectOne(URL + '/11').flush(vigente);
    atrasada.flush(liquidacion({ estado: 'CLOSED' }));

    expect(c.filaSeleccionada()?.proveedorId).toBe(2);
    expect(c.liquidacionSeleccionada()).toEqual(vigente);
    expect(c.cargandoLiquidacion()).toBe(false);
  });

  it('calcular usa directamente la respuesta del POST sin consultar de nuevo la liquidación', () => {
    c.seleccionar(fila());
    c.calcular();
    const calculo = http.expectOne(URL + '/calcular');
    expect(calculo.request.method).toBe('POST');
    expect(calculo.request.body).toEqual({
      productorId: 1, periodoInicio: c.cicloSeleccionado.inicio, periodoFin: c.cicloSeleccionado.fin,
    });
    const calculada = liquidacion({
      periodoInicio: c.cicloSeleccionado.inicio, periodoFin: c.cicloSeleccionado.fin,
    });
    calculo.flush(calculada);

    expect(c.liquidacionSeleccionada()).toEqual(calculada);
    expect(c.cargandoLiquidacion()).toBe(false);
    http.expectNone((r) => r.method === 'GET');
  });

  it.each(['proveedor', 'ciclo'] as const)('descarta el cálculo atrasado al cambiar de %s', (cambio) => {
    c.seleccionar(fila());
    c.calcular();
    const atrasado = http.expectOne(URL + '/calcular');
    let actual = fila({ proveedorId: 2 });
    if (cambio === 'ciclo') {
      c.cambiarCiclo(c.ciclos[1]);
      actual = fila();
      flushPlanilla([actual]);
    }
    c.seleccionar(actual);
    c.calcular();
    http.expectNone((r) => r.method === 'POST' && r.url === URL + '/calcular');
    atrasado.flush(liquidacion());

    expect(c.liquidacionSeleccionada()).toBeNull();
    expect(c.calculandoLiquidacion()).toBe(false);
    c.calcular();
    const vigente = http.expectOne(URL + '/calcular');
    expect(c.cargandoLiquidacion()).toBe(true);
    const calculada = liquidacion({
      id: 11, productorId: actual.proveedorId,
      periodoInicio: c.cicloSeleccionado.inicio, periodoFin: c.cicloSeleccionado.fin,
    });
    vigente.flush(calculada);
    expect(c.liquidacionSeleccionada()).toEqual(calculada);
    expect(c.cargandoLiquidacion()).toBe(false);
  });

  it('un cálculo pendiente permite ir a global pero bloquea el cierre hasta que termine', async () => {
    const proveedor = fila();
    c.planilla.set([proveedor]);
    c.seleccionar(proveedor);
    c.calcular();
    const pendiente = http.expectOne(URL + '/calcular');
    c.cambiarTab('global');
    await c.pagarATodos();

    expect(c.tab).toBe('global');
    expect(c.calculandoLiquidacion()).toBe(true);
    expect(c.escrituraEnCurso()).toBe(true);
    expect(dialogo.actual()).toBeNull();
    http.expectNone((r) => r.method === 'POST' && r.url === URL + '/cerrar-ciclo');
    pendiente.flush(liquidacion({
      periodoInicio: c.cicloSeleccionado.inicio, periodoFin: c.cicloSeleccionado.fin,
    }));

    expect(c.calculandoLiquidacion()).toBe(false);
    expect(c.escrituraEnCurso()).toBe(false);
    const pago = c.pagarATodos();
    expect(dialogo.actual()?.opciones.titulo).toBe('Confirmar pago a 1 proveedor(es)');
    dialogo.responder(false);
    await pago;
    http.expectNone((r) => r.method === 'POST' && r.url === URL + '/cerrar-ciclo');
  });

  it('una planilla atrasada no reemplaza las filas del ciclo que está seleccionado', () => {
    c.cargarPlanilla();
    const atrasada = http.expectOne((r) => r.url === URL + '/planilla');
    c.cambiarCiclo(c.ciclos[1]);
    const actual = fila({ proveedorId: 2 });
    flushPlanilla([actual]);
    atrasada.flush([fila()]);

    expect(c.planilla()).toEqual([actual]);
    expect(c.cargandoPlanilla()).toBe(false);
  });

  it('pago global confirma solo los abiertos y presenta su importe como bruto estimado', async () => {
    c.planilla.set([
      fila({ importePendiente: 100 }),
      fila({ proveedorId: 2, importePendiente: 999, tieneLiquidacionCerrada: true, liquidacionId: 20 }),
      fila({ proveedorId: 3, liquidado: true, importePendiente: 80, tieneLiquidacionCerrada: true, liquidacionId: 30 }),
    ]);
    expect(c.pendientesParaPagar().map((f) => f.proveedorId)).toEqual([1]);
    expect(c.pendientesReaperturaCount()).toBe(1);
    const pago = c.pagarATodos();

    expect(dialogo.actual()?.opciones.titulo).toBe('Confirmar pago a 1 proveedor(es)');
    expect(dialogo.actual()?.opciones.mensaje).toContain('Importe bruto estimado: S/ 100.00');
    expect(dialogo.actual()?.opciones.mensaje).toContain('El neto final incluye los descuentos y bonificaciones');
    dialogo.responder(false);
    await pago;
    http.expectNone((r) => r.method === 'POST');
    expect(c.cerrandoCiclo()).toBe(false);
  });

  it('no vuelve a cerrar pagos con nuevas entregas que requieren reapertura', async () => {
    c.planilla.set([fila({ tieneLiquidacionCerrada: true, liquidacionId: 10, importePendiente: 30 })]);
    await c.pagarATodos();

    expect(dialogo.actual()).toBeNull();
    http.expectNone((r) => r.method === 'POST');
    expect(avisos.info).toHaveBeenCalledWith(expect.stringContaining('requieren reapertura'));
  });

  it('en pago único se busca por proveedor y se selecciona sin campos de fecha Desde/Hasta', async () => {
    c.planilla.set([fila(), fila({ proveedorId: 2, proveedorNombre: 'Luis Mamani', proveedorApellidos: 'Mamani' })]);
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const entrada = el.querySelector('input[aria-label="Nombre del proveedor"]') as HTMLInputElement;
    expect(entrada).not.toBeNull();
    expect(el.querySelector('.payment-filters input[type="date"]')).toBeNull();
    entrada.value = 'Mamani';
    entrada.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const opciones = el.querySelectorAll('.payment-provider-field [role="option"]');
    expect(opciones).toHaveLength(1);
    opciones[0].dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));

    expect(c.filaSeleccionada()?.proveedorId).toBe(2);
    http.expectNone((r) => r.method === 'POST');
  });

  it('Imprimir todo con un pago prepara una sola boleta con sus fechas, tarifa y ajustes reales', async () => {
    c.planilla.set([fila({ liquidado: true, tieneLiquidacionCerrada: true, liquidacionId: 10, tarifaAplicada: 9.9 })]);
    c.cambiarTab('global');
    c.imprimirTodo();
    const guardada = liquidacion({
      estado: 'CLOSED', litrosTotales: 100, montoTotal: 52, ajustesTotal: 10, montoNeto: 62,
      detalle: [
        { recojoId: 1, fechaHora: '2025-04-10T12:00:00Z', litros: 20, precioAplicado: 2.6, subtotal: 52 },
        { recojoId: 2, fechaHora: '2025-04-11T12:00:00Z', litros: 80, precioAplicado: 0, subtotal: 0 },
      ],
      ajustes: [
        { id: 1, tipo: 'PENALIDAD', monto: 20, montoConSigno: -20 },
        { id: 2, tipo: 'BONIFICACION', monto: 30, montoConSigno: 30 },
      ],
    });
    http.expectOne(URL + '/10').flush(guardada);
    fixture.detectChanges();
    await esperarImpresion();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.payment-receipt')).toHaveLength(1);
    const boleta = el.querySelector('#workerReceipt') as HTMLElement;
    expect(document.body.classList.contains('print-worker')).toBe(true);
    expect(document.body.classList.contains('print-worker-lote')).toBe(false);
    expect(document.getElementById('boleta-print-page')).not.toBeNull();
    const fechas = Array.from(boleta.querySelectorAll('.worker-meta-item b')).map((item) => item.textContent?.trim());
    expect(fechas.slice(1, 3)).toEqual(['10/04/2025', '16/04/2025']);
    const valor = (etiqueta: string) => Array.from(boleta.querySelectorAll('.worker-info-row'))
      .find((row) => row.querySelector('span')?.textContent?.trim() === etiqueta)?.querySelector('strong')?.textContent?.trim();
    expect(valor('TARIFA APLICADA')).toBe('S/ 2.60');
    expect(valor('DESCUENTOS')).toBe('− S/ 20.00');
    expect(valor('BONIFICACIONES')).toBe('+ S/ 30.00');
    expect(boleta.querySelector('.worker-amount-card strong')?.textContent?.trim()).toBe('S/ 62.00');
    expect(boleta.querySelector('.worker-day small')?.textContent?.trim()).toBe('10/04');
    expect(window.print).toHaveBeenCalledTimes(1);

    window.dispatchEvent(new Event('afterprint'));
    comprobarLimpieza();
  });

  it('destruir el componente limpia la impresión pendiente y evita abrir el diálogo nativo', async () => {
    c.filaSeleccionada.set(fila());
    c.liquidacionSeleccionada.set(liquidacion());
    c.imprimirBoleta();
    expect(document.getElementById('boleta-print-page')).not.toBeNull();
    fixture.destroy();
    comprobarLimpieza();
    await esperarImpresion();

    expect(window.print).not.toHaveBeenCalled();
  });

  it('el historial obtiene el comprobante completo antes de imprimir y conserva su periodo', async () => {
    const resumen = liquidacion({ id: 31, productorId: 2, estado: 'CLOSED', detalle: [], ajustes: [] });
    c.filaSeleccionada.set(fila());
    c.liquidacionSeleccionada.set(liquidacion({
      id: 99, periodoInicio: c.cicloSeleccionado.inicio, periodoFin: c.cicloSeleccionado.fin,
    }));
    c.imprimirHistorial(resumen);
    const lectura = http.expectOne(URL + '/31');
    expect(lectura.request.method).toBe('GET');
    expect(c.boletasParaImprimir()).toEqual([]);
    expect(window.print).not.toHaveBeenCalled();
    const completa = liquidacion({ id: 31, productorId: 2, estado: 'CLOSED' });
    lectura.flush(completa);
    fixture.detectChanges();
    await esperarImpresion();

    expect(c.boletasParaImprimir()[0].liquidacion).toEqual(completa);
    const boleta = fixture.nativeElement.querySelector('.payment-receipt') as HTMLElement;
    expect(boleta.textContent).toContain('10/04/2025');
    expect(boleta.textContent).toContain('16/04/2025');
    expect(boleta.querySelector('.worker-day strong')?.textContent?.trim()).toBe('50.0 L');
    expect(window.print).toHaveBeenCalledTimes(1);
  });
});
