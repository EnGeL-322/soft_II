import { ExportacionesService } from '../../core/services/exportaciones.service';
import { BuscadorComponent, OpcionBuscador } from '../../shared/ui/buscador.component';
import { DatePipe, DecimalPipe } from '@angular/common';
import { finalize, forkJoin, of, Subscription, switchMap } from 'rxjs';
import { Component, OnInit, computed, signal, inject } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { PERMISO } from '../../core/config/navegacion';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
  Acopiador,
  EstadoRecojo,
  FuenteRecojo,
  PageResponse,
  Productor,
  Recojo,
  ReporteAcopiador,
  ResultadoCalidad,
  Ruta,
} from '../../core/models/models';
import { AcopiadoresService } from '../../core/services/acopiadores.service';
import { ProductoresService } from '../../core/services/productores.service';
import { RecojoService, LecturaInstrumento, LecturaEvaluada } from '../../core/services/recojo.service';
import { RutasService } from '../../core/services/rutas.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';
import { paginaDisponible } from '../../shared/ui/paginacion-local';
import { PaginadorComponent, TAMANO_PAGINA_PREDETERMINADO } from '../../shared/ui/paginador.component';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { mensajeDeError } from '../../core/notificaciones/mensaje-error';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';
import { IconoComponent } from '../../shared/ui/icono.component';
import {
  PeriodoEntregas,
  agruparPorDia,
  deIso,
  fechaIsoLocal,
  instantesDelRango,
  moverPeriodo,
  rangoDePeriodo,
  resumirEntregas,
} from './entregas-por-fecha';

/** Entregas directas: se trae todo el periodo (500 por página, el techo del backend) hasta este tope. */
const TAMANO_PAGINA_ENTREGAS = 500;
const MAXIMO_PAGINAS_ENTREGAS = 10;

/**
 * Entrega directa en la web: solo proveedor y litros (decisión del cliente, 2026-10-14). El análisis
 * de calidad lo registra el tester con la app (OCR del voucher) y aquí, en Calidad, se aprueba.
 */
interface FormRecepcion {
  productorId?: number;
  litros?: number;
}

const FORM_VACIO: FormRecepcion = {};

/**
 * Dos vistas del mismo registro (ver app.routes.ts, `data.modo`):
 *  - `ruta`: lo que recogen los acopiadores con la app. Solo consulta, cancelación y reporte.
 *  - `planta`: la leche que el proveedor trae él mismo a planta. Aquí se registra la entrega y su
 *    análisis en un solo formulario (Recojo + ControlCalidad).
 */
