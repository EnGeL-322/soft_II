# huata_app

App móvil Flutter de Huata Lácteos: una sola app, 4 experiencias por rol tras el login
(`ACOPIADOR`, `PROVEEDOR`, `CALIDAD` = testeador, `PRODUCTOR_QUESO`), contra el mismo backend
Spring Boot que usa `huata-web/`. Ver `CLAUDE.md` en la raíz del repo para el contexto de negocio
completo.

## Ejecutar en desarrollo

```
flutter pub get
flutter run                 # con un emulador/dispositivo Android o iOS conectado
```

Por defecto apunta a `http://10.0.2.2:8080/api/v1` (emulador Android) o `http://localhost:8080/api/v1`
(otros targets nativos). Para apuntar a otro backend:

```
flutter run --dart-define=HUATA_API_BASE_URL=http://TU_HOST:8080/api/v1
```

## Build web (solo para probar sin compilar un APK/IPA)

GPS real, cámara y OCR on-device **no funcionan** en este build — son plugins nativos sin
equivalente de navegador completo para este caso de uso. Sirve para navegar login, listas y
formularios. Ver la nota larga en `Dockerfile` y en `features/testeador/voucher_screen.dart`.

```
flutter build web --release
docker compose up -d movil   # desde la raíz del repo; sirve en http://localhost:8082
```

## Estructura

- `lib/core/` — API client (Dio + JWT), modelos (espejo de los DTOs del backend), sesión/auth,
  cola offline (`local/`, sqflite en móvil / memoria en web vía conditional export), sincronización
  automática (`sync/`), parser de voucher OCR (`ocr/`, puro Dart, con test).
- `lib/features/` — una carpeta por experiencia (`acopiador/`, `proveedor/`, `testeador/`,
  `productor_queso/`), más `auth/` (login) y `shell/` (enrutador por rol).
- `lib/shared/` — widgets comunes (scaffold, tarjetas, avisos).

## Pruebas

```
flutter analyze
flutter test
```

`test/core/ocr/voucher_ocr_parser_test.dart` verifica el parser contra las dos muestras reales de
voucher (SmartScan AI / LACTOMAT) documentadas en `CLAUDE.md`. `test/widget_test.dart` es un smoke
test de que la app arranca en el login sin sesión guardada.

## Limitaciones conocidas (no resueltas a propósito, ver el resumen de la fase para el detalle)

- El testeador no puede enviar su recorrido GPS al backend: `POST /api/v1/ubicaciones` solo acepta
  acopiadores (columna `acopiador_id` NOT NULL). Se captura localmente pero no se sincroniza.
- Un control de calidad exige un `Recojo` ya existente (`recojoId` NOT NULL): el testeador no puede
  adelantarse al acopiador y registrar calidad antes de que exista la recepción del día.
- La foto del voucher no se sube a ningún lado: no existe un endpoint de carga de archivos en el
  backend (`EvidenciaCalidadRequest.urlArchivo` espera una URL ya existente, no bytes de imagen).
- La app del proveedor no muestra estimación de cobro ni historial de pagos: esos endpoints exigen
  `pagos.read`, permiso que el rol `PROVEEDOR` no tiene.
- El proveedor no puede consultar el historial de sus propias solicitudes de cambio de zona (mismo
  motivo: exige `proveedores.read`, que tampoco tiene).
