import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:provider/provider.dart';
import 'package:uuid/uuid.dart';

import '../../core/api/calidad_api.dart';
import '../../core/api/recojos_api.dart';
import '../../core/api/rutas_api.dart' show mensajeDeError;
import '../../core/models/calidad.dart';
import '../../core/models/productor.dart';
import '../../core/models/recojo.dart';
import '../../core/ocr/mlkit_lineas.dart';
import '../../core/ocr/voucher_ocr_parser.dart';
import '../../core/sync/sync_service.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';

/// Captura de voucher + OCR + confirmación humana antes de guardar (RF-85, RN-14, RNF-20).
///
/// Con [entregaDirecta] la misma pantalla recibe en planta la leche que el proveedor trajo él
/// mismo: se registra la entrega (litros) y, sobre ella, el análisis con su voucher. En la web esa
/// entrega solo lleva proveedor y litros; la calidad la registra la app y la web la aprueba (V48).
///
/// El testeador pasa ANTES que el acopiador (pliego, sección 10; V47). Si el proveedor todavía no
/// tiene recojo hoy, el control se guarda a su nombre y el backend lo une al recojo cuando el
/// acopiador lo registre. Si ya hay recojo de hoy, se guarda directamente sobre él.
///
/// La foto del voucher se sube al backend después de guardar el control, junto con el texto crudo
/// del OCR, los valores que propuso y su confianza (RNF-20). Sin señal, queda en la cola del
/// teléfono (`SyncService`, `TipoCola.voucher`) y se sube sola al volver la conexión.
class VoucherScreen extends StatefulWidget {
  const VoucherScreen({super.key, required this.proveedor, this.entregaDirecta = false});
  final Productor proveedor;

  /// true: entrega directa en planta (se registra la entrega y su análisis); false: testeo en ruta.
  final bool entregaDirecta;

  @override
  State<VoucherScreen> createState() => _VoucherScreenState();
}

class _VoucherScreenState extends State<VoucherScreen> {
  final _formKey = GlobalKey<FormState>();
  final _snCtrl = TextEditingController();
  final _temperaturaCtrl = TextEditingController();
  final _densidadCtrl = TextEditingController();
  final _puntoCongCtrl = TextEditingController();
  final _grasaCtrl = TextEditingController();
  final _sngCtrl = TextEditingController();
  final _solidosTotalesCtrl = TextEditingController();
  final _motivoRechazoCtrl = TextEditingController();
  double? _aguaAnadidaLeida;
  List<String> _advertenciasOcr = const [];
  LecturaVoucherOcr? _lectura;
  double? _confianzaOcr;

  String _resultado = ResultadoCalidad.aceptada;
  String _aguaAnadida = AguaAnadida.noEvidenciada;
  String _lugol = ResultadoLugol.negativa;

  bool _leyendoOcr = false;
  String? _textoOcr;
  XFile? _foto;

  final _litrosCtrl = TextEditingController();
  /// Uno por pantalla: si se reintenta tras un error, el backend devuelve la misma entrega en vez de
  /// crear otra (idempotencia por clientUuid).
  final _uuidEntrega = const Uuid().v4();

  bool _cargandoRecojos = true;
  List<Recojo> _recojosHoy = [];
  Recojo? _recojoSeleccionado;

  bool _guardando = false;
  String? _error;
  String? _aviso;

  @override
  void initState() {
    super.initState();
    if (widget.entregaDirecta) {
      _cargandoRecojos = false;
    } else {
      _cargarRecojosHoy();
    }
    _recuperarFotoPerdida();
  }

  @override
  void dispose() {
    _snCtrl.dispose();
    _temperaturaCtrl.dispose();
    _densidadCtrl.dispose();
    _puntoCongCtrl.dispose();
    _grasaCtrl.dispose();
    _sngCtrl.dispose();
    _solidosTotalesCtrl.dispose();
    _motivoRechazoCtrl.dispose();
    _litrosCtrl.dispose();
    super.dispose();
  }

  Future<void> _cargarRecojosHoy() async {
    setState(() => _cargandoRecojos = true);
    try {
      final hoy = DateTime.now();
      final inicio = DateTime(hoy.year, hoy.month, hoy.day);
      final recojos = await context.read<RecojosApi>().listar(
        productorId: widget.proveedor.id,
        desde: inicio,
        hasta: inicio.add(const Duration(days: 1)),
      );
      setState(() {
        _recojosHoy = recojos.where((r) => r.estado != 'CANCELLED').toList();
        _recojoSeleccionado = _recojosHoy.length == 1 ? _recojosHoy.first : null;
        _cargandoRecojos = false;
      });
    } catch (e) {
      setState(() {
        _error = mensajeDeError(e);
        _cargandoRecojos = false;
      });
    }
  }

