import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, signal, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { PERMISO } from '../../core/config/navegacion';
import { TarifaLeche } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { LiquidacionesService } from '../../core/services/liquidaciones.service';
import { ReglasLeche, ReglasLecheService } from '../../core/services/reglas-leche.service';
import { ReglasAcopio, ReglasAcopioService } from '../../core/services/reglas-acopio.service';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { VerificacionService } from '../../core/services/verificacion.service';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { VencimientoProductosComponent } from './vencimiento-productos.component';

/**
 * "Reglas de la leche": cuánto se paga por litro (normal, mala calidad, excelencia), cuándo se retira
 * a un proveedor por mala calidad, si la leche observada entra al almacén y cuánto cuesta la leche
 * para producir. Todo lo que el cliente pidió que se pueda configurar sin tocar código.
 */
@Component({
  selector: 'app-reglas-leche',
  standalone: true,
  imports: [FormsModule, DecimalPipe, VencimientoProductosComponent],
  templateUrl: './reglas-leche.component.html',
  styleUrl: './reglas-leche.component.css',
})
export class ReglasLecheComponent implements OnInit {
  protected readonly notificaciones = inject(NotificacionesService);
  private readonly reglasAcopioService = inject(ReglasAcopioService);
  private readonly dialogo = inject(DialogoService);
  readonly hoy = hoyLocal();
  readonly tarifas = signal<TarifaLeche[]>([]);
  readonly reglas = signal<ReglasLeche | null>(null);
  readonly guardandoReglas = signal(false);
  readonly guardandoTarifa = signal(false);
  readonly error = signal('');

  // Formulario de reglas (copia editable de lo guardado).
  detecciones = 2;
  observadaEsMalaCalidad = true;
  observadaEntraAInventario = true;
  usarCostoFijo = false;
  costoFijo: number | null = null;

  // Reglas de acopio (V57): geocerca y distancia, se apagan para demostraciones.
  readonly reglasAcopio = signal<ReglasAcopio | null>(null);
  readonly guardandoAcopio = signal(false);
  exigirGeocerca = true;
  exigirDistancia = true;
  distanciaMaxima = 200;

  // Formulario de tarifa nueva.
  desde = hoyLocal();
  precioNormal: number | null = null;
  precioMalaCalidad: number | null = null;
  precioExcelencia: number | null = null;
  codigo = '';
  readonly avisoCodigo = signal('');

  /** La tarifa que rige hoy: la más reciente que ya empezó. */
  readonly tarifaVigente = computed<TarifaLeche | null>(
    () =>
      [...this.tarifas()]
        .filter((t) => t.vigenteDesde <= this.hoy && (!t.vigenteHasta || t.vigenteHasta >= this.hoy))
        .sort((a, b) => b.vigenteDesde.localeCompare(a.vigenteDesde))[0] ?? null,
  );

  /** Tarifas que ya no rigen hoy (pasadas o programadas), de la más reciente a la más antigua. */
  readonly tarifasAnteriores = computed(() =>
    [...this.tarifas()]
      .filter((t) => t.id !== this.tarifaVigente()?.id)
      .sort((a, b) => b.vigenteDesde.localeCompare(a.vigenteDesde)),
  );

