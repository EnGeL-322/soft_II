# DESARROLLO.md — Sistema Huata Lácteos · Control Integral
**Versión 2 (final).** Reemplaza a `DESARROLLO1.md`. Verificado línea por línea contra el prototipo `huata_lacteos_verde_andino_v2_7.html` (18 201 líneas, 387 funciones JS, 13 vistas internas + portal público).

Este documento define **qué se construye y en qué orden**. `CLAUDE.md` define **cómo** se construye (calidad técnica, disciplina por fases, separación de capas, pruebas). Ambos se leen juntos.

Proyecto del curso de **Software II**. El sílabo de "Pruebas y Despliegue del Software" (UPeU, 2026-2) se usa únicamente como **catálogo de buenas prácticas** (tipos de prueba, herramientas, CI/CD, despliegue, observabilidad); no fija fechas, notas ni evaluaciones de este proyecto.

**Fuentes:**
- `huata_lacteos_verde_andino_v2_7.html` — prototipo funcional (HTML/JS con `localStorage`). Referencia de reglas de negocio, roles y UX. No se porta literalmente: no tiene backend, ni persistencia real, ni autenticación real.
- Sílabo "Pruebas y Despliegue del Software" — catálogo técnico (sección 3).
- **Tabla de corrección de densidad a 15 °C** (fotografías del material impreso de planta, 2026) — incorporada como regla de negocio en la sección 7.5. Es material **nuevo**: el prototipo HTML no la implementa.

### Qué cambió respecto de `DESARROLLO1.md`
| # | Cambio | Motivo |
|---|---|---|
| 1 | La regla de decisión de recepción se reescribió por completo | El prototipo **redefine `receptionDecision()` cuatro veces** (líneas 9578, 10161, 13785, 14334). La versión anterior del documento describía la **primera**, que en tiempo de ejecución queda sobrescrita. La autoritativa es la de la línea 14334. |
| 2 | Se añadieron 3 reglas de negocio que faltaban por completo | Proveedor Excelencia, retarificación retroactiva de la semana, y retiro automático por segunda incidencia. |
| 3 | Se añadió la corrección de densidad a 15 °C | Material nuevo aportado por el equipo (sección 7.5). |
| 4 | Se añadieron 6 módulos que el documento anterior no mencionaba | Portal público, Trazabilidad, Cierre de jornada, Seguimiento GPS de rutas, Evidencia fotográfica, Impresión de boletas. |
| 5 | Se corrigieron 4 contradicciones internas | Angular LTS vs. Angular 20; `@DynamicPropertySource` vs. `@ServiceConnection`; Excel en Fase 2 vs. Fase 3; "cuatro fases" vs. "Fases 1-3". |
| 6 | Toda fase tiene ahora **criterio de salida verificable** | Antes solo la Fase 1 lo tenía. |
| 7 | Se cerraron los huecos de asignación | Auditoría, gestión de usuarios, pantalla de configuración, datos semilla, dashboard. |

---

## 1. Qué es el sistema

Huata Lácteos es una empresa de acopio de leche en la zona de Puno. El sistema gestiona el ciclo completo desde el recojo en campo hasta la liquidación semanal y la producción de queso:

- **Proveedores** por zona, con estados (Activo, Pendiente aprobación, En revisión, Inactivo) y tipos (Regular, Rotativo, Entrega directa).
- **Recepción/Acopio**: cada entrega registra litros, envases, capacidad, densidad, referencia de campo, punto crioscópico, prueba de agua y prueba de Lugol. El sistema **decide automáticamente** calidad, aceptación y precio.
- **Calidad**: historial de incidencias, reincidencia, evidencia fotográfica (vouchers de analizador), y **corrección de densidad por temperatura**.
- **Pagos**: liquidación semanal (jueves→miércoles, pago el viernes siguiente) con ajustes, marcas de pago y boletas imprimibles.
- **Producción de quesos**: producción por lote y ventas por tipo de comprador.
- **Zonas y rendimiento**, **rutas con seguimiento de avance**, **recojo móvil** conciliado con la recepción de planta.
- **Trazabilidad**, **cierre de jornada**, **reportes**, **auditoría**, **importación/exportación Excel** y **respaldo JSON**.
- **Portal público** de consulta de calidad, sin autenticación.

---

## 2. Inventario verificado del prototipo

Este es el contraste literal entre el HTML y el alcance del proyecto. Todo lo marcado **NUEVO** no existía en `DESARROLLO1.md`.

### 2.1 Vistas internas (13)

| Vista (`id`) | Nombre en el menú | Estado en v1 | Fase |
|---|---|---|---|
| `inicio` | Centro de operación (dashboard, alertas, cierre de jornada, búsqueda global) | Parcial (dashboard difuso) | 2 |
| `recepcion` | Recepción / Acopio | Cubierto | 1 |
| `calidad` | Calidad y evidencia | Cubierto (sin evidencia ni corrección de densidad) | 1 |
| `rutas` | Rutas y movilidad (+ seguimiento de avance) | Parcial | 3 |
| `proveedores` | Proveedores (+ perfil, analítica, rotación de zona) | Parcial | 1 / 2 |
| `pagos` | Pagos y liquidación (+ boletas, pago masivo) | Cubierto | 2 |
| `quesos` | Producción · Quesos | Cubierto (modelo desactualizado) | 2 |
| `zonas` | Zonas y rendimiento (+ zone trading) | Cubierto | 2 |
| `trazabilidad` | Seguimiento de producción | **NUEVO — no estaba** | 3 |
| `reportes` | Reportes | Cubierto | 3 |
| `config` | Configuración | **NUEVO — la UI no estaba asignada** | 1 / 2 |
| `excel` | Datos y respaldo (Excel + backup JSON) | Cubierto | 3 |
| `workerReceipt` | Boleta imprimible del proveedor | **NUEVO — no estaba** | 2 |

### 2.2 Portal público — **NUEVO, no estaba en `DESARROLLO1.md`**

Secciones `#portada`, `#controles`, `#consulta` y `publicZoneGrid`. Es un sitio **sin autenticación** que expone:
- KPIs agregados de acopio (litros de leche, litros de agua detectada, pureza ponderada).
- Grilla de zonas públicas (`publicZones`: Quillallaka, I Collana, Faon y Yasin).
- Tabla paginada de controles de calidad (`publicQualityRecords`) con resultado Apto / Observado / Retirado.
- **Consulta de proveedor por código** (`lookupPublicProvider`) — un ciudadano introduce un código y ve los resultados de calidad de ese proveedor.

Esto tiene implicancias serias que el documento anterior no contemplaba: es superficie de ataque pública, expone datos que identifican a personas por código, y necesita rate limiting, caché y una decisión explícita de qué se publica. Se trata en la sección 9 (Fase 2) y en el riesgo 4.

### 2.3 Funcionalidad transversal encontrada en el HTML

| Capacidad | Funciones del prototipo | Estado en v1 |
|---|---|---|
| Auditoría de toda escritura | `audit()`, `renderAuditV130`, `auditExcel` (buffer de 150 registros) | **Sin fase asignada** |
| Cierre y reapertura de jornada | `closeOperationalDayV135`, `reopenOperationalDayV135`, `dayClosureV135`, `db.dailyClosuresV135` | **NUEVO** |
| Alertas operativas | `buildOperationalAlertsV135` (fuera de horario, rechazos, evidencia faltante) | **NUEVO** |
| Búsqueda global | `globalSearchItemsV135`, `renderGlobalSearchV135` | **NUEVO** |
| Notificaciones clicables | `renderNotisV2`, `openNotificationV140`, `notiRouteInfoV140` | **NUEVO** |
| Paginación reutilizable | `smartPager*` (9 funciones), `applyAllSmartPagersV130` | **NUEVO** |
| Impresión | `printWorkerPayment`, `printAdminPayment`, `printLandscapeReport`, `startBulkPrintPendingV139`, clases `.printable` | **NUEVO** |
| Evidencia fotográfica | `evidenceForReceptionV133`, `handleIncidentVoucher`, `openProviderEvidenceV133`, `milkAnalyzerSamplesV123` | **NUEVO** |
| Rotación de zona de proveedor | `providerEffectiveZoneV133`, `providerZoneRotationRecordV133`, `updateProviderConfigRotationV134` | **NUEVO** |
| Seguimiento GPS de ruta | `renderRouteTrackingMapV136`, `simulateRouteNextPointV136`, `db.routeTrackProgressV136` | **NUEVO** |
| Recojo móvil pendiente | `db.mobilePickupsV140`, `usePendingPickupV140`, `renderPendingMobilePickupsV140` | Mencionado sin detalle |
| Analítica de proveedor | `providerInsight*` (12 funciones), `renderProviderTrendChart`, `providerRegularityScore` | **NUEVO** |
| Respaldo/restauración JSON | `exportBackup`, `importBackup`, `SCHEMA_VERSION` | **NUEVO** |
| Gestión de código de configuración | `verifyConfigCode` | **NUEVO** |

---

## 3. Prácticas de testing y DevOps tomadas como referencia

- **Pirámide de pruebas completa**: unitarias (JUnit 5), dobles de prueba (Mockito), integración (Testcontainers + PostgreSQL), frontend (Angular TestBed/HttpTestingController), E2E (Cypress), API (Postman/Newman), con cobertura mínima **80 %** en lógica de negocio (JaCoCo).
- **CI/CD**: Git Flow con revisión de código, análisis estático (SonarCloud) como quality gate, GitHub Actions para compilar/probar/analizar/desplegar.
- **Contenedores y despliegue**: Docker multi-stage, Docker Compose, HTTPS, estrategias rolling/blue-green/canary.
- **Calidad no funcional**: rendimiento (JMeter/k6), seguridad (OWASP Top 10 + escaneo de dependencias).
- **Observabilidad**: Spring Boot Actuator, Prometheus, Grafana, logs estructurados, alertas, rollback y respaldos.

Estas prácticas estructuran las Fases 0-3 de la sección 9 como estándar de ingeniería del proyecto, no como requisito de una evaluación externa.

---

## 4. Stack tecnológico

> **Actualizado al cerrar la Fase 0.** La tabla anterior describía un stack que no es el que se construyó (Java 21 / Boot 3.5 / Angular 20 / paquetes en inglés). Lo que sigue es el stack **verificado contra el código**, no el planificado. Las decisiones que cambiaron se justifican debajo de la tabla.

| Capa | Elección | Motivo |
|---|---|---|
| Backend | **Java 17 (LTS)** + **Spring Boot 4.1.x** | Boot 4 exige Java 17 como mínimo y trae soporte de seguridad vigente, así que resuelve por sí solo el riesgo de EOL que motivaba la versión anterior de esta tabla. Ver 4.1. |
| Build | **Maven** | Más documentación específica de Spring Boot + JaCoCo + Failsafe/Surefire. Trade-off: Gradle es más rápido en builds incrementales; con varios desarrolladores, la configuración declarativa de Maven reduce fricción de onboarding. |
| Base de datos | **PostgreSQL 17** | Es el motor de las pruebas de integración con Testcontainers. PostgreSQL 18 es la última mayor, pero 17 tiene más rodaje con drivers y Testcontainers. |
| Frontend | **Angular 21** — versión **fijada**, standalone components | Todos parten de la misma versión de CLI y dependencias. Se fijó en la 21 porque es con la que se generó el proyecto; bajar a la 20 no aporta nada y obliga a rehacer la configuración de build. |
| Arquitectura | **Monolito modular**, un repo | Un solo desplegable para un equipo reducido. Microservicios agregarían complejidad operativa sin beneficio a esta escala. |
| Repositorio | **Monorepo** (`huata/`, `huata-web/`) | Un pipeline, un versionado, los cambios de contrato API se revisan en el mismo PR. Los directorios se llaman así y no `/backend` y `/frontend`: renombrarlos solo movería historial de Git sin ganar nada. |
| Librerías de apoyo | Apache POI (Excel servidor), springdoc-openapi 3.x (Swagger), mapeo manual con `record` (DTO) | POI se justifica en la decisión de Excel (sección 12). springdoc **3.x** es la línea que soporta Boot 4; la 2.x es solo para Boot 3. |

