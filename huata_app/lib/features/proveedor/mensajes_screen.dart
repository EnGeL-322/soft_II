import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../core/api/mensajeria_api.dart';
import '../../core/mensajeria/mensajeria_en_vivo.dart';
import '../../core/models/mensajeria.dart';
import '../../core/sync/sync_service.dart';
import '../../core/theme/huata_theme.dart';
import '../../core/api/rutas_api.dart' show mensajeDeError;

/// Pantalla completa del chat del proveedor; una conversación permanente con la planta.
///
/// Lo que se escribe va primero a la cola del teléfono (se ve al instante con reloj) y `SyncService`
/// lo manda en orden cuando hay señal. Lo que manda la planta llega por el canal en vivo; si el
/// canal está caído, un sondeo de respaldo cada 30 s mantiene la conversación al día.
class MensajesProveedorScreen extends StatefulWidget { const MensajesProveedorScreen({super.key}); @override State<MensajesProveedorScreen> createState()=>_MensajesProveedorScreenState(); }
class _MensajesProveedorScreenState extends State<MensajesProveedorScreen> {
  final _texto=TextEditingController(); ConversacionDetalle? _detalle; String? _error; Timer? _respaldo; Timer? _marcarLeido;
  late final MensajeriaEnVivo _vivo; late final SyncService _sync;
  final List<StreamSubscription<dynamic>> _subs=[];

  @override void initState(){
    super.initState();
    _vivo=context.read<MensajeriaEnVivo>(); _sync=context.read<SyncService>();
    _vivo.iniciar();
    _subs.add(_vivo.eventos.listen(_alEvento));
    _subs.add(_sync.mensajesConfirmados.listen((m)=>_incorporar([m])));
    _cargar();
    _respaldo=Timer.periodic(const Duration(seconds:30),(_){if(!_vivo.conectado)_cargar(silencioso:true);});
  }
  @override void dispose(){_respaldo?.cancel();_marcarLeido?.cancel();for(final s in _subs){s.cancel();}_vivo.detener();_texto.dispose();super.dispose();}

  Future<void> _cargar({bool silencioso=false}) async { try{final d=await context.read<MensajeriaApi>().miConversacion();_vivo.registrarVistos(d.mensajes.map((m)=>m.id));if(mounted)setState((){_detalle=d;_error=null;});}catch(e){if(!silencioso&&mounted&&_detalle==null)setState(()=>_error=mensajeDeError(e));} }

  void _alEvento(EventoMensajeria e){
    switch(e){
      // Volvió la conexión: se recarga (marca como leído lo que llegó mientras tanto).
      case CanalListo(): _cargar(silencioso:true);
      case MensajeNuevo(:final mensaje): _incorporar([mensaje]); if(!mensaje.mio)_programarLeido();
      case MensajesRecuperados(:final mensajes): _incorporar(mensajes); if(mensajes.any((m)=>!m.mio))_programarLeido();
      case EstadoActualizado(:final ids,:final estado): final d=_detalle; if(d!=null&&mounted)setState(()=>_detalle=ConversacionDetalle(conversacion:d.conversacion,mensajes:[for(final m in d.mensajes) ids.contains(m.id)?m.conEstado(estado):m]));
    }
  }

  /// Mensajes confirmados por el servidor (canal, SYNC o respuesta del POST). Nunca se pinta uno dos veces.
  void _incorporar(List<MensajeProveedor> nuevos){
    final d=_detalle; if(d==null||!mounted)return;
    final porId={for(final m in d.mensajes)m.id:m}; for(final m in nuevos){porId[m.id]=m;}
    setState(()=>_detalle=ConversacionDetalle(conversacion:d.conversacion,mensajes:porId.values.toList()..sort((a,b)=>a.id.compareTo(b.id))));
  }

  /// La conversación está abierta: lo que acaba de llegar ya lo está viendo. Abrirla de nuevo lo
  /// marca como leído y la planta ve los checks en azul.
  void _programarLeido(){_marcarLeido?.cancel();_marcarLeido=Timer(const Duration(milliseconds:400),()=>_cargar(silencioso:true));}

  Future<void> _enviar() async { final texto=_texto.text.trim();if(texto.isEmpty)return;_texto.clear();await _sync.encolarMensaje(texto); }

