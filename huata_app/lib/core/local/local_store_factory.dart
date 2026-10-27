// Selección en tiempo de compilación: `sqflite` real cuando `dart:io` existe (Android/iOS/desktop),
// la cola en memoria en el resto (web). Mismo idioma que usa el propio paquete `http` para separar
// su cliente IO del cliente de navegador — ver el comentario en `local_store.dart`.
export 'local_store_memory.dart' if (dart.library.io) 'local_store_sqflite.dart';