### 4.1 Consecuencias de usar Spring Boot 4.1 en vez de 3.5

El riesgo que documentaba la versión anterior de esta sección (Boot 3.5 fuera de soporte de seguridad open-source desde el 30-06-2026) **ya no aplica**: la línea 4.1 está soportada. Lo que sí cambia son tres cosas concretas que rompen los tutoriales escritos para Boot 3:

1. **Jackson 3.** Boot 4 usa Jackson 3, cuyo paquete es `tools.jackson.databind`, no `com.fasterxml.jackson.databind`. No hay un bean del `ObjectMapper` de Jackson 2 en el contexto: inyectar el de `com.fasterxml` hace fallar el arranque.
2. **Testcontainers 2.x.** El BOM de Boot 4.1 trae Testcontainers 2.0.x, que renombró los módulos: los artefactos son `testcontainers-junit-jupiter` y `testcontainers-postgresql`, no `junit-jupiter` y `postgresql`.
3. **`@ServiceConnection`** sigue siendo la forma correcta de conectar Testcontainers en la Fase 1 (no `@DynamicPropertySource`).

Se mantiene el escaneo de dependencias automatizado (OWASP Dependency-Check, perfil `seguridad` del `pom.xml`) como gate de Pull Request: deja de ser una mitigación de EOL y pasa a ser higiene normal.

### 4.2 Nomenclatura del dominio — **resuelta: español**

Identificadores de código, paquetes y entidades en **español** (`Productor`, `Recojo`, `IncidenciaCalidad`), igual que la UI y los mensajes. Es lo que ya está construido y lo que entiende el equipo sin traducir mentalmente el dominio.

Coste asumido: alguna mezcla inevitable con el inglés de los frameworks (`findByCodigoIgnoreCase`, `PageResponse`). Es menor que renombrar 122 archivos. El glosario de la sección 6.1 sigue sirviendo para leer el prototipo, que está en inglés.

**Nombres que aún no coinciden con este documento.** El código llama `Recojo` a lo que las secciones 7.x llaman `Reception`/recepción, y `Productor` a lo que llaman `Provider`/proveedor. Son la misma entidad. No se renombra, pero al implementar la sección 7 hay que tenerlo presente: `ReceptionDecisionService` se llamará `DecisionRecepcionService` y operará sobre `Recojo` + `ControlCalidad`.

---

## 5. Arquitectura

### 5.1 Backend — monolito modular, empaquetado por *feature*

```
backend/src/main/java/com/huatalacteos/core/
├── provider/       Provider, ProviderZoneRotation, ProviderService, ProviderController, dto/
├── reception/      Reception, ReceptionDecisionService, DensityCorrectionService, ReceptionRepository, ReceptionController
├── quality/        QualityIncident, QualityService, Evidence, DensityCorrectionTable
├── payment/        PaymentCycle, PaymentAdjustment, PaymentMark, PaymentService, SettlementService
├── cheese/         CheeseProduction, CheeseSale, CheeseService
├── zone/           Zone, ZoneService, ZonePerformanceService
├── route/          Route, RouteStatus, RouteZoneVisit, MobilePickup, RouteService
├── operations/     DailyClosure, OperationalAlertService          ← NUEVO
├── traceability/   TraceabilityService (solo lectura)             ← NUEVO
├── report/         ReportService (solo lectura, agrega de otros módulos)
├── publicapi/      PublicQualityController, PublicLookupService   ← NUEVO
├── auth/           User, Role, JwtService, SecurityConfig, UserService
├── audit/          AuditLog, AuditService (transversal, invocado por los demás)
├── config/         BusinessSettings, BusinessSettingsService
└── shared/         excepciones comunes, utilidades, respuesta de error estándar
```

**Por qué por feature y no por capa técnica global**: con 14 módulos, empaquetar por capa esparce las reglas de cada módulo y borra los límites del dominio. Por feature cada módulo queda cohesionado y con dueño claro (`CODEOWNERS`). Costo: un poco más de boilerplate (cada módulo repite su `dto/`).

**Reglas de capas dentro de cada módulo:**
- `Controller`: solo HTTP — recibe DTO, valida forma (`@Valid`), llama al service, mapea respuesta. Cero lógica de negocio.
- `Service`: toda la lógica de negocio y transacciones (`@Transactional`). Es la capa que se testea con JUnit + Mockito.
- `Repository`: acceso a datos vía Spring Data JPA. Nada de calcular precio en una query.
- Autorización con `@PreAuthorize` en `Controller` o `Service`. Ocultar un botón en Angular **no** es una barrera de seguridad.

### 5.2 Frontend — Angular 20

- Standalone components, un feature folder por módulo, lazy-loaded por ruta.
- Un `ApiService` por módulo que envuelve `HttpClient` — es lo que se testea con `HttpTestingController`.
- Guards de ruta por rol **solo para UX**; el backend vuelve a validar todo.
- **Dos aplicaciones de ruteo**: el shell interno autenticado y el portal público (`/publico/**`), sin guard y con su propio layout.
- Componentes compartidos que el prototipo ya tiene resueltos y conviene extraer una sola vez: paginador con búsqueda (equivalente a `smartPager*`), badge de calidad/estado, tabla imprimible, visor de evidencia.

### 5.3 Contrato API

- REST, JSON, prefijo `/api/v1/...`. El portal público usa `/api/v1/public/...` y es el **único** grupo de endpoints con `permitAll()`.
- Errores en formato consistente (`{timestamp, status, error, message, path}`) vía `@ControllerAdvice` global.
- Swagger/OpenAPI (springdoc) desde el día uno — de ahí se deriva la colección de Postman/Newman.

---

## 6. Modelo de dominio

| Entidad | Descripción | Campos clave | Origen en el prototipo |
|---|---|---|---|
| `Provider` | Persona/proveedor de leche | code, lastName, firstName, zone, type, status, source, contact, approvalBy, notes, **excellence** | `db.providers` |
| `ProviderZoneRotation` | **NUEVO** · Zona efectiva de un proveedor en una fecha (proveedores "Rotativos" cambian de zona por pasto) | providerId, zone, effectiveFrom, updatedBy | `providerEffectiveZoneV133`, `db` rotaciones V133/V134 |
| `Zone` | Catálogo de zonas de acopio | name | `ZONES` — 9 zonas fijas |
| `Reception` | Entrega registrada | codigoRecepcion, providerId, date, time, origin (RUTA/PLANTA), liters, containers, capacity, **density**, **temperature**, **correctedDensity**, fieldRef, **freezingPoint**, water, lugol, quality, accepted, priceApplied, notes, source | `db.receptions` + `receptionDecision()` |
| `QualityIncident` | Incidencia de calidad | receptionId, providerId, date, type, origin, density, fieldRef, freezingPoint, water, lugol, decision, priceApplied, **voucherImage** | `db.qualityHistory` |
| `Evidence` | **NUEVO** · Fotografía del voucher del analizador o del control manual | receptionId / incidentId, kind, image, capturedAt | `evidenceForReceptionV133`, `milkAnalyzerSamplesV123` |
| `DensityCorrectionEntry` | **NUEVO** · Fila de la tabla de corrección a 15 °C | reading, temperature, correctedDensity, category | Tabla impresa (sección 7.5) |
| `PaymentCycle` | Ciclo semanal jueves→miércoles, pago viernes (fin + 2 días) | start, end, payDate | `cycleFor()` |
| `PaymentAdjustment` | Ajuste de liquidación | providerId, cycleStart, type, amount, reason | `db.paymentAdjustments` |
| `PaymentMark` | **NUEVO** · Marca de pago efectuado | key (cycleStart\|providerId), paid, paidAt, method | `db.paymentMarks` |
| `CheeseProduction` | **Modelo corregido** · Producción por lote | date, quantity, batch, notes | `db.quesosV2.prod` |
| `CheeseSale` | **Modelo corregido** · Venta por tipo de comprador | date, buyerType (proveedor/mayorista/publico), unitPrice, quantity, total, invoiced, notes | `db.quesosV2.ventas` |
| `Route` | Ruta de recojo | id, name, zones, operator, vehicle | `db.routesV135` |
| `RouteStatus` / `RouteZoneVisit` / `RouteTrackProgress` | **NUEVO** · Estado diario, visita por zona y avance de la ruta | routeId, date, status, coverage, points | `db.routeStatusV135`, `db.routeZoneVisitsV140`, `db.routeTrackProgressV136` |
| `MobilePickup` | Recojo enviado desde la app móvil, pendiente de conciliar | providerId, liters, quickQuality, collectorName, date, time, synced, usedInReceptionId | `db.mobilePickupsV140` |
| `DailyClosure` | **NUEVO** · Cierre de jornada operativa | date, closedBy, closedAt, reopenedBy, notes | `db.dailyClosuresV135` |
| `AuditLog` | Toda escritura queda registrada | ts, userId, role, action, entity, detail | `db.audit` — RNF-04 |
| `BusinessSettings` | Reglas parametrizables | normalPrice, lowPrice, **excellencePrice**, densityMin, densityMax, fieldRefMin, fieldRefMax, **freezingPointRef**, receptionClose, **lastTruck**, **dailyMin**, **dailyMax**, adminEmail | `db.settings` |
| `User` / `Role` | Autenticación real | username, passwordHash, role, enabled | `prototypeUsers` — **solo referencia de roles, jamás de seguridad** |

**Aviso sobre `prototypeUsers`**: el prototipo trae cinco cuentas con la contraseña `huata2026` en texto plano dentro del propio JavaScript. No se porta nada de eso; se reemplaza por BCrypt + JWT (sección 8.2).

### 6.1 Glosario ES → identificador de código
`Proveedor→Provider` · `Recepción/Acopio→Reception` · `Calidad→QualityIncident` · `Evidencia→Evidence` · `Zona→Zone` · `Rotación de zona→ProviderZoneRotation` · `Ruta→Route` · `Recojo móvil→MobilePickup` · `Ciclo de pago→PaymentCycle` · `Ajuste→PaymentAdjustment` · `Liquidación→Settlement` · `Producción de queso→CheeseProduction` · `Venta de queso→CheeseSale` · `Cierre de jornada→DailyClosure` · `Trazabilidad→Traceability` · `Auditoría→AuditLog` · `Configuración→BusinessSettings`

---

## 7. Reglas de negocio críticas (requieren prueba unitaria exhaustiva)

Son la lógica con más ramas de decisión y no dependen de infraestructura: el mejor candidato para **pruebas parametrizadas de JUnit 5**.

> **Advertencia de lectura del prototipo.** `receptionDecision()` está definida **cuatro veces** en el HTML (líneas 9578, 10161, 13785 y 14334). En JavaScript la última definición sobrescribe a las anteriores, así que **la única versión que realmente corre es la de la línea 14334**. `DESARROLLO1.md` documentaba la primera. Todo lo que sigue está tomado de la versión vigente.

### 7.1 Decisión de calidad/precio en la recepción (`ReceptionDecisionService`)

Entradas: `water` (No evidenciada / Sospecha / Confirmada), `lugol` (Negativa / Positiva), `quality` declarada, `density`, el proveedor y su marca `excellence`, y `BusinessSettings`.

```
adulterada = water == "Confirmada" || lugol == "Positiva" || qualityDeclarada == "Adulterada"
bajaCalidad = water == "Sospecha" || qualityDeclarada == "Baja calidad"
              || (density > 0 && (density < densityMin || density > densityMax))
previas = incidencias de mala calidad de origen "Operación" de este proveedor,
          excluyendo la recepción que se está editando

1. Si adulterada  → quality = Adulterada,   accepted = false, price = 0
2. Si bajaCalidad → quality = Baja calidad, accepted = true,  price = lowPrice
3. Caso contrario → quality = Apta,         accepted = true,
                    price = excellence ? excellencePrice : normalPrice
```

