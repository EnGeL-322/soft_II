const fs = require('fs');
const { JSDOM } = require('../node_modules/jsdom');
const root='src/app/features/productores/';
const path=root+'productores.component.ts';
let ts=fs.readFileSync(path,'utf8');
const old=ts.match(/template: `([\s\S]*?)`,\s*\}\)/)[1];
const oldForm=old.slice(old.indexOf('    @if (mostrarForm())'));
const dom=new JSDOM(fs.readFileSync('design-reference/proveedores.html','utf8'));
const d=dom.window.document;
for (const el of d.querySelectorAll('*')) for (const attr of [...el.attributes]) if(attr.name.startsWith('on') || attr.name==='data-perm') el.removeAttribute(attr.name);
d.querySelector('section').classList.add('active');
d.querySelector('.smart-pagination').outerHTML=`@if (pagina(); as pg) {<app-paginador [page]="pg.page" [totalElements]="pg.totalElements" [totalPages]="pg.totalPages" (anterior)="cambiarPagina(pg.page - 1)" (siguiente)="cambiarPagina(pg.page + 1)"/>}`;
d.getElementById('providersBody').innerHTML=`@for(p of pagina()?.content ?? []; track p.id) {<tr><td><b>{{p.codigo}}</b></td><td>{{p.apellidos}} {{p.nombres}}</td><td>{{p.rutaNombre ?? '—'}}</td><td><app-badge-estado [estado]="p.estado"/></td><td class="no-print"><button class="btn btn-soft" (click)="editar(p)">Editar</button> <button class="btn btn-blue" (click)="irA(p, 'historial')">Historial de calidad</button> <button class="btn btn-amber" (click)="irA(p, 'seguimiento')">Seguimiento</button></td></tr>} @empty {<tr><td colspan="5">Sin proveedores que coincidan.</td></tr>}`;
let html=d.querySelector('section').outerHTML;
const replace=(a,b)=>{html=html.replace(a,b);};
replace('class="btn btn-primary"','class="btn btn-primary" (click)="nuevo()"');
replace('class="btn btn-blue"','class="btn btn-blue" (click)="exportar()"');
replace('id="providerTabCharts" class="provider-view-tab active"','id="providerTabCharts" class="provider-view-tab" [class.active]="vista === \'charts\'" (click)="vista = \'charts\'"');
replace('id="providerTabData" class="provider-view-tab"','id="providerTabData" class="provider-view-tab" [class.active]="vista === \'data\'" (click)="vista = \'data\'"');
replace('id="providerDataPanel" class="provider-view-panel"','id="providerDataPanel" class="provider-view-panel" [class.active]="vista === \'data\'"');
replace('id="providerChartsPanel" class="provider-view-panel active"','id="providerChartsPanel" class="provider-view-panel" [class.active]="vista === \'charts\'"');
replace('id="providerSearch"','id="providerSearch" [(ngModel)]="q" (ngModelChange)="buscar()"');
replace('id="providerChartSearch"','id="providerChartSearch" [(ngModel)]="analisisTexto"');
replace('title="Ver todos"','title="Ver todos" (click)="analisisTexto = \'\'"');
replace('<strong id="providerKpiCount">0</strong>','<strong id="providerKpiCount">{{pagina()?.totalElements ?? 0}}</strong>');
replace('<strong id="providerKpiLiters">0 L</strong>','<strong id="providerKpiLiters">{{totalAnalisis | number:\'1.1-1\'}} L</strong>');
replace('<strong id="providerKpiQuality">0%</strong>','<strong id="providerKpiQuality">{{calidadApta | number:\'1.1-1\'}}%</strong>');
replace('<strong id="providerKpiIncidents">0</strong>','<strong id="providerKpiIncidents">{{observacionesAnalisis}}</strong>');
replace('<strong id="providerSelectedDateText">—</strong>','<input type="date" aria-label="Día de análisis" [(ngModel)]="diaAnalisis"/>');
replace('Haz clic en un punto del gráfico de volumen.','Selecciona la fecha para analizar el día.');
replace('<div id="providerTrendChart" class="provider-trend-chart"></div>','<div id="providerTrendChart" class="provider-trend-chart"><app-grafico-lineas [puntos]="serieAnalisis" etiquetaSerie="Litros"/></div>');
replace('<div id="providerQualityDonut" class="provider-quality-donut provider-quality-donut-compact"></div>','<div id="providerQualityDonut" class="provider-quality-donut provider-quality-donut-compact" [style.background]="\'conic-gradient(#539479 \'+calidadApta+\'%, #eddfc3 0)\'"><strong>{{calidadApta | number:\'1.1-1\'}}%</strong></div>');
replace('<div id="providerQualityLegend" class="provider-quality-chart-legend provider-quality-legend-compact"></div>','<div id="providerQualityLegend" class="provider-quality-chart-legend provider-quality-legend-compact">@for(g of distribucion; track g.nombre) {<p>{{g.nombre}} <b>{{g.cantidad}}</b></p>}</div>');
replace('<div id="providerLabResult" class="provider-lab-result"></div>','<div id="providerLabResult" class="provider-lab-result">@for(r of filasDia; track r.id) {<p>{{r.productorNombre}} · <app-badge-estado [estado]="r.resultadoCalidad ?? \'PENDIENTE\'"/></p>} @empty {<p>Sin controles en la fecha seleccionada.</p>}</div>');
replace('<div id="providerZoneChart" class="provider-horizontal-chart provider-zone-chart-daily"></div>','<div id="providerZoneChart" class="provider-horizontal-chart provider-zone-chart-daily"><app-grafico-barras [barras]="zonasAnalisis"/></div>');
replace('<div id="providerVolumeRanking" class="provider-horizontal-chart"></div>','<div id="providerVolumeRanking" class="provider-horizontal-chart"><app-grafico-barras [barras]="rankingAnalisis"/></div>');
html += oldForm.replace('<div class="table-title">','<div class="prototype-modal" role="dialog" aria-modal="true" aria-label="Proveedor"><div class="prototype-modal-box"><div class="table-title">').replace(/\n    \}\s*$/, '\n</div></div>\n}');
fs.writeFileSync(root+'productores.component.html',html);
ts=ts.replace(/template: `[\s\S]*?`,\s*\}\)/,"templateUrl: './productores.component.html',\n})");
ts="import { DecimalPipe } from '@angular/common';\nimport { Recojo } from '../../core/models/models';\nimport { RecojoService } from '../../core/services/recojo.service';\nimport { ExportacionesService } from '../../core/services/exportaciones.service';\nimport { GraficoLineasComponent } from '../../shared/ui/grafico-lineas.component';\nimport { GraficoBarrasComponent } from '../../shared/ui/grafico-barras.component';\n"+ts;
ts=ts.replace('FormsModule, BadgeEstadoComponent, PaginadorComponent','FormsModule, DecimalPipe, BadgeEstadoComponent, PaginadorComponent, GraficoLineasComponent, GraficoBarrasComponent');
ts=ts.replace('private readonly route: ActivatedRoute,','private readonly route: ActivatedRoute,\n    private readonly recojosService: RecojoService,\n    private readonly exportaciones: ExportacionesService,');
ts=ts.replace('ngOnInit(): void {','ngOnInit(): void {\n    this.cargarAnalisis();');
ts=ts.replace('export class ProductoresComponent implements OnInit {',`export class ProductoresComponent implements OnInit {
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
`);
fs.writeFileSync(path,ts);
