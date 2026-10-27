# Puntos establecidos en tiempo real

Backend: Spring WebSocket/STOMP (módulos ya presentes en `pom.xml`). Flutter:
`stomp_dart_client`, Provider/ChangeNotifier y el `TokenStore` existente sobre
`flutter_secure_storage`.

## Contrato

- Handshake: `/api/ws/puntos`, JWT en `Authorization: Bearer` para Flutter nativo.
  Flutter web usa `token` como parámetro porque el navegador no permite esa cabecera.
  No se registran credenciales ni frames; nginx omite el acceso a los canales WebSocket.
- Suscripción: `/topic/rutas/{rutaId}/puntos`. Se comprueba la ruta del día del
  acopiador (o la habitual si no tiene asignación). No se aceptan comodines,
  destinos de otra sesión ni publicaciones del cliente.
- Suscripción adicional: `/user/queue/puntos`, únicamente del propio usuario,
  para puntos asignados directamente que pueden pertenecer a otra ruta.
- Cada SUBSCRIBE pide un `receipt`; el servidor lo confirma después de registrar
  la suscripción. Solo después de ambos recibos se carga REST.
- Las publicaciones usan `@TransactionalEventListener(AFTER_COMMIT)` y un DTO
  inmutable capturado después de `saveAndFlush`. Un rollback no emite eventos.

```json
{
  "tipo": "PUNTO_ACTUALIZADO",
  "punto": {
    "id": 15,
    "nombre": "Cruce principal",
    "latitud": -15.65,
    "longitud": -69.98,
    "activo": true,
    "rutaId": 1,
    "proveedor": { "id": 3, "codigo": "DEMO", "nombre": "Proveedor DEMO" },
    "version": 2
  }
}
```

Tipos: `PUNTO_ACTIVADO` (también creación), `PUNTO_DESACTIVADO`,
`PUNTO_ACTUALIZADO`, `PUNTO_ELIMINADO`. El DTO también conserva los campos
REST anteriores, `acopiadorId` y `actualizadoEn`. La migración V45 añade
`version` para bloqueo optimista y `eliminado` para la baja definitiva lógica.

## REST

- `GET /api/v1/puntos-establecidos/me`: se conserva la lista de activos.
- `GET /api/v1/puntos-establecidos/me/snapshot`: `{puntos, bajas}`. La lista
  activa y las bajas versionadas se leen juntas. Las bajas nunca se dibujan;
  evitan que una activación vieja resucite un punto después de una reconexión.
- `GET /api/v1/puntos-establecidos?incluirInactivos=true`: administración,
  requiere `rutas.write` para incluir inactivos. No incluye eliminados.
- `POST /api/v1/puntos-establecidos`: crear.
- `PUT /api/v1/puntos-establecidos/{id}`: editar datos de un punto activo.
- `PUT /api/v1/puntos-establecidos/{id}/estado`: `{"activo": true|false}`.
- `DELETE /api/v1/puntos-establecidos/{id}`: se conserva desactivar.
- `DELETE /api/v1/puntos-establecidos/{id}/definitivo`: eliminar lógicamente,
  conservando auditoría y referencias; no permite reactivación.

## Ciclo de la app

`RutaDiaScreen` identifica la ruta, abre STOMP y espera las suscripciones.
`PuntosSocketService` carga el snapshot y combina los eventos que llegan mientras
REST está pendiente. Descarta versiones iguales o menores y mantiene las versiones
de bajas. El mapa y la lista usan la misma colección ordenada por nombre/id, por
lo que contador y números se recalculan juntos. La lista de proveedores es
independiente: desactivar una referencia no retira al proveedor de su ruta.

Ante un corte, STOMP reconecta con 1, 2, 4, 8, 16 y hasta 30 segundos; cada
suscripción nueva dispara un snapshot. `WidgetsBindingObserver` renueva el socket
al recibir `resumed`, y los cambios de red también lo renuevan. Un error REST
reintenta con el mismo backoff, conservando el último estado visible. No hay
polling de puntos ni REST periódico después de una sincronización exitosa.
La pantalla cierra el socket y sus timers en `dispose()` y cuando termina la sesión.

El broker simple está en memoria en una instancia de Spring Boot. Si se despliega
con varias instancias, debe sustituirse por un broker compartido antes de repartir
conexiones entre ellas. La resincronización REST cubre reinicios de una instancia.

## Verificación

```powershell
# huata/
mvn -Dtest=PuntoEstablecidoServiceTests,PuntosTiempoRealTests test
# huata_app/
flutter analyze
flutter test
# huata-web/
npm test -- --watch=false --include=src/app/features/rutas/rutas.component.spec.ts
```

En un teléfono/emulador con la nueva app, entrar como acopiador y abrir Ruta del
día. Desde la web, crear un punto de esa ruta, editar nombre/coordenadas,
desactivar, activar y eliminar. Comprobar lista, mapa y numeración sin refrescar.
Desconectar la red del teléfono, desactivar un punto en la web y recuperar la red:
el snapshot debe retirarlo. Repetir dejando la app en segundo plano; al volver
debe actualizar sin mostrar una lista vacía durante la petición. Reiniciar el
backend para comprobar reconexión. Cerrar sesión para comprobar que se detiene.