La regla 1 tiene prioridad sobre la 2: hay que probarlo explícitamente con entradas que disparen ambas.

> **Implementada** en `DecisionRecepcionService` (lógica pura, sin repositorios) y aplicada en
> `CalidadService`: el resultado que llega en `ControlCalidadRequest.resultado` ya **no** se guarda
> tal cual. Antes era posible declarar apta una muestra con agua confirmada. Ahora lo que el operador
> declara entra como una señal más —puede empeorar la decisión, nunca mejorarla— y el resultado lo
> fija la regla. La banda de densidad se lee de `parametros_calidad` y las tarifas de la tarifa
> vigente, así que son configurables sin desplegar.
>
> **Supuesto a confirmar:** `ControlCalidad.densidad` se interpreta como densidad **ya corregida a
> 15 °C** (g/mL), que es lo que declara la unidad del parámetro sembrado. La corrección desde la
> lectura del lactodensímetro la hace `CorreccionDensidadService` en la vía del analizador (7.5).
>
> **`previas` no se usa todavía**: solo interviene en la regla 7.2, que sigue pendiente de
> confirmación. Las tres reglas de decisión no dependen de ella.

**Casos de prueba parametrizados mínimos**: agua confirmada · Lugol positivo · densidad bajo el mínimo · densidad sobre el máximo · densidad exactamente en `densityMin` y en `densityMax` (boundary, ambos deben ser **aptos**) · densidad = 0 (no evaluada, no debe disparar baja calidad) · todo normal · todo normal con proveedor Excelencia · adulteración y baja calidad simultáneas · primera incidencia vs. segunda incidencia.

### 7.2 Reincidencia y retiro automático — **regla nueva, no estaba en v1**

`previas` cuenta incidencias de mala calidad **de origen "Operación"** (las de origen Demo/Histórico no cuentan), deduplicadas por `receptionId`.

- `previas == 0` → primera detección. La entrega se rechaza o se acepta a tarifa reducida según 7.1, y **toda la semana del proveedor se reliquida a `lowPrice`** (ver 7.7).
- `previas >= 1` → segunda detección. Además de lo anterior, **el proveedor pasa a RETIRADO al guardar la recepción**.

Esto contradice el comentario del propio prototipo en los datos DEMO ("el sistema alerta, pero no inactiva automáticamente"), que corresponde a la versión antigua. **Confirmar con la administración de Huata cuál es la regla real antes de implementar**: retiro automático o alerta con decisión manual. Es la decisión abierta más importante del documento.

### 7.3 Estado del proveedor (máquina de estados)

```
(alta) --------------------------------> Activo          (desde 2026-10-26, sin aprobación)
Pendiente aprobación --aprobación manual--> Activo     (solo proveedores dados de alta antes)
Activo --recepción Adulterada--> En revisión           (automático)
Activo --segunda incidencia--> Retirado / Inactivo     (automático, ver 7.2)
Activo <--> Inactivo                                    (manual, motivo obligatorio)
Inactivo --reactivar--> Activo                          (manual)
```

Un proveedor que no está `Activo` **no puede** recibir nuevas recepciones — se valida en el `Service`, no solo en el formulario. Toda transición queda auditada con su motivo.

> **Estado de implementación (Fase 1).** La máquina manual está implementada en `EstadoProductor`
> (tabla de transiciones) y aplicada en `ProductorService.cambiarEstado`: un salto no declarado se
> rechaza con 422, el motivo es obligatorio y cada transición queda en `registros_auditoria` con
> actor, estado de origen y motivo. **El alta nace `ACTIVO`** (decisión del cliente, 2026-10-26: quien
> crea al proveedor desde Usuarios o importando el Excel ya lo revisa; no hay segundo paso). Los que ya
> estaban `PENDIENTE_APROBACION` se siguen aprobando o rechazando igual. La guardia "solo un
> ACTIVO entrega" vive en `ProductorService.exigirQuePuedeEntregar` y la llaman **las dos** vías de
> escritura: `RecojoService` y el endpoint de ingesta del equipo de medición.
>
> **Las dos transiciones automáticas NO están implementadas**, a propósito: dependen de la regla 7.2,
> que este mismo documento marca como pendiente de confirmar con la administración de Huata. Por eso
> `RETIRADO` existe pero hoy es inalcanzable. Cuando se confirme, la regla solo tiene que llamar al
> mismo `cambiarEstado`.
>
> **Tres aristas asumidas que este diagrama no dibuja y conviene confirmar**, porque sin ellas el
> modelo se bloquea: `PENDIENTE_APROBACION → INACTIVO` (rechazar un alta equivocada sin tener que
> aprobarla primero), y `EN_REVISION → ACTIVO` / `EN_REVISION → INACTIVO` (el diagrama no dice cómo
> se sale de una revisión, y sin salida el proveedor observado no volvería a entregar nunca).
>
> **Deuda conocida que esto deja a la vista:** `Productor` tiene ahora dos formas de estar apagado,
> el campo heredado `activo` (borrado lógico de `BaseEntity`, que usa `DELETE /productores/{id}`) y
> el estado `INACTIVO`. Se pueden contradecir: hoy es posible quedar `activo=false` con
> `estado=ACTIVO`. Unificarlos es trabajo pendiente; mientras tanto, la fuente de verdad para
> *"¿puede entregar?"* es el estado.

### 7.4 Proveedor Excelencia — **regla nueva, no estaba en v1**

Un proveedor marcado `excellence` cobra `excellencePrice` (por defecto **S/ 2.00/L**, parametrizable) en lugar de `normalPrice` (S/ 1.70/L), **mientras el ciclo permanezca sin incidencias de mala calidad**. Si el ciclo tiene una sola incidencia, la tarifa preferencial se pierde para toda la semana y se aplica `lowPrice` (S/ 1.30/L). La marca es administrable (`toggleProviderExcellenceV129`) y su cambio se audita.

> **Implementada** (V10): columna `productores.excelencia` y `tarifas_leche.precio_excelencia`. La
> marca se concede y se retira por `PATCH /productores/{id}/excelencia`, exige motivo y queda
> auditada. Es una marca, **no un estado**: convive con la máquina de 7.3 y sobrevive a un ciclo
> ACTIVO → INACTIVO → ACTIVO.
>
> `precio_excelencia` admite NULL a propósito: una tarifa anterior a esta regla no tenía precio de
> Excelencia, e inventarle uno reescribiría el pasado que 7.7 recalcula. Con NULL, el proveedor
> marcado cobra la tarifa normal en lugar de romper la liquidación.

### 7.5 Corrección de densidad a 15 °C — **regla nueva, aportada por el equipo**

El prototipo compara la densidad cruda contra `densityMin`/`densityMax` (28–32 en grados del lactodensímetro) **sin corregir por temperatura**, que es incorrecto: la densidad de la leche varía con la temperatura de la muestra. El voucher del analizador que el propio prototipo transcribe (`milkAnalyzerSamplesV123`) ya trae el campo `temperature`, así que el dato existe pero no se usa.

**Regla.** Antes de aplicar 7.1, `DensityCorrectionService` convierte la lectura del lactodensímetro a densidad corregida a 15 °C:

```
densidadCorregida(D, T) = 1 + (D × 10 + 2 × (T − 15)) / 10000
```

donde `D` = lectura del lactodensímetro (25–34) y `T` = temperatura de la muestra en °C (15–34). Equivale a **+0,0002 por cada grado sobre 15 °C**, que es la corrección estándar del lactodensímetro de Quevenne.

**Clasificación por bandas** (colores de la tabla impresa):

| Categoría | Densidad corregida a 15 °C |
|---|---|
| CON AGUA | ≤ 1,0278 |
| MALO | 1,0280 – 1,0298 |
| REGULAR | 1,0300 – 1,0308 |
| BUENO | 1,0310 – 1,0318 |
| MUY BUENO | ≥ 1,0320 |

**Enganche con 7.1:** `CON AGUA` se trata como sospecha de agua (dispara `bajaCalidad`, y con Lugol positivo escala a adulteración); `MALO` dispara `bajaCalidad`; `REGULAR`, `BUENO` y `MUY BUENO` son aptos. La categoría se guarda en la `Reception` junto con la densidad corregida, para que el historial y los reportes puedan explicar por qué se decidió lo que se decidió.

**Tabla de referencia (T = temperatura °C, D = lectura del lactodensímetro):**

| T \ D | 25 | 26 | 27 | 28 | 29 | 30 | 31 | 32 | 33 | 34 |
|---|---|---|---|---|---|---|---|---|---|---|
| **15** | 1.0250 | 1.0260 | 1.0270 | 1.0280 | 1.0290 | 1.0300 | 1.0310 | 1.0320 | 1.0330 | 1.0340 |
| **16** | 1.0252 | 1.0262 | 1.0272 | 1.0282 | 1.0292 | 1.0302 | 1.0312 | 1.0322 | 1.0332 | 1.0342 |
| **17** | 1.0254 | 1.0264 | 1.0274 | 1.0284 | 1.0294 | 1.0304 | 1.0314 | 1.0324 | 1.0334 | 1.0344 |
| **18** | 1.0256 | 1.0266 | 1.0276 | 1.0286 | 1.0296 | 1.0306 | 1.0316 | 1.0326 | 1.0336 | 1.0346 |
| **19** | 1.0258 | 1.0268 | 1.0278 | 1.0288 | 1.0298 | 1.0308 | 1.0318 | 1.0328 | 1.0338 | 1.0348 |
| **20** | 1.0260 | 1.0270 | 1.0280 | 1.0290 | 1.0300 | 1.0310 | 1.0320 | 1.0330 | 1.0340 | 1.0350 |
| **21** | 1.0262 | 1.0272 | 1.0282 | 1.0292 | 1.0302 | 1.0312 | 1.0322 | 1.0332 | 1.0342 | 1.0352 |
| **22** | 1.0264 | 1.0274 | 1.0284 | 1.0294 | 1.0304 | 1.0314 | 1.0324 | 1.0334 | 1.0344 | 1.0354 |
| **23** | 1.0266 | 1.0276 | 1.0286 | 1.0296 | 1.0306 | 1.0316 | 1.0326 | 1.0336 | 1.0346 | 1.0356 |
| **24** | 1.0268 | 1.0278 | 1.0288 | 1.0298 | 1.0308 | 1.0318 | 1.0328 | 1.0338 | 1.0348 | 1.0358 |
| **25** | 1.0270 | 1.0280 | 1.0290 | 1.0300 | 1.0310 | 1.0320 | 1.0330 | 1.0340 | 1.0350 | 1.0360 |
| **26** | 1.0272 | 1.0282 | 1.0292 | 1.0302 | 1.0312 | 1.0322 | 1.0332 | 1.0342 | 1.0352 | 1.0362 |
| **27** | 1.0274 | 1.0284 | 1.0294 | 1.0304 | 1.0314 | 1.0324 | 1.0334 | 1.0344 | 1.0354 | 1.0364 |
| **28** | 1.0276 | 1.0286 | 1.0296 | 1.0306 | 1.0316 | 1.0326 | 1.0336 | 1.0346 | 1.0356 | 1.0366 |
| **29** | 1.0278 | 1.0288 | 1.0298 | 1.0308 | 1.0318 | 1.0328 | 1.0338 | 1.0348 | 1.0358 | 1.0368 |
| **30** | 1.0280 | 1.0290 | 1.0300 | 1.0310 | 1.0320 | 1.0330 | 1.0340 | 1.0350 | 1.0360 | 1.0370 |
| **31** | 1.0282 | 1.0292 | 1.0302 | 1.0312 | 1.0322 | 1.0332 | 1.0342 | 1.0352 | 1.0362 | 1.0372 |
| **32** | 1.0284 | 1.0294 | 1.0304 | 1.0314 | 1.0324 | 1.0334 | 1.0344 | 1.0354 | 1.0364 | 1.0374 |
| **33** | 1.0286 | 1.0296 | 1.0306 | 1.0316 | 1.0326 | 1.0336 | 1.0346 | 1.0356 | 1.0366 | 1.0376 |
| **34** | 1.0288 | 1.0298 | 1.0308 | 1.0318 | 1.0328 | 1.0338 | 1.0348 | 1.0358 | 1.0368 | 1.0378 |