@Component({
  selector: 'app-recojo',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    DecimalPipe,
    BadgeEstadoComponent,
    PaginadorComponent,
    BuscadorComponent,
    IconoComponent,
  ],
  templateUrl: './recojo.component.html',
  styleUrl: './recojo.component.css',
})
export class RecojoComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de acopio, calidad, proveedores, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['acopio', 'calidad', 'proveedores'], () => {
    this.buscar();
    if (!this.esPlanta) this.cargarReporte();
  });
  protected readonly notificaciones = inject(NotificacionesService);
  private readonly dialogo = inject(DialogoService);
  exportar() { this.exportaciones.descargar('Recepciones').subscribe(blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='Huata_Recepciones.xlsx';a.click();URL.revokeObjectURL(url);}); }

  readonly pagina = signal<PageResponse<Recojo> | null>(null);
  readonly productores = signal<Productor[]>([]);
  readonly rutas = signal<Ruta[]>([]);
  readonly acopiadores = signal<Acopiador[]>([]);
  readonly mostrarForm = signal(false);
  readonly guardando = signal(false);
  readonly errorForm = signal('');
  readonly reportesAcopiador = signal<ReporteAcopiador[]>([]);
  readonly cargandoReporte = signal(false);

  readonly hoyTexto = new Date().toLocaleDateString('es-PE', { day: '2-digit', month: 'short' });

  filtroProductorId?: number;
  filtroEstado?: EstadoRecojo;
  filtroTexto = '';
  filtroResultado?: ResultadoCalidad | 'PENDIENTE';
  filtroFuente?: FuenteRecojo;
  page = 0;
  size: number = TAMANO_PAGINA_PREDETERMINADO;
  private consulta?: Subscription;
  periodoReporte: 'DIA' | 'SEMANA' | 'MES' = 'MES';
  fechaReporte = this.fechaLocal(new Date());
  acopiadorReporteId?: number;

  form: FormRecepcion = { ...FORM_VACIO };

  // ---- Entregas directas por fechas (solo vista `planta`) ----
  readonly hoy = fechaIsoLocal(new Date());
  /** Por defecto la semana de liquidación en curso (jueves → miércoles). */
  readonly periodoEntregas = signal<PeriodoEntregas>('SEMANA');
  readonly referenciaEntregas = signal(this.hoy);
  readonly entregas = signal<Recojo[]>([]);
  readonly cargandoEntregas = signal(false);
  /** Total que reporta el servidor; si supera lo traído, se avisa que hay que acotar el periodo. */
  readonly totalEntregasServidor = signal(0);
  /** Días plegados por la persona (por defecto todos abiertos). */
  readonly diasPlegados = signal<ReadonlySet<string>>(new Set());
  /** Un solo selector "Mostrar" en lugar de los filtros de resultado y estado por separado. */
  filtroPlanta: '' | ResultadoCalidad | 'PENDIENTE' | 'CANCELLED' = '';

  readonly rangoEntregas = computed(() => rangoDePeriodo(this.periodoEntregas(), this.referenciaEntregas()));
  readonly diasEntregas = computed(() => agruparPorDia(this.entregas()));
  readonly resumenEntregas = computed(() => resumirEntregas(this.entregas()));
  readonly entregasIncompletas = computed(() => this.totalEntregasServidor() > this.entregas().length);
  /** No se avanza a periodos que todavía no empiezan. */
  readonly puedeAvanzar = computed(() => this.rangoEntregas().hasta < this.hoy);
  readonly incluyeHoy = computed(() => {
    const r = this.rangoEntregas();
    return r.desde <= this.hoy && this.hoy <= r.hasta;
  });

  /** "Hoy, lunes 27 de octubre", "jue 23 – mié 29 oct" u "octubre de 2026". */
  readonly etiquetaRango = computed(() => {
    const { desde, hasta } = this.rangoEntregas();
    if (this.periodoEntregas() === 'MES') {
      return deIso(desde).toLocaleDateString('es-PE', { month: 'long', year: 'numeric' });
    }
    if (desde === hasta) return this.tituloDia(desde);
    const d = deIso(desde);
    const h = deIso(hasta);
    const mismoMes = d.getMonth() === h.getMonth();
    const inicio = d.toLocaleDateString('es-PE', { weekday: 'short', day: 'numeric', ...(mismoMes ? {} : { month: 'short' }) });
    const fin = h.toLocaleDateString('es-PE', { weekday: 'short', day: 'numeric', month: 'short' });
    return `${inicio} – ${fin}`;
  });

  elegirPeriodo(periodo: PeriodoEntregas): void {
    this.referenciaEntregas.set(this.hoy);
    this.periodoEntregas.set(periodo);
    this.diasPlegados.set(new Set());
    this.buscar();
  }

  moverEntregas(paso: 1 | -1): void {
    if (paso === 1 && !this.puedeAvanzar()) return;
    const siguiente = moverPeriodo(this.periodoEntregas(), this.referenciaEntregas(), this.rangoEntregas(), paso);
    this.referenciaEntregas.set(siguiente.referencia);
    this.diasPlegados.set(new Set());
    this.buscar();
  }

  irAHoy(): void {
    this.periodoEntregas.set('DIA');
    this.referenciaEntregas.set(this.hoy);
    this.buscar();
  }

  /** Elegir una fecha en el calendario lleva al periodo (día, semana o mes) que la contiene. */
  elegirDia(fecha: string): void {
    if (!fecha) return;
    this.referenciaEntregas.set(fecha);
    this.diasPlegados.set(new Set());
    this.buscar();
  }

  abrirCalendario(input: HTMLInputElement): void {
    try {
      input.showPicker();
    } catch {
      input.focus();
    }
  }

  cambiarFiltroPlanta(valor: typeof this.filtroPlanta): void {
    this.filtroPlanta = valor;
    this.filtroEstado = valor === 'CANCELLED' ? 'CANCELLED' : undefined;
    this.filtroResultado = valor === '' || valor === 'CANCELLED' ? undefined : valor;
    this.buscar();
  }

  alternarDia(fecha: string): void {
    this.diasPlegados.update((s) => {
      const nuevo = new Set(s);
      if (nuevo.has(fecha)) nuevo.delete(fecha);
      else nuevo.add(fecha);
      return nuevo;
    });
  }

  diaAbierto(fecha: string): boolean {
    return !this.diasPlegados().has(fecha);
  }

  tituloDia(fecha: string): string {
    const texto = deIso(fecha).toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' });
    return fecha === this.hoy ? `Hoy, ${texto}` : texto.charAt(0).toUpperCase() + texto.slice(1);
  }

  codigoRecepcion(r: Recojo): string {
    return `REC-${r.id.toString().padStart(6, '0')}`;
  }

  private readonly auth = inject(AuthService);
  /**
   * Registrar una entrega directa es de planta (planta.recepcion); el backend lo vuelve a validar.
   * computed y no un valor fijo: si los permisos cambian con la pantalla abierta (se refrescan al
   * volver a la pestaña), el botón aparece o desaparece sin tener que salir y volver.
   */
  readonly puedeRegistrarEntrega = computed(() => this.auth.puede(PERMISO.plantaRecepcion));
  /** Cancelar exige acopio.write en el backend: sin él, el botón solo devolvería un 403. */
  readonly puedeCancelar = computed(() => this.auth.puede(PERMISO.acopioWrite));

  /** true en «Entregas directas»; false en «Recojos en ruta». */
  get esPlanta(): boolean {
    return this.route.snapshot.data['modo'] === 'planta';
  }

  readonly recepcionesHoy = computed(() => {
    const hoy = new Date().toDateString();
    return (this.pagina()?.content ?? []).filter((r) => new Date(r.fechaHora).toDateString() === hoy).length;
  });

  readonly litrosHoy = computed(() => {
    const hoy = new Date().toDateString();
    return (this.pagina()?.content ?? [])
      .filter((r) => new Date(r.fechaHora).toDateString() === hoy)
      .reduce((s, r) => s + r.litros, 0);
  });

  readonly totalReporte = computed(() => ({
    litros: this.reportesAcopiador().reduce((s, r) => s + r.litros, 0),
    recepciones: this.reportesAcopiador().reduce((s, r) => s + r.recepciones, 0),
    proveedores: this.reportesAcopiador().reduce((s, r) => s + r.proveedoresVisitados, 0),
    noEntregados: this.reportesAcopiador().reduce((s, r) => s + r.noEntregados, 0),
  }));

  constructor(
    private readonly recojoService: RecojoService,
    private readonly productoresService: ProductoresService,
    private readonly rutasService: RutasService,
    private readonly acopiadoresService: AcopiadoresService,
    private readonly route: ActivatedRoute,
    private readonly exportaciones: ExportacionesService,
  ) {}

  // ---- Opciones de los buscadores escribibles ----
  readonly opcProductores = computed<OpcionBuscador[]>(() =>
    this.productores().map((x) => ({
      valor: x.id,
      etiqueta: `${x.codigo} · ${x.nombres} ${x.apellidos}`,
      detalle: x.zonaPrincipal || undefined,
    })),
  );

  readonly opcProductoresConTodos = computed<OpcionBuscador[]>(() => [
    { valor: undefined, etiqueta: 'Todos los productores' },
    ...this.opcProductores(),
  ]);

  readonly opcAcopiadoresConTodos = computed<OpcionBuscador[]>(() => [
    { valor: undefined, etiqueta: 'Todos los acopiadores' },
    ...this.acopiadores().map((x) => ({
      valor: x.id,
      etiqueta: `${x.nombres} ${x.apellidos}`,
      detalle: x.zona || 'Sin zona',
    })),
  ]);

  ngOnInit(): void {
    // Cada vista muestra solo su origen: no se mezclan recojos de ruta con entregas directas.
    this.filtroFuente = this.esPlanta ? 'PLANTA' : 'RUTA';
    this.productoresService.listar({ size: 200 }).subscribe((pg) => this.productores.set(pg.content));
    this.rutasService.listar().subscribe((pg) => this.rutas.set(pg.content));
    this.acopiadoresService.listar('', 0, 200).subscribe((pg) => this.acopiadores.set(pg.content));
    this.buscar();
    if (!this.esPlanta) this.cargarReporte();
    if (this.esPlanta && this.route.snapshot.queryParamMap.get('nuevo')) {
      this.abrirFormulario();
    }
  }

  cargarReporte(): void {
    const referencia = new Date(`${this.fechaReporte}T12:00:00`);
    let desde = new Date(referencia);
    let hasta = new Date(referencia);
    if (this.periodoReporte === 'SEMANA') {
      const dia = referencia.getDay() || 7;
      desde.setDate(referencia.getDate() - dia + 1);
      hasta = new Date(desde);
      hasta.setDate(desde.getDate() + 6);
    } else if (this.periodoReporte === 'MES') {
      desde = new Date(referencia.getFullYear(), referencia.getMonth(), 1);
      hasta = new Date(referencia.getFullYear(), referencia.getMonth() + 1, 0);
    }
    this.cargandoReporte.set(true);
    this.recojoService
      .reporteAcopiadores(this.fechaLocal(desde), this.fechaLocal(hasta), this.acopiadorReporteId)
      .pipe(finalize(() => this.cargandoReporte.set(false)))
      .subscribe({ next: (r) => this.reportesAcopiador.set(r), error: () => this.reportesAcopiador.set([]) });
  }

  private fechaLocal(fecha: Date): string {
    const y = fecha.getFullYear();
    const m = String(fecha.getMonth() + 1).padStart(2, '0');
    const d = String(fecha.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  buscar(): void {
    this.page = 0;
    this.cargar();
  }

  cambiarPagina(p: number): void {
    this.page = p;
    this.cargar();
  }

  cambiarTamano(tamano: number): void {
    this.size = tamano;
    this.buscar();
  }

  /** Todos los filtros van al servidor: la paginación cuenta y muestra solo lo filtrado. */
  private cargar(): void {
    this.consulta?.unsubscribe();
    if (this.esPlanta) {
      this.cargarEntregas();
      return;
    }
    this.consulta = this.recojoService
      .listar({
        productorId: this.filtroProductorId,
        estado: this.filtroEstado,
        q: this.filtroTexto,
        fuente: this.filtroFuente,
        resultado: this.filtroResultado,
        page: this.page,
        size: this.size,
      })
      .subscribe((pg) => {
        const otra = paginaDisponible(pg);
        if (otra !== null) {
          this.cambiarPagina(otra);
          return;
        }
        this.pagina.set(pg);
      });
  }

  /**
   * Entregas directas del periodo completo (no una página): se agrupan por día y los totales tienen
   * que cuadrar con todo el periodo. Los filtros siguen yendo al servidor.
   */
  private cargarEntregas(): void {
    const { desde, hasta } = instantesDelRango(this.rangoEntregas());
    const pedir = (page: number) =>
      this.recojoService.listar({
        productorId: this.filtroProductorId,
        estado: this.filtroEstado,
        q: this.filtroTexto,
        fuente: 'PLANTA',
        resultado: this.filtroResultado,
        desde,
        hasta,
        page,
        size: TAMANO_PAGINA_ENTREGAS,
      });
    this.cargandoEntregas.set(true);
    this.consulta = pedir(0)
      .pipe(
        switchMap((primera) => {
          const paginas = Math.min(primera.totalPages, MAXIMO_PAGINAS_ENTREGAS);
          const resto = Array.from({ length: Math.max(0, paginas - 1) }, (_, i) => pedir(i + 1));
          return resto.length ? forkJoin([of(primera), ...resto]) : of([primera]);
        }),
        finalize(() => this.cargandoEntregas.set(false)),
      )
      .subscribe({
        next: (paginas) => {
          this.entregas.set(paginas.flatMap((p) => p.content));
          this.totalEntregasServidor.set(paginas[0]?.totalElements ?? 0);
        },
        error: (e) => {
          this.entregas.set([]);
          this.totalEntregasServidor.set(0);
          this.notificaciones.error(mensajeDeError(e, 'No se pudieron cargar las entregas.'));
        },
      });
  }

  /* ---- Botón "Llamar datos": ticket del analizador de leche (opcional) ---- */
  readonly leyendoInstrumento = signal(false);
  /** null mientras no se sabe; false si el backend no tiene HUATA_INSTRUMENTO_URL. */
  readonly instrumentoConfigurado = signal<boolean | null>(null);
  /** Ticket de la última lectura. Solo se muestra y se guarda con la entrega: no se edita. */
  readonly medicion = signal<LecturaInstrumento | null>(null);
  readonly avisoInstrumento = signal('');

  /** Filas del ticket, en el mismo orden en que las imprime el aparato (ver W.jpeg / W1.jpeg). */
  filasMedicion(): ReadonlyArray<{ etiqueta: string; valor: string }> {
    const m = this.medicion();
    if (!m) return [];
    const num = (v: number | undefined, sufijo: string, decimales = 2) =>
      v === null || v === undefined ? '—' : `${v.toFixed(decimales)}${sufijo}`;
    return [
      { etiqueta: 'Temperatura', valor: num(m.temperatura, ' °C', 1) },
      { etiqueta: 'Grasa', valor: num(m.grasaPct, ' %') },
      { etiqueta: 'Sólidos no grasos', valor: num(m.solidosNoGrasosPct, ' %') },
      // Grados de lactometro, no g/mL: por eso la etiqueta lo dice.
      { etiqueta: 'Densidad (lactómetro)', valor: num(m.densidadLactometrica, '', 1) },
      { etiqueta: 'Proteína', valor: num(m.proteinaPct, ' %') },
      { etiqueta: 'Lactosa', valor: num(m.lactosaPct, ' %') },
      { etiqueta: 'Sales', valor: num(m.salesPct, ' %') },
      { etiqueta: 'Sólidos totales', valor: num(m.solidosTotalesPct, ' %', 1) },
      { etiqueta: 'Agua añadida', valor: num(m.aguaAnadidaPct, ' %', 1) },
      { etiqueta: 'Punto de congelación', valor: num(m.puntoCongelacion, ' °C', 3) },
      { etiqueta: 'pH', valor: num(m.ph, '', 1) },
    ];
  }

  /**
   * Pide al equipo su ticket. Se guarda con la entrega como medición (medicion_instrumento) y su
   * temperatura en el recojo; no decide la calidad: eso es del análisis que registra la app.
   */
  llamarDatos(): void {
    if (this.leyendoInstrumento() || this.instrumentoConfigurado() === false) {
      return;
    }
    this.errorForm.set('');
    this.avisoInstrumento.set('');
    this.leyendoInstrumento.set(true);
    this.recojoService
      .leerInstrumento()
      .pipe(finalize(() => this.leyendoInstrumento.set(false)))
      .subscribe({
        next: (respuesta: LecturaEvaluada) => {
          this.medicion.set(respuesta.lectura);
          const sn = respuesta.lectura.analizadorSn ? ` (analizador ${respuesta.lectura.analizadorSn})` : '';
          this.avisoInstrumento.set(`Lectura tomada del equipo${sn}. Se guardará junto con la entrega.`);
        },
        error: (e) => this.errorForm.set(e.error?.message ?? 'No se pudo leer el equipo de medición.'),
      });
  }

  abrirFormulario(): void {
    this.form = { ...FORM_VACIO };
    this.errorForm.set('');
    this.medicion.set(null);
    this.avisoInstrumento.set('');
    this.mostrarForm.set(true);
    if (this.instrumentoConfigurado() === null) {
      this.recojoService.estadoInstrumento().subscribe({
        next: (e) => this.instrumentoConfigurado.set(e.configurado),
        error: () => this.instrumentoConfigurado.set(false),
      });
    }
  }

  registrar(): void {
    if (!this.form.productorId || !this.form.litros) {
      this.errorForm.set('Selecciona el proveedor y registra los litros recibidos.');
      return;
    }
    this.errorForm.set('');
    this.guardando.set(true);
    const medicion = this.medicion();
    this.recojoService
      .registrar({
        productorId: this.form.productorId,
        fechaHora: new Date().toISOString(),
        litros: this.form.litros,
        temperatura: medicion?.temperatura,
        fuente: 'PLANTA',
        medicion: medicion ?? undefined,
      })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: () => {
          this.mostrarForm.set(false);
          this.notificaciones.exito('Entrega registrada. El análisis lo registra el tester con la app.');
          // La entrega nueva es de hoy: si el periodo a la vista no lo incluye, se muestra hoy.
          if (this.incluyeHoy()) this.buscar();
          else this.irAHoy();
        },
        error: (e) => this.errorForm.set(e?.error?.message ?? 'No se pudo registrar la entrega.'),
      });
  }

  async cancelar(r: Recojo): Promise<void> {
    const motivo = await this.dialogo.pedirTexto({
      titulo: '¿Cancelar esta recepción?',
      mensaje: `${r.productorNombre} · ${r.litros} L. Queda registrada como cancelada y no se paga.`,
      etiqueta: 'Motivo de la cancelación',
      obligatorio: true,
      confirmar: 'Cancelar recepción',
      cancelar: 'Volver',
      tipo: 'peligro',
    });
    if (!motivo) return;
    this.recojoService.cancelar(r.id, motivo).subscribe({
      next: () => {
        this.notificaciones.exito('Recepción cancelada.');
        this.cargar();
      },
      error: (e) => this.notificaciones.error(mensajeDeError(e, 'No se pudo cancelar la recepción.')),
    });
  }
}
