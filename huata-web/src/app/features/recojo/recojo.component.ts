import { ExportacionesService } from '../../core/services/exportaciones.service';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import {
  Acopiador,
  AguaAnadida,
  Aspecto,
  EstadoRecojo,
  FuenteRecojo,
  Olor,
  PageResponse,
  Productor,
  Recojo,
  ResultadoCalidad,
  ResultadoLugol,
  Ruta,
  TarifaLeche,
} from '../../core/models/models';
import { AcopiadoresService } from '../../core/services/acopiadores.service';
import { CalidadService } from '../../core/services/calidad.service';
import { LiquidacionesService } from '../../core/services/liquidaciones.service';
import { ProductoresService } from '../../core/services/productores.service';
import { RecojoService } from '../../core/services/recojo.service';
import { RutasService } from '../../core/services/rutas.service';
import { UbicacionesService } from '../../core/services/ubicaciones.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';
import { PaginadorComponent } from '../../shared/ui/paginador.component';

/** No enviar mas de una posicion cada X segundos aunque el navegador reporte cambios mas seguido. */
const INTERVALO_MINIMO_MS = 15_000;

interface FormRecepcion {
  productorId?: number;
  fuente: FuenteRecojo;
  rutaId?: number;
  acopiadorId?: number;
  fechaHoraLocal: string;
  litros?: number;
  numeroEnvases?: number;
  capacidadReferenciaL?: number;
  temperatura?: number;
  densidad?: number;
  puntoCongelacion?: number;
  aguaAnadida: AguaAnadida;
  lugol: ResultadoLugol;
  resultado: ResultadoCalidad;
  olor: Olor;
  aspecto: Aspecto;
  motivoRechazo?: string;
  litrosAppMovil?: number;
  responsableRecepcion?: string;
  revisionVisual: string;
  equipoUtilizado?: string;
  responsableCalidad?: string;
}

const FORM_VACIO: FormRecepcion = {
  fuente: 'RUTA',
  fechaHoraLocal: new Date(Date.now()-new Date().getTimezoneOffset()*60000).toISOString().slice(0,16),
  aguaAnadida: 'NO_EVIDENCIADA',
  lugol: 'NEGATIVA',
  resultado: 'ACEPTADA',
  olor: 'NORMAL',
  aspecto: 'NORMAL',
  revisionVisual: 'Recepción registrada en planta de acopio.',
};

/**
 * Recepción y acopio: registro compacto por proveedor con precio, resultado y control de calidad
 * en un solo formulario — igual que el prototipo — pero guardando en dos recursos reales
 * (Recojo + ControlCalidad) en vez de un solo array en memoria.
 */
