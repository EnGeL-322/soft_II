import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, afterRenderEffect, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, filter, interval } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/services/auth.service';
import { ConversacionDetalle, ConversacionProveedor, DestinoAviso, MensajeProveedor, MensajeriaService } from '../../core/services/mensajeria.service';
import { EventoMensajeria, MensajeriaTiempoRealService } from '../../core/services/mensajeria-tiempo-real.service';
import { MensajeriaSalidaService } from '../../core/services/mensajeria-salida.service';

/** Sondeo de respaldo, solo mientras el canal en vivo esta caido. */
const SONDEO_SIN_CANAL_MS = 30_000;

@Component({selector:'app-mensajeria-flotante',standalone:true,imports:[FormsModule,DatePipe],templateUrl:'./mensajeria-flotante.component.html',styleUrls:['./mensajeria-flotante.component.css','./mensajeria-aviso.component.css']})
export class MensajeriaFlotanteComponent {
  private readonly api=inject(MensajeriaService); private readonly auth=inject(AuthService); private readonly destroyRef=inject(DestroyRef);
  readonly vivo=inject(MensajeriaTiempoRealService); readonly salida=inject(MensajeriaSalidaService);
  // Por permiso, no por rol: asi funciona al concederlo desde "Usuarios y roles". El proveedor tiene
  // el mismo permiso para SU conversacion (app movil), no para la bandeja de planta.
  readonly puedeUsar=computed(()=>this.auth.puede('mensajeria.read') && !this.auth.tieneRol('PROVEEDOR'));
  readonly puedeEnviar=computed(()=>this.puedeUsar() && this.auth.puede('mensajeria.write'));
  readonly abierta=signal(false); readonly conversaciones=signal<ConversacionProveedor[]>([]); readonly seleccionada=signal<ConversacionDetalle|null>(null);
  readonly cargando=signal(false); readonly error=signal(''); readonly avisoAbierto=signal(false); readonly enviandoAviso=signal(false);
  /** Lo escrito en la conversacion abierta que todavia no confirma el servidor (reloj o error). */
  readonly enCola=computed(()=>{const id=this.seleccionada()?.conversacion.productorId;return this.salida.cola().filter(m=>m.productorId===id);});
  readonly enLinea=computed(()=>this.vivo.estado()==='conectado');
  busqueda=''; texto=''; textoAviso=''; destinoAviso:DestinoAviso='TODOS'; zonaAviso=''; seleccionados=new Set<number>();
  private conectado=false;
  private readonly refrescarLista$=new Subject<void>();
  private readonly marcarLeido$=new Subject<number>();
  private readonly lista=viewChild<ElementRef<HTMLElement>>('lista');
  /** La persona esta viendo el final de la conversacion (no se fue a leer mensajes viejos). */
  private pegadoAbajo=true;
  private vistaElemento?:HTMLElement; private vistaConversacion?:number; private vistaTotal=0;

