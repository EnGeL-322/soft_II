import { ExportacionesService } from '../../core/services/exportaciones.service';
import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
  ControlCalidad,
  EstadoIncidencia,
  IncidenciaCalidad,
  ParametroCalidad,
  Productor,
  Recojo,
  ResultadoCalidad,
} from '../../core/models/models';
import { CalidadService } from '../../core/services/calidad.service';
import { ProductoresService } from '../../core/services/productores.service';
import { RecojoService } from '../../core/services/recojo.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';

type Tab = 'registrar' | 'historial' | 'seguimiento' | 'parametros';

const CONTROL_VACIO = {
  recojoId: undefined as number | undefined,
  densidad: undefined as number | undefined,
  puntoCongelacion: undefined as number | undefined,
  acidez: undefined as number | undefined,
  grasa: undefined as number | undefined,
  solidosNoGrasos: undefined as number | undefined,
  solidosTotales: undefined as number | undefined,
  revisionVisual: '',
  resultado: 'ACEPTADA' as ResultadoCalidad,
  equipoUtilizado: '',
  responsable: '',
};

/**
 * Calidad y evidencia: registrar control, historial por proveedor y seguimiento de incidencias
 * (botones "Historial de calidad" / "Seguimiento" del UI/UX, con deep-link desde notificaciones).
 */
@Component({
  selector: 'app-calidad',
  standalone: true,
  imports: [FormsModule, DatePipe, BadgeEstadoComponent],
  templateUrl: './calidad.component.html',
})
export class CalidadComponent implements OnInit {
  fechaDiaria = new Intl.DateTimeFormat('en-CA').format(new Date());
  readonly recepciones = signal<Recojo[]>([]);
  get recepcionesDiarias() { return this.recepciones().filter(r=>r.estado!=='CANCELLED' && r.fechaHora.slice(0,10)===this.fechaDiaria); }
  cargarRecepciones(page=0) { this.recojoService.listar({page,size:200}).subscribe(pg=>{this.recepciones.update(r=>page===0?pg.content:[...r,...pg.content]);if(page+1<pg.totalPages)this.cargarRecepciones(page+1);}); }
  exportar() { this.exportaciones.descargar('HistorialCalidad').subscribe(blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='Huata_HistorialCalidad.xlsx';a.click();URL.revokeObjectURL(url);}); }

  readonly tab = signal<Tab>('seguimiento');
  readonly productores = signal<Productor[]>([]);
  readonly recojosProductor = signal<Recojo[]>([]);
  readonly historial = signal<ControlCalidad[]>([]);
  readonly seguimiento = signal<IncidenciaCalidad[]>([]);
  readonly parametros = signal<ParametroCalidad[]>([]);

  productorId?: number;
  control = { ...CONTROL_VACIO };
  nuevaIncidencia = { tipo: '', descripcion: '' };

  constructor(
    private readonly calidadService: CalidadService,
    private readonly productoresService: ProductoresService,
    private readonly recojoService: RecojoService,
    private readonly route: ActivatedRoute,
    private readonly exportaciones: ExportacionesService,
  ) {}

  ngOnInit(): void {
    this.cargarRecepciones();
    this.productoresService.listar({ size: 200 }).subscribe((pg) => this.productores.set(pg.content));
    this.calidadService.parametros().subscribe((p) => this.parametros.set(p));

    this.route.queryParamMap.subscribe((params) => {
      const tabParam = params.get('tab') as Tab | null;
      if (tabParam) {
        this.tab.set(tabParam);
      }
      const productorParam = params.get('productorId');
      if (productorParam) {
        this.productorId = Number(productorParam);
        this.cargarProductorSeleccionado();
      }
    });
  }

  cargarRecojosDeProductor(): void {
    if (!this.productorId) {
      return;
    }
    this.recojoService.listar({ productorId: this.productorId, size: 50 }).subscribe((pg) => {
      this.recojosProductor.set(pg.content.filter((r) => r.estado !== 'CANCELLED'));
    });
  }

  cargarProductorSeleccionado(): void {
    if (!this.productorId) {
      return;
    }
    this.calidadService.historial(this.productorId).subscribe((h) => this.historial.set(h));
    this.calidadService.seguimiento(this.productorId).subscribe((s) => this.seguimiento.set(s));
    this.cargarRecojosDeProductor();
  }

  registrarControl(): void {
    if (!this.control.recojoId) {
      return;
    }
    this.calidadService.registrarControl({ ...this.control, recojoId: this.control.recojoId }).subscribe(() => {
      this.control = { ...CONTROL_VACIO };
      if (this.productorId) {
        this.calidadService.historial(this.productorId).subscribe((h) => this.historial.set(h));
      }
    });
  }

  registrarIncidencia(): void {
    if (!this.productorId) {
      return;
    }
    this.calidadService
      .registrarIncidencia({ productorId: this.productorId, tipo: this.nuevaIncidencia.tipo, descripcion: this.nuevaIncidencia.descripcion })
      .subscribe(() => {
        this.nuevaIncidencia = { tipo: '', descripcion: '' };
        this.calidadService.seguimiento(this.productorId!).subscribe((s) => this.seguimiento.set(s));
      });
  }

  avanzarIncidencia(i: IncidenciaCalidad): void {
    const siguiente: EstadoIncidencia = i.estado === 'ABIERTA' ? 'EN_SEGUIMIENTO' : 'CERRADA';
    this.calidadService.cambiarEstadoIncidencia(i.id, siguiente).subscribe(() => this.cargarProductorSeleccionado());
  }
}
