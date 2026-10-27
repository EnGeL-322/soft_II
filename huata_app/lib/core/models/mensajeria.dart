class ConversacionProveedor {
  const ConversacionProveedor({required this.productorId, required this.productorNombre, required this.productorCodigo, required this.zona, required this.mensajesSinLeer});
  final int productorId; final String productorNombre; final String productorCodigo; final String? zona; final int mensajesSinLeer;
  factory ConversacionProveedor.fromJson(Map<String,dynamic> json)=>ConversacionProveedor(productorId:json['productorId'] as int,productorNombre:json['productorNombre'] as String,productorCodigo:json['productorCodigo'] as String,zona:json['zona'] as String?,mensajesSinLeer:(json['mensajesSinLeer'] as num).toInt());
}

/// `pendiente` y `error` existen solo en el teléfono (el mensaje todavía está en la cola offline);
/// los otros tres los informa el servidor y son los checks: ✓ enviado, ✓✓ entregado, ✓✓ azul leído.
enum EstadoMensaje {
  pendiente,
  error,
  enviado,
  entregado,
  leido;

  static EstadoMensaje delServidor(String? valor) => switch (valor) {
    'ENTREGADO' => EstadoMensaje.entregado,
    'LEIDO' => EstadoMensaje.leido,
    _ => EstadoMensaje.enviado,
  };
}

class MensajeProveedor {
  const MensajeProveedor({required this.id,required this.autorNombre,required this.mio,required this.cuerpo,required this.creadoEn,required this.leidoEn,this.productorId,this.clientUuid,this.estado=EstadoMensaje.enviado,this.error});
  /// -1 mientras el mensaje sigue en la cola del teléfono y todavía no tiene id del servidor.
  final int id; final String autorNombre; final bool mio; final String cuerpo; final DateTime creadoEn; final DateTime? leidoEn;
  final int? productorId; final String? clientUuid; final EstadoMensaje estado;
  /// Motivo del rechazo cuando `estado == error`.
  final String? error;
  bool get enCola => estado == EstadoMensaje.pendiente || estado == EstadoMensaje.error;

  factory MensajeProveedor.fromJson(Map<String,dynamic> json)=>MensajeProveedor(id:json['id'] as int,autorNombre:json['autorNombre'] as String,mio:json['mio'] as bool,cuerpo:json['cuerpo'] as String,creadoEn:DateTime.parse(json['creadoEn'] as String).toLocal(),leidoEn:json['leidoEn']==null?null:DateTime.parse(json['leidoEn'] as String).toLocal(),productorId:(json['productorId'] as num?)?.toInt(),clientUuid:json['clientUuid'] as String?,estado:EstadoMensaje.delServidor(json['estado'] as String?));

  /// Lo escrito por el proveedor que todavía espera en la cola offline.
  factory MensajeProveedor.enCola({required String clientUuid, required String cuerpo, required DateTime creadoEn, String? error}) =>
      MensajeProveedor(id:-1,autorNombre:'',mio:true,cuerpo:cuerpo,creadoEn:creadoEn,leidoEn:null,clientUuid:clientUuid,estado:error==null?EstadoMensaje.pendiente:EstadoMensaje.error,error:error);

  MensajeProveedor conEstado(EstadoMensaje nuevo) => MensajeProveedor(id:id,autorNombre:autorNombre,mio:mio,cuerpo:cuerpo,creadoEn:creadoEn,leidoEn:leidoEn,productorId:productorId,clientUuid:clientUuid,estado:nuevo,error:error);
}
class ConversacionDetalle {
  const ConversacionDetalle({required this.conversacion,required this.mensajes}); final ConversacionProveedor conversacion; final List<MensajeProveedor> mensajes;
  factory ConversacionDetalle.fromJson(Map<String,dynamic> json)=>ConversacionDetalle(conversacion:ConversacionProveedor.fromJson(json['conversacion'] as Map<String,dynamic>),mensajes:(json['mensajes'] as List).map((m)=>MensajeProveedor.fromJson(m as Map<String,dynamic>)).toList());
}

/// Lo que se ve en el chat: lo confirmado por el servidor, en orden, y debajo lo que todavía está en
/// la cola. Un mensaje que ya llegó al servidor (mismo clientUuid) no se pinta dos veces aunque la
/// cola todavía no se haya enterado: pasa cuando el eco del canal llega antes que la respuesta del POST.
List<MensajeProveedor> mensajesParaMostrar(List<MensajeProveedor> confirmados, List<MensajeProveedor> enCola) {
  final porId = <int, MensajeProveedor>{for (final m in confirmados) m.id: m};
  final ordenados = porId.values.toList()..sort((a, b) => a.id.compareTo(b.id));
  final yaEnServidor = {for (final m in ordenados) if (m.clientUuid != null) m.clientUuid!};
  return [...ordenados, ...enCola.where((m) => !yaEnServidor.contains(m.clientUuid))];
}
