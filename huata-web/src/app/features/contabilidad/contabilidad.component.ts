import { DecimalPipe } from '@angular/common';
import { BuscadorComponent, OpcionBuscador } from '../../shared/ui/buscador.component';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AsientoContable,
  ContabilidadService,
  LibroMayor,
  PlanCuenta,
} from '../../core/services/contabilidad.service';
import { PaginacionLocal } from '../../shared/ui/paginacion-local';
import { PaginadorComponent } from '../../shared/ui/paginador.component';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';

/**
 * Plan de cuentas, libro diario y libro mayor de la queseria (V21). Solo lectura: los asientos se
 * generan automaticamente desde Compras, Producción de queso y Ventas de queso.
 */
@Component({
  selector: 'app-contabilidad',
  standalone: true,
  imports: [PaginadorComponent, FormsModule, DecimalPipe, BuscadorComponent],
  templateUrl: './contabilidad.component.html',
})
export class ContabilidadComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de contabilidad, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['contabilidad'], () => {
    this.cargarLibroDiario();
    if (this.cuentaSeleccionada) this.cargarLibroMayor();
  });
  tab: 'diario' | 'mayor' | 'plan' = 'diario';

  desde = primerDiaDelMes();
  hasta = hoy();
  cuentaSeleccionada = '';

  readonly planCuentas = signal<PlanCuenta[]>([]);
  readonly cuentasConMovimiento = signal<PlanCuenta[]>([]);
  readonly asientos = signal<AsientoContable[]>([]);
  readonly libroMayor = signal<LibroMayor | null>(null);
  readonly pagCuentas = new PaginacionLocal();
  readonly pagMovimientos = new PaginacionLocal();
  readonly cargando = signal(false);
  readonly error = signal('');

  constructor(private readonly contabilidad: ContabilidadService) {}

  readonly opcCuentas = computed<OpcionBuscador[]>(() =>
    this.cuentasConMovimiento().map((c) => ({ valor: c.codigo, etiqueta: `${c.codigo} · ${c.nombre}` })),
  );

  ngOnInit(): void {
    this.contabilidad.planCuentas().subscribe({
      next: (c) => this.planCuentas.set(c),
      error: () => this.error.set('No se pudo cargar el plan de cuentas.'),
    });
    this.contabilidad.cuentasConMovimiento().subscribe({
      next: (c) => {
        this.cuentasConMovimiento.set(c);
        if (!this.cuentaSeleccionada && c.length > 0) {
          this.cuentaSeleccionada = c[0].codigo;
          this.cargarLibroMayor();
        }
      },
    });
    this.cargarLibroDiario();
  }

  cambiarTab(tab: 'diario' | 'mayor' | 'plan'): void {
    this.tab = tab;
    if (tab === 'diario') this.cargarLibroDiario();
    if (tab === 'mayor') this.cargarLibroMayor();
  }

  cargarLibroDiario(): void {
    this.cargando.set(true);
    this.error.set('');
    this.contabilidad.libroDiario(this.desde, this.hasta).subscribe({
      next: (a) => {
        this.asientos.set(a);
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(e.error?.message ?? 'No se pudo cargar el libro diario.');
        this.cargando.set(false);
      },
    });
  }

  cargarLibroMayor(): void {
    if (!this.cuentaSeleccionada) return;
    this.cargando.set(true);
    this.error.set('');
    this.contabilidad.libroMayor(this.cuentaSeleccionada, this.desde, this.hasta).subscribe({
      next: (l) => {
        this.libroMayor.set(l);
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(e.error?.message ?? 'No se pudo cargar el libro mayor.');
        this.cargando.set(false);
      },
    });
  }

  etiquetaOrigen(origen: string): string {
    const etiquetas: Record<string, string> = {
      COMPRA: 'Compra',
      PRODUCCION_CONSUMO: 'Producción · consumo',
      PRODUCCION_MANO_OBRA: 'Producción · mano de obra',
      PRODUCCION_TERMINADO: 'Producción · terminado',
      VENTA: 'Venta',
      VENTA_COSTO: 'Venta · costo',
      ANULACION_VENTA: 'Anulación de venta',
    };
    return etiquetas[origen] ?? origen;
  }
}

function hoy(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function primerDiaDelMes(): string {
  const d = new Date();
  d.setDate(1);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
