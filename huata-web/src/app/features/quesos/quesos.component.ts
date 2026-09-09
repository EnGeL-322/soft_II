import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { Queso } from '../../core/models/models';
import { QuesoRequest, QuesosService } from '../../core/services/quesos.service';
import { ExportacionesService } from '../../core/services/exportaciones.service';
import { GraficoBarrasComponent } from '../../shared/ui/grafico-barras.component';
@Component({selector:'app-quesos',standalone:true,imports:[FormsModule,DecimalPipe,GraficoBarrasComponent],templateUrl:'./quesos.component.html'})
export class QuesosComponent implements OnInit {
  readonly quesos=signal<Queso[]>([]);
  readonly guardando=signal(false);
  readonly error=signal('');
  tab:'resumen'|'historial'='resumen';
  periodo:'semana'|'mes'='semana';
  modal:'produccion'|'venta'|null=null;
  pagina=1;
  cantidad:number|null=null;
  form:QuesoRequest={fecha:'',fuente:'Leche propia',producidosKg:0,vendidosKg:0};
  readonly totalProducido=computed(()=>this.quesos().reduce((s,q)=>s+q.producidosKg,0));
  readonly totalVendido=computed(()=>this.quesos().reduce((s,q)=>s+q.vendidosKg,0));
  readonly totalVenta=computed(()=>this.quesos().reduce((s,q)=>s+(q.totalVenta??0),0));
  get paginas(){return Math.max(1,Math.ceil(this.quesos().length/10));}
  get visibles(){return this.quesos().slice((this.pagina-1)*10,this.pagina*10);}
  get grafico(){const fecha=new Date();fecha.setDate(fecha.getDate()-(this.periodo==='semana'?6:29));const desde=fecha.toISOString().slice(0,10);const map=new Map<string,{etiqueta:string;valor:number;valorSecundario:number}>();this.quesos().filter(q=>q.fecha>=desde).forEach(q=>{const p=map.get(q.fecha)??{etiqueta:q.fecha.slice(5),valor:0,valorSecundario:0};p.valor+=q.producidosKg;p.valorSecundario+=q.vendidosKg;map.set(q.fecha,p);});return [...map].sort(([a],[b])=>a.localeCompare(b)).map(([,p])=>p);}
  get compradores(){const map=new Map<string,number>();this.quesos().filter(q=>q.vendidosKg>0).forEach(q=>{const nombre=q.comprador||'Sin comprador registrado';map.set(nombre,(map.get(nombre)??0)+(q.totalVenta??0));});return [...map].map(([nombre,total])=>({nombre,total})).sort((a,b)=>b.total-a.total);}
  constructor(private readonly service:QuesosService,private readonly exportaciones:ExportacionesService){}
  ngOnInit(){this.cargar();}
  cargar(){this.service.listar('1900-01-01').subscribe({next:q=>this.quesos.set(q),error:()=>this.error.set('No se pudo cargar el historial de quesos.')});}
  abrir(tipo:'produccion'|'venta'){this.modal=tipo;this.cantidad=null;this.error.set('');const now=new Date();this.form={fecha:new Date(now.getTime()-now.getTimezoneOffset()*60000).toISOString().slice(0,10),fuente:tipo==='venta'?'Venta':'Leche propia',producidosKg:0,vendidosKg:0};}
  guardar(){if(this.guardando()||!this.cantidad||this.cantidad<=0)return;this.error.set('');this.guardando.set(true);const request={...this.form,producidosKg:this.modal==='produccion'?this.cantidad:0,vendidosKg:this.modal==='venta'?this.cantidad:0};this.service.crear(request).pipe(finalize(()=>this.guardando.set(false))).subscribe({next:()=>{this.modal=null;this.pagina=1;this.cargar();},error:e=>this.error.set(e.error?.message??'No se pudo guardar el movimiento.')});}
  exportar(){this.exportaciones.descargar('Quesos').subscribe(blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='Huata_Quesos.xlsx';a.click();URL.revokeObjectURL(url);});}
}
