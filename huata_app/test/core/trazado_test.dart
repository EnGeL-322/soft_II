import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/api/trazados_api.dart';
import 'package:huata_app/core/models/trazado.dart';

/// "Pintar ruta del día" del acopiador (V27), del lado del móvil.
///
/// Lo que se fija aquí es lo que decide qué ve y qué envía la pantalla: con qué trazo parte a dibujar
/// (para que quien va por la designada no tenga que redibujarla) y que el cuerpo que se guarda en la
/// cola offline sea exactamente el que se iba a enviar.
void main() {
  Map<String, dynamic> punto(int s, double lat) => {'secuencia': s, 'latitud': lat, 'longitud': -69.9};

  Map<String, dynamic> respuesta({
    List<Map<String, dynamic>>? delDia,
    List<Map<String, dynamic>>? predeterminado,
    List<Map<String, dynamic>>? deLaRuta,
  }) => {
    'acopiadorId': 10,
    'acopiadorNombre': 'Juan Condori',
    'rutaId': 3,
    'rutaNombre': 'Ruta COLLANA I',
    'zona': 'COLLANA I',
    'fecha': '2026-09-22',
    'delDia': delDia == null
        ? null
        : {
            'id': 501,
            'acopiadorId': 10,
            'acopiadorNombre': 'Juan Condori',
            'rutaId': 3,
            'rutaNombre': 'Ruta COLLANA I',
            'tipo': 'DIA',
            'fecha': '2026-09-22',
            'sigueRutaDesignada': false,
            'puntos': delDia,
          },
    'predeterminado': predeterminado == null
        ? null
        : {
            'id': 900,
            'acopiadorId': 10,
            'acopiadorNombre': 'Juan Condori',
            'rutaId': 3,
            'rutaNombre': 'Ruta COLLANA I',
            'tipo': 'PREDETERMINADO',
            'sigueRutaDesignada': true,
            'puntos': predeterminado,
          },
    'deLaRuta': deLaRuta ?? const [],
  };

  group('MiTrazado', () {
    test('lee los tres recorridos que manda el backend', () {
      final mio = MiTrazado.fromJson(
        respuesta(
          delDia: [punto(0, -15.6), punto(1, -15.61)],
          predeterminado: [punto(0, -15.5)],
          deLaRuta: [punto(0, -15.4), punto(1, -15.41), punto(2, -15.42)],
        ),
      );

      expect(mio.rutaNombre, 'Ruta COLLANA I');
      expect(mio.delDia!.puntos, hasLength(2));
      expect(mio.delDia!.sigueRutaDesignada, isFalse);
      expect(mio.predeterminado!.esPredeterminado, isTrue);
      expect(mio.deLaRuta, hasLength(3));
    });

    /// Se parte de lo ya pintado hoy: el acopiador está corrigiendo su propio dibujo.
    test('si ya pintó hoy, la base para dibujar es ese trazado', () {
      final mio = MiTrazado.fromJson(
        respuesta(
          delDia: [punto(0, -15.6), punto(1, -15.61)],
          predeterminado: [punto(0, -15.5)],
          deLaRuta: [punto(0, -15.4)],
        ),
      );

      expect(mio.baseParaPintar, hasLength(2));
    });

    /// Sin nada pintado hoy, su recorrido habitual; es lo que evita empezar de cero cada día.
    test('sin trazado de hoy, la base es su recorrido predeterminado', () {
      final mio = MiTrazado.fromJson(
        respuesta(predeterminado: [punto(0, -15.5), punto(1, -15.51)], deLaRuta: [punto(0, -15.4)]),
      );

      expect(mio.baseParaPintar, hasLength(2));
      expect(mio.baseParaPintar.first.latitud, -15.5);
    });

    /// El respaldo acordado: sin trazado propio, la geometría de la ruta designada.
    test('sin trazado propio, la base es la geometría de la ruta', () {
      final mio = MiTrazado.fromJson(respuesta(deLaRuta: [punto(0, -15.4), punto(1, -15.41)]));

      expect(mio.delDia, isNull);
      expect(mio.predeterminado, isNull);
      expect(mio.baseParaPintar.first.latitud, -15.4);
    });

    test('un acopiador sin nada dibujado en ningún sitio no rompe la pantalla', () {
      final mio = MiTrazado.fromJson(respuesta());

      expect(mio.baseParaPintar, isEmpty);
    });
  });

  group('cuerpo que se envía (y se encola)', () {
    test('manda la fecha en que se pintó, no la del envío', () {
      final cuerpo = TrazadosApi.cuerpo(
        fecha: DateTime(2026, 9, 20),
        sigueRutaDesignada: false,
        puntos: [TrazadoPunto(secuencia: 0, latitud: -15.6, longitud: -69.9)],
      );

      // Es lo que hace que un trazado que esperó en la cola quede en su día y no en el de la sincronización.
      expect(cuerpo['fecha'], '2026-09-20');
      expect(cuerpo['sigueRutaDesignada'], isFalse);
    });

    test('la observación vacía no se envía como cadena en blanco', () {
      final cuerpo = TrazadosApi.cuerpo(
        fecha: DateTime(2026, 9, 20),
        sigueRutaDesignada: true,
        observacion: '   ',
        puntos: [TrazadoPunto(secuencia: 0, latitud: -15.6, longitud: -69.9)],
      );

      expect(cuerpo.containsKey('observacion'), isFalse);
    });

    test('conserva el orden de los puntos, que es el recorrido', () {
      final cuerpo = TrazadosApi.cuerpo(
        fecha: DateTime(2026, 9, 20),
        sigueRutaDesignada: true,
        puntos: [
          TrazadoPunto(secuencia: 0, latitud: -15.60, longitud: -69.90),
          TrazadoPunto(secuencia: 1, latitud: -15.61, longitud: -69.91),
          TrazadoPunto(secuencia: 2, latitud: -15.62, longitud: -69.92),
        ],
      );

      final puntos = cuerpo['puntos'] as List;
      expect(puntos.map((p) => (p as Map)['secuencia']), [0, 1, 2]);
      expect((puntos.last as Map)['latitud'], -15.62);
    });
  });
}