@Component({
  selector: 'app-recojo',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, BadgeEstadoComponent, PaginadorComponent],
  templateUrl: './recojo.component.html',
})
export class RecojoComponent implements OnInit, OnDestroy {
  exportar() { this.exportaciones.descargar('Recepciones').subscribe(blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='Huata_Recepciones.xlsx';a.click();URL.revokeObjectURL(url);}); }

  readonly pagina = signal<PageResponse<Recojo> | null>(null);
  readonly productores = signal<Productor[]>([]);
  readonly rutas = signal<Ruta[]>([]);
  readonly acopiadores = signal<Acopiador[]>([]);
  readonly tarifas = signal<TarifaLeche[]>([]);
  readonly mostrarForm = signal(false);
  readonly guardando = signal(false);
  readonly errorForm = signal('');

  readonly compartiendoGps = signal(false);
  readonly gpsMensaje = signal('');
  readonly gpsError = signal(false);
  private watchId: number | null = null;
  private ultimoEnvioGps = 0;
  private consulta?: Subscription;
  recepcionGuardadaId: number | null = null;

  readonly hoyTexto = new Date().toLocaleDateString('es-PE', { day: '2-digit', month: 'short' });

  filtroProductorId?: number;
  filtroEstado?: EstadoRecojo;
  filtroTexto = '';
  filtroResultado?: ResultadoCalidad | 'PENDIENTE';
  filtroFuente?: FuenteRecojo;
  page = 0;

  form: FormRecepcion = { ...FORM_VACIO };

  readonly tarifaVigente = computed<TarifaLeche | null>(() => {
    const lista = [...this.tarifas()].sort((a, b) => (a.vigenteDesde < b.vigenteDesde ? 1 : -1));
    return lista[0] ?? null;
  });

  readonly filaVisibles = computed(() => this.pagina()?.content ?? []);

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

  constructor(
    private readonly recojoService: RecojoService,
    private readonly calidadService: CalidadService,
    private readonly productoresService: ProductoresService,
    private readonly rutasService: RutasService,
    private readonly acopiadoresService: AcopiadoresService,
    private readonly liquidacionesService: LiquidacionesService,
    private readonly ubicacionesService: UbicacionesService,
    private readonly route: ActivatedRoute,
    private readonly exportaciones: ExportacionesService,
  ) {}

  ngOnInit(): void {
    this.productoresService.listar({ size: 200 }).subscribe((pg) => this.productores.set(pg.content));
    this.rutasService.listar().subscribe((pg) => this.rutas.set(pg.content));
    this.acopiadoresService.listar('', 0, 200).subscribe((pg) => this.acopiadores.set(pg.content));
    this.liquidacionesService.tarifas().subscribe((t) => this.tarifas.set(t));
    this.buscar();
    if (this.route.snapshot.queryParamMap.get('nuevo')) {
      this.abrirFormulario();
    }
  }

  ngOnDestroy(): void {
    this.consulta?.unsubscribe();
    this.detenerGps();
  }

  /** GPS real via la Geolocation API del navegador (no una simulación): pide permiso nativo al acopiador. */
  alternarGps(): void {
    if (this.compartiendoGps()) {
      this.detenerGps();
      return;
    }
    if (!('geolocation' in navigator)) {
      this.gpsError.set(true);
      this.gpsMensaje.set('Este navegador no soporta geolocalización.');
      return;
    }
    this.gpsError.set(false);
    this.gpsMensaje.set('Solicitando permiso de ubicación…');
    this.watchId = navigator.geolocation.watchPosition(
      (posicion) => this.onPosicion(posicion),
      (error) => {
        this.gpsError.set(true);
        this.gpsMensaje.set('No se pudo obtener tu ubicación: ' + error.message);
        this.compartiendoGps.set(false);
      },
      { enableHighAccuracy: true, maximumAge: 10_000, timeout: 20_000 },
    );
    this.compartiendoGps.set(true);
  }

  private detenerGps(): void {
    if (this.watchId !== null) {
      navigator.geolocation.clearWatch(this.watchId);
      this.watchId = null;
    }
    this.compartiendoGps.set(false);
    this.gpsMensaje.set('Se dejó de compartir la ubicación.');
    this.gpsError.set(false);
  }

  private onPosicion(posicion: GeolocationPosition): void {
    const ahora = Date.now();
    if (ahora - this.ultimoEnvioGps < INTERVALO_MINIMO_MS) {
      return;
    }
    this.ultimoEnvioGps = ahora;
    const { latitude, longitude, accuracy, speed } = posicion.coords;
    this.ubicacionesService.reportar(latitude, longitude, accuracy, speed ?? undefined).subscribe({
      next: () => {
        this.gpsError.set(false);
        this.gpsMensaje.set('Compartiendo ubicación · última actualización ' + new Date().toLocaleTimeString());
      },
      error: (err) => {
        this.gpsError.set(true);
        this.gpsMensaje.set(err?.error?.message ?? 'No se pudo enviar la ubicación al servidor.');
      },
    });
  }

  buscar(): void {
    this.page = 0;
    this.cargar();
  }

  cambiarPagina(p: number): void {
    this.page = p;
    this.cargar();
  }

  private cargar(): void {
    this.consulta?.unsubscribe();
    this.consulta = this.recojoService
      .listar({ productorId: this.filtroProductorId, estado: this.filtroEstado, page: this.page, size: 50, q: this.filtroTexto, fuente: this.filtroFuente, resultado: this.filtroResultado })
      .subscribe((pg) => this.pagina.set(pg));
  }

  abrirFormulario(): void {
    this.recepcionGuardadaId = null;
    this.form = { ...FORM_VACIO, fechaHoraLocal: new Date(Date.now()-new Date().getTimezoneOffset()*60000).toISOString().slice(0,16) };
    this.errorForm.set('');
    this.mostrarForm.set(true);
  }

  /** Auto-eleva el resultado si agua añadida o lugol indican algo peor que lo seleccionado (igual que el prototipo). */
  actualizarDecision(): void {
    const severidad: Record<ResultadoCalidad, number> = { ACEPTADA: 0, OBSERVADA: 1, RECHAZADA: 2 };
    let peor = this.form.resultado;
    if (this.form.aguaAnadida === 'CONFIRMADA' || this.form.lugol === 'POSITIVA') {
      peor = 'RECHAZADA';
    } else if (
      (this.form.aguaAnadida === 'SOSPECHA' || this.form.lugol === 'NO_REALIZADA') &&
      severidad[peor] < severidad['OBSERVADA']
    ) {
      peor = 'OBSERVADA';
    }
    this.form.resultado = peor;
  }

  decisionTexto(): string {
    switch (this.form.resultado) {
      case 'ACEPTADA':
        return 'Entrega apta.';
      case 'OBSERVADA':
        return 'Entrega observada: aplica tarifa reducida.';
      default:
        return 'Entrega rechazada: no se liquida a precio normal.';
    }
  }

  precioAplicar(): number | null {
    const tarifa = this.tarifaVigente();
    if (!tarifa) return null;
    return this.form.resultado === 'ACEPTADA' ? tarifa.precioNormal : tarifa.precioObservada;
  }

  montoEstimado(): number | null {
    const precio = this.precioAplicar();
    if (precio == null || !this.form.litros) return null;
    return precio * this.form.litros;
  }

  registrar(): void {
    if (this.recepcionGuardadaId !== null) return;
    if (!this.form.productorId || !this.form.litros) {
      this.errorForm.set('Selecciona el proveedor y registra los litros recibidos.');
      return;
    }
    this.errorForm.set('');
    this.guardando.set(true);
    this.recojoService
      .registrar({
        productorId: this.form.productorId,
        rutaId: this.form.rutaId,
        acopiadorId: this.form.acopiadorId,
        fechaHora: new Date(this.form.fechaHoraLocal).toISOString(),
        litros: this.form.litros,
        temperatura: this.form.temperatura,
        fuente: this.form.fuente,
        numeroEnvases: this.form.numeroEnvases,
        capacidadReferenciaL: this.form.capacidadReferenciaL,
        responsableRecepcion: this.form.responsableRecepcion,
        litrosAppMovil: this.form.litrosAppMovil,
      })
      .subscribe({
        next: (recojo) => {
          this.recepcionGuardadaId = recojo.id;
          this.calidadService
            .registrarControl({
              recojoId: recojo.id,
              densidad: this.form.densidad,
              puntoCongelacion: this.form.puntoCongelacion,
              revisionVisual: this.form.revisionVisual,
              resultado: this.form.resultado,
              equipoUtilizado: this.form.equipoUtilizado,
              responsable: this.form.responsableCalidad,
              aguaAnadida: this.form.aguaAnadida,
              lugol: this.form.lugol,
              olor: this.form.olor,
              aspecto: this.form.aspecto,
              motivoRechazo: this.form.resultado === 'RECHAZADA' ? this.form.motivoRechazo : undefined,
            })
            .subscribe({
              next: () => {
                this.guardando.set(false);
                this.mostrarForm.set(false);
                this.buscar();
              },
              error: (e) => {
                this.guardando.set(false);
                this.errorForm.set(
                  'La recepción se guardó, pero no se pudo registrar la calidad: ' +
                    (e?.error?.message ?? 'intenta desde Calidad y evidencia.'),
                );
                this.buscar();
              },
            });
        },
        error: (e) => {
          this.guardando.set(false);
          this.errorForm.set(e?.error?.message ?? 'No se pudo registrar la recepción.');
        },
      });
  }

  cancelar(r: Recojo): void {
    const motivo = window.prompt('Motivo de la cancelación:');
    if (!motivo) {
      return;
    }
    this.recojoService.cancelar(r.id, motivo).subscribe(() => this.cargar());
  }
}