  @override Widget build(BuildContext context){
    final d=_detalle; final sync=context.watch<SyncService>(); final vivo=context.watch<MensajeriaEnVivo>();
    return Scaffold(backgroundColor:const Color(0xfff4f8f5),appBar:AppBar(title:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[const Text('Huata Messaging',style:TextStyle(fontSize:17,fontWeight:FontWeight.w700)),Text(d==null?'Mensajes con la planta':'Huata Lácteos · ${d.conversacion.zona??'Planta'}',style:const TextStyle(fontSize:11,color:HuataColors.muted))]),actions:[IconButton(onPressed:_cargar,icon:const Icon(Icons.refresh),tooltip:'Actualizar')]),
      body:SafeArea(child:Column(children:[if(!vivo.conectado)_SinConexion(pendientes:sync.pendientesMensaje),Expanded(child:_contenido(d,sync.colaMensajes)),_redactor()])));
  }

  Widget _contenido(ConversacionDetalle? d,List<MensajeProveedor> enCola){
    if(_error!=null&&d==null)return Center(child:Padding(padding:const EdgeInsets.all(24),child:Column(mainAxisSize:MainAxisSize.min,children:[Text(_error!,textAlign:TextAlign.center),const SizedBox(height:12),OutlinedButton(onPressed:_cargar,child:const Text('Reintentar'))])));
    if(d==null&&enCola.isEmpty)return const Center(child:CircularProgressIndicator());
    final mensajes=mensajesParaMostrar(d?.mensajes??const [],enCola);
    // Lista invertida: lo último queda abajo y a la vista sin tener que desplazarse.
    return RefreshIndicator(onRefresh:_cargar,child:ListView.builder(reverse:true,padding:const EdgeInsets.fromLTRB(16,16,16,24),itemCount:mensajes.length+1,itemBuilder:(context,i){
      if(i==mensajes.length)return const Center(child:Padding(padding:EdgeInsets.only(bottom:16),child:Text('Conversación permanente con Huata',style:TextStyle(color:HuataColors.muted,fontSize:12))));
      final m=mensajes[mensajes.length-1-i];
      return _Burbuja(mensaje:m,onReintentar:m.clientUuid==null?null:()=>_sync.reintentarMensaje(m.clientUuid!),onEliminar:m.clientUuid==null?null:()=>_sync.eliminarMensaje(m.clientUuid!));
    }));
  }

  Widget _redactor()=>Container(color:Colors.white,padding:const EdgeInsets.fromLTRB(12,9,12,12),child:Row(crossAxisAlignment:CrossAxisAlignment.end,children:[Expanded(child:TextField(controller:_texto,maxLength:1000,minLines:1,maxLines:4,textCapitalization:TextCapitalization.sentences,decoration:const InputDecoration(counterText:'',hintText:'Escribe un mensaje...'))),const SizedBox(width:8),IconButton.filled(onPressed:_enviar,icon:const Icon(Icons.send),tooltip:'Enviar') ]));
}

class _SinConexion extends StatelessWidget {
  const _SinConexion({required this.pendientes}); final int pendientes;
  @override Widget build(BuildContext context)=>Container(width:double.infinity,color:const Color(0xfffff6e5),padding:const EdgeInsets.symmetric(horizontal:14,vertical:8),child:Row(children:[const Icon(Icons.cloud_off,size:16,color:Color(0xff8a5a12)),const SizedBox(width:8),Expanded(child:Text('Sin conexión: los mensajes se enviarán cuando haya señal.${pendientes>0?' $pendientes pendiente(s).':''}',style:const TextStyle(fontSize:12,color:Color(0xff7a5413))))]));
}

class _Burbuja extends StatelessWidget {
  const _Burbuja({required this.mensaje,this.onReintentar,this.onEliminar}); final MensajeProveedor mensaje; final VoidCallback? onReintentar; final VoidCallback? onEliminar;
  @override Widget build(BuildContext context){
    final mio=mensaje.mio; final fallido=mensaje.estado==EstadoMensaje.error;
    final hora='${mensaje.creadoEn.hour.toString().padLeft(2,'0')}:${mensaje.creadoEn.minute.toString().padLeft(2,'0')}';
    return Align(alignment:mio?Alignment.centerRight:Alignment.centerLeft,child:Container(constraints:const BoxConstraints(maxWidth:310),margin:const EdgeInsets.only(bottom:9),padding:const EdgeInsets.all(11),
      decoration:BoxDecoration(color:fallido?const Color(0xfffbe4e2):mio?HuataColors.greenSoft:Colors.white,border:Border.all(color:fallido?const Color(0xffe8b4ae):mio?HuataColors.greenSoft:HuataColors.line),borderRadius:BorderRadius.only(topLeft:const Radius.circular(14),topRight:const Radius.circular(14),bottomLeft:Radius.circular(mio?14:3),bottomRight:Radius.circular(mio?3:14))),
      child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        if(!mio)Text(mensaje.autorNombre,style:const TextStyle(fontSize:11,fontWeight:FontWeight.bold,color:HuataColors.green)),
        Text(mensaje.cuerpo),const SizedBox(height:3),
        if(fallido)...[
          Text('No se envió: ${mensaje.error??'rechazado por el servidor'}',style:const TextStyle(fontSize:11,color:Color(0xffa33030))),
          Row(mainAxisSize:MainAxisSize.min,children:[TextButton(onPressed:onReintentar,child:const Text('Reintentar')),TextButton(onPressed:onEliminar,child:const Text('Eliminar'))]),
        ] else Align(alignment:Alignment.centerRight,child:Row(mainAxisSize:MainAxisSize.min,children:[Text(hora,style:const TextStyle(fontSize:10,color:HuataColors.muted)),if(mio)...[const SizedBox(width:4),_Marca(estado:mensaje.estado)]])),
      ])));
  }
}

/// Reloj pendiente · ✓ enviado · ✓✓ entregado · ✓✓ azul leído.
class _Marca extends StatelessWidget {
  const _Marca({required this.estado}); final EstadoMensaje estado;
  @override Widget build(BuildContext context)=>switch(estado){
    EstadoMensaje.pendiente=>const Icon(Icons.schedule,size:13,color:HuataColors.muted,semanticLabel:'Pendiente de envío'),
    EstadoMensaje.enviado=>const Icon(Icons.done,size:14,color:HuataColors.muted,semanticLabel:'Enviado'),
    EstadoMensaje.entregado=>const Icon(Icons.done_all,size:14,color:HuataColors.muted,semanticLabel:'Entregado'),
    EstadoMensaje.leido=>const Icon(Icons.done_all,size:14,color:Color(0xff1f7ae0),semanticLabel:'Leído'),
    EstadoMensaje.error=>const Icon(Icons.error_outline,size:14,color:Color(0xffa33030)),
  };
}