  /// En Android de gama baja el sistema puede matar la app mientras la cámara está abierta; al
  /// volver, la foto no llega por el `await` original sino por `retrieveLostData`. Sin esto el
  /// testeador saca la foto y "no pasa nada".
  Future<void> _recuperarFotoPerdida() async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
    try {
      final perdida = await ImagePicker().retrieveLostData();
      if (perdida.isEmpty || perdida.file == null || !mounted) return;
      await _leerVoucher(perdida.file!);
    } catch (_) {
      // Sin foto recuperable: el testeador simplemente vuelve a tomarla.
    }
  }

  Future<void> _tomarFoto(ImageSource origen) async {
    if (kIsWeb) {
      setState(() => _aviso = 'La cámara y el OCR solo están disponibles en la app instalada (Android/iOS), no en este build web de prueba.');
      return;
    }
    // El manifiesto declara CAMERA; en Android eso obliga a tener el permiso concedido incluso para
    // abrir la cámara del sistema, o image_picker falla con camera_access_denied. Antes esa
    // excepción ni siquiera estaba dentro del try y el botón "no hacía nada".
    if (origen == ImageSource.camera && !await _permisoCamara()) return;

    XFile? foto;
    try {
      foto = await ImagePicker().pickImage(source: origen, maxWidth: 2000, imageQuality: 95);
    } on PlatformException catch (e) {
      if (!mounted) return;
      setState(() => _aviso = e.code.contains('access_denied')
          ? 'La app no tiene permiso para usar la cámara. Actívalo en Ajustes > Aplicaciones > Huata > Permisos.'
          : 'No se pudo abrir la cámara (${e.code}). Intenta de nuevo o elige la foto desde la galería.');
      return;
    }
    if (foto == null || !mounted) return;
    await _leerVoucher(foto);
  }

  Future<bool> _permisoCamara() async {
    var estado = await Permission.camera.status;
    if (estado.isGranted) return true;
    estado = await Permission.camera.request();
    if (estado.isGranted) return true;
    if (!mounted) return false;
    setState(() => _aviso = estado.isPermanentlyDenied
        ? 'El permiso de cámara está bloqueado. Actívalo en Ajustes para leer el voucher, o completa los valores a mano.'
        : 'Sin permiso de cámara no se puede leer el voucher. Puedes elegir una foto de la galería o completar a mano.');
    if (estado.isPermanentlyDenied) await openAppSettings();
    return false;
  }

  Future<void> _leerVoucher(XFile foto) async {
    setState(() {
      _foto = foto;
      _leyendoOcr = true;
      _aviso = null;
    });
    final recognizer = TextRecognizer(script: TextRecognitionScript.latin);
    try {
      final resultado = await recognizer.processImage(InputImage.fromFilePath(foto.path));
      // Líneas con su posición e inclinación: el parser endereza la foto y reconstruye las filas
      // etiqueta-valor que ML Kit separa en bloques distintos (ver VoucherOcrParser).
      final lectura = VoucherOcrParser.parsearLineas(lineasDeMlKit(resultado), textoCrudo: resultado.text);
      if (!mounted) return;
      setState(() {
        _textoOcr = resultado.text;
        _lectura = lectura;
        _confianzaOcr = confianzaPromedio(resultado);
        // Solo se sobrescribe lo que el OCR sí leyó: si el testeador ya escribió un valor a mano y
        // repite la foto, no se le borra lo que el OCR no alcanzó a ver.
        _aplicar(_snCtrl, lectura.analizadorSn);
        _aplicar(_temperaturaCtrl, lectura.temperatura?.toString());
        _aplicar(_densidadCtrl, lectura.densidad?.toString());
        _aplicar(_puntoCongCtrl, lectura.puntoCongelacion?.toString());
        _aplicar(_grasaCtrl, lectura.grasa?.toString());
        _aplicar(_sngCtrl, lectura.solidosNoGrasos?.toString());
        _aplicar(_solidosTotalesCtrl, lectura.solidosTotales?.toString());
        _aguaAnadidaLeida = lectura.aguaAnadidaPorcentaje;
        _advertenciasOcr = lectura.advertencias;
        _aviso = lectura.camposLeidos == 0
            ? 'El OCR no reconoció ningún valor del voucher. Toma otra foto con buena luz, el voucher plano '
                'y ocupando casi toda la pantalla, o completa los valores a mano.'
            : 'El OCR leyó ${lectura.camposLeidos} valor(es). Revísalos y corrígelos antes de guardar (RN-14): '
                'el sistema no decide nada por sí solo.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _aviso = 'No se pudo leer el voucher automáticamente. Completa los valores a mano.');
    } finally {
      await recognizer.close();
      if (mounted) setState(() => _leyendoOcr = false);
    }
  }

  void _aplicar(TextEditingController ctrl, String? valor) {
    if (valor != null && valor.isNotEmpty) ctrl.text = valor;
  }

  static double? _numero(TextEditingController c) => double.tryParse(c.text.trim().replaceAll(',', '.'));

  static String? _validarNumero(String? v) {
    if (v == null || v.trim().isEmpty) return null;
    return double.tryParse(v.trim().replaceAll(',', '.')) == null ? 'Número no válido' : null;
  }

  Future<void> _guardar() async {
    if (!_formKey.currentState!.validate()) return;
    // Con varios recojos hoy hay que decir a cuál corresponde; sin ninguno, el control queda a la
    // espera del acopiador.
    if (!widget.entregaDirecta && _recojosHoy.isNotEmpty && _recojoSeleccionado == null) {
      setState(() => _error = 'Selecciona a qué recojo de hoy corresponde este control.');
      return;
    }
    setState(() {
      _guardando = true;
      _error = null;
    });
    final calidadApi = context.read<CalidadApi>();
    final recojosApi = context.read<RecojosApi>();
    final sync = context.read<SyncService>();
    final messenger = ScaffoldMessenger.of(context);
    // Entrega directa: primero la entrega (litros) y el análisis cuelga de ella, en planta.
    Recojo? entrega;
    if (widget.entregaDirecta) {
      try {
        entrega = await recojosApi.registrar(RecojoRequest(
          clientUuid: _uuidEntrega,
          productorId: widget.proveedor.id,
          fechaHora: DateTime.now(),
          litros: _numero(_litrosCtrl)!,
          fuente: 'PLANTA',
        ));
      } catch (e) {
        if (!mounted) return;
        setState(() {
          _error = mensajeDeError(e);
          _guardando = false;
        });
        return;
      }
    }
    final recojo = entrega ?? _recojoSeleccionado;
    final request = ControlCalidadRequest(
      recojoId: recojo?.id,
      productorId: recojo == null ? widget.proveedor.id : null,
      lugarAnalisis: widget.entregaDirecta ? LugarAnalisis.planta : LugarAnalisis.campo,
      fechaHora: DateTime.now(),
      densidad: _numero(_densidadCtrl),
      puntoCongelacion: _numero(_puntoCongCtrl),
      temperatura: _numero(_temperaturaCtrl),
      grasa: _numero(_grasaCtrl),
      solidosNoGrasos: _numero(_sngCtrl),
      solidosTotales: _numero(_solidosTotalesCtrl),
      resultado: _resultado,
      equipoUtilizado: _snCtrl.text.trim().isEmpty ? null : _snCtrl.text.trim(),
      aguaAnadida: _aguaAnadida,
      lugol: _lugol,
      motivoRechazo: _resultado == ResultadoCalidad.rechazada ? _motivoRechazoCtrl.text.trim() : null,
    );
    try {
      final controlId = await calidadApi.registrarControl(request);
      // El control ya quedó guardado; la foto es su evidencia (RN-12) y se sube después. Si no hay
      // señal, espera en la cola del teléfono: el control no se pierde por una foto pendiente.
      final foto = _foto;
      if (foto != null) {
        try {
          final enCola = await _subirOEncolarFoto(controlId, foto, calidadApi, sync);
          if (enCola) {
            messenger.showSnackBar(const SnackBar(
              content: Text('Control guardado. La foto del voucher se subirá sola cuando haya señal.'),
            ));
          }
        } catch (_) {
          // El control SÍ se guardó: no se muestra como error del formulario, porque volver a
          // guardarlo crearía un segundo control para el mismo recojo.
          messenger.showSnackBar(const SnackBar(
            content: Text('Control guardado, pero no se pudo conservar la foto del voucher en el teléfono.'),
          ));
        }
      }
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _guardando = false;
      });
    }
  }

  /// Devuelve true si la foto quedó en la cola en vez de subirse ahora.
  Future<bool> _subirOEncolarFoto(int controlId, XFile foto, CalidadApi api, SyncService sync) async {
    // Se copia al almacenamiento de la app: la foto de la cámara vive en una carpeta temporal que
    // Android puede vaciar antes de que vuelva la señal.
    final uuid = const Uuid().v4();
    final dir = Directory('${(await getApplicationDocumentsDirectory()).path}/vouchers');
    await dir.create(recursive: true);
    final copia = await File(foto.path).copy('${dir.path}/$uuid.jpg');

    final subida = SubidaVoucher(
      controlId: controlId,
      rutaFoto: copia.path,
      clientUuid: uuid,
      textoOcr: _textoOcr,
      valoresOcr: _lectura == null ? null : jsonEncode(_lectura!.valoresPropuestos()),
      confianzaOcr: _confianzaOcr,
    );
    try {
      await api.subirVoucher(subida);
      await copia.delete();
      return false;
    } catch (_) {
      await sync.encolarVoucher(subida);
      return true;
    }
  }

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: widget.proveedor.nombreCompleto,
      body: _cargandoRecojos
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (widget.entregaDirecta)
                      SeccionCard(
                        titulo: 'Entrega directa en planta',
                        subtitulo: 'El proveedor trajo la leche. Registra los litros y analiza la muestra.',
                        child: TextFormField(
                          controller: _litrosCtrl,
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          decoration: const InputDecoration(labelText: 'Litros recibidos'),
                          validator: (v) {
                            final n = double.tryParse((v ?? '').trim().replaceAll(',', '.'));
                            return n == null || n <= 0 ? 'Ingresa los litros recibidos' : null;
                          },
                        ),
                      )
                    else
                    SeccionCard(
                      titulo: 'Recojo de hoy',
                      child: _recojosHoy.isEmpty
                          ? const Text(
                              'El acopiador todavía no pasó por este proveedor. El análisis se guarda '
                              'ahora y se une solo al recojo cuando el acopiador lo registre.',
                            )
                          : DropdownButtonFormField<Recojo>(
                              initialValue: _recojoSeleccionado,
                              items: _recojosHoy
                                  .map((r) => DropdownMenuItem(value: r, child: Text('${r.litros.toStringAsFixed(1)} L · ${_hora(r.fechaHora)}')))
                                  .toList(),
                              onChanged: (v) => setState(() => _recojoSeleccionado = v),
                              decoration: const InputDecoration(labelText: 'Recojo'),
                            ),
                    ),
                    const SizedBox(height: 12),
                    SeccionCard(
                      titulo: 'Voucher del analizador',
                      subtitulo: 'SmartScan AI / LACTOMAT. La foto y el texto original quedan como evidencia.',
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          OutlinedButton.icon(
                            onPressed: _leyendoOcr ? null : () => _tomarFoto(ImageSource.camera),
                            icon: _leyendoOcr
                                ? const SizedBox(height: 16, width: 16, child: CircularProgressIndicator(strokeWidth: 2))
                                : const Icon(Icons.camera_alt_outlined),
                            label: Text(_leyendoOcr ? 'Leyendo…' : (_foto == null ? 'Tomar foto del voucher' : 'Tomar otra foto')),
                          ),
                          TextButton.icon(
                            onPressed: _leyendoOcr ? null : () => _tomarFoto(ImageSource.gallery),
                            icon: const Icon(Icons.photo_library_outlined),
                            label: const Text('Elegir foto de la galería'),
                          ),
                          if (_foto != null)
                            Padding(
                              padding: const EdgeInsets.only(top: 8),
                              child: Text('Foto capturada: ${_foto!.name}', style: const TextStyle(color: HuataColors.muted, fontSize: 12)),
                            ),
                          if (_aviso != null) ...[const SizedBox(height: 10), EstadoAviso(mensaje: _aviso!)],
                          if (_textoOcr != null)
                            ExpansionTile(
                              tilePadding: EdgeInsets.zero,
                              title: const Text('Texto reconocido por el OCR', style: TextStyle(fontSize: 13)),
                              children: [Text(_textoOcr!, style: const TextStyle(fontSize: 12, color: HuataColors.muted))],
                            ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 12),
                    SeccionCard(
                      titulo: 'Valores (edítalos si el OCR se equivocó)',
                      child: Column(
                        children: [
                          TextFormField(controller: _snCtrl, decoration: const InputDecoration(labelText: 'N° de serie / equipo')),
                          const SizedBox(height: 10),
                          TextFormField(
                            controller: _temperaturaCtrl,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true, signed: true),
                            decoration: const InputDecoration(labelText: 'Temperatura (°C)'),
                            validator: _validarNumero,
                          ),
                          const SizedBox(height: 10),
                          TextFormField(
                            controller: _densidadCtrl,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true),
                            decoration: const InputDecoration(labelText: 'Densidad'),
                            validator: _validarNumero,
                          ),
                          const SizedBox(height: 10),
                          TextFormField(
                            controller: _puntoCongCtrl,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true, signed: true),
                            decoration: const InputDecoration(labelText: 'Punto de congelación (°C)'),
                            validator: _validarNumero,
                          ),
                          const SizedBox(height: 10),
                          TextFormField(
                            controller: _grasaCtrl,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true),
                            decoration: const InputDecoration(labelText: 'Grasa (%)'),
                            validator: _validarNumero,
                          ),
                          const SizedBox(height: 10),
                          TextFormField(
                            controller: _sngCtrl,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true),
                            decoration: const InputDecoration(labelText: 'Sólidos no grasos - SNG (%)'),
                            validator: _validarNumero,
                          ),
                          const SizedBox(height: 10),
                          TextFormField(
                            controller: _solidosTotalesCtrl,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true),
                            decoration: const InputDecoration(labelText: 'Sólidos totales (%)'),
                            validator: _validarNumero,
                          ),
                          if (_aguaAnadidaLeida != null) ...[
                            const SizedBox(height: 10),
                            EstadoAviso(
                              mensaje: 'El voucher indica ${_aguaAnadidaLeida!.toStringAsFixed(1)} % de agua añadida. '
                                  'Tenlo en cuenta al elegir "Agua añadida" abajo: el sistema no lo decide solo (RN-10).',
                            ),
                          ],
                          for (final advertencia in _advertenciasOcr) ...[
                            const SizedBox(height: 10),
                            EstadoAviso(mensaje: advertencia, esError: true),
                          ],
                        ],
                      ),
                    ),
                    const SizedBox(height: 12),
                    SeccionCard(
                      titulo: 'Evaluación',
                      child: Column(
                        children: [
                          DropdownButtonFormField<String>(
                            initialValue: _resultado,
                            items: ResultadoCalidad.valores.map((v) => DropdownMenuItem(value: v, child: Text(v))).toList(),
                            onChanged: (v) => setState(() => _resultado = v!),
                            decoration: const InputDecoration(labelText: 'Resultado'),
                          ),
                          const SizedBox(height: 10),
                          DropdownButtonFormField<String>(
                            initialValue: _aguaAnadida,
                            items: AguaAnadida.valores.map((v) => DropdownMenuItem(value: v, child: Text(v))).toList(),
                            onChanged: (v) => setState(() => _aguaAnadida = v!),
                            decoration: const InputDecoration(labelText: 'Agua añadida'),
                          ),
                          const SizedBox(height: 10),
                          DropdownButtonFormField<String>(
                            initialValue: _lugol,
                            items: ResultadoLugol.valores.map((v) => DropdownMenuItem(value: v, child: Text(v))).toList(),
                            onChanged: (v) => setState(() => _lugol = v!),
                            decoration: const InputDecoration(labelText: 'Prueba de Lugol'),
                          ),
                          if (_resultado == ResultadoCalidad.rechazada) ...[
                            const SizedBox(height: 10),
                            TextFormField(
                              controller: _motivoRechazoCtrl,
                              decoration: const InputDecoration(labelText: 'Motivo de rechazo'),
                              validator: (v) => (_resultado == ResultadoCalidad.rechazada && (v == null || v.trim().isEmpty))
                                  ? 'Indica el motivo del rechazo'
                                  : null,
                            ),
                          ],
                        ],
                      ),
                    ),
                    if (_error != null) ...[const SizedBox(height: 12), EstadoAviso(mensaje: _error!, esError: true)],
                    const SizedBox(height: 16),
                    ElevatedButton(
                      onPressed: _guardando || _cargandoRecojos ? null : _guardar,
                      child: _guardando
                          ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                          : const Text('Guardar control de calidad'),
                    ),
                  ],
                ),
              ),
            ),
    );
  }

  String _hora(DateTime d) => '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
}
