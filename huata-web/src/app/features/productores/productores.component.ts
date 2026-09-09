import { DecimalPipe } from '@angular/common';
import { Recojo } from '../../core/models/models';
import { RecojoService } from '../../core/services/recojo.service';
import { ExportacionesService } from '../../core/services/exportaciones.service';
import { GraficoLineasComponent } from '../../shared/ui/grafico-lineas.component';
import { GraficoBarrasComponent } from '../../shared/ui/grafico-barras.component';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { EstadoProductor, PageResponse, Productor, Ruta } from '../../core/models/models';
import { ProductoresService, ProductorRequest } from '../../core/services/productores.service';
import { RutasService } from '../../core/services/rutas.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';
import { PaginadorComponent } from '../../shared/ui/paginador.component';

const VACIO: ProductorRequest = { codigo: '', nombres: '', apellidos: '', documentoIdentidad: '', telefono: '', notas: '' };

@Component({
  selector: 'app-productores',
  standalone: true,
  imports: [FormsModule, DecimalPipe, BadgeEstadoComponent, PaginadorComponent, GraficoLineasComponent, GraficoBarrasComponent],
  templateUrl: './productores.component.html',
})
export class ProductoresComponent implements OnInit {
  vista: 'charts' | 'data' = 'charts';
  analisisTexto = '';
  diaAnalisis = '';
  readonly recepciones = signal<Recojo[]>([]);
  cargarAnalisis(page = 0) {
    this.recojosService.listar({page, size: 200}).subscribe(pg => {
      this.recepciones.update(rows => page === 0 ? pg.content : [...rows, ...pg.content]);
      if (page + 1 < pg.totalPages) this.cargarAnalisis(page + 1);
      else if (!this.diaAnalisis) this.diaAnalisis = [...this.recepciones()].map(r => r.fechaHora.slice(0, 10)).sort().at(-1) ?? '';
    });
  }
  get filasAnalisis() { return this.recepciones().filter(r => r.estado !== 'CANCELLED' && (!this.analisisTexto || (r.productorNombre+' '+r.rutaNombre).toLowerCase().includes(this.analisisTexto.toLowerCase()))); }
  get filasDia() { return this.filasAnalisis.filter(r => !this.diaAnalisis || r.fechaHora.slice(0,10) === this.diaAnalisis); }
  get totalAnalisis() { return this.filasAnalisis.reduce((s,r) => s+r.litros,0); }
  get calidadApta() { const controladas=this.filasDia.filter(r=>r.resultadoCalidad);return controladas.length ? controladas.filter(r=>r.resultadoCalidad==='ACEPTADA').length/controladas.length*100:0; }
  get observacionesAnalisis() { return this.filasDia.filter(r=>r.resultadoCalidad==='OBSERVADA'||r.resultadoCalidad==='RECHAZADA').length; }
  get distribucion() { return ['ACEPTADA','OBSERVADA','RECHAZADA'].map(nombre=>({nombre,cantidad:this.filasDia.filter(r=>r.resultadoCalidad===nombre).length})); }
  get serieAnalisis() { const map=new Map<string,number>();this.filasAnalisis.forEach(r=>{const d=r.fechaHora.slice(0,10);map.set(d,(map.get(d)??0)+r.litros);});return [...map].sort(([a],[b])=>a.localeCompare(b)).map(([etiqueta,valor])=>({etiqueta,valor})); }
  get zonasAnalisis() { const map=new Map<string,Set<number>>();this.filasDia.forEach(r=>{const z=r.rutaNombre??'Sin ruta';if(!map.has(z))map.set(z,new Set());map.get(z)!.add(r.productorId);});return [...map].map(([etiqueta,p])=>({etiqueta,valor:p.size})); }
  get rankingAnalisis() { const map=new Map<string,number>();this.filasDia.forEach(r=>map.set(r.productorNombre,(map.get(r.productorNombre)??0)+r.litros));return [...map].map(([etiqueta,valor])=>({etiqueta,valor})).sort((a,b)=>b.valor-a.valor).slice(0,10); }
  exportar() { this.exportaciones.descargar('Proveedores').subscribe(blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='Huata_Proveedores.xlsx';a.click();URL.revokeObjectURL(url);}); }

  readonly pagina = signal<PageResponse<Productor> | null>(null);
  readonly rutas = signal<Ruta[]>([]);
  readonly mostrarForm = signal(false);
  readonly editandoId = signal<number | null>(null);

  q = '';
  estado?: EstadoProductor;
  rutaId?: number;
  page = 0;
  form: ProductorRequest = { ...VACIO };

  constructor(
    private readonly productoresService: ProductoresService,
    private readonly rutasService: RutasService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly recojosService: RecojoService,
    private readonly exportaciones: ExportacionesService,
  ) {}

  ngOnInit(): void {
    this.cargarAnalisis();
    this.rutasService.listar().subscribe((pg) => this.rutas.set(pg.content));
    this.buscar();
    if (this.route.snapshot.queryParamMap.get('nuevo')) {
      this.nuevo();
    }
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
    this.productoresService
      .listar({ q: this.q, estado: this.estado, rutaId: this.rutaId, page: this.page })
      .subscribe((pg) => this.pagina.set(pg));
  }

  nuevo(): void {
    this.editandoId.set(null);
    this.form = { ...VACIO };
    this.mostrarForm.set(true);
  }

  editar(p: Productor): void {
    this.editandoId.set(p.id);
    this.form = {
      codigo: p.codigo,
      nombres: p.nombres,
      apellidos: p.apellidos,
      documentoIdentidad: p.documentoIdentidad,
      telefono: p.telefono,
      rutaId: p.rutaId,
      centroAcopioId: p.centroAcopioId,
      notas: p.notas,
    };
    this.mostrarForm.set(true);
  }

  cerrarForm(): void {
    this.mostrarForm.set(false);
  }

  guardar(): void {
    const id = this.editandoId();
    const accion = id ? this.productoresService.actualizar(id, this.form) : this.productoresService.crear(this.form);
    accion.subscribe(() => {
      this.mostrarForm.set(false);
      this.cargar();
    });
  }

  irA(p: Productor, tab: 'historial' | 'seguimiento'): void {
    this.router.navigate(['/calidad'], { queryParams: { productorId: p.id, tab } });
  }
}
