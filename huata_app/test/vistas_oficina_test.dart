import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/api/personas_api.dart';
import 'package:huata_app/core/auth/session.dart';
import 'package:huata_app/core/models/acopio_oficina.dart';
import 'package:huata_app/core/models/produccion_oficina.dart';
import 'package:huata_app/core/models/ventas_pagos.dart';
import 'package:huata_app/features/shell/modulos_app.dart';

Session _sesion(List<String> roles, List<String> permisos) =>
    Session(nombreCompleto: 'X', username: 'x@huata.pe', roles: roles, permisos: permisos);

void main() {
  group('ciclo de liquidación (jueves a miércoles, como la web)', () {
    test('un sábado pertenece al ciclo que empezó el jueves anterior', () {
      final c = CicloPago.recientes(1, DateTime(2026, 10, 24)).single; // sábado
      expect(c.inicio, DateTime(2026, 10, 22)); // jueves
      expect(c.fin, DateTime(2026, 10, 28)); // miércoles
    });

    test('un jueves empieza su propio ciclo y un miércoles cierra el anterior', () {
      expect(CicloPago.recientes(1, DateTime(2026, 10, 22)).single.inicio, DateTime(2026, 10, 22));
      expect(CicloPago.recientes(1, DateTime(2026, 10, 21)).single.inicio, DateTime(2026, 10, 15));
    });

    test('los ciclos anteriores retroceden de semana en semana', () {
      final ciclos = CicloPago.recientes(3, DateTime(2026, 10, 24));
      expect(ciclos.map((c) => c.inicio), [DateTime(2026, 10, 22), DateTime(2026, 10, 15), DateTime(2026, 10, 8)]);
      expect(ciclos.first.etiqueta, '22 oct — 28 oct 2026');
    });
  });

  group('lectura de las respuestas del backend', () {
    test('resumen del día', () {
      final r = ResumenOperacion.fromJson({
        // Litros, como en OperacionService.ResumenDia: los aceptados incluyen los observados.
        'aceptados': 140.5,
        'observados': 20,
        'rechazados': 10,
        'recepcionesAceptadas': 4,
        'controlesObservados': 1,
        'entregasRechazadas': 1,
        'compraEstimada': 210.4,
        'proveedores': 5,
        'pendientesCalidad': 2,
        'liquidacionesPendientes': 3,
        'rutas': [
          {'nombre': 'Ruta A', 'litros': 120.5, 'recepciones': 4},
          {'nombre': 'Ruta B', 'litros': 30, 'recepciones': 1},
        ],
        'cierre': {'cerrado': false, 'litrosSistema': 150.5, 'litrosPlanta': 0},
      });
      expect(r.litrosTotales, 150.5);
      expect(r.entregasBuenas, 3);
      expect(r.litrosBuenos, 120.5);
      expect(r.compraEstimada, 210.4);
      expect(r.cierre!.cerrado, isFalse);
    });

    test('fila de planilla: nombre completo y liquidación cerrada', () {
      final f = FilaPlanilla.fromJson({
        'proveedorId': 7,
        'proveedorCodigo': 'P-007',
        'proveedorNombre': 'Ana',
        'proveedorApellidos': 'Quispe',
        'zona': null,
        'excelencia': true,
        'litrosEntregados': 80,
        'litrosRechazados': 0,
        'litrosPagados': 0,
        'litrosPendientes': 80,
        'tarifaAplicada': 1.6,
        'importePendiente': 128,
        'liquidado': true,
        'liquidacionId': 33,
        'tieneLiquidacionCerrada': false,
      });
      expect(f.proveedorNombre, 'Ana Quispe');
      expect(f.liquidacionId, 33);
      expect(f.importePendiente, 128);
    });

    test('venta por cobrar y venta anulada', () {
      Venta v(Map<String, dynamic> extra) => Venta.fromJson({
        'id': 1,
        'loteCodigo': 'L1',
        'productoNombre': 'Queso',
        'fecha': '2026-10-20',
        'cliente': 'Tienda',
        'unidades': 3,
        'tipoComprador': 'MAYORISTA',
        'precioUnitario': 12,
        'total': 36,
        'condicion': 'CREDITO_7D',
        'medioCobro': 'EFECTIVO',
        'estado': 'POR_COBRAR',
        'anulada': false,
        ...extra,
      });
      expect(v({}).porCobrar, isTrue);
      expect(v({'anulada': true}).porCobrar, isFalse);
      expect(v({'estado': 'COBRADO'}).porCobrar, isFalse);
    });

    test('tarifa de venta: el precio depende del tipo de comprador', () {
      final t = TarifaProducto.fromJson({
        'productoId': 1,
        'productoNombre': 'Queso',
        'precioPublicoGeneral': 15,
        'precioMayorista': 12,
        'precioProveedor': 10,
        'vigenteDesde': '2026-01-01',
        'promocion': false,
      });
      expect(t.precioPara('PUBLICO_GENERAL'), 15);
      expect(t.precioPara('MAYORISTA'), 12);
      expect(t.precioPara('PROVEEDOR'), 10);
    });

    test('materia prima y movimiento de kardex', () {
      final m = MateriaPrima.fromJson({'id': 1, 'nombre': 'Cuajo', 'unidadMedida': 'GRAMO', 'stock': 250, 'activo': true});
      expect(m.unidad, 'g');
      expect(m.lecheAcopio, isFalse);
      final mov = MovimientoInventario.fromJson({'id': 1, 'tipo': 'AJUSTE_ENTRADA', 'cantidad': 5, 'fecha': '2026-10-20T10:00:00Z', 'usuario': 'a'});
      expect(mov.esEntrada, isTrue);
    });

    test('zona con sus proveedores y acopiadores', () {
      final z = ZonaAsignacion.fromJson({
        'zona': 'Norte',
        'rutaId': 2,
        'rutaNombre': 'Ruta Norte',
        'proveedores': [
          {'id': 1, 'codigo': 'P1', 'nombres': 'Luis', 'apellidos': 'Mamani', 'ordenEnRuta': 2},
        ],
        'acopiadores': [
          {'id': 9, 'nombres': 'Rosa', 'apellidos': 'Condori'},
        ],
      });
      expect(z.proveedores.single.nombre, 'Luis Mamani');
      expect(z.proveedores.single.orden, 2);
      expect(z.acopiadores, ['Rosa Condori']);
    });

    test('conciliación de una ruta sin recepción anotada', () {
      final c = ConciliacionRuta.fromJson({
        'rutaId': 1,
        'rutaNombre': 'Ruta A',
        'litrosEnRuta': 100,
        'litrosEntregadosEnPlanta': 0,
        'proveedoresAcopiados': 5,
        'proveedoresNoEntregaron': 1,
        'detalle': [
          {'fechaHora': '2026-10-20T10:00:00Z', 'litros': 20, 'estadoVisita': 'ACOPIADO', 'calidad': 'ACEPTADA'},
        ],
      });
      expect(c.recibida, isFalse);
      expect(c.detalle.single.calidad, 'ACEPTADA');
    });
  });

  test('el administrador ve todas las vistas de la web en la app, agrupadas', () {
    const todos = [
      'panel.read', 'calidad.read', 'calidad.write', 'calidad.aprobar', 'acopio.read', 'rutas.read', 'zonas.read',
      'proveedores.read', 'acopiadores.read', 'usuarios.read', 'recetas.read', 'produccion.read', 'compras.read',
      'inventario.read', 'quesos.read', 'pagos.read', 'contabilidad.read', 'parametros.read',
    ];
    final ids = modulosDisponibles(_sesion(['ADMINISTRADOR'], todos)).map((m) => m.id).toList();
    expect(ids, containsAll([
      'panel', 'calidad', 'recojos', 'recepcion', 'usuarios', 'proveedores', 'acopiadores', 'rutas', 'zonas',
      'cambios-zona', 'recetas', 'produccion', 'compras', 'inventario', 'ventas', 'pagos-oficina', 'contabilidad',
      'precios-calidad', 'cuenta',
    ]));
    expect(ids.toSet().length, ids.length, reason: 'sin módulos repetidos');
    expect(ids.last, 'cuenta');
  });

  test('los ids del catálogo son únicos', () {
    final ids = catalogoModulos.map((m) => m.id).toList();
    expect(ids.toSet().length, ids.length);
  });
}
