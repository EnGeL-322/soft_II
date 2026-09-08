# 03 — ARQUITECTURA Y MODELO FUNCIONAL SUGERIDO

> Este documento es una propuesta de implementación derivada de los requisitos. No reemplaza el Excel final.

## Arquitectura

### Frontend web
- Angular.
- Panel administrativo responsive.
- Consumir API REST con JWT.
- Guards por rol.
- Tablas con filtros/paginación.

### App móvil
- Flutter / Android.
- SQLite para persistencia offline.
- Cola local de sincronización.
- UUID generado en dispositivo para evitar duplicados.
- Captura de foto/firma/evidencia.
- Sincronización automática y reintentos.
- FCM para push cuando esté habilitado.

### Backend
- Java 21 + Spring Boot.
- Spring Security + JWT.
- PostgreSQL.
- API REST versionada, por ejemplo `/api/v1`.
- Auditoría: createdAt, updatedAt, createdBy, updatedBy.
- Evitar borrado físico de registros críticos.

## Entidades mínimas recomendadas

- `User`
- `Role`
- `Producer`
- `Collector` / `Acopiador`
- `CollectionCenter`
- `Route`
- `RouteAssignment`
- `Vehicle` (si Huata confirma gestión de movilidad)
- `MilkCollection` / `Recojo`
- `QualityTest`
- `QualityParameter`
- `QualityEvidence`
- `QualityEquipment`
- `EquipmentCheck` / mantenimiento
- `Liquidation`
- `LiquidationDetail`
- `Notification`
- `SyncRecord` / estado de sincronización
- `BusinessParameter` para tarifas, rangos y límites configurables

## Estados recomendados

### Recojo
- DRAFT
- SAVED_OFFLINE
- PENDING_SYNC
- SYNCED
- CANCELLED (con motivo, no borrado)

### Calidad
- PENDING
- ACCEPTED / ACEPTADA
- OBSERVED / OBSERVADA
- REJECTED / RECHAZADA

### Evidencia
- PENDING_UPLOAD
- UPLOADED
- FAILED

### Liquidación
- DRAFT
- CALCULATED
- REVIEWED
- CLOSED

## Endpoints orientativos

- `POST /auth/login`
- `GET/POST/PUT /producers`
- `GET/POST/PUT /collectors`
- `GET/POST/PUT /routes`
- `GET/POST/PUT /collection-centers`
- `GET /mobile/me/route-today`
- `GET /mobile/me/assigned-producers`
- `POST /collections`
- `POST /collections/batch-sync`
- `POST /collections/{id}/quality-tests`
- `POST /collections/{id}/evidence`
- `GET /producers/{id}/quality-history`
- `GET /liquidations`
- `POST /liquidations/calculate`
- `GET /reports/production`
- `GET /notifications`
- `GET/POST /business-parameters`

## Sincronización offline

Cada registro móvil debería tener:
- `clientUuid`
- `serverId` opcional hasta sincronizar
- `syncStatus`
- `localCreatedAt`
- `serverUpdatedAt`
- `retryCount`

El backend debe tratar `clientUuid` como llave idempotente para que reintentar no duplique recojos.

## Configuración de negocio

No hardcodear:
- precios por litro;
- rangos de densidad/acidez/etc.;
- límite de temperatura;
- cantidad máxima de incidencias;
- nombres de zonas;
- canales de notificación.

Crear una configuración versionada con fecha de vigencia y usuario que hizo el cambio.
