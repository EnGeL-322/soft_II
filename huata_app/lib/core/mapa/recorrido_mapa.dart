import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:latlong2/latlong.dart' as ll;

/// Cada cuántos metros se guarda un punto del recorrido (acopiador y testeador). Pedido del
/// cliente: 10 m, para que el recorrido dibujado siga las calles y no corte esquinas. Es un filtro
/// de distancia y no de tiempo (RN-19): parado no se generan puntos; el latido de ruta_dia_screen
/// se encarga de que la web lo siga viendo conectado.
const int distanciaEntrePuntosGpsMetros = 10;

/// Cómo se dibuja el recorrido real en el mapa.
enum ModoRecorrido {
  trazado('Trazado', Icons.timeline),
  puntos('Puntos', Icons.scatter_plot),
  ambos('Ambos', Icons.route);

  const ModoRecorrido(this.etiqueta, this.icono);
  final String etiqueta;
  final IconData icono;
}

/// Preferencia de la persona: ver el recorrido como línea, como puntos o ambos. Se guarda en el
/// teléfono para que no haya que elegirla cada vez que se abre el mapa.
class PreferenciasMapa extends ChangeNotifier {
  PreferenciasMapa({FlutterSecureStorage? storage}) : _storage = storage ?? const FlutterSecureStorage();

  final FlutterSecureStorage _storage;
  static const _clave = 'huata_modo_recorrido';

  ModoRecorrido _modo = ModoRecorrido.trazado;
  ModoRecorrido get modo => _modo;

  Future<void> cargar() async {
    try {
      final guardado = await _storage.read(key: _clave);
      final elegido = ModoRecorrido.values.where((m) => m.name == guardado);
      if (elegido.isNotEmpty) {
        _modo = elegido.first;
        notifyListeners();
      }
    } catch (_) {
      // Sin almacenamiento disponible se usa el valor por defecto; no es un error para el usuario.
    }
  }

  Future<void> cambiar(ModoRecorrido modo) async {
    if (modo == _modo) return;
    _modo = modo;
    notifyListeners();
    try {
      await _storage.write(key: _clave, value: modo.name);
    } catch (_) {
      // Si no se pudo guardar, igual se aplica en esta sesión.
    }
  }
}

/// Capas del mapa para un recorrido real según el modo elegido. Devuelve la línea, los puntos, o
/// las dos cosas; el último punto (donde está ahora) siempre se marca más grande.
List<Widget> capasRecorrido(List<ll.LatLng> puntos, ModoRecorrido modo, {required Color color}) {
  if (puntos.isEmpty) return const [];
  final conLinea = modo != ModoRecorrido.puntos && puntos.length > 1;
  final conPuntos = modo != ModoRecorrido.trazado;
  return [
    if (conLinea) PolylineLayer(polylines: [Polyline(points: puntos, strokeWidth: 4, color: color)]),
    CircleLayer(
      circles: [
        if (conPuntos)
          for (final p in puntos.take(puntos.length - 1))
            CircleMarker(
              point: p,
              radius: 4,
              color: color,
              borderColor: Colors.white,
              borderStrokeWidth: 1.5,
            ),
        CircleMarker(
          point: puntos.last,
          radius: 8,
          color: color,
          borderColor: Colors.white,
          borderStrokeWidth: 3,
        ),
      ],
    ),
  ];
}

/// Selector "Ver recorrido como: Trazado · Puntos · Ambos", encima del mapa.
class SelectorModoRecorrido extends StatelessWidget {
  const SelectorModoRecorrido({super.key, required this.modo, required this.onCambio});

  final ModoRecorrido modo;
  final ValueChanged<ModoRecorrido> onCambio;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
      child: Row(
        children: [
          const Text('Ver recorrido:', style: TextStyle(fontWeight: FontWeight.w600)),
          const SizedBox(width: 8),
          Expanded(
            child: SegmentedButton<ModoRecorrido>(
              showSelectedIcon: false,
              segments: [
                for (final m in ModoRecorrido.values)
                  ButtonSegment(value: m, label: Text(m.etiqueta), icon: Icon(m.icono, size: 18)),
              ],
              selected: {modo},
              onSelectionChanged: (s) => onCambio(s.first),
            ),
          ),
        ],
      ),
    );
  }
}
