import { DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Recojo, Ruta, Productor } from '../../core/models/models';
import { RecojoService } from '../../core/services/recojo.service';
import { RutasService } from '../../core/services/rutas.service';
import { ProductoresService } from '../../core/services/productores.service';
import { ExportacionesService } from '../../core/services/exportaciones.service';
import { GraficoLineasComponent } from '../../shared/ui/grafico-lineas.component';
@Component({selector:'app-zonas',standalone:true,imports:[FormsModule,DecimalPipe,GraficoLineasComponent],templateUrl:'./zonas.component.html'})
export class ZonasComponent implements OnInit {
 readonly recepciones=signal<Recojo[]>([]); readonly rutas=signal<Ruta[]>([]); readonly proveedores=signal<Productor[]>([]); readonly error=signal('');
 tab='analisis'; rango=7; busqueda=''; proveedorId=0; zona=''; detalles=false;
 constructor(private readonly recojos:RecojoService,private readonly rutasService:RutasService,private readonly productores:ProductoresService,private readonly exportaciones:ExportacionesService){}
 ngOnInit(){this.cargar();this.cargarRutas();this.cargarProveedores();}
 private cargarRutas(page=0){this.rutasService.listar('',page,200).subscribe({next:p=>{this.rutas.update(v=>page===0?p.content:[...v,...p.content]);if(page+1<p.totalPages)this.cargarRutas(page+1);},error:()=>this.error.set('No se pudieron cargar las zonas.')});}
 private cargarProveedores(page=0){this.productores.listar({page,size:200}).subscribe({next:p=>{this.proveedores.update(v=>page===0?p.content:[...v,...p.content]);if(page+1<p.totalPages)this.cargarProveedores(page+1);},error:()=>this.error.set('No se pudieron cargar los proveedores.')});}
 private cargar(page=0){const inicio=new Date();inicio.setDate(inicio.getDate()-60);this.recojos.listar({desde:inicio.toISOString(),page,size:200}).subscribe({next:p=>{this.recepciones.update(v=>page===0?p.content:[...v,...p.content]);if(page+1<p.totalPages)this.cargar(page+1);},error:()=>this.error.set('No se pudo cargar el rendimiento.')});}
 private readonly formatoDia = new Intl.DateTimeFormat('en-CA',{timeZone:'America/Lima'});
 private readonly fechas = new Map<string,string>();
 private diasRango = 0; private diasCache:string[]=[];
 dia(fecha:string){let dia=this.fechas.get(fecha);if(!dia){dia=this.formatoDia.format(new Date(fecha));this.fechas.set(fecha,dia);}return dia;}
 get dias(){if(this.diasRango===this.rango)return this.diasCache;this.diasRango=this.rango;return this.diasCache=Array.from({length:this.rango},(_,i)=>{const d=new Date();d.setDate(d.getDate()-this.rango+1+i);return this.dia(d.toISOString());});}
 zonaDe(r:Recojo){return this.rutas().find(z=>z.id===r.rutaId)?.zona??'Sin zona';}
 get zonas(){return [...new Set(this.rutas().map(r=>r.zona??'Sin zona'))];}
 get filas(){return this.recepciones().filter(r=>r.estado!=='CANCELLED'&&this.dias.includes(this.dia(r.fechaHora)));}
 get opciones(){const q=this.busqueda.toLowerCase();return this.proveedores().filter(p=>(p.codigo+' '+p.nombres+' '+p.apellidos+' '+(p.rutaNombre??'')).toLowerCase().includes(q));}
 get proveedor(){return this.proveedores().find(p=>p.id===this.proveedorId);}
 get entregas(){return this.filas.filter(r=>r.productorId===this.proveedorId);}
 get promedio(){return this.entregas.length?this.entregas.reduce((s,r)=>s+r.litros,0)/this.entregas.length:0;}
 get calidad(){return [...this.entregas].sort((a,b)=>b.fechaHora.localeCompare(a.fechaHora))[0]?.resultadoCalidad??'Sin control';}
 get regularidad(){return new Set(this.entregas.map(r=>this.dia(r.fechaHora))).size/this.rango*100;}
 get serieProveedor(){return this.dias.map(d=>({etiqueta:d.slice(5),valor:this.entregas.filter(r=>this.dia(r.fechaHora)===d).reduce((s,r)=>s+r.litros,0)}));}
 get serie(){return this.dias.map(d=>({etiqueta:d.slice(5),valor:this.filas.filter(r=>this.dia(r.fechaHora)===d&&(!this.zona||this.zonaDe(r)===this.zona)).reduce((s,r)=>s+r.litros,0)}));}
 get actual(){return this.serie.at(-1)?.valor??0;}
 get diferencia(){return this.actual-(this.serie.at(-2)?.valor??0);}
 get variacion(){const anterior=this.serie.at(-2)?.valor??0;return anterior?this.diferencia/anterior*100:null;}
 get maximo(){return Math.max(0,...this.serie.map(p=>p.valor));}
 get minimo(){return Math.min(...this.serie.map(p=>p.valor));}
 get ranking(){const total=this.filas.reduce((s,r)=>s+r.litros,0);return this.zonas.map(z=>{const rows=this.filas.filter(r=>this.zonaDe(r)===z);const litros=rows.reduce((s,r)=>s+r.litros,0);const actual=rows.filter(r=>this.dia(r.fechaHora)===this.dias.at(-1)).reduce((s,r)=>s+r.litros,0);const anterior=rows.filter(r=>this.dia(r.fechaHora)===this.dias.at(-2)).reduce((s,r)=>s+r.litros,0);return {zona:z,litros,participacion:total?litros/total*100:0,actual,anterior,cambio:actual-anterior};}).sort((a,b)=>b.litros-a.litros);}
 conteo(signo:number){return this.ranking.filter(r=>Math.sign(r.cambio)===signo).length;}
 exportar(){this.exportaciones.descargar('ResumenZonas').subscribe({next:blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='Huata_ResumenZonas.xlsx';a.click();URL.revokeObjectURL(url);},error:()=>this.error.set('No se pudo exportar el resumen.')});}
}