**Notas de implementación:**
1. La tabla impresa **se corta en 1,0344**: las celdas cuyo valor supera ese número aparecen en blanco (por eso la tabla tiene forma de escalera en la fotografía). Las celdas de arriba están calculadas con la fórmula y **deben confirmarse contra el original impreso** antes de sembrarlas. El servicio debe marcar toda corrección > 1,0344 como *fuera de tabla* y exigir revisión manual, no aceptarla en silencio.
2. La tabla **no cubre temperaturas menores a 15 °C**, pero el voucher de analizador transcrito en el prototipo registra 12,2 °C. Hay que decidir si se extrapola con la misma fórmula (−0,0002 por grado bajo 15) o si se rechaza la muestra por fuera de rango. **Decisión pendiente para el equipo de calidad.**
3. La tabla se carga como **datos semilla versionados** (`DensityCorrectionEntry`), no como constantes en código, para que un cambio de norma no exija recompilar. Se acompaña de un test que verifica que la tabla sembrada coincide con la fórmula en todo el rango.
4. `densityMin`/`densityMax` de `BusinessSettings` (hoy 28 y 32) quedan expresados en la misma unidad que la lectura, y su relación con las bandas de arriba debe validarse con planta: 28 coincide con el inicio de MALO y 32 con el inicio de MUY BUENO a 15 °C.

**Casos de prueba parametrizados**: las cuatro esquinas (D25/T15, D25/T34, D34/T15, D34/T34) · cada frontera de banda exacta (1,0278 / 1,0280 / 1,0298 / 1,0300 / 1,0308 / 1,0310 / 1,0318 / 1,0320) · un valor por encima de 1,0344 (fuera de tabla) · temperatura fuera de rango (14 y 35) · lectura fuera de rango (24 y 35).

### 7.6 Ciclo de pago semanal

```
día = díaDeLaSemana(fecha)              // domingo = 0 ... jueves = 4
retroceso = (día − 4 + 7) % 7
inicio = fecha − retroceso              // siempre jueves
fin    = inicio + 6                     // miércoles
pago   = fin + 2                        // viernes
```

Portar la fórmula tal cual, con pruebas para **los siete días de la semana** como fecha de entrada, para no romper el cálculo en los bordes jueves/miércoles.

### 7.7 Liquidación semanal y retarificación retroactiva — **regla nueva, no estaba en v1**

La tarifa **no** es la que se guardó en cada recepción: se recalcula por proveedor y por ciclo.

```
tarifaDelCiclo(proveedor, ciclo):
  1. si el ciclo tiene alguna recepción de mala calidad  → lowPrice
  2. si el proveedor es Excelencia                        → excellencePrice
  3. en otro caso                                         → normalPrice

bruto = Σ(litros × tarifaDelCiclo) sobre las recepciones aceptadas del ciclo
neto  = bruto + Σ(ajustes)
```

donde una recepción cuenta como mala calidad si `quality ∈ {Baja calidad, Adulterada}` **o** `water ∈ {Sospecha, Confirmada}` **o** `lugol == Positiva`.

**Signo de los ajustes** (confirmado contra `adjustmentValue()` del prototipo): `Descuento de queso`, `Penalidad` y `Adelanto` **restan**; `Bonificación` **suma**. Esto resuelve la duda que quedaba abierta en `DESARROLLO1.md` §6.4.

Consecuencia importante para el diseño: una sola incidencia detectada el miércoles **cambia el importe de todas las entregas del jueves anterior en adelante**. La liquidación tiene que ser un cálculo derivado, no un valor congelado en la recepción, y la interfaz debe explicar el motivo del recálculo al proveedor.

**Casos de prueba**: ciclo limpio normal · ciclo limpio Excelencia · ciclo con una incidencia el último día (todo el ciclo baja a `lowPrice`) · ciclo con incidencia para proveedor Excelencia (pierde la preferencial) · ciclo con recepciones rechazadas (no suman al bruto) · cada tipo de ajuste y su signo · ciclo sin recepciones.

### 7.8 Cierre de jornada y alertas operativas — **regla nueva**

Una jornada puede cerrarse y reabrirse (con registro de quién y cuándo). Las alertas que el sistema levanta para una fecha son:
- recepciones registradas **después de `receptionClose`** (10:00 por defecto) → "fuera de horario";
- recepciones rechazadas o adulteradas;
- recepciones observadas (baja calidad);
- recepciones no aptas **sin evidencia adjunta**;
- litros totales del día fuera del rango `dailyMin`–`dailyMax` (14 000–18 000).

Reglas a definir con planta: ¿el cierre bloquea nuevas recepciones de esa fecha? ¿Quién puede reabrir? Hoy el prototipo no lo impide.

### 7.9 Zona efectiva del proveedor — **regla nueva**

Los proveedores de tipo `Rotativo` cambian de zona por traslado o abastecimiento de pasto. La zona que vale para reportes, rutas y rendimiento es la **zona efectiva a la fecha de la entrega**, no la zona actual de la ficha. Se resuelve con `ProviderZoneRotation` (histórico con `effectiveFrom`).

Consecuencia: el resumen por zonas y las métricas de ruta deben unirse por zona efectiva a la fecha. Un reporte de agosto no puede cambiar porque en septiembre el proveedor se mudó.

### 7.10 Conciliación del recojo móvil

El acopiador registra en campo un `MobilePickup` (proveedor, litros, calidad rápida, acopiador, fecha/hora, marca de sincronización). En planta, ese recojo aparece como **pendiente de validar** y el operador lo convierte en `Reception`, que es la que pasa por `ReceptionDecisionService` y define calidad y precio reales. El `MobilePickup` queda enlazado (`usedInReceptionId`) y ya no vuelve a listarse.

Reglas a definir: tolerancia aceptable entre litros de campo y litros de planta, qué pasa si no coinciden, y caducidad de un recojo nunca conciliado.

---

## 8. Roles y autorización

### 8.1 Matriz de roles y permisos — **implementada, no solo descrita**

> **Actualizado al implementar la autorización.** La versión anterior de esta tabla listaba las
> autoridades en inglés (`ROLE_ADMIN`, `ROLE_CASHIER`…), en contradicción con la sección 4.2, que ya
> había resuelto la nomenclatura del dominio en español, y con el código, que desde el principio usa
> `ADMINISTRADOR`. Se unifica en español. La fuente de verdad es ahora
> `V8__permisos_por_recurso.sql` más `MatrizPermisos.java`, no esta tabla.

**Un solo mecanismo.** El prototipo tenía dos vías paralelas y descoordinadas —acceso por vista
(`roleViewsV135`) y acceso por permiso (`permissions`)—. En el sistema real hay una sola: **permisos
por recurso y operación**, con código `<recurso>.<acción>` (27 permisos sobre 15 recursos). Cada
endpoint se anota con `@PreAuthorize("hasAuthority('" + Permiso.X + "')")` y el menú lateral de
Angular se deriva de la misma lista, que el backend devuelve en `/auth/login` y `/auth/me`. No hay
dos listas que puedan desincronizarse. Esto resuelve también la segunda observación de la versión
anterior: `Proveedor` y `Gerencia` ya no tienen permisos vacíos con vistas asignadas.

**Dos tipos de permiso.** La mayoría son `read`/`write` sobre un recurso. Dos son **permisos de
acción**: `acopio.cerrar-jornada` y `pagos.cerrar-ciclo`. Conceden dar por terminado un periodo sin
conceder la escritura del módulo, que es lo que permite que el administrador cierre sin poder
registrar ni pagar. El código de permiso es `<recurso>.<acción>`, y `acción` no está restringida a
`read`/`write`.

| Rol | Responsabilidad | Escribe en | Lee |
|---|---|---|---|
| `ADMINISTRADOR` | Usuarios, roles, parámetros, catálogos, auditoría, respaldos | proveedores, acopiadores, rutas, usuarios, parámetros, respaldo · **+ cerrar jornada y cerrar ciclo** | los 15 recursos |
| `SUPERVISOR` | Aprobaciones, incidencias, calidad, pagos | acopio, calidad, proveedores, pagos, quesos · + cerrar jornada y cerrar ciclo | todo menos usuarios y respaldo |
| `OPERADOR` | Recepción de acopio en planta | acopio · + cerrar jornada | panel, acopio, calidad, rutas, proveedores, trazabilidad |
| `CALIDAD` | Densidad, adulteración, evidencia, historial | calidad | panel, calidad, acopio, proveedores, trazabilidad, reportes |
| `SECRETARIA` | Liquidaciones, pagos, comprobantes, producción y venta de quesos | pagos, quesos · + cerrar ciclo | panel, pagos, quesos, acopio, proveedores, reportes |
| `ACOPIADOR` | Recojo en ruta desde la app | acopio | panel, acopio, rutas, proveedores |
| `GERENCIA` | Indicadores y reportes | — | panel, acopio, calidad, proveedores, pagos, quesos, zonas, trazabilidad, reportes |
| `PROVEEDOR` | Consulta de sus propios datos | — | — (solo el portal público) |

