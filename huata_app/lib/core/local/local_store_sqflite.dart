import 'dart:convert';

import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import 'local_store.dart';

/// Cola offline real (Android/iOS/desktop): una tabla `cola_sync` con una fila por escritura
/// pendiente, agrupada por `tipo` (`recojo`, `gps_testeador`, ...) para que un mismo dispositivo
/// pueda encolar varias clases de datos sin mezclar sus colas.
class LocalStoreImpl implements LocalStore {
  Database? _db;

  Future<Database> get _database async {
    final db = _db;
    if (db != null) return db;
    final path = p.join(await getDatabasesPath(), 'huata_offline.db');
    final opened = await openDatabase(
      path,
      version: 1,
      onCreate: (db, version) => db.execute('''
        CREATE TABLE cola_sync (
          tipo TEXT NOT NULL,
          client_uuid TEXT NOT NULL,
          payload TEXT NOT NULL,
          creado_en TEXT NOT NULL,
          ultimo_error TEXT,
          PRIMARY KEY (tipo, client_uuid)
        )
      '''),
    );
    _db = opened;
    return opened;
  }

  @override
  Future<void> init() async {
    await _database;
  }

  @override
  Future<void> encolar(String tipo, String clientUuid, Map<String, dynamic> payload) async {
    final db = await _database;
    await db.insert('cola_sync', {
      'tipo': tipo,
      'client_uuid': clientUuid,
      'payload': jsonEncode(payload),
      'creado_en': DateTime.now().toIso8601String(),
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }

  @override
  Future<List<PendienteSync>> pendientes(String tipo) async {
    final db = await _database;
    // rowid desempata dos escrituras del mismo instante: el orden de envío es el de escritura.
    final filas = await db.query('cola_sync', where: 'tipo = ?', whereArgs: [tipo], orderBy: 'creado_en ASC, rowid ASC');
    return filas
        .map(
          (f) => PendienteSync(
            clientUuid: f['client_uuid'] as String,
            payload: jsonDecode(f['payload'] as String) as Map<String, dynamic>,
            creadoEn: DateTime.parse(f['creado_en'] as String),
            ultimoError: f['ultimo_error'] as String?,
          ),
        )
        .toList();
  }

  @override
  Future<void> eliminar(String tipo, String clientUuid) async {
    final db = await _database;
    await db.delete('cola_sync', where: 'tipo = ? AND client_uuid = ?', whereArgs: [tipo, clientUuid]);
  }

  @override
  Future<void> marcarError(String tipo, String clientUuid, String error) async {
    final db = await _database;
    await db.update(
      'cola_sync',
      {'ultimo_error': error},
      where: 'tipo = ? AND client_uuid = ?',
      whereArgs: [tipo, clientUuid],
    );
  }

  @override
  Future<void> limpiarError(String tipo, String clientUuid) async {
    final db = await _database;
    await db.update(
      'cola_sync',
      {'ultimo_error': null},
      where: 'tipo = ? AND client_uuid = ?',
      whereArgs: [tipo, clientUuid],
    );
  }
}
