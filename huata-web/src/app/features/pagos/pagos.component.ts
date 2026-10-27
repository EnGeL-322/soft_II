import { DecimalPipe } from '@angular/common';
import { BuscadorComponent, OpcionBuscador } from '../../shared/ui/buscador.component';
import { ChangeDetectorRef, Component, DestroyRef, OnDestroy, OnInit, signal, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, firstValueFrom, forkJoin, map } from 'rxjs';
import { PERMISO } from '../../core/config/navegacion';
import { Ciclo, ciclosRecientes } from '../../core/config/ciclos';
import { AuthService } from '../../core/services/auth.service';
import {
  FilaPlanilla,
  LiquidacionesService,
} from '../../core/services/liquidaciones.service';
import { Liquidacion, Productor, TipoAjuste } from '../../core/models/models';
import { ProductoresService } from '../../core/services/productores.service';
import { paginaDisponible } from '../../shared/ui/paginacion-local';
import { PaginadorComponent, TAMANO_PAGINA_PREDETERMINADO } from '../../shared/ui/paginador.component';
import { PaginacionLocal } from '../../shared/ui/paginacion-local';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { mensajeDeError } from '../../core/notificaciones/mensaje-error';
import { DiaBoleta, bonificacionesDeBoleta, descuentosDeBoleta, diasDeBoleta, fechaDeBoleta, litrosAdicionalesDeBoleta, tarifaDeBoleta } from './boleta-pago';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';

export type { DiaBoleta } from './boleta-pago';

interface BoletaImpresion {
  liquidacion: Liquidacion;
  fila: FilaPlanilla | null;
}

type Tab = 'unico' | 'global' | 'historial';

/** Orden de impresión de "Imprimir todo": alfabético por apellido (RF-31 no exige otro orden). */
export function ordenarPorApellido<T extends { fila: { proveedorApellidos: string } }>(items: T[]): T[] {
  return [...items].sort((a, b) => a.fila.proveedorApellidos.localeCompare(b.fila.proveedorApellidos, 'es'));
}

/**
 * Pagos y liquidaciones (RF-25 a RF-31): planilla del ciclo, pago único por proveedor, pago global
 * ("Pagar a todos") e historial de liquidaciones cerradas. Cada cierre genera su asiento contable
 * automáticamente (ver Contabilidad > Libro diario, origen "Pago de liquidación").
 */
@Component({
  selector: 'app-pagos',
  standalone: true,
  imports: [PaginadorComponent, FormsModule, DecimalPipe, BuscadorComponent],
  templateUrl: './pagos.component.html',
  styleUrl: './pagos.component.css',
  styles: [
    '@media print { :host-context(body.print-worker) .contenido-normal, :host-context(body.print-worker-lote) .contenido-normal { display: none !important; } }',
  ],
})
export class PagosComponent implements OnInit, OnDestroy {
  /** Tiempo real: si alguien guarda algo de pagos, acopio, calidad, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['pagos', 'acopio', 'calidad'], () => {
    // Un pago, cierre o cálculo en curso no se interrumpe: al terminar, la pantalla ya recarga.
    if (this.escrituraEnCurso()) return;
    this.cargarPlanilla();
    if (this.tab === 'historial') this.cargarHistorial();
  });
  protected readonly notificaciones = inject(NotificacionesService);
  private readonly dialogo = inject(DialogoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private solicitudPlanilla = 0;
  private solicitudLiquidacion = 0;
  private solicitudHistorial = 0;
  private destruido = false;
  private limpiarImpresion: (() => void) | null = null;
  readonly ciclos: Ciclo[] = ciclosRecientes(26);
  cicloSeleccionado = this.ciclos[0];

  tab: Tab = 'unico';

  readonly planilla = signal<FilaPlanilla[]>([]);
  readonly pagPlanillaGlobal = new PaginacionLocal();
  readonly pagPlanillaUnico = new PaginacionLocal();
  readonly cargandoPlanilla = signal(false);
  readonly filaSeleccionada = signal<FilaPlanilla | null>(null);
  readonly liquidacionSeleccionada = signal<Liquidacion | null>(null);
  readonly cargandoLiquidacion = signal(false);
  readonly calculandoLiquidacion = signal(false);
  readonly cerrandoCiclo = signal(false);
  readonly pagandoLiquidacion = signal(false);
  readonly guardandoAjuste = signal(false);

  readonly historial = signal<Liquidacion[]>([]);
  /** Paginación del historial de pagos (servidor). */
  readonly historialTotal = signal(0);
  historialPagina = 0;
  historialTamano: number = TAMANO_PAGINA_PREDETERMINADO;
  readonly cargandoHistorial = signal(false);
  historialProductorId: number | null = null;