  constructor(){
    // Como en WhatsApp: al abrir una conversacion y al enviar, se baja al ultimo mensaje. Si llega
    // uno nuevo, solo se baja si ya estaba abajo; si estaba leyendo mas arriba, no se lo mueve.
    afterRenderEffect(()=>{
      const d=this.seleccionada(); const cola=this.enCola(); const el=this.lista()?.nativeElement;
      if(!el||!d)return;
      const total=d.mensajes.length+cola.length;
      const otraVista=el!==this.vistaElemento||d.conversacion.productorId!==this.vistaConversacion;
      const crecio=total>this.vistaTotal;
      const envieYo=crecio&&(cola.length>0||!!d.mensajes.at(-1)?.mio);
      // Si ya estaba abajo se mantiene pegado al fondo ante cualquier cambio, no solo cuando crece:
      // al confirmarse, el mensaje con reloj se reemplaza por el definitivo (algo mas alto) sin que
      // cambie la cantidad, y sin esto quedaba un trozo del final fuera de la vista.
      if(otraVista||envieYo||this.pegadoAbajo){el.scrollTop=el.scrollHeight;this.pegadoAbajo=true;}
      this.vistaElemento=el;this.vistaConversacion=d.conversacion.productorId;this.vistaTotal=total;
    });
    effect(()=>{const usar=this.puedeUsar();untracked(()=>this.conectarCanal(usar));});
    this.destroyRef.onDestroy(()=>this.conectarCanal(false));
    this.vivo.eventos$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(e=>this.alEvento(e));
    this.salida.confirmados$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(m=>this.incorporar(m,false));
    this.refrescarLista$.pipe(debounceTime(400),takeUntilDestroyed(this.destroyRef)).subscribe(()=>this.buscar());
    // Leer en vivo: si la conversacion esta abierta cuando llega un mensaje, se marca como leido.
    this.marcarLeido$.pipe(debounceTime(300),takeUntilDestroyed(this.destroyRef)).subscribe(id=>this.recargarDetalle(id));
    interval(SONDEO_SIN_CANAL_MS).pipe(filter(()=>this.puedeUsar() && !this.enLinea()),takeUntilDestroyed(this.destroyRef)).subscribe(()=>this.buscar());
  }

  private conectarCanal(usar:boolean){
    if(usar && !this.conectado){this.conectado=true;this.vivo.iniciar();this.buscar();}
    else if(!usar && this.conectado){this.conectado=false;this.vivo.detener();}
  }

  private alEvento(e:EventoMensajeria){
    switch(e.type){
      case 'LISTO': this.refrescarLista$.next(); this.salida.vaciar(); if(this.abierta()){const id=this.seleccionada()?.conversacion.productorId;if(id)this.marcarLeido$.next(id);} break;
      case 'MENSAJE': this.incorporar(e.mensaje,true); break;
      // Lo perdido mientras no habia conexion. Los contadores salen del servidor, no se suman aqui.
      case 'SYNC': e.mensajes.forEach(m=>this.incorporar(m,false)); if(e.mensajes.length)this.refrescarLista$.next(); break;
      case 'ESTADO': this.seleccionada.update(d=>d && d.conversacion.productorId===e.productorId ? {...d,mensajes:d.mensajes.map(m=>e.ids.includes(m.id)?{...m,estado:e.estado}:m)} : d);
        // Bandeja compartida: si otra persona de la planta leyo, el "sin leer" baja tambien aqui.
        if(e.estado==='LEIDO')this.refrescarLista$.next(); break;
    }
  }

  /** Un mensaje que confirmo el servidor (respuesta del POST, canal o SYNC). Nunca se pinta dos veces. */
  private incorporar(m:MensajeProveedor, enVivo:boolean){
    this.salida.confirmar(m.clientUuid);
    this.vivo.registrarVistos([m.id]);
    const abierta=this.abierta() && this.seleccionada()?.conversacion.productorId===m.productorId;
    let nuevo=false;
    this.seleccionada.update(d=>{
      if(!d || d.conversacion.productorId!==m.productorId || d.mensajes.some(x=>x.id===m.id))return d;
      nuevo=true;
      return {...d,mensajes:[...d.mensajes,m].sort((a,b)=>a.id-b.id)};
    });
    if(abierta && !m.mio)this.marcarLeido$.next(m.productorId);
    if(!enVivo)return;
    const yaVisto=this.seleccionada()?.conversacion.productorId===m.productorId && !nuevo;
    if(yaVisto)return;
    // Misma regla que el servidor: solo cuenta lo que la planta no ha leido. Lo que escribio otra
    // persona de la planta (un aviso, por ejemplo) ya nace leido para la bandeja compartida.
    const sinLeer=!m.mio && !m.leidoEn && !abierta;
    this.conversaciones.update(lista=>lista.map(c=>c.productorId!==m.productorId?c:{...c,ultimoMensaje:m.cuerpo,ultimoMensajeAt:m.creadoEn,mensajesSinLeer:c.mensajesSinLeer+(sinLeer?1:0)}));
  }