  constructor(
    private readonly reglasService: ReglasLecheService,
    private readonly liquidaciones: LiquidacionesService,
    private readonly verificacion: VerificacionService,
    protected readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  puedeEditar(): boolean {
    return this.auth.puede('parametros.write');
  }

  puedeVerTarifas(): boolean {
    return this.auth.puede(PERMISO.pagosRead) || this.auth.puede('parametros.write');
  }

  cargar(): void {
    this.reglasAcopioService.vigentes().subscribe({
      next: (r) => this.aplicarAcopio(r),
      error: () => this.error.set('No se pudieron cargar las reglas de acopio.'),
    });
    this.reglasService.vigentes().subscribe({
      next: (r) => this.aplicar(r),
      error: (e) => this.error.set(e.error?.message ?? 'No se pudieron cargar las reglas.'),
    });
    if (this.puedeVerTarifas()) {
      this.liquidaciones.tarifas().subscribe({
        next: (t) => {
          this.tarifas.set(t);
          const v = this.tarifaVigente();
          if (v) {
            this.precioNormal = v.precioNormal;
            this.precioMalaCalidad = v.precioObservada;
            this.precioExcelencia = v.precioExcelencia ?? null;
          }
        },
      });
    }
  }

  private aplicar(r: ReglasLeche): void {
    this.reglas.set(r);
    this.detecciones = r.deteccionesParaRetiro;
    this.observadaEsMalaCalidad = r.observadaEsMalaCalidad;
    this.observadaEntraAInventario = r.observadaEntraAInventario;
    this.usarCostoFijo = r.costoLecheLitro != null;
    this.costoFijo = r.costoLecheLitro;
  }

  private aplicarAcopio(r: ReglasAcopio): void {
    this.reglasAcopio.set(r);
    this.exigirGeocerca = r.exigirGeocerca;
    this.exigirDistancia = r.exigirDistancia;
    this.distanciaMaxima = r.distanciaMaximaMetros;
  }

  /** Las dos reglas de ubicación apagadas: modo demostración. */
  acopioSinRestricciones(): boolean {
    const r = this.reglasAcopio();
    return !!r && !r.exigirGeocerca && !r.exigirDistancia;
  }

  async guardarReglasAcopio(): Promise<void> {
    if (!this.puedeEditar() || this.guardandoAcopio()) return;
    if (!this.distanciaMaxima || this.distanciaMaxima < 20 || this.distanciaMaxima > 5000) {
      this.error.set('La distancia al proveedor debe estar entre 20 y 5000 metros.');
      return;
    }
    const apaga =
      (!this.exigirGeocerca && this.reglasAcopio()?.exigirGeocerca) ||
      (!this.exigirDistancia && this.reglasAcopio()?.exigirDistancia);
    const ok = await this.dialogo.confirmar({
      titulo: apaga ? '¿Apagar reglas de ubicación del acopio?' : '¿Guardar las reglas de acopio?',
      mensaje: apaga
        ? 'Los acopiadores podrán registrar leche fuera de su zona o lejos del proveedor. Úsalo solo para demostraciones y vuelve a encenderlas después.'
        : 'Se aplican desde ahora en la app de los acopiadores.',
      confirmar: apaga ? 'Sí, apagar' : 'Sí, guardar',
      cancelar: 'Cancelar',
      tipo: apaga ? 'peligro' : 'confirmacion',
    });
    if (!ok) {
      const r = this.reglasAcopio();
      if (r) this.aplicarAcopio(r);
      return;
    }
    this.error.set('');
    this.guardandoAcopio.set(true);
    this.reglasAcopioService
      .guardar({
        exigirGeocerca: this.exigirGeocerca,
        exigirDistancia: this.exigirDistancia,
        distanciaMaximaMetros: Math.round(this.distanciaMaxima),
      })
      .pipe(finalize(() => this.guardandoAcopio.set(false)))
      .subscribe({
        next: (r) => {
          this.aplicarAcopio(r);
          this.notificaciones.exito('Reglas de acopio guardadas. La app las toma al abrir la ruta.');
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudieron guardar las reglas de acopio.'),
      });
  }

  /** "2026-10-15" -> "15/10/2026". */
  fechaLegible(iso: string): string {
    const [a, m, d] = iso.slice(0, 10).split('-');
    return `${d}/${m}/${a}`;
  }

  /** Ayuda bajo cada precio nuevo: compara con el que rige hoy. */
  comparar(nuevo: number | null, actual: number | null | undefined): string {
    if (actual == null) return '';
    const hoy = `Hoy: S/ ${actual.toFixed(2)}.`;
    if (nuevo == null || nuevo === actual) return hoy;
    const dif = nuevo - actual;
    return `${hoy} ${dif > 0 ? 'Sube' : 'Baja'} S/ ${Math.abs(dif).toFixed(2)}.`;
  }

  retiroActivo(): boolean {
    return !!this.detecciones && this.detecciones > 0;
  }

  /** Frase que resume la regla de retiro, para confirmar que se entendió. */
  textoRetiro(): string {
    if (!this.detecciones || this.detecciones <= 0) {
      return 'Nunca se retira automáticamente: administración decide cada caso.';
    }
    const que = this.observadaEsMalaCalidad ? 'leche observada o rechazada' : 'leche rechazada';
    return `Al registrar ${this.detecciones} ${this.detecciones === 1 ? 'vez' : 'veces'} ${que}, el proveedor queda retirado y ya no puede entregar.`;
  }

  guardarReglas(): void {
    if (!this.puedeEditar() || this.guardandoReglas()) return;
    if (this.detecciones == null || this.detecciones < 0 || this.detecciones > 20) {
      this.error.set('El número de detecciones debe estar entre 0 y 20.');
      return;
    }
    if (this.usarCostoFijo && (!this.costoFijo || this.costoFijo <= 0)) {
      this.error.set('Escribe el costo por litro (mayor que cero) o elige usar la tarifa del día.');
      return;
    }
    this.error.set('');
    this.guardandoReglas.set(true);
    this.reglasService
      .guardar({
        deteccionesParaRetiro: Math.round(this.detecciones),
        observadaEsMalaCalidad: this.observadaEsMalaCalidad,
        observadaEntraAInventario: this.observadaEntraAInventario,
        costoLecheLitro: this.usarCostoFijo ? this.costoFijo : null,
      })
      .pipe(finalize(() => this.guardandoReglas.set(false)))
      .subscribe({
        next: (r) => {
          this.aplicar(r);
          this.notificaciones.exito('Se guardaron las reglas. Se aplican desde ahora.');
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudieron guardar las reglas.'),
      });
  }

  solicitarCodigo(): void {
    this.verificacion.solicitarCodigo('PRECIOS').subscribe({
      next: (r) =>
        this.avisoCodigo.set(
          `Código enviado a ${r.destinatarioEnmascarado}. Vence en ${r.minutosVigencia} minutos.`,
        ),
      error: (e) => this.avisoCodigo.set(e.error?.message ?? 'No se pudo enviar el código.'),
    });
  }

  guardarTarifa(): void {
    if (!this.puedeEditar() || this.guardandoTarifa()) return;
    if (
      !this.desde ||
      !this.precioNormal ||
      this.precioNormal <= 0 ||
      this.precioMalaCalidad == null ||
      this.precioMalaCalidad < 0 ||
      !/^\d{6}$/.test(this.codigo)
    ) {
      this.error.set('Revisa la fecha, los precios y el código de 6 dígitos que llegó a tu correo.');
      return;
    }
    if (this.precioMalaCalidad > this.precioNormal) {
      this.error.set('El precio por leche de mala calidad no puede ser mayor que el normal.');
      return;
    }
    this.error.set('');
    this.guardandoTarifa.set(true);
    this.liquidaciones
      .guardarTarifa({
        precioNormal: this.precioNormal,
        precioObservada: this.precioMalaCalidad,
        precioExcelencia: this.precioExcelencia ?? undefined,
        vigenteDesde: this.desde,
        codigo: this.codigo,
      })
      .pipe(finalize(() => this.guardandoTarifa.set(false)))
      .subscribe({
        next: () => {
          this.codigo = '';
          this.avisoCodigo.set('');
          this.notificaciones.exito(`Nuevos precios guardados: rigen desde el ${this.desde}.`);
          this.cargar();
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudieron guardar los precios.'),
      });
  }
}

function hoyLocal(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