  /** proveedorId cuya boleta se está buscando para imprimir desde "Pago global" (una por una). */
  readonly imprimiendoFila = signal<number | null>(null);

  /**
   * Boletas actualmente en el DOM listas para imprimir: SIEMPRE la misma plantilla (el @for de
   * pagos.component.html), con un elemento para el flujo individual (Pago único, o "Imprimir
   * boleta" de una fila de Pago global) y N para "Imprimir todo". Nunca hay dos versiones del
   * diseño de boleta, solo cambia cuántas hay en el arreglo.
   */
  readonly boletasParaImprimir = signal<BoletaImpresion[]>([]);
  readonly imprimiendoTodo = signal(false);
  readonly imprimiendo = signal(false);
  readonly fechaImpresion = signal('');

  ajusteTipo: TipoAjuste = 'ADELANTO';
  ajusteMonto: number | null = null;
  ajusteMotivo = '';

  // ---- Reapertura de un pago cerrado (código de 6 dígitos por correo) ----
  readonly pidiendoCodigoReapertura = signal(false);
  readonly reabriendo = signal(false);
  /** Correo enmascarado ("sec***@huata.pe") a donde se mandó el código; null = aún no se pidió. */
  readonly codigoEnviadoA = signal<string | null>(null);
  codigoReapertura = '';

  readonly error = signal('');

  constructor(
    private readonly liquidaciones: LiquidacionesService,
    private readonly productores: ProductoresService,
    private readonly auth: AuthService,
  ) {}

  /**
   * Padrón completo de proveedores. El buscador de "Pagar a un proveedor" debe encontrar a
   * cualquiera, no solo a quienes ya tienen entregas en el ciclo: la planilla del periodo es un
   * subconjunto y dejaba fuera al resto.
   */
  readonly todosProveedores = signal<Productor[]>([]);

  get opcCiclos(): OpcionBuscador[] {
    return this.ciclos.map((c) => ({ valor: c, etiqueta: c.etiqueta }));
  }

  get opcProveedores(): OpcionBuscador[] {
    // Primero los del ciclo (con su zona y montos) y después el resto del padrón, para que el
    // buscador llame a TODOS los proveedores reales y no solo a los que ya tienen planilla.
    const enPlanilla = new Set(this.planilla().map((fila) => fila.proveedorId));
    const opciones: OpcionBuscador[] = this.planilla().map((fila) => ({
      valor: fila.proveedorId,
      etiqueta: fila.proveedorNombre,
      detalle: `${fila.proveedorCodigo} · ${fila.zona ?? 'Sin zona'}`,
    }));
    for (const p of this.todosProveedores()) {
      if (enPlanilla.has(p.id)) continue;
      opciones.push({
        valor: p.id,
        etiqueta: `${p.nombres} ${p.apellidos}`.trim(),
        detalle: `${p.codigo} · ${p.zonaPrincipal ?? p.rutaNombre ?? 'Sin zona'}`,
      });
    }
    return opciones.sort((a, b) => a.etiqueta.localeCompare(b.etiqueta, 'es'));
  }