  abrir(){this.abierta.set(true);this.cargar();} cerrar(){this.abierta.set(false);} cargar(){this.cargando.set(true);this.error.set('');this.api.listar(this.busqueda).subscribe({next:v=>{this.conversaciones.set(v);this.cargando.set(false);const actual=this.seleccionada()?.conversacion.productorId;if(actual)this.seleccionar(actual);else if(v[0])this.seleccionar(v[0].productorId);},error:()=>{this.error.set('No se pudo cargar Huata Messaging.');this.cargando.set(false);}});}
  buscar(){this.api.listar(this.busqueda).subscribe({next:v=>this.conversaciones.set(v),error:()=>{}});}
  seleccionar(id:number){this.api.detalle(id).subscribe({next:v=>this.mostrarDetalle(id,v),error:()=>this.error.set('No se pudo abrir la conversación.')});}
  private recargarDetalle(id:number){if(this.seleccionada()?.conversacion.productorId!==id)return;this.api.detalle(id).subscribe({next:v=>this.mostrarDetalle(id,v),error:()=>{}});}
  private mostrarDetalle(id:number,v:ConversacionDetalle){this.seleccionada.set(v);this.vivo.registrarVistos(v.mensajes.map(m=>m.id));this.conversaciones.update(lista=>lista.map(x=>x.productorId===id?{...x,mensajesSinLeer:0}:x));}
  /** Se muestra al instante con reloj; la bandeja de salida lo manda en cuanto hay conexion. */
  enviar(){const cuerpo=this.texto.trim();const productor=this.seleccionada()?.conversacion.productorId;if(!cuerpo||!productor)return;this.salida.encolar(productor,cuerpo);this.texto='';}
  alDesplazar(){const el=this.lista()?.nativeElement;if(el)this.pegadoAbajo=el.scrollHeight-el.scrollTop-el.clientHeight<80;}
  reintentar(clientUuid:string){this.salida.reintentar(clientUuid);}
  descartar(clientUuid:string){this.salida.eliminar(clientUuid);}
  /** ✓ enviado · ✓✓ entregado · ✓✓ en color leido. */
  marca(m:MensajeProveedor){return m.estado==='ENVIADO'?'✓':'✓✓';}
  abrirAviso(){this.error.set('');this.textoAviso='';this.destinoAviso='TODOS';this.zonaAviso='';this.seleccionados=new Set();this.avisoAbierto.set(true);}
  cerrarAviso(){this.avisoAbierto.set(false);}
  cambiarDestino(destino:DestinoAviso){this.destinoAviso=destino;}
  alternarProveedor(id:number, marcado:boolean){if(marcado)this.seleccionados.add(id);else this.seleccionados.delete(id);}
  zonas(){return [...new Set(this.conversaciones().map(c=>c.zona).filter((z):z is string=>!!z))].sort((a,b)=>a.localeCompare(b));}
  cantidadAviso(){if(this.destinoAviso==='TODOS')return this.conversaciones().length;if(this.destinoAviso==='ZONA')return this.conversaciones().filter(c=>c.zona===this.zonaAviso).length;return this.seleccionados.size;}
  enviarAviso(){const cuerpo=this.textoAviso.trim();if(!cuerpo)return;this.enviandoAviso.set(true);this.error.set('');this.api.enviarAviso(cuerpo,this.destinoAviso,this.zonaAviso,Array.from(this.seleccionados)).subscribe({next:r=>{this.enviandoAviso.set(false);this.cerrarAviso();this.cargar();this.error.set(`Aviso enviado a ${r.destinatarios} proveedor(es).`);},error:e=>{this.enviandoAviso.set(false);this.error.set(e?.error?.message || 'No se pudo enviar el aviso.');}});}
  iniciales(nombre:string){return nombre.split(' ').filter(Boolean).slice(0,2).map(x=>x[0]).join('');}
  pendientes(){return this.conversaciones().reduce((n,c)=>n+c.mensajesSinLeer,0);}
}
