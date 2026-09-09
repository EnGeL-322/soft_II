import { DatePipe, DecimalPipe } from '@angular/common';
import { finalize } from 'rxjs';
import { ExportacionesService } from '../../core/services/exportaciones.service';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EstadoLiquidacion, Liquidacion, PageResponse, Productor, TarifaLeche } from '../../core/models/models';
import { LiquidacionesService } from '../../core/services/liquidaciones.service';
import { ProductoresService } from '../../core/services/productores.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';
import { PaginadorComponent } from '../../shared/ui/paginador.component';

@Component({
  selector: 'app-liquidaciones',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, BadgeEstadoComponent, PaginadorComponent],
  templateUrl: './liquidaciones.component.html',
})
export class LiquidacionesComponent implements OnInit {
  readonly tab = signal<'liquidaciones' | 'tarifas'>('liquidaciones');
  readonly pagina = signal<PageResponse<Liquidacion> | null>(null);
  readonly productores = signal<Productor[]>([]);
  readonly tarifas = signal<TarifaLeche[]>([]);
  readonly ultimaCalculada = signal<Liquidacion | null>(null);
  readonly error = signal('');
  readonly guardando = signal(false);
  vista: 'unico' | 'global' | 'reportes' = 'unico';
  readonly ciclos = Array.from({ length: 52 }, (_, i) => {
    const inicio = new Date();
    inicio.setDate(inicio.getDate() - ((inicio.getDay() + 3) % 7) - i * 7);
    const fin = new Date(inicio); fin.setDate(fin.getDate() + 6);
    const local = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    return { inicio: local(inicio), fin: local(fin) };
  });
  ciclo = 0;
  get precioAplicado() { const l = this.ultimaCalculada(); return l?.litrosTotales ? l.montoTotal / l.litrosTotales : null; }
  seleccionarCiclo() { this.calc.periodoInicio = this.ciclos[this.ciclo].inicio; this.calc.periodoFin = this.ciclos[this.ciclo].fin; this.ultimaCalculada.set(null); }
  verDetalle(l: Liquidacion) { this.liquidacionesService.obtener(l.id).subscribe({ next: detalle => { this.ultimaCalculada.set(detalle); this.vista = 'unico'; }, error: () => this.error.set('No se pudo cargar el detalle.') }); }
  imprimir() { window.print(); }
  exportar() { this.exportaciones.descargar('Pagos').subscribe({ next: blob => { const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = 'Huata_Pagos.xlsx'; a.click(); URL.revokeObjectURL(url); }, error: () => this.error.set('No se pudo exportar el reporte.') }); }

  filtroEstado?: EstadoLiquidacion;
  page = 0;

  calc: { productorId?: number; periodoInicio: string; periodoFin: string } = { periodoInicio: '', periodoFin: '' };
  nuevaTarifa: { precioNormal?: number; precioObservada?: number; vigenteDesde: string } = { vigenteDesde: '' };

  constructor(
    private readonly liquidacionesService: LiquidacionesService,
    private readonly productoresService: ProductoresService,
    private readonly exportaciones: ExportacionesService,
  ) {}

  ngOnInit(): void {
    this.seleccionarCiclo();
    this.productoresService.listar({ size: 200 }).subscribe((pg) => this.productores.set(pg.content));
    this.liquidacionesService.tarifas().subscribe((t) => this.tarifas.set(t));
    this.buscar();
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
    this.liquidacionesService.listar({ estado: this.filtroEstado, page: this.page }).subscribe((pg) => this.pagina.set(pg));
  }

  calcular(): void {
    if (!this.calc.productorId || this.guardando()) {
      return;
    }
    this.error.set('');
    this.guardando.set(true);
    this.liquidacionesService
      .calcular({ productorId: this.calc.productorId, periodoInicio: this.calc.periodoInicio, periodoFin: this.calc.periodoFin })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({ next: (l) => {
        this.ultimaCalculada.set(l);
        this.cargar();
      }, error: e => this.error.set(e.error?.message ?? 'No se pudo calcular la liquidación.') });
  }

  cambiarEstado(l: Liquidacion, estado: EstadoLiquidacion): void {
    this.liquidacionesService.cambiarEstado(l.id, estado).subscribe({ next: detalle => { this.ultimaCalculada.set(detalle); this.cargar(); }, error: e => this.error.set(e.error?.message ?? 'No se pudo actualizar la liquidación.') });
  }

  crearTarifa(): void {
    if (this.nuevaTarifa.precioNormal == null || this.nuevaTarifa.precioObservada == null) {
      return;
    }
    this.liquidacionesService
      .crearTarifa({
        precioNormal: this.nuevaTarifa.precioNormal,
        precioObservada: this.nuevaTarifa.precioObservada,
        vigenteDesde: this.nuevaTarifa.vigenteDesde,
      })
      .subscribe(() => {
        this.nuevaTarifa = { vigenteDesde: '' };
        this.liquidacionesService.tarifas().subscribe((t) => this.tarifas.set(t));
      });
  }
}
