import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Recojo } from '../../../core/models/models';
import { RecojoService } from '../../../core/services/recojo.service';
import { BadgeEstadoComponent } from '../../../shared/ui/badge-estado.component';
import { GraficoBarrasComponent } from '../../../shared/ui/grafico-barras.component';
import { GraficoLineasComponent } from '../../../shared/ui/grafico-lineas.component';
import { recargarEnVivo } from '../../../core/services/cambios-tiempo-real.service';

/** Días hacia atrás que se analizan. Antes se descargaba todo el historial al abrir el padrón. */
const PERIODOS = [30, 90, 365] as const;

/**
 * Indicadores y gráficos del acopio por proveedor. Vivía dentro del padrón y se cargaba siempre, aun
 * sin mirarlo; ahora solo se monta (y pide los recojos) al pulsar "Reportes", y por un periodo.
 * Solo usa registros existentes: lo que no está en la evidencia no se inventa.
 */
@Component({
  selector: 'app-reporte-proveedores',
  standalone: true,
  imports: [FormsModule, DecimalPipe, BadgeEstadoComponent, GraficoLineasComponent, GraficoBarrasComponent],
  templateUrl: './reporte-proveedores.component.html',
})
export class ReporteProveedoresComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de proveedores, acopio, calidad, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['proveedores', 'acopio', 'calidad'], () => this.cargar());
  private readonly recojos = inject(RecojoService);

  readonly periodos = PERIODOS;
  readonly dias = signal<number>(PERIODOS[0]);
  readonly texto = signal('');
  readonly dia = signal('');
  readonly cargando = signal(false);
  readonly error = signal('');
  private readonly registros = signal<Recojo[]>([]);

  readonly filas = computed(() => {
    const t = this.texto().trim().toLowerCase();
    return this.registros().filter(
      (r) => r.estado !== 'CANCELLED' && (!t || `${r.productorNombre} ${r.rutaNombre ?? ''}`.toLowerCase().includes(t)),
    );
  });
  readonly filasDia = computed(() => this.filas().filter((r) => !this.dia() || r.fechaHora.slice(0, 10) === this.dia()));
  readonly proveedores = computed(() => new Set(this.filas().map((r) => r.productorId)).size);
  readonly litros = computed(() => this.filas().reduce((s, r) => s + r.litros, 0));
  readonly calidadApta = computed(() => {
    const controladas = this.filasDia().filter((r) => r.resultadoCalidad);
    return controladas.length
      ? (controladas.filter((r) => r.resultadoCalidad === 'ACEPTADA').length / controladas.length) * 100
      : 0;
  });
  readonly observaciones = computed(
    () => this.filasDia().filter((r) => r.resultadoCalidad === 'OBSERVADA' || r.resultadoCalidad === 'RECHAZADA').length,
  );
  readonly distribucion = computed(() =>
    (['ACEPTADA', 'OBSERVADA', 'RECHAZADA'] as const).map((nombre) => ({
      nombre,
      cantidad: this.filasDia().filter((r) => r.resultadoCalidad === nombre).length,
    })),
  );
  readonly serie = computed(() =>
    sumarPor(this.filas(), (r) => r.fechaHora.slice(0, 10)).sort((a, b) => a.etiqueta.localeCompare(b.etiqueta)),
  );
  readonly porZona = computed(() => {
    const zonas = new Map<string, Set<number>>();
    this.filasDia().forEach((r) => {
      const z = r.rutaNombre ?? 'Sin ruta';
      if (!zonas.has(z)) zonas.set(z, new Set());
      zonas.get(z)!.add(r.productorId);
    });
    return [...zonas].map(([etiqueta, p]) => ({ etiqueta, valor: p.size }));
  });
  readonly ranking = computed(() =>
    sumarPor(this.filasDia(), (r) => r.productorNombre)
      .sort((a, b) => b.valor - a.valor)
      .slice(0, 10),
  );

  ngOnInit(): void {
    this.cargar();
  }

  cambiarPeriodo(dias: number): void {
    this.dias.set(dias);
    this.cargar();
  }

  private cargar(): void {
    this.registros.set([]);
    this.dia.set('');
    this.error.set('');
    this.cargando.set(true);
    this.pagina(0, fechaLocal(-this.dias()));
  }

  private pagina(page: number, desde: string): void {
    this.recojos.listar({ desde, page, size: 200 }).subscribe({
      next: (pg) => {
        this.registros.update((rows) => [...rows, ...pg.content]);
        if (page + 1 < pg.totalPages) {
          this.pagina(page + 1, desde);
          return;
        }
        this.cargando.set(false);
        this.dia.set([...this.registros()].map((r) => r.fechaHora.slice(0, 10)).sort().at(-1) ?? '');
      },
      error: () => {
        this.cargando.set(false);
        this.error.set('No se pudieron cargar los recojos del periodo.');
      },
    });
  }
}

function sumarPor(filas: Recojo[], clave: (r: Recojo) => string): { etiqueta: string; valor: number }[] {
  const mapa = new Map<string, number>();
  filas.forEach((r) => mapa.set(clave(r), (mapa.get(clave(r)) ?? 0) + r.litros));
  return [...mapa].map(([etiqueta, valor]) => ({ etiqueta, valor }));
}

/** Fecha local (no UTC) desplazada `dias` respecto de hoy, en formato AAAA-MM-DD. */
function fechaLocal(dias: number): string {
  const d = new Date();
  d.setDate(d.getDate() + dias);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