**El administrador no es un superusuario operativo.** Lee los 15 recursos, escribe en datos maestros
y configuración y ejecuta las dos acciones de cierre, pero **no registra recepciones, no ejecuta
pagos y no registra producción ni venta de quesos**. Es lo que dicen los subtítulos del propio prototipo (`vista-administrador.html`:
*"el registro de recepción ya no se realiza en este perfil"*, *"la ejecución de pagos corresponde a
Secretaría y Finanzas"*) y lo que ya declaraba `V2__roles_base.sql`. La razón de fondo es la
auditoría: si el administrador pudiera registrar una recepción, `registros_auditoria` dejaría de
identificar a quien operó de verdad.

**Cambio de alcance respecto del prototipo:** el cierre de jornada exigía antes `acopio.write`, que
tiene el `ACOPIADOR`; es decir, alguien en ruta podía dar por cerrada la recepción de planta. Al
convertirse en permiso de acción propio, el cierre queda en `OPERADOR`, `SUPERVISOR` y
`ADMINISTRADOR`; el acopiador sigue registrando recojos y ya no cierra la jornada (regla 7.8). El
cierre de ciclo queda en `SECRETARIA`, `SUPERVISOR` y `ADMINISTRADOR`.

**Ventas por operación (V54, pedido del cliente 2026-10-20).** `quesos.write` ya no vende: registrar
una venta exige `ventas.registrar`, marcarla cobrada `ventas.cobrar` y anularla `ventas.anular`. Así,
desde Usuarios y roles, se puede dar a un rol o a una persona solo vender, solo cobrar o solo anular.
`quesos.write` queda para registrar lotes y aparece en la vista Producción. La migración copia los tres
permisos a todo rol o usuario que tenía `quesos.write`, así que nadie gana ni pierde acceso.

**Usuarios reúne a todas las personas (V55, pedido del cliente 2026-10-24).** "Usuarios" es la única
pantalla donde se crean personas: pestañas Internos, Acopiadores, Proveedores y Roles, cada una visible
con su propio permiso (`usuarios.read`, `acopiadores.read`, `proveedores.read`), así que nadie gana ni
pierde acceso. Las rutas web `/productores` y `/acopiadores` redirigen a `/usuarios?tipo=…`. V55 solo
mueve en el catálogo las vistas `productores` y `acopiadores` a la ruta `usuarios` (las pantallas se
cuentan por ruta distinta). Se quitó el alta de proveedores desde el mapa (`POST
/puntos-establecidos/con-proveedor`): en Rutas solo se ubica o se mueve a quien ya existe. Los listados
aceptan filtros nuevos: acopiadores por `zona` y `rutaId`; proveedores por `zona` y `acopiadorId`.

**Roles y vistas más simple (V58, pedido del cliente 2026-10-25).** Cada vista se asigna con tres
botones, **No · Ver · Todo** (Todo = la vista y todas sus acciones); el detalle acción por acción
(p. ej. vender sí, anular no) queda en "Ajustar qué puede hacer" y se abre solo cuando la vista está a
medida. No cambia ningún permiso: es otra forma de encender los mismos. V58 renombra las vistas
`recojo` y `conciliacion` a "Recojos en ruta" y "Recepción de rutas", como en el menú.

**Roles y vistas por separado en cada usuario (pedido del cliente 2026-10-27).** La ficha de un
usuario interno pasa de dos pasos a tres: **Datos · Roles · Vistas**. En Roles se eligen uno o más
roles (con su descripción y cuántas pantallas trae cada uno); en Vistas se ajusta, solo para esa
persona, lo que dan sus roles. A Vistas solo se llega con al menos un rol. En la lista de Internos
cada fila tiene accesos directos a sus Roles y a sus Vistas. Solo cambia la web: el backend y los
permisos son los mismos (roles + extras − quitados).

**Insumos y editor de recetas rehechos (pedido del cliente 2026-10-27).** Problema reportado: al
escribir "sal" en una receta salía *Ya existe una materia prima llamada "sal"* y "sal" no aparecía en
ninguna pantalla; la salida fue crear "zal". Causa: dar de baja es lógico (la fila queda por el
kardex) y el alta comparaba el nombre contra todas las filas, también las dadas de baja. Reglas nuevas:
- El nombre de un insumo se compara **sin mayúsculas, tildes ni espacios de más** ("Azúcar" = "azucar ").
- Crear un insumo con el nombre de uno **dado de baja lo reactiva** (misma fila, mismo historial). Su
  unidad solo cambia si nunca tuvo movimientos; con kardex se conserva.
- Renombrar a un nombre ocupado y cambiar la unidad de un insumo con movimientos se rechazan con un
  aviso claro (antes el primero era un error 500).
- La receta se guarda en **una sola transacción**: cada ingrediente va por id (existente) o por
  nombre + unidad (nuevo o reactivado). Antes la web creaba los insumos uno por uno y, si la receta
  fallaba, quedaban sueltos. El mismo insumo dos veces en una receta se rechaza.
- Una receta sigue sin poder apuntar por id a un insumo dado de baja (regla vigente); el editor lo
  muestra como "Dado de baja" con el botón **Volver a usarlo**.
- Editor: cada fila dice qué es el ingrediente (*En inventario* con su stock, *Nuevo*, *Se
  reactivará*, *Dado de baja*) y, si se parece a uno existente, pregunta *¿Quisiste decir «sal»?*. La
  unidad de un insumo existente la fija el inventario (antes "5 gramos" de un insumo en kg se guardaba
  como 5 kg). Los errores se muestran dentro de la ventana. `GET /inventario/materias-primas` acepta
  `incluirInactivos=true` para esto; sin el parámetro responde como antes.

**Nombres casi iguales: se bloquean siempre (decisión del cliente 2026-10-27).** Tras ver "yogurd"
junto a "yogurt" y "zal" junto a "sal", el cliente pidió que el sistema no lo permita. Regla única en
`common/NombresParecidos.java` (copia para avisar al escribir: `core/util/nombres-parecidos.ts`;
la del servidor es la que manda), aplicada a **productos e insumos**, al crear y al renombrar, contra
activos, pausados y dados de baja:
- Iguales: sin mayúsculas, tildes ni espacios de más.
- Casi iguales: a 1 letra de distancia (2 si el nombre tiene 8 o más). No se comparan nombres de
  menos de 3 letras, y si los números difieren son distintos ("Envase 1 L" / "Envase 5 L").
- No hay botón para saltarse el bloqueo: si de verdad es otro, se le pone un nombre que se distinga.
  La web ofrece "Usar «yogurt»" (abre su receta) y deshabilita Guardar.
- Limpieza autorizada por el cliente: se eliminaron los productos "yogurd" y "zal" (sin
  producciones) y el insumo "zal" (sin movimientos, compras ni recetas).

**DNI y teléfono opcionales (pedido del cliente, 2026-10-24).** En acopiadores y proveedores el DNI
y el teléfono pueden quedar vacíos; si se escriben, deben ser válidos (DNI de 8 dígitos, sin repetir).
El código del proveedor sigue siendo su DNI cuando lo tiene; sin DNI se genera `PRV-00001`… (el código
es obligatorio y único en BD). La aprobación ya no exige DNI. La ficha del proveedor no pide acopiador
responsable ni orden de visita: el acopiador sale de la ruta y el orden se define en Rutas y mapa (la
ficha ya no los borra al editar; al cambiar de ruta se descartan).

**Dónde vive cada cosa:** el catálogo de permisos es esquema versionado (V8) porque añadir uno
implica anotar un endpoint; la asignación rol → permiso es dato editable, para poder ajustar
responsabilidades sin desplegar. La matriz está escrita dos veces —en V8 y en `MatrizPermisos.java`,
que es la que usa el perfil `test` sobre H2, donde Flyway no corre— y `MigracionesFlywayIT` compara
ambas fila por fila para que no puedan divergir en silencio.

### 8.2 Autenticación

**JWT stateless** con Spring Security (`Authorization: Bearer`), porque el frontend es una SPA separada y el módulo de recojo móvil apunta a un cliente móvil independiente. Alternativa descartada: sesión + cookie (más simple para CSRF, menos flexible con una app móvil).

Contraseñas con BCrypt. El endpoint de login limita intentos. **Nada de `prototypeUsers`**: las cinco cuentas con contraseña `huata2026` en el JS son una demo, no un mecanismo de seguridad.

Cada endpoint valida el rol en el backend (`@PreAuthorize`). El guard de Angular es solo UX.

### 8.3 Portal público

`/api/v1/public/**` es el único grupo con `permitAll()`. Requisitos propios:
- Solo expone datos agregados y el resultado de calidad por código de proveedor; nunca nombres, contactos, importes ni liquidaciones.
- Rate limiting por IP en la consulta por código (evita enumerar todos los códigos de proveedor).
- Caché de los agregados (no golpear la base en cada visita).
- Decisión explícita y documentada, firmada por la administración de Huata, sobre **qué se publica**: hoy el prototipo publica litros de agua detectada y porcentaje de pureza por proveedor, lo cual es información sensible para la persona identificada por ese código.

---

## 9. Fases de desarrollo

`CLAUDE.md` dice "no adelantes trabajo de fases futuras salvo que se pida explícitamente". **Son cuatro fases: 0, 1, 2 y 3.** Ejemplo de la regla: nada de Prometheus/Grafana en la Fase 1, aunque sea tentador dejarlo preparado.

Cada fase tiene ahora un **criterio de salida verificable**. Una fase no se da por cerrada sin él.

### Fase 0 — Fundación

- Monorepo con `/backend`, `/frontend`, `docker-compose.yml` (Postgres local de desarrollo, separado del Postgres efímero de Testcontainers).
- Spring Initializr: Java 21, Maven, Spring Boot 3.5.x; dependencias Web, Data JPA, Validation, Security, Actuator, driver PostgreSQL, Testcontainers (scope test), springdoc-openapi.
- `ng new` con **Angular 20 fijado** (no "la LTS vigente"), standalone, routing, HttpClient.
- `application.yml` por perfil (`dev`, `test`, `prod`) — placeholders, sin secretos.
- README con cómo levantar todo localmente.
- **Git Flow y protección de rama desde el día 1**: ramas `main`/`develop`/`feature/*`, al menos una revisión obligatoria antes de mergear a `develop`, y un `CODEOWNERS` con un dueño por módulo de la sección 5.1.
- **Escaneo de dependencias activo desde ya** (OWASP Dependency-Check o Dependabot) como gate de PR — es la mitigación del riesgo de EOL de Boot 3.5.
- **Decidir la nomenclatura del dominio** (sección 4.2) antes de crear la primera clase.

**Criterio de salida:** cualquier integrante clona el repo y levanta backend + frontend + Postgres con un solo comando; un PR de prueba no puede mergearse sin revisión; el escaneo de dependencias corre y reporta.

### Fase 1 — Núcleo de dominio + estrategia integral de pruebas

Alcance **acotado a propósito**. No es el sistema completo.

| Incluido | Diferido |
|---|---|
| `Provider` (alta, aprobación, inactivación, reactivación) | Pagos y liquidación (Fase 2) |
| `Zone` + datos semilla de las 9 zonas | Quesos (Fase 2) |
| `BusinessSettings` (entidad + **pantalla de configuración**) | Zonas/rendimiento como reporte (Fase 2) |
| `Reception` + `ReceptionDecisionService` | Rutas, seguimiento GPS y recojo móvil (Fase 3) |
| `DensityCorrectionService` + tabla semilla (7.5) | Import/export Excel y respaldo (Fase 3) |
| `QualityIncident` (historial, reincidencia) | Reportes agregados y trazabilidad (Fase 3) |
| `AuditLog` **transversal desde el primer commit de escritura** | Portal público (Fase 2) |
| `User`/`Role`: login, hash BCrypt, JWT, **CRUD de usuarios y asignación de roles** | Evidencia fotográfica (Fase 2) |

**Por qué este recorte:** `Provider` y `Reception` concentran casi toda la lógica de negocio real; el resto de módulos son en gran parte CRUD sobre un patrón ya validado. Conviene construirlos después de que el núcleo (autenticación, capas, convenciones de test) esté probado y estable.

**Por qué `AuditLog` y el CRUD de usuarios entran aquí y no después** (corrección respecto de v1, donde no tenían fase): si las escrituras de `Provider` y `Reception` nacen sin auditoría, hay que retrofitear cada service más adelante; y sin gestión de usuarios no hay forma de que otro integrante pruebe un rol distinto al suyo.

**Tareas, en orden de prioridad.** Las tareas 1 y 2 son independientes entre sí (comparten `BusinessSettings` solo de lectura) y se asignan a personas distintas; de la 3 en adelante hay dependencia.

1. `Zone`, `BusinessSettings`, `AuditService`, `Provider` con repositorio y service (reglas de estado de 7.3), tests unitarios JUnit 5 con patrón AAA.
2. `DensityCorrectionService` (7.5) y `ReceptionDecisionService` (7.1, 7.2, 7.4) con **tests parametrizados exhaustivos**. Es la pieza de más valor: lógica pura, cubrible al 100 %.
3. `Reception` entidad + persistencia; Mockito para mockear repositorios en los tests de service.
4. `auth`: `User`, `Role`, BCrypt, `JwtService`, `SecurityConfig`, login, CRUD de usuarios y `@PreAuthorize` en los controllers ya existentes.
5. Tests de integración con **Testcontainers + PostgreSQL usando `@ServiceConnection`** para `ProviderRepository` y `ReceptionRepository`; al menos un `@WebMvcTest`/`MockMvc` por controller, incluyendo un caso de 403 por rol insuficiente.
6. Angular: formulario de alta de proveedor, formulario de recepción con **previsualización en vivo de densidad corregida, categoría, calidad y precio**, y pantalla de configuración. Tests con `TestBed` + `HttpTestingController` sobre componentes y `ApiService`.
7. Cypress E2E: alta de proveedor → aprobación → recepción apta → recepción con densidad corregida fuera de banda → verificar decisión y el registro en el historial de calidad.
8. Colección Postman ejecutable con Newman: casos felices, un rechazo por regla de negocio y un 401/403.
9. JaCoCo ≥ 80 % en la capa de servicio/dominio; documentar evidencia (cobertura, resultados de tests).

**Criterio de salida:** cobertura ≥ 80 % en `service`/`domain` verificada con JaCoCo; toda la sección 7.1–7.5 cubierta con tests parametrizados; los flujos E2E pasan en verde; ninguna escritura de `Provider` o `Reception` ocurre sin su registro en `AuditLog` (probado); un usuario con rol insuficiente recibe 403 del backend aunque el botón esté oculto (probado).

### Fase 2 — CI/CD, despliegue y cobertura funcional económica

Dos frentes en paralelo. El de infraestructura es el que da la nota de despliegue; el funcional evita que la Fase 3 se vuelva inabordable.

**Infraestructura y entrega:**
- Git Flow completo (`main`/`develop`/`feature`/`release`), pull requests con revisión.
- Análisis estático: SonarCloud con quality gate en el pipeline.
- GitHub Actions: workflow de CI (build + test + JaCoCo + Sonar + Newman + escaneo de dependencias) en cada PR; workflow de CD (build de imágenes, push a registro, deploy) al mergear a `main`.
- Dockerfile multi-stage para backend (Spring Boot) y frontend (build Angular servido con Nginx).
- `docker-compose` de orquestación: backend + frontend + Postgres + reverse proxy.
- **Despliegue en VPS Linux** con Docker Compose y reverse proxy (Traefik o Caddy) para HTTPS automático vía Let's Encrypt, en lugar de un PaaS administrado. Motivo: queremos implementar nosotros las estrategias de despliegue; un PaaS las abstrae. Trade-off: más configuración a cambio de control real.
- **Blue-green** como estrategia principal (dos entornos + switch de proxy), documentando cómo rolling y canary extenderían el mismo mecanismo.
- Secretos vía GitHub Actions secrets y variables de entorno; nada hardcodeado.
- Perfiles de Spring (`dev`/`prod`), versionado semántico de imágenes y releases.
- Pruebas de rendimiento (JMeter o k6) sobre el endpoint de recepción, el de mayor escritura esperada.
- Seguridad: checklist OWASP Top 10 y escaneo de dependencias como gate en CI.

**Cobertura funcional, con el mismo estándar de pruebas que la Fase 1:**
- **Pagos y liquidación** (7.6, 7.7): ciclos, ajustes, marcas de pago, retarificación retroactiva, boleta imprimible del proveedor y comprobante interno, pago masivo por ciclo.
- **Quesos** con el modelo corregido: producción por lote y ventas por tipo de comprador (proveedor / mayorista / público) con marca de boleta.
- **Zonas y rendimiento**, resolviendo por **zona efectiva a la fecha** (7.9).
- **Dashboard / Centro de operación**: KPIs, alertas operativas y cierre de jornada (7.8). *Corrección respecto de v1: el dashboard estaba marcado "2/3" y no aparecía en ninguna lista de tareas.*
- **Evidencia fotográfica** asociada a recepción e incidencia, con límite de tamaño y almacenamiento fuera de la fila de la base.
- **Portal público** (8.3) con rate limiting, caché y el alcance de publicación aprobado por escrito.

**Criterio de salida:** un commit a `main` llega a producción sin intervención manual; un despliegue blue-green se ejecuta y se demuestra el switch sin caída; el quality gate de Sonar bloquea un PR deliberadamente malo; el informe de JMeter/k6 del endpoint de recepción está archivado; el checklist OWASP está completo con hallazgos registrados.

### Fase 3 — Observabilidad, módulos restantes, hardening y cierre

- **Observabilidad**: Spring Boot Actuator (health, metrics) + Prometheus + Grafana con dashboards básicos; logs estructurados; alertas mínimas (reglas de Grafana sobre tasa de error y latencia).
- **Continuidad**: estrategia de rollback **probada**, no solo documentada; respaldos de PostgreSQL (`pg_dump` programado) con **restauración verificada**.
- **Hardening**: cerrar los hallazgos OWASP de la Fase 2, revisar configuración TLS, rotación de secretos.
- **Módulos restantes:**
  - **Rutas y movilidad**: rutas por zona, estado diario, visitas por zona, eventos manuales y avance de la ruta.
  - **Recojo móvil** y su conciliación con la recepción de planta (7.10).
  - **Trazabilidad / Seguimiento de producción**: recorrido de un lote desde el recojo hasta el queso vendido, con búsqueda y filtro por fecha y acopiador.
  - **Reportes** con filtros combinados y exportación.
  - **Importación/exportación Excel** y **respaldo/restauración JSON** con `schemaVersion` (ver decisión en 12).
- **Documentación técnica**: arquitectura, guía de despliegue, runbook, documentación de API, y el flujo completo commit → producción.

**Criterio de salida:** un rollback se ejecuta y se verifica en un entorno real; un `pg_dump` se restaura en limpio y el sistema arranca con esos datos; los dashboards de Grafana muestran tráfico real y una alerta se dispara en una prueba controlada; los 12 módulos del inventario de la sección 2 tienen al menos un flujo E2E; el runbook lo puede seguir alguien que no escribió el código.

---

## 10. Requisitos de pruebas y cobertura mínima

| Tipo | Herramienta | Alcance mínimo | Cuándo |
|---|---|---|---|
| Unitarias | JUnit 5, patrón AAA | Toda lógica de negocio no trivial: servicios, cálculos, transiciones de estado | Fase 1 en adelante |
| Parametrizadas | JUnit 5 `@ParameterizedTest` | Secciones 7.1, 7.2, 7.4, 7.5, 7.6, 7.7 completas | Fase 1 (7.1–7.6), Fase 2 (7.7) |
| Dobles de prueba | Mockito | Mockear repositorios y dependencias en tests de service | Fase 1 |
| Cobertura | JaCoCo | ≥ 80 % en capa de servicio/dominio | Verificado en Fase 1, gate automático en CI desde Fase 2 |
| Integración backend | Testcontainers + PostgreSQL con `@ServiceConnection`, `@SpringBootTest`/`@WebMvcTest`/`MockMvc` | Repositorios críticos + al menos un controller por módulo + un caso 403 | Fase 1 |
| Frontend | Angular `TestBed` + `HttpTestingController` | Componentes y servicios con lógica; no bindings triviales | Fase 1 |
| E2E | Cypress | Flujos de negocio críticos; en Fase 3, uno por módulo | Fase 1 (mínimo), ampliar en 2 y 3 |
| API | Postman/Newman | Todos los endpoints expuestos: casos felices, errores de regla de negocio, 401/403 | Fase 1, integrado a CI en Fase 2 |
| Rendimiento | JMeter o k6 | Endpoint de recepción (mayor escritura) y consulta pública (mayor lectura) | Fase 2 |
| Seguridad | Checklist OWASP Top 10 + escaneo de dependencias | Todo el backend; con foco en `/api/v1/public/**` | Escaneo desde Fase 0, checklist en Fase 2, revisión en Fase 3 |
| Datos | Test de tabla semilla | La tabla de corrección de densidad sembrada coincide con la fórmula en todo el rango | Fase 1 |
| Restauración | Prueba manual documentada | `pg_dump` → restauración limpia → arranque | Fase 3 |

---

## 11. Riesgos

1. **Spring Boot 3.5 sin parches de seguridad oficiales (EOL open-source).** Se gana estabilidad y comunidad, se pierde soporte gratuito. Mitigación: escaneo de dependencias en cada PR desde la Fase 0, monitoreo de `spring.io/security`, y plan de upgrade documentado.
2. **Equipo de varios desarrolladores sobre un solo monorepo.** Riesgo de conflictos de merge y de trabajo duplicado. Mitigación: dueño por módulo vía `CODEOWNERS`, Git Flow con revisión obligatoria desde la Fase 0, y una sincronización corta antes de cada fase para repartir tareas.
3. **Sin fechas ni rúbrica de Software II en este documento.** Las fases están ordenadas por dependencia técnica, no por calendario. Si el curso tiene entregas parciales, conviene mapearlas cuanto antes para saber cuánto alcance por entrega es realista.
4. **Portal público con datos identificables.** El prototipo publica agua detectada y pureza por código de proveedor. Sin acuerdo escrito sobre qué se publica, esto es un problema de privacidad, no un detalle de UI. Mitigación: aprobación firmada del alcance de publicación antes de exponer el endpoint, más rate limiting y caché.
5. **Alcance real muy superior al estimado en la versión anterior del documento.** El prototipo tiene 13 vistas internas, un portal público y 387 funciones; `DESARROLLO1.md` reconocía 8 módulos. Mitigación: el inventario de la sección 2 es ahora la lista de verificación de cierre, y las Fases 2 y 3 llevan carga funcional explícita en lugar de un bullet suelto.
6. **Reglas de negocio contradictorias dentro del propio prototipo.** `receptionDecision()` está definida cuatro veces y los comentarios de los datos DEMO describen una regla distinta a la vigente. Mitigación: la sección 7 fija la versión autoritativa, y la regla de retiro automático (7.2) queda marcada como pendiente de confirmación con la administración **antes** de implementarla.
7. **La corrección de densidad cambia decisiones históricas.** Aplicar 7.5 hará que entregas antes aceptadas queden como observadas y viceversa. Mitigación: la corrección aplica desde la puesta en producción hacia adelante; los datos migrados conservan su decisión original marcada como "criterio anterior".

---

## 12. Decisiones

### Confirmadas por el equipo
- Spring Boot 3.5.x en lugar de 4.1.x, con el riesgo de EOL aceptado explícitamente (sección 4).
- Angular 20 fijado, no "la última LTS al arrancar" (sección 4).
- Maven sobre Gradle; monorepo; monolito modular (sección 4).
- Nomenclatura de código en inglés con UI en español (sección 4.2) — **pendiente de ratificación antes de la primera clase**.
- JWT stateless para autenticación (sección 8.2).
- VPS + Docker Compose + reverse proxy en lugar de PaaS, para poder demostrar blue-green (Fase 2).
- Alcance de Fase 1 acotado a Proveedores + Recepción/Calidad + Auth + Auditoría + Configuración.

### Excel (RF-11) — resuelta
El prototipo importa y exporta Excel enteramente en el navegador (SheetJS + `localStorage`), sin validación de servidor, y la importación **reemplaza colecciones completas**. Con una base Postgres compartida y usuarios concurrentes eso es inaceptable.

**Decisión: importación siempre por backend** (Apache POI), reutilizando los mismos services validados que usan los formularios manuales, para que quede auditada y con las mismas reglas de negocio; **exportación del lado del cliente** a partir de datos ya traídos por la API, porque exportar no arriesga la integridad. La importación es aditiva o de actualización por clave natural, nunca un reemplazo total. **Fase 3** (queda corregida la contradicción de v1, que la ubicaba en Fase 2 en una sección y en Fase 3 en otra).

Hojas a soportar, tomadas del prototipo: Proveedores, Recepciones, HistorialCalidad, Pagos, ResumenZonas, Quesos, Configuracion, Auditoria, ReporteFiltrado. Se mantiene la plantilla descargable.

### App móvil: módulos y actualizaciones (2026-10-25) — resuelta
Pedido del cliente: app "totalmente profesional", con barra de módulos y sin reinstalar en cada cambio.
La app se entrega como **APK directo**.

- **Módulos por permiso.** Después del login, la app muestra una barra con los módulos de la persona
  (`huata_app/lib/features/shell/modulos_app.dart`): abajo en el teléfono (con "Más" si son más de
  5), lateral en tableta. Quién ve cada módulo sale de los roles y permisos del backend, los mismos de
  "Roles y vistas": quien tiene dos roles ve los módulos de ambos (antes solo veía el primero). "Mi
  cuenta" está siempre: versión, envíos pendientes y cerrar sesión. Reemplaza el menú "otras
  funciones" y el botón suelto de aprobar.
- **Parches de código con Shorebird.** Los cambios en Dart llegan solos al abrir la app. Requiere una
  cuenta de Shorebird que crea el equipo; los pasos están en `docs/APP_MOVIL_ACTUALIZACIONES.md`.
- **Versión nueva del APK (cambios nativos)** desde nuestro servidor: `GET /api/v1/public/app-movil/version`
  (variables `HUATA_APP_*`) y el APK en `descargas/` servido por `frontend`. Si hay versión nueva, la
  app muestra una franja para actualizar; si la instalada es menor que la mínima, no deja registrar
  hasta actualizar (lo pendiente de enviar se conserva).

### Mi cuenta: cada persona edita la suya (2026-10-25) — resuelta
`/api/v1/cuenta` (solo autenticado, siempre la cuenta del token). Está en la web (avatar arriba a la
derecha → `/mi-cuenta`) y en el módulo "Mi cuenta" de la app. Reglas decididas por el cliente:
- **Todos** cambian su contraseña (mínimo 8, distinta de la actual) y su **usuario**, confirmando con
  la contraseña actual. Hay un límite de 5 intentos por minuto por cuenta (`huata.cuenta.intentos-por-minuto`).
  Cambiar el usuario devuelve un JWT nuevo: el anterior deja de valer porque el usuario es su `subject`.
  En la app se bloquea mientras haya registros sin enviar, porque la cola del teléfono va a nombre
  del usuario.
- **Proveedores y acopiadores** cambian teléfono y correo, **no su nombre**: identifica sus pagos y lo
  corrige la planta en su ficha. El teléfono se copia a la ficha. El correo propio ya no se pisa al
  editar la ficha.
- El personal interno edita nombre, teléfono y correo.
- Todo cambio queda en la auditoría ("Mi cuenta"), con el valor anterior y el nuevo, nunca la contraseña.
- Sin señal, la app muestra la cuenta pero no deja editarla: estos cambios no se encolan.

### Tiempo real en todo el sistema (2026-10-26) — resuelta
Pedido del cliente: que todo esté en tiempo real y todos vean lo mismo al momento, web y app móvil.

- **Canal de cambios `/api/ws/cambios`** (WebSocket nativo, mismo AUTH con JWT que los otros canales).
  `RegistroCambios` escucha los eventos de alta/cambio/baja de Hibernate de **todas** las entidades y,
  **después del commit**, avisa "cambió `modulo/entidad/id`". Si la transacción se revierte, no se avisa.
  Un servicio nuevo queda en tiempo real sin tocar nada.
- **El aviso no lleva datos** (ni litros, ni montos, ni DNI, ni GPS). La pantalla vuelve a pedir lo suyo
  por la API REST, que valida los permisos de siempre.
- **Quién recibe qué** (`ModulosDeCambio`): quien tenga un permiso de lectura del módulo; además el
  proveedor recibe lo suyo (sus recojos, calidad, liquidación), el acopiador lo de su ruta y cada cuenta
  los cambios en sus propios permisos. Un paquete que no figura en el catálogo no se difunde.
  Si cambian los roles o permisos de una cuenta, el canal los relee al momento y la web/app actualizan
  la sesión (aparecen o desaparecen secciones sin volver a entrar).
- **Quedan fuera**: los puntos GPS (ya tienen su canal y llegan cada ~10 m), la mensajería (su canal),
  auditoría, códigos de verificación y notificaciones.
- **Siguen igual** los canales existentes: ubicaciones, puntos de ruta, acopio (conciliación) y mensajería.
- **Web**: `CambiosTiempoRealService` + `recargarEnVivo([...], recarga)` en cada pantalla; indicador
  "En vivo" arriba. **App**: `CambiosEnVivo` + mixin `EnVivo` con recarga silenciosa (sin spinner; si falla
  por falta de señal se conserva lo que se veía).
- **No se recarga** donde pisaría trabajo en curso: formularios de configuración (Precios y calidad,
  Mi cuenta, Roles), Pagos durante un pago/cierre, Rutas mientras se edita, y la ruta del acopiador
  mientras tenga recojos en la cola offline (evita mostrarlos como pendientes y registrarlos dos veces).
- **Portal público**: no entra (no tiene sesión y el canal exige JWT).

### Vistas de la web en la app móvil (2026-10-26) — resuelta
Pedido del cliente: que las vistas asignadas a cada usuario en la web se vean también en la app.

- **Cada vista de la web tiene su módulo en la app**, con el mismo permiso de acceso y agrupado igual
  que el menú web (`huata_app/lib/features/shell/modulos_app.dart`, función `veVista`). Asignar o quitar
  una vista en "Roles y vistas" hace aparecer o desaparecer el módulo al momento (canal de cambios).
- Pantallas propias (`huata_app/lib/features/oficina/`), con los mismos endpoints que la web y en vivo:
  Inicio, Calidad, Recojos, Recepción de rutas · Usuarios, Proveedores, Acopiadores, Rutas y mapa (GPS en
  vivo), Zonas, Cambios de zona · Recetas y productos, Producción, Compras, Inventario · Ventas, Pagos a
  proveedores, Contabilidad · Precios y calidad.
- **Roles de campo** (acopiador, testeador, proveedor): las lecturas que su rol trae para que funcione su
  propia pantalla (`panel.read`, `acopio.read`, `rutas.read`, `proveedores.read`, `calidad.read`) no les
  abren vistas de oficina. Si además tienen un rol de oficina, las ven.
- **Siguen en la web** por necesitar impresora, archivos o edición de mapa: imprimir boletas de pago,
  reabrir un pago con código, dibujar rutas y polígonos, alta completa de proveedores/acopiadores (DNI,
  firma, cuenta de acceso), importar Excel, configurar precios de venta. En la app esas vistas se consultan.
- Toda acción pide confirmación; el cambio de precio de la leche exige además el código de verificación.

### Flujo de producción corregible (2026-10-27) — resuelta
El flujo acopio → almacén → receta → lote → venta ya descontaba y costeaba solo, pero un error no tenía salida. Desde V59:
- **Ajuste de inventario** (Inventario → Ajustar, también en la app): sale o entra stock de un insumo con motivo obligatorio (merma, conteo físico, leche descartada). Movimiento `AJUSTE_ENTRADA/AJUSTE_SALIDA` + asiento 2411/6121 al costo de referencia. No deja el stock negativo.
- **Anular un lote** (Producción → historial → Anular este lote): solo si no salió ninguna unidad; devuelve la leche y los insumos, saca el producto de cámara y revierte los tres asientos. Con ventas, primero se anulan las ventas.
- **Anular una compra** (Compras → Anular): solo si lo comprado sigue en el almacén; revierte su asiento y deja de contar como egreso en el reporte semanal. Si ya se usó, se corrige con un ajuste.
- Producción abre Compras con el insumo que falta y la cantidad ya puestos. La leche no se compra: entra al recibir rutas o entregas directas.
- Nada se borra ni se edita: cada corrección es un movimiento nuevo con su motivo y queda en auditoría.

### Receta → inventario → compra, vencimiento y venta FEFO (2026-10-27) — resuelta
- Al guardar una receta se abre Inventario con solo sus insumos (cuánto lleva cada unidad y cuánto hay) y un botón **Comprar** por insumo.
- Compras solo ofrece insumos que lleva alguna receta vigente (filtro de pantalla; el backend no lo impide).
- **Todo lote tiene vencimiento.** Los días que dura cada producto se configuran en Configuración → Precios y calidad → Vencimiento de productos (`PATCH /productos/{id}/vencimiento`). Sin ese dato el producto no se puede producir; un lote puede cambiar su fecha a mano.
- **La venta se hace por producto**, no por lote: sale primero lo que vence antes y, si un lote no alcanza, se completa con el siguiente (`POST /quesos/ventas/por-producto`). Cada lote tocado queda como una venta propia para conservar su costo. Web y app.
- **Quesos reconstruidos**: al registrar producción, sección "¿Salieron quesos mal?" con Reconstruidos y Recorte (unidades y kg). Quedan en el lote; siguen contando en el stock y se venden al precio del producto.

### Prototipo de producción integrado: todo por ID (2026-10-28, V61) — resuelta
El cliente aprobó `HUATA-PROTOTIPO-FINAL-LOGICA.html`. Se llevó al sistema sin perder la lógica que ya tenía
(costeo, asientos, FEFO, anulaciones, auditoría). Las decisiones, cuando las dos lógicas chocaban:
- **La leche sigue entrando por el acopio real** (recepción de rutas y entregas directas). El "Registrar acopio"
  manual del prototipo no se implementó: duplicaría la leche y rompería trazabilidad, calidad y liquidación.
- **Compra ≠ stock (del prototipo).** La compra queda `PENDIENTE_RECEPCION`. La **recepción** (total o parcial,
  con lote y vencimiento del proveedor) es la que mete el insumo al almacén. Se mantiene la compra con varias
  líneas, como una factura.
  - El **asiento contable pasa a la recepción**, por el valor recibido: el inventario contable entra cuando entra
    el físico.
  - Anular una compra revierte solo lo recibido. Una compra pendiente se anula sin mover nada.
  - "Llegó junto con la compra" (`recibirAhora`) recibe en el mismo acto, para compras al contado.
  - **Compatibilidad:** si el cliente no envía `recibirAhora` (la app móvil), la compra se recibe en el acto,
    como antes de V61. Las compras anteriores quedaron `RECIBIDA`.
- **Órdenes de producción (OP2026-0001)**, del prototipo, pero **opcionales**: "Producir" abre el registro de
  siempre ya lleno y el lote cumple la orden. Anular ese lote la reabre.
  - Si está "lista" o "esperando insumos" **no se guarda**: se calcula en vivo.
  - Las órdenes **reservan el almacén en el orden en que se planificaron**, para que dos órdenes no cuenten la
    misma leche (mejora sobre el prototipo).
- **Sugerencias de abastecimiento** (`GET /produccion/necesidades`): lo requerido por las órdenes pendientes,
  menos el almacén, **menos lo ya comprado que no llegó** (mejora: no se compra dos veces).
  - La leche se informa, pero no se compra.
  - "Agregar bloque a compra" arma **una** compra con una línea por insumo marcado.
- **IDs visibles derivados del id**, sin columnas nuevas: insumos MP-0001, productos P-0001, compras OC-0001,
  recepciones RC-0001. Las órdenes guardan su código (OP + año + id), como los lotes. Todo se relaciona por id.
- **Maestro de insumos:** `tipo` (materia prima, insumo o empaque) y `sinonimos`.
  - Un nombre nuevo que ya es sinónimo de otro insumo se rechaza (evita "sal" / "sal fina" duplicados).
  - Inventario busca también por código y por sinónimo.
- **Responsable:** se usa el `createdBy` de la auditoría (quien registró la compra, la recepción o la orden). No
  se agregó un campo nuevo.
- **Navegación del prototipo**, sin pantallas duplicadas (son vistas `?vista=` de las mismas pantallas):
  - Producción: Catálogo y recetas (Catálogo · Recetas) y Producción (Órdenes · Registrar · Historial · Reportes).
  - Abastecimiento: Compras (Compras · Recepciones).
  - Inventario: Stock · Kardex · Lotes y vencimientos. Lotes muestra el producto en cámara y los lotes de
    insumos recibidos.
- **API nueva:**
  - `GET /compras/pendientes`
  - `GET /compras/recepciones`
  - `POST /compras/{id}/recepciones`
  - `GET|POST /produccion/ordenes`
  - `POST /produccion/ordenes/{id}/anular`
  - `GET /produccion/necesidades`
  - `LoteQuesoRequest.ordenId` (opcional) y `CompraRequest.recibirAhora` (opcional).
- **Pruebas:** `RecepcionesDeCompraTests` y `OrdenesDeProduccionTests` (backend); `compra-formulario.spec` y
  `produccion-queso.component.spec` (web).
- **Pendiente:** la app móvil sigue registrando compras recibidas en el acto (compatible). Si se quiere el flujo
  con recepción en el celular, falta agregarle la bandeja de Recepciones y las órdenes.
- **Ajuste (pedido del cliente):** en «Nueva orden» el producto se elegía con tarjetas que crecían hacia el
  costado. Ahora es un buscador (`app-buscador`) que se despliega hacia abajo, y cada opción muestra su código y
  cuánto alcanza. Además, `.pd-modal` se centra sin `transform`: el transform hacía que la lista `position: fixed`
  del buscador se abriera corrida hacia un costado.

### Registrar compra: «escribo sal y siempre pone cuajo» (2026-10-28) — resuelta
Reporte del cliente: al escribir un insumo que ninguna receta lleva («sal», «zal»), el campo se limpiaba
y terminaba eligiendo «cuajo», el único insumo comprable. El filtro por recetas sigue igual (el cliente
está de acuerdo); lo que estaba mal era el comportamiento.
- **Causa raíz, en el buscador compartido** (`shared/ui/buscador.component.ts`, lo usan 12 pantallas): la
  lista filtrada era un `computed` que solo leía el texto cuando `filtrando` era verdadero, y `filtrando`
  era un campo común, no una señal. Al hacer foco se calculaba con `filtrando = false`, quedaba **sin
  dependencias** y no se recalculaba nunca más. Escribir no filtraba, Enter elegía la primera opción y al
  salir se borraba lo escrito. Lo mismo pasaba con `opciones`: si llegaban tarde, no se veían. Ahora todo
  es señal. Además: filtra sin tildes, Tab cierra la lista, y salir habiendo escrito el nombre exacto elige
  esa opción. 6 pruebas de regresión que fallaban con el código anterior.
- **La lista vacía explica por qué** (`[sinCoincidencias]`): «Sal no lo lleva ninguna receta: agrégalo a
  una receta», «zal no existe», «la leche no se compra». Hay un enlace a Recetas y productos.
- **Formulario de compra** (`features/compras/compra-formulario.ts`, con sus pruebas):
  - Las filas a medio llenar ya no se descartan sin aviso al guardar; cada fila dice qué le falta.
  - Un insumo repetido se marca apenas se elige («ya está en la fila 1: suma la cantidad ahí»).
  - Cantidad mayor a cero y precio no negativo (cero se acepta).
  - La fecha no puede ser futura (también se valida en la web).
  - Al elegir un insumo se propone el último precio pagado.
  - Aviso, sin bloquear, si el precio se aleja más de 5 veces del último. Atrapa «S/ 20 por g» cuando
    era por kg.
  - La ✕ de la única fila la vacía en lugar de quedar deshabilitada.
- **Backend:** `CompraService.registrar` rechaza la leche del acopio («La leche no se compra»). La web
  ya no la ofrecía, pero la API sí la aceptaba y la contaba dos veces. Tiene su prueba en `CompraServiceTests`.

### Rediseño del módulo Producción en la web (2026-10-28) — resuelta
El cliente rechazó todas las pantallas de Producción por difíciles de usar. Se rehicieron las 4: Recetas y
productos, Producción (Registrar · Historial · Reportes), Compras e Inventario. **Backend, endpoints, permisos y
reglas no cambian**; la lógica de cada componente se conserva.
- **Sistema visual propio del módulo**: `huata-web/src/produccion-ui.css` (se carga después de `tema-azul.css`).
  Clases `pd-*` bajo `.pd`, con los colores del tema azul. Lleva `!important` solo donde los temas globales
  pisan `h1`, `th`, `td`, `input` y `table`.
- **Las 4 pantallas usan la misma estructura**: encabezado con acción principal, franja de 4 indicadores, tarjetas
  blancas, tablas con chips de estado (Disponible / Agotado / Vence pronto / Sin receta / Pausado / Anulada) y
  buscador.
- **Formularios en ventana**: la receta y la compra se abren en un panel lateral; producto, ajuste y edición de
  insumo, en un modal.
- Producción tiene pestañas Registrar · Historial · Reportes en la propia página. El historial tiene buscador
  y estado de cada lote. Los reportes muestran indicadores (producidos, vendidos, ingresos, saldo).
- Recetas: tabla de productos con la receta en palabras y botones Receta · Datos · Pausar · Eliminar.
  "Datos" (`editarProducto`) ya existía en el componente, pero no tenía botón.
- Compras: alta de proveedor dentro del formulario; el proveedor recién creado queda elegido.
- Inventario: las acciones del menú "⋯" quedan a la vista en cada fila (Comprar, Ajustar, Movimientos,
  Editar, Eliminar).

Detalle de Registrar producción:
- **Tarjetas de producto** en lugar del buscador: cada una dice cuánto alcanza ("Alcanza para 15 unidades", "Sin receta", "No alcanza") con el mismo `GET /produccion/capacidad`, una consulta por producto.
- **"Pusiste a producir" y "Salieron" juntos**, a la vista (antes "Salieron" estaba escondido en "Más detalles").
- **Una sola tabla "Se usa del almacén"** (Hay · Se usa · Queda) en lugar de dos listas repetidas; el insumo que falta trae su propio "Comprar".
- Vencimiento, costo aproximado y responsable en una línea de resumen.
- Al guardar, el formulario **queda abierto y limpio** con un aviso del lote guardado, y debajo se ve **"Hoy se produjo"**.
- **No hay etapa de maduración**: la empresa no madura sus productos y el cliente no la quiere. El lote se vende apenas se registra.

### Eliminar productos (2026-10-27) — resuelta
Pedido del cliente: no encontraba cómo eliminar un producto y el mensaje ("ya tiene producción o precios") no decía dónde quitarlo.
- **Solo impide eliminar el historial de producción**: un lote (aun anulado) tiene movimientos de inventario y asientos; las ventas cuelgan de los lotes. Con alguno, se pausa.
- **Precios y recetas sin lotes se eliminan junto con el producto**: son configuración, nada se vendió con ellos. La auditoría anota cuántos precios se fueron.
- En Recetas y productos cada tarjeta muestra estado (Se produce / Pausado) y botones con texto: Editar receta, Pausar/Reanudar, Eliminar. Si no se puede eliminar, el botón queda deshabilitado y la tarjeta dice por qué (`lotesRegistrados` en `GET /productos`).
- Los tres quesos de V28 ya no se vuelven a sembrar en cada arranque fuera del perfil `test`: reaparecían después de eliminarlos.

### Pendientes de confirmar con la administración de Huata
0. **Costo de los insumos al producir**: hoy el lote se costea con el **último precio de compra** (`costoUnitarioReferencial`), mientras el asiento de venta dice "a costo promedio". ¿Se pasa a costo promedio ponderado? Es una política contable: no se cambió sin confirmación.
1. **Retiro automático por segunda incidencia (7.2)**: ¿automático o alerta con decisión manual? Bloquea la implementación de `ReceptionDecisionService`.
2. **Temperaturas bajo 15 °C (7.5)**: ¿extrapolar la fórmula o rechazar la muestra?
3. **Valores por encima de 1,0344 (7.5)**: confirmar contra la tabla impresa si esas celdas realmente están en blanco.
4. **Relación entre `densityMin`/`densityMax` y las bandas de color (7.5)**: ¿los parámetros actuales 28–32 se reemplazan por las bandas o conviven?
5. **Cierre de jornada (7.8)**: ¿bloquea nuevas recepciones de esa fecha? ¿quién reabre?
6. **Conciliación de recojo móvil (7.10)**: tolerancia de litros y caducidad.
7. **Alcance del portal público (8.3)**: qué se publica y con qué granularidad.
8. **Migración de datos históricos**: el prototipo trae proveedores de una planilla real (13–19 de agosto). Definir si se migran a producción, con qué origen y quién valida.

---

## 13. Trazabilidad de requisitos

### 13.1 Requisitos heredados del prototipo

| Requisito | Descripción | Fase |
|---|---|---|
| RF-01 | Administrar proveedores | 1 |
| RF-02 | Registrar acopio y rendimiento | 1 |
| RF-03 | Calcular precio e importe de compra | 1 |
| RF-04 | Registrar control de calidad | 1 |
| RF-05 | Consultar historial de calidad del proveedor | 1 |
| RF-06 | Inactivar proveedor por reincidencia o adulteración | 1 |
| RF-07 | Generar pagos semanales de los viernes | 2 |
| RF-08 | Mostrar dashboard de control | **2** (antes "2/3", sin dueño) |
| RF-09 | Generar resumen por zonas | 2 |
| RF-10 | Controlar producción y venta de quesos | 2 |
| RF-11 | Importar y exportar Excel | **3** (contradicción de v1 resuelta) |
| RF-12 | Consultar reportes e historial | **3** (antes "2/3") |
| RNF-01 | Interfaz responsive y usable | Transversal; verificada con Cypress en viewport móvil en cada fase |
| RNF-02 | Rendimiento de consultas | Fase 2 (JMeter/k6) |
| RNF-03 | Seguridad y control de acceso | Fase 1 (auth, roles, 403) + Fase 2 (OWASP, portal público) |
| RNF-04 | Integridad y respaldo de datos | Fase 1 (transacciones, auditoría) + Fase 3 (respaldo y restauración verificada) |
| RNF-05 | Mantenibilidad y evolución | Transversal — es lo que `CLAUDE.md` cuida en cada fase |

### 13.2 Requisitos nuevos detectados al auditar el prototipo y el material de planta

| Requisito | Descripción | Origen | Fase |
|---|---|---|---|
| RF-13 | Corregir densidad a 15 °C por temperatura y clasificar en las cinco bandas | Tabla impresa (7.5) | 1 |
| RF-14 | Gestionar usuarios y asignación de roles | Hueco: 8 roles definidos, ningún CRUD | 1 |
| RF-15 | Administrar parámetros de negocio desde la interfaz | `config` view | 1 |
| RF-16 | Registrar toda escritura en auditoría consultable | `audit()`, `renderAuditV130` | 1 |
| RF-17 | Adjuntar y consultar evidencia fotográfica de calidad | `evidenceForReceptionV133` | 2 |
| RF-18 | Cerrar y reabrir la jornada operativa con alertas | `closeOperationalDayV135` (7.8) | 2 |
| RF-19 | Emitir boleta imprimible de liquidación al proveedor | `printWorkerPayment`, `workerReceipt` | 2 |
| RF-20 | Publicar portal público de consulta de calidad | `#portada`, `#consulta` (8.3) | 2 |
| RF-21 | Gestionar rotación de zona del proveedor y usar la zona efectiva | `providerEffectiveZoneV133` (7.9) | 2 |
| RF-22 | Seguir el avance de rutas y conciliar recojos móviles | `renderRouteTrackingV136`, `usePendingPickupV140` (7.10) | 3 |
| RF-23 | Trazabilidad del lote desde recojo hasta venta | vista `trazabilidad` | 3 |
| RF-24 | Respaldo y restauración de datos en formato portable | `exportBackup`, `importBackup` | 3 |
| RNF-06 | Rate limiting y caché en endpoints públicos | Riesgo 4 | 2 |
| RNF-07 | Búsqueda global y paginación con búsqueda en todas las tablas | `globalSearch*`, `smartPager*` | 2 |

### 13.3 Casos de prueba

Los casos CP-01…CP-17 del prototipo son un buen punto de partida literal para los tests unitarios y E2E de cada fase; hay que adaptarlos a JUnit/Cypress. **Ojo con CP-03**, que espera "100 L de calidad normal → precio S/ 1.70/L e importe S/ 175.00": el importe no corresponde al precio y hay que corregirlo antes de usarlo como caso de prueba (100 × 1,70 = 170,00).

Faltan casos para las reglas nuevas. Como mínimo hay que añadir: corrección de densidad en cada frontera de banda (RF-13), retarificación retroactiva de la semana (7.7), tarifa de Excelencia y su pérdida (7.4), retiro por segunda incidencia (7.2), zona efectiva en un reporte histórico (7.9) y conciliación de recojo móvil (7.10).
