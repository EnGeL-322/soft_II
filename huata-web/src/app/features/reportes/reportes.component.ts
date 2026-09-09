import { DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, Subscription } from 'rxjs';
import { EventoReporte, ReportesService } from '../../core/services/reportes.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';
@Component({selector:'app-reportes',standalone:true,imports:[FormsModule,DecimalPipe,BadgeEstadoComponent],templateUrl:'./reportes.component.html'})
export class ReportesComponent implements OnInit {
  readonly eventos=signal<EventoReporte[]>([]);
  readonly cargando=signal(false);
  readonly error=signal('');
  desde=''; hasta=''; texto=''; proveedor=''; zona=''; tipo=''; estado=''; pagina=1; size=10;
  private solicitud?: Subscription;
  constructor(private readonly reportes:ReportesService){}
  ngOnInit(){this.limpiar();}
  private normalizar(texto:string|null){return (texto??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();}
  get filtrados(){const match=(v:string|null,q:string)=>this.normalizar(v).includes(this.normalizar(q));return this.eventos().filter(e=>match([e.referencia,e.codigo,e.zona,e.tipo,e.estado,e.detalle].join(' '),this.texto)&&match(e.referencia+' '+(e.codigo??''),this.proveedor)&&match(e.zona,this.zona)&&match(e.tipo,this.tipo)&&match(e.estado,this.estado));}
  get visibles(){return this.filtrados.slice((this.pagina-1)*this.size,this.pagina*this.size);}
  get paginas(){return Math.max(1,Math.ceil(this.filtrados.length/this.size));}
  get litros(){return this.filtrados.filter(e=>e.tipo==='Recepción'&&e.estado!=='CANCELLED').reduce((s,e)=>s+(e.litros??0),0);}
  get incidencias(){return this.filtrados.filter(e=>e.tipo==='Calidad'&&e.estado!=='ACEPTADA').length;}
  get pagos(){return this.filtrados.filter(e=>e.tipo==='Pago').reduce((s,e)=>s+(e.monto??0),0);}
  get proveedores(){return [...new Set(this.eventos().filter(e=>e.tipo!=='Quesos').map(e=>e.referencia))];}
  get zonas(){return [...new Set(this.eventos().map(e=>e.zona).filter(Boolean))];}
  filtrar(){this.pagina=1;}
  cargar(){this.solicitud?.unsubscribe();this.error.set('');if(!this.desde||!this.hasta||this.desde>this.hasta){this.eventos.set([]);this.error.set('Selecciona un periodo válido.');return;}this.cargando.set(true);this.solicitud=this.reportes.eventos(this.desde,this.hasta).pipe(finalize(()=>this.cargando.set(false))).subscribe({next:datos=>{this.eventos.set(datos);this.filtrar();},error:e=>{this.eventos.set([]);this.error.set(e.error?.message??'No se pudo cargar el reporte.');}});}
  limpiar(){const hoy=new Date();const inicio=new Date(hoy);inicio.setDate(inicio.getDate()-29);const local=(d:Date)=>new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,10);this.desde=local(inicio);this.hasta=local(hoy);this.texto=this.proveedor=this.zona=this.tipo=this.estado='';this.cargar();}
  imprimir(){window.print();}
  exportar(){const celda=(v:unknown)=>{let text=String(v??'');if(/^[=+@\-\t\r]/.test(text))text="'"+text;return '"'+text.replaceAll('"','""')+'"';};const filas=[['Fecha','Tipo','Proveedor / referencia','Código','Zona','Litros','Monto','Estado','Detalle'],...this.filtrados.map(e=>[e.fecha,e.tipo,e.referencia,e.codigo,e.zona,e.litros,e.monto,e.estado,e.detalle])];const blob=new Blob(['\uFEFF'+filas.map(f=>f.map(celda).join(';')).join('\r\n')],{type:'text/csv;charset=utf-8'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='Huata_ReporteFiltrado.csv';a.click();URL.revokeObjectURL(url);}
  ngOnDestroy(){this.solicitud?.unsubscribe();}
}