  operacionEnCurso(): boolean {
    return this.pagandoLiquidacion() || this.cerrandoCiclo() || this.guardandoAjuste() ||
      this.reabriendo() || this.pidiendoCodigoReapertura();
  }

  escrituraEnCurso(): boolean {
    return this.operacionEnCurso() || this.calculandoLiquidacion();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.solicitudPlanilla++;
    this.solicitudLiquidacion++;
    this.solicitudHistorial++;
    this.limpiarImpresion?.();
  }

  ngOnInit(): void {
    this.cargarPlanilla();
    this.cargarProveedores();
    // La pestaña la elige el menú lateral (?vista=); sin vista, "Pagar a un proveedor".
    this.ruta.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const vista = params.get('vista');
      const tab: Tab = vista === 'global' || vista === 'historial' ? vista : 'unico';
      if (tab === this.tab) return;
      if (this.operacionEnCurso()) {
        // Un pago o cierre en curso no se abandona: el menú vuelve a marcar la pestaña actual.
        this.router.navigate([], { relativeTo: this.ruta, queryParams: { vista: this.tab }, replaceUrl: true });
        return;
      }
      this.cambiarTab(tab);
    });
  }

  /** Si falla, el buscador sigue funcionando con la planilla del ciclo. */
  private cargarProveedores(): void {
    this.productores.listar({ size: 500 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (pagina) => { if (!this.destruido) this.todosProveedores.set(pagina.content); },
        error: () => {},
      });
  }

  puedeEscribir(): boolean {
    return this.auth.puede(PERMISO.pagosWrite);
  }

  puedeCerrarCiclo(): boolean {
    return this.auth.puede(PERMISO.pagosCerrarCiclo);
  }

  cambiarTab(tab: Tab): void {
    if (this.operacionEnCurso()) return;
    this.tab = tab;
    if (tab === 'historial') this.cargarHistorial();
  }

  cambiarCiclo(ciclo = this.cicloSeleccionado): void {
    if (this.operacionEnCurso()) return;
    this.cicloSeleccionado = ciclo;
    this.solicitudLiquidacion++;
    this.planilla.set([]);
    this.filaSeleccionada.set(null);
    this.liquidacionSeleccionada.set(null);
    this.cargandoLiquidacion.set(false);
    this.codigoEnviadoA.set(null);
    this.codigoReapertura = '';
    this.cargarPlanilla();
  }

  seleccionarProveedor(id: number | null): void {
    if (this.operacionEnCurso() || id == null) return;
    const fila = this.planilla().find((f) => f.proveedorId === id);
    if (fila) {
      this.seleccionar(fila);
      this.pagPlanillaUnico.irA(Math.floor(this.planilla().indexOf(fila) / this.pagPlanillaUnico.tamano()));
      return;
    }
    // Proveedor del padrón sin entregas en el ciclo: se muestra su ficha con montos en cero para
    // que se entienda por qué no hay nada que pagarle, en vez de no hacer nada al seleccionarlo.
    const proveedor = this.todosProveedores().find((p) => p.id === id);
    if (!proveedor) return;
    this.seleccionar({
      proveedorId: proveedor.id,
      proveedorCodigo: proveedor.codigo,
      proveedorNombre: `${proveedor.nombres} ${proveedor.apellidos}`.trim(),
      proveedorApellidos: proveedor.apellidos,
      zona: proveedor.zonaPrincipal ?? proveedor.rutaNombre ?? null,
      excelencia: proveedor.excelencia,
      litrosEntregados: 0,
      litrosRechazados: 0,
      litrosPagados: 0,
      litrosPendientes: 0,
      tarifaAplicada: 0,
      importePendiente: 0,
      liquidado: false,
      liquidacionId: null,
      tieneLiquidacionCerrada: false,
    });
  }

  totalPendiente(): number {
    return this.planilla().reduce((s, f) => s + (f.liquidado ? 0 : f.importePendiente), 0);
  }

  pendientesCount(): number {
    return this.planilla().filter((f) => !f.liquidado).length;
  }

  pagadosCount(): number {
    return this.planilla().filter((f) => f.liquidado).length;
  }

  /** Incluye pagos cerrados que luego recibieron acopios nuevos y ahora requieren reapertura. */
  pagadosParaImprimir(): FilaPlanilla[] {
    return this.planilla().filter((f) => f.tieneLiquidacionCerrada && f.liquidacionId != null);
  }

  pendientesParaPagar(): FilaPlanilla[] {
    return this.planilla().filter((f) => !f.liquidado && !f.tieneLiquidacionCerrada);
  }

  pendientesReaperturaCount(): number {
    return this.planilla().filter((f) => !f.liquidado && f.tieneLiquidacionCerrada).length;
  }

  private seleccionVigente(solicitud: number, proveedorId: number, inicio: string): boolean {
    return !this.destruido && solicitud === this.solicitudLiquidacion &&
      this.filaSeleccionada()?.proveedorId === proveedorId && this.cicloSeleccionado.inicio === inicio;
  }

  private mostrarError(e: unknown, mensaje: string): void {
    if (!this.destruido) this.error.set(mensajeDeError(e, mensaje));
  }

  cargarPlanilla(): void {
    const solicitud = ++this.solicitudPlanilla;
    const ciclo = this.cicloSeleccionado;
    this.cargandoPlanilla.set(true);
    this.error.set('');
    this.liquidaciones.planilla(ciclo.inicio, ciclo.fin)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        if (solicitud === this.solicitudPlanilla) this.cargandoPlanilla.set(false);
      }))
      .subscribe({
        next: (filas) => {
          if (solicitud !== this.solicitudPlanilla || this.destruido) return;
          this.planilla.set(filas);
          const seleccionada = this.filaSeleccionada();
          if (seleccionada) {
            const actualizada = filas.find((f) => f.proveedorId === seleccionada.proveedorId) ?? null;
            this.filaSeleccionada.set(actualizada);
            if (!actualizada) this.liquidacionSeleccionada.set(null);
          }
        },
        error: (e) => {
          if (solicitud === this.solicitudPlanilla) this.mostrarError(e, 'No se pudo cargar la planilla del periodo.');
        },
      });
  }

  seleccionar(fila: FilaPlanilla): void {
    if (this.operacionEnCurso()) return;
    const solicitud = ++this.solicitudLiquidacion;
    const inicio = this.cicloSeleccionado.inicio;
    this.filaSeleccionada.set(fila);
    this.liquidacionSeleccionada.set(null);
    this.cargandoLiquidacion.set(false);
    this.error.set('');
    this.ajusteMonto = null;
    this.ajusteMotivo = '';
    this.codigoReapertura = '';
    this.codigoEnviadoA.set(null);
    if (fila.tieneLiquidacionCerrada && fila.liquidacionId != null) {
      this.cargandoLiquidacion.set(true);
      this.liquidaciones.obtener(fila.liquidacionId)
        .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
          if (solicitud === this.solicitudLiquidacion) this.cargandoLiquidacion.set(false);
        }))
        .subscribe({
          next: (l) => {
            if (this.seleccionVigente(solicitud, fila.proveedorId, inicio)) this.liquidacionSeleccionada.set(l);
          },
          error: (e) => {
            if (this.seleccionVigente(solicitud, fila.proveedorId, inicio)) this.mostrarError(e, 'No se pudo cargar la liquidación.');
          },
        });
    }
  }

  calcular(): void {
    const fila = this.filaSeleccionada();
    if (!fila || !this.puedeEscribir() || this.cargandoLiquidacion() || this.escrituraEnCurso() || fila.tieneLiquidacionCerrada) return;
    const solicitud = ++this.solicitudLiquidacion;
    const ciclo = this.cicloSeleccionado;
    this.cargandoLiquidacion.set(true);
    this.calculandoLiquidacion.set(true);
    this.error.set('');
    this.liquidaciones.calcular(fila.proveedorId, ciclo.inicio, ciclo.fin)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        this.calculandoLiquidacion.set(false);
        if (solicitud === this.solicitudLiquidacion) this.cargandoLiquidacion.set(false);
      }))
      .subscribe({
        next: (l) => {
          if (this.seleccionVigente(solicitud, fila.proveedorId, ciclo.inicio)) this.liquidacionSeleccionada.set(l);
        },
        error: (e) => {
          if (this.seleccionVigente(solicitud, fila.proveedorId, ciclo.inicio)) this.mostrarError(e, 'No se pudo calcular la liquidación.');
        },
      });
  }

  async cerrarSeleccionada(): Promise<void> {
    const l = this.liquidacionSeleccionada();
    if (!l || l.estado === 'CLOSED' || !this.puedeEscribir() || this.escrituraEnCurso()) return;
    this.pagandoLiquidacion.set(true);
    this.error.set('');
    let pagada = false;
    try {
      const ok = await this.dialogo.confirmar({
        titulo: 'Confirmar pago al proveedor',
        mensaje: l.productorNombre + ' · S/ ' + l.montoNeto.toFixed(2) + '. Periodo ' +
          fechaDeBoleta(l.periodoInicio) + ' al ' + fechaDeBoleta(l.periodoFin) +
          '. Al confirmar se registra el pago y se abre la boleta para imprimir.',
        confirmar: 'Pagar e imprimir boleta',
        cancelar: 'Volver',
      });
      if (!ok || this.destruido) return;
      const cerrada = await firstValueFrom(this.liquidaciones.cambiarEstado(l.id, 'CLOSED').pipe(takeUntilDestroyed(this.destroyRef)));
      if (this.destruido) return;
      this.liquidacionSeleccionada.set(cerrada);
      this.notificaciones.exito('Pago registrado para ' + cerrada.productorNombre + ': S/ ' + cerrada.montoNeto.toFixed(2) + '.');
      this.cargarPlanilla();
      pagada = true;
    } catch (e) {
      this.mostrarError(e, 'No se pudo registrar el pago. Inténtalo de nuevo.');
    } finally {
      this.pagandoLiquidacion.set(false);
    }
    // Pago único = un solo proveedor: al confirmar, se imprime su boleta en el mismo paso.
    if (pagada && !this.destruido) this.imprimirBoleta();
  }

  puedeReabrir(): boolean {
    return this.auth.puede(PERMISO.pagosReabrir);
  }

  async pedirCodigoReapertura(): Promise<void> {
    if (!this.puedeReabrir() || this.operacionEnCurso()) return;
    this.error.set('');
    this.pidiendoCodigoReapertura.set(true);
    try {
      const emitido = await firstValueFrom(this.liquidaciones.solicitarCodigoReapertura().pipe(takeUntilDestroyed(this.destroyRef)));
      if (this.destruido) return;
      this.codigoEnviadoA.set(emitido.destinatarioEnmascarado);
      this.notificaciones.info('Código enviado a ' + emitido.destinatarioEnmascarado + '. Vence en ' + emitido.minutosVigencia + ' minutos.');
    } catch (e) { this.mostrarError(e, 'No se pudo enviar el código.'); }
    finally { this.pidiendoCodigoReapertura.set(false); }
  }

  async confirmarReapertura(): Promise<void> {
    const l = this.liquidacionSeleccionada();
    const codigo = this.codigoReapertura.trim();
    if (!l || l.estado !== 'CLOSED' || !this.puedeReabrir() || this.escrituraEnCurso()) return;
    if (!/^\d{6}$/.test(codigo)) {
      this.error.set('Ingresa el código de 6 dígitos que recibiste por correo.');
      return;
    }
    this.reabriendo.set(true);
    this.error.set('');
    try {
      const ok = await this.dialogo.confirmar({
        titulo: '¿Reabrir el pago de ' + l.productorNombre + '?',
        mensaje: 'El pago del periodo ' + fechaDeBoleta(l.periodoInicio) + ' al ' + fechaDeBoleta(l.periodoFin) +
          ' quedará anulado. Podrás revisar los acopios y volver a registrar el pago.',
        confirmar: 'Reabrir pago', tipo: 'peligro',
      });
      if (!ok || this.destruido) return;
      await firstValueFrom(this.liquidaciones.reabrir(l.id, codigo).pipe(takeUntilDestroyed(this.destroyRef)));
      if (this.destruido) return;
      this.codigoReapertura = '';
      this.codigoEnviadoA.set(null);
      this.solicitudLiquidacion++;
      this.filaSeleccionada.set(null);
      this.liquidacionSeleccionada.set(null);
      this.notificaciones.exito('Pago reabierto. Selecciona al proveedor para revisar y recalcular su liquidación.');
      this.cargarPlanilla();
    } catch (e) { this.mostrarError(e, 'No se pudo reabrir el pago.'); }
    finally { this.reabriendo.set(false); }
  }

  async agregarAjuste(): Promise<void> {
    const l = this.liquidacionSeleccionada();
    if (!l || l.estado === 'CLOSED' || !this.puedeEscribir() || this.escrituraEnCurso()) return;
    if (this.ajusteMonto == null || !Number.isFinite(this.ajusteMonto) || this.ajusteMonto <= 0) {
      this.error.set('Indica un monto de ajuste mayor a cero.');
      return;
    }
    this.guardandoAjuste.set(true);
    this.error.set('');
    try {
      const actualizada = await firstValueFrom(this.liquidaciones.agregarAjuste(l.id, {
        tipo: this.ajusteTipo, monto: this.ajusteMonto, motivo: this.ajusteMotivo.trim() || undefined,
      }).pipe(takeUntilDestroyed(this.destroyRef)));
      if (this.destruido) return;
      this.liquidacionSeleccionada.set(actualizada);
      this.ajusteMonto = null;
      this.ajusteMotivo = '';
      this.notificaciones.exito('Ajuste agregado. El importe neto está actualizado.');
    } catch (e) { this.mostrarError(e, 'No se pudo agregar el ajuste.'); }
    finally { this.guardandoAjuste.set(false); }
  }

  async eliminarAjuste(ajusteId: number): Promise<void> {
    const l = this.liquidacionSeleccionada();
    if (!l || l.estado === 'CLOSED' || !this.puedeEscribir() || this.escrituraEnCurso()) return;
    this.guardandoAjuste.set(true);
    this.error.set('');
    try {
      const ok = await this.dialogo.confirmar({
        titulo: '¿Quitar este ajuste?', mensaje: 'Se actualizará el importe neto de ' + l.productorNombre + '.', confirmar: 'Quitar ajuste',
      });
      if (!ok || this.destruido) return;
      const actualizada = await firstValueFrom(this.liquidaciones.eliminarAjuste(l.id, ajusteId).pipe(takeUntilDestroyed(this.destroyRef)));
      if (!this.destruido) this.liquidacionSeleccionada.set(actualizada);
    } catch (e) { this.mostrarError(e, 'No se pudo quitar el ajuste.'); }
    finally { this.guardandoAjuste.set(false); }
  }

  async pagarATodos(): Promise<void> {
    if (!this.puedeCerrarCiclo() || this.escrituraEnCurso() || this.cargandoPlanilla()) return;
    const pendientes = this.pendientesParaPagar();
    if (!pendientes.length) {
      this.notificaciones.info('No hay liquidaciones abiertas por pagar. Los pagos cerrados con nuevas entregas requieren reapertura.');
      return;
    }
    const ciclo = this.cicloSeleccionado;
    const estimado = pendientes.reduce((s, f) => s + f.importePendiente, 0);
    this.cerrandoCiclo.set(true);
    this.error.set('');
    try {
      const ok = await this.dialogo.confirmar({
        titulo: 'Confirmar pago a ' + pendientes.length + ' proveedor(es)',
        mensaje: 'Periodo ' + ciclo.etiqueta + '. Importe bruto estimado: S/ ' + estimado.toFixed(2) +
          '. El neto final incluye los descuentos y bonificaciones registrados. Confirma cuando hayas entregado los pagos.',
        confirmar: 'Confirmar pagos', cancelar: 'Volver',
      });
      if (!ok || this.destruido) return;
      const resultado = await firstValueFrom(this.liquidaciones.cerrarCiclo(ciclo.inicio, ciclo.fin).pipe(takeUntilDestroyed(this.destroyRef)));
      if (this.destruido) return;
      this.liquidacionSeleccionada.set(null);
      this.solicitudLiquidacion++;
      this.notificaciones.exito(resultado.proveedoresLiquidados + ' proveedor(es) pagado(s) · S/ ' + resultado.montoNetoTotal.toFixed(2) + '.');
      this.cargarPlanilla();
    } catch (e) { this.mostrarError(e, 'No se pudo registrar el pago de los proveedores.'); }
    finally { this.cerrandoCiclo.set(false); }
  }

  imprimirParaFila(fila: FilaPlanilla): void {
    if (!fila.liquidacionId || this.imprimiendo() || this.operacionEnCurso()) return;
    this.imprimiendo.set(true);
    this.imprimiendoFila.set(fila.proveedorId);
    this.error.set('');
    this.liquidaciones.obtener(fila.liquidacionId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (liquidacion) => {
        this.boletasParaImprimir.set([{ liquidacion, fila }]);
        this.ejecutarImpresion();
      },
      error: (e) => {
        this.imprimiendo.set(false);
        this.imprimiendoFila.set(null);
        this.mostrarError(e, 'No se pudo preparar la boleta.');
      },
    });
  }

  imprimirHistorial(liquidacion: Liquidacion): void {
    if (this.imprimiendo() || this.operacionEnCurso()) return;
    this.imprimiendo.set(true);
    this.error.set('');
    this.liquidaciones.obtener(liquidacion.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (completa) => {
        const fila = this.planilla().find((f) => f.proveedorId === completa.productorId) ?? null;
        this.boletasParaImprimir.set([{ liquidacion: completa, fila }]);
        this.ejecutarImpresion();
      },
      error: (e) => {
        this.imprimiendo.set(false);
        this.mostrarError(e, 'No se pudo preparar el comprobante del historial.');
      },
    });
  }

  /** Filtro del historial cambiado: vuelve a la primera página. */
  filtrarHistorial(): void {
    this.historialPagina = 0;
    this.cargarHistorial();
  }

  cambiarPaginaHistorial(p: number): void {
    this.historialPagina = p;
    this.cargarHistorial();
  }

  cambiarTamanoHistorial(tamano: number): void {
    this.historialTamano = tamano;
    this.filtrarHistorial();
  }

  cargarHistorial(): void {
    const solicitud = ++this.solicitudHistorial;
    this.cargandoHistorial.set(true);
    this.error.set('');
    this.liquidaciones.listar(this.historialProductorId ?? undefined, 'CLOSED', this.historialPagina, this.historialTamano)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        if (solicitud === this.solicitudHistorial) this.cargandoHistorial.set(false);
      }))
      .subscribe({
        next: (pagina) => {
          if (solicitud !== this.solicitudHistorial || this.destruido) return;
          const otra = paginaDisponible(pagina);
          if (otra !== null) return this.cambiarPaginaHistorial(otra);
          this.historial.set(pagina.content);
          this.historialTotal.set(pagina.totalElements);
          this.historialPagina = pagina.page;
        },
        error: (e) => {
          if (solicitud === this.solicitudHistorial) this.mostrarError(e, 'No se pudo cargar el historial.');
        },
      });
  }

  diasBoleta(l: Liquidacion | null): DiaBoleta[] { return l ? diasDeBoleta(l) : []; }
  descuentoBoleta(l: Liquidacion): number { return descuentosDeBoleta(l); }
  bonificacionBoleta(l: Liquidacion): number { return bonificacionesDeBoleta(l); }
  tarifaBoleta(l: Liquidacion): string { return tarifaDeBoleta(l); }
  fechaBoleta(fecha: string): string { return fechaDeBoleta(fecha); }
  litrosAdicionalesBoleta(l: Liquidacion): number { return litrosAdicionalesDeBoleta(l); }

  imprimirBoleta(): void {
    const liquidacion = this.liquidacionSeleccionada();
    const fila = this.filaSeleccionada();
    if (!liquidacion || !fila || this.imprimiendo() || this.operacionEnCurso()) return;
    this.imprimiendo.set(true);
    this.boletasParaImprimir.set([{ liquidacion, fila }]);
    this.ejecutarImpresion();
  }

  imprimirTodo(): void {
    const pagados = this.pagadosParaImprimir();
    if (!pagados.length || this.imprimiendo() || this.operacionEnCurso()) return;
    this.imprimiendo.set(true);
    this.imprimiendoTodo.set(true);
    this.error.set('');
    forkJoin(pagados.map((fila) => this.liquidaciones.obtener(fila.liquidacionId!).pipe(map((liquidacion) => ({ liquidacion, fila })))))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (items) => {
          this.boletasParaImprimir.set(ordenarPorApellido(items));
          this.ejecutarImpresion();
        },
        error: (e) => {
          this.imprimiendoTodo.set(false);
          this.imprimiendo.set(false);
          this.mostrarError(e, 'No se pudieron preparar las boletas.');
        },
      });
  }

  private ejecutarImpresion(): void {
    if (this.destruido) return;
    const claseBody = this.boletasParaImprimir().length > 1 ? 'print-worker-lote' : 'print-worker';
    this.fechaImpresion.set(new Date().toLocaleString('es-PE', {
      timeZone: 'America/Lima', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
    }));
    const estilo = document.createElement('style');
    estilo.id = 'boleta-print-page';
    estilo.textContent = '@media print { @page { size: 160mm 110mm; margin: 0; } ' +
      'html, body { width: 160mm !important; height: auto !important; min-height: 0 !important; ' +
      'overflow: visible !important; margin: 0 !important; padding: 0 !important; } }';
    document.head.appendChild(estilo);
    document.body.classList.add(claseBody);
    const limpiar = () => {
      clearTimeout(temporizador);
      document.body.classList.remove(claseBody);
      estilo.remove();
      this.boletasParaImprimir.set([]);
      this.imprimiendo.set(false);
      this.imprimiendoFila.set(null);
      this.imprimiendoTodo.set(false);
      window.removeEventListener('afterprint', limpiar);
      this.limpiarImpresion = null;
    };
    const temporizador = setTimeout(async () => {
      try {
        this.changeDetector.detectChanges();
        const imagenes = Array.from(document.querySelectorAll<HTMLImageElement>('.payment-receipt img'));
        await Promise.all(imagenes.map((imagen) => typeof imagen.decode === 'function' ? imagen.decode().catch(() => {}) : Promise.resolve()));
        if (!this.destruido && this.limpiarImpresion === limpiar) window.print();
      } catch (e) {
        limpiar();
        this.mostrarError(e, 'No se pudo abrir la impresión de la boleta.');
      }
    }, 0);
    this.limpiarImpresion = limpiar;
    window.addEventListener('afterprint', limpiar);
  }

  etiquetaAjuste(tipo: TipoAjuste): string {
    return (
      { DESCUENTO_QUESO: 'Descuento de queso', PENALIDAD: 'Penalidad', ADELANTO: 'Adelanto', BONIFICACION: 'Bonificación' }[
        tipo
      ] ?? tipo
    );
  }
}
