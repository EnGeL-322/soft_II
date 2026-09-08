# PROMPT MAESTRO — Arquitectura escalable y modular de Huata Lácteos

> Este documento es el "prompt" de referencia para construir Huata en fases. Está pensado para
> guiar tanto a una IA como a cualquier desarrollador que se sume al proyecto. Su fuente de
> verdad es [`Documentos/CONTEXTO_DESARROLLO_HUATA/`](Documentos/CONTEXTO_DESARROLLO_HUATA/00_LEEME_PRIMERO.md)
> (paquete de contexto oficial: requerimientos, decisiones, arquitectura sugerida, UI/UX y
> Project Charter). Si algo aquí contradice ese paquete, **manda el paquete de contexto**.

## 0. Rol y misión

Eres el equipo de desarrollo de **Huata Lácteos**, sistema web + móvil para digitalizar el
acopio de leche (productores → recojo en ruta → control de calidad → liquidación → reportes),
reemplazando papel, libretas y hojas de cálculo. Objetivo formal del Charter: reducir ≥74% el
uso de procesos manuales.

Trabajas por **fases** para escalar rápido sin retrabajo:

- **Fase 1 (actual):** Backend (Java 17 + Spring Boot) + Web (Angular). ← este prompt la ejecuta.
- **Fase 2:** App móvil (Flutter) offline-first con SQLite + sincronización contra el mismo backend.
- **Fase 3:** Endurecer seguridad, notificaciones push (FCM), reportes avanzados.

## 1. Reglas no negociables (vienen del paquete de contexto)

1. **No inventes datos internos de Huata** (tarifas definitivas, modelos/series de equipos,
   límites de temperatura, placas/movilidades, políticas de retiro de proveedor). Todo lo que
   esté "por confirmar" se implementa como **configuración editable**, nunca como constante en código.
2. **Zonas vs. rutas:** no se codifican las 9 zonas históricas como enum fijo. Rutas y centros
   de acopio son las entidades oficiales; "zona" es un campo de texto configurable dentro de ruta.
3. **Precios de leche (S/ 1.75 normal, S/ 1.30 observada):** son valores operativos de referencia,
   no constantes de negocio. Se modelan como tarifa vigente con fecha, editable por Administración.
4. **Trazabilidad y auditoría:** toda entidad relevante guarda `createdAt/updatedAt/createdBy/
   updatedBy`. No se hace borrado físico de recojos, calidad o liquidaciones: se inactiva/cancela
   con motivo.
5. **Offline-first es un requisito del móvil, no del backend web** — pero el backend ya debe
   exponer lo necesario para que la Fase 2 no requiera rediseño: `clientUuid` idempotente en
   `Recojo`, endpoints listos para sincronización por lotes.
6. **El administrador supervisa y consulta; no duplica el registro de campo/planta.** El registro
   operativo (recojo, calidad) lo hacen acopiador y personal de planta.
7. Todo dato de ejemplo cargado para desarrollo se marca explícitamente **DEMO** (mismo criterio
   que ya usa el prototipo HTML de Huata) y vive detrás de un perfil `dev`, nunca en `prod`.

## 2. Estructura de carpetas (monorepo, separada por plataforma y modular por dominio)

```
soft_II/
├── huata/            → BACKEND (Java 17 + Spring Boot, modular por dominio)
│   └── src/main/java/org/ramiro/huata/
│       ├── common/        (BaseEntity, PageResponse, excepciones, manejo de errores)
│       ├── security/      (JWT, filtros, SecurityConfig)
│       ├── auth/          (login)
│       ├── usuarios/      (Usuario, Rol)
│       ├── productores/
│       ├── acopiadores/
│       ├── rutas/         (Ruta, CentroAcopio, AsignacionRuta)
│       ├── recojo/        (registro de acopio en campo)
│       ├── calidad/       (ControlCalidad, ParametroCalidad, IncidenciaCalidad, Evidencia)
│       ├── liquidaciones/ (TarifaLeche, Liquidacion, LiquidacionDetalle)
│       ├── parametros/    (ParametroNegocio: configuración general)
│       ├── notificaciones/
│       ├── quesos/        (producción/venta de quesos — módulo histórico, ver sección 3)
│       ├── auditoria/     (bitácora: quién/cuándo/qué acción)
│       ├── portal/        (endpoints públicos, sin login: consulta de calidad por código)
│       ├── exportacion/   (genera los .xlsx reales de "Datos y respaldo", con Apache POI)
│       ├── Dockerfile, .dockerignore
│       └── reportes/      (agregaciones de solo lectura: dashboard, zonas, trazabilidad)
│
├── huata-web/        → WEB (Angular, modular por feature)
│   └── src/app/
│       ├── core/          (interceptores, guards, modelos y TODOS los servicios HTTP — se
│       │                    centralizan aquí porque casi toda pantalla necesita el mismo
│       │                    "picker" de productores/rutas/acopiadores)
│       ├── shared/        (layout/shell, badge de estado, paginador reutilizables)
│       └── features/      (una carpeta por pantalla: dashboard, productores, acopiadores,
│                            rutas, recojo, calidad, liquidaciones, quesos, zonas,
│                            trazabilidad, reportes, usuarios, parametros, datos-respaldo,
│                            auth, portal — cada una con su componente standalone)
│   Dockerfile, nginx.conf, proxy.conf.json
│
├── docker-compose.yml → levanta db + backend + web con un solo comando (sección 6)
│
└── huata_movil/      → MÓVIL (Flutter — se retoma en Fase 2, sin tocar en esta fase)
```

Cada módulo del backend sigue el mismo patrón interno (Entity → Repository → Service →
Controller con DTOs como `record`), y cada feature del web sigue el mismo patrón
(model → service HTTP → componente de listado con tabla compartida → componente de formulario).
Así, agregar un módulo nuevo (p. ej. "Quesos" si Huata lo aprueba) es copiar el patrón, no
inventar uno nuevo.

## 3. Alcance funcional de la Fase 1 (backend + web)

Prioridad tomada de `01_CONTEXTO_COMPLETO_PARA_DESARROLLO.md` y del menú del prototipo HTML.
A pedido explícito del cliente, la Fase 1 replica **todos** los módulos visibles del prototipo,
incluidos los que el Project Charter no exige — quedan igual construidos pero rotulados como
"módulo histórico" donde corresponde, para que quede claro qué es alcance oficial y qué no:

1. Autenticación (JWT) y usuarios/roles (ADMINISTRADOR, ACOPIADOR, CALIDAD), con tabla de
   Roles (responsabilidad/acceso) y bitácora de Auditoría.
2. Productores, Acopiadores, Rutas y Centros de acopio.
3. Recojo de leche (registro, listado, cancelación con motivo).
4. Calidad: parámetros configurables (densidad, punto de congelación, acidez, grasa, sólidos),
   control de calidad por recojo, evidencia (foto/voucher), historial por proveedor y
   seguimiento de incidencias/adulteraciones.
5. Liquidaciones: tarifa configurable, cálculo por proveedor/periodo, detalle por recojo.
6. Producción · Quesos — **módulo histórico** (fuera del Charter oficial), CRUD completo con
   "total venta" nulo cuando no hay evidencia (no se inventa el monto).
7. Zonas y rendimiento — comparativo semana actual vs. anterior por zona (la zona sigue siendo
   texto libre de `Ruta`, no una lista fija: decisión C-05).
8. Seguimiento de producción / Trazabilidad — buscador libre (código, zona, fecha, id de
   recepción) **+ mapa GPS real** (Leaflet + OpenStreetMap, sin API key): el acopiador comparte su
   ubicación desde "Recepción / Acopio" con la Geolocation API del navegador (permiso nativo del
   dispositivo, sin simulación) y su última posición aparece en el mapa. Módulo `ubicaciones`
   (`UbicacionGps`, ligado al `Acopiador` vía su `Usuario`).
9. Datos y respaldo — exporta cada tabla a `.xlsx` real (Apache POI) en vez de generarlo en el
   navegador.
10. Portal público (`/portal`, sin login) — un proveedor consulta su propia calidad con su
    código; no expone documento/teléfono ni datos de otros proveedores.
11. Reportes/dashboard: producción total, por ruta, por productor, tendencia de calidad,
    incidencias, liquidaciones — KPIs primero, con filtros.
12. Notificaciones enlazadas a la incidencia/registro que las origina.
13. Configuración de parámetros de negocio, rangos de calidad, roles y auditoría.

**Se mantiene fuera** (no tiene equivalente pedido ni en el prototipo ni en el Charter):
pasarelas de pago/transacciones financieras reales.

## 4. Identidad visual (debe verse como el prototipo)

Paleta "crema pastel + verde" ya usada en el prototipo HTML de Huata:

```
--bg:#f3ecdc;     --panel:#fbf7ec;   --panel2:#ece1c9;  --text:#2b2417; --muted:#7a6f57; --line:#e6dcc4;
--green:#336751;  --green2:#539479;  --green-soft:#e5e6d6;
--blue:#3e6f5a;   --blue-soft:#e7e6d3;
--amber:#9d7736;  --amber-soft:#ecdfc3;
--red:#a15344;    --red-soft:#f1e3d4;
```

Convenciones de UI que se replican en Angular:

- Barra lateral con navegación por módulo (mismo orden que el prototipo).
- Tablas largas con **buscador + filtros + paginación + orden**.
- Estados de calidad como badge de color: **ACEPTADA** (verde) / **OBSERVADA** (ámbar) /
  **RECHAZADA** (rojo).
- Botones "Historial de calidad" y "Seguimiento" que abren la tabla correspondiente; una
  notificación de incidencia enlaza directo al registro.
- Reportes con KPIs arriba y filtros debajo.

## 5. Stack técnico (Fase 1)

- **Backend:** Java 17, Spring Boot (ya fijado en `huata/pom.xml`), Spring Web, Spring Data JPA,
  Spring Security + JWT, Bean Validation, Apache POI (Excel real para "Datos y respaldo"). Perfil
  `dev` con H2 en memoria (para levantar sin instalar nada); perfil `postgres` con PostgreSQL real
  — el objetivo declarado en el Charter — orquestado por el `docker-compose.yml` raíz.
- **Web:** Angular (ya scaffolded en `huata-web/`), standalone components, `HttpClient` con
  interceptor JWT, guards por rol. Se desactiva SSR (`server.ts`/`main.server.ts`): es un panel
  administrativo autenticado, no necesita SEO y SSR complica el uso de `localStorage`/JWT.
- **Móvil (Fase 2, no se toca ahora):** Flutter + SQLite + sincronización contra los mismos
  endpoints REST (`clientUuid` ya reservado en `Recojo` desde la Fase 1).

## 6. Cómo ejecutar

### Opción A — todo con Docker (recomendada, es "lo real")

Un solo comando levanta PostgreSQL + backend (Java 17/Spring Boot) + web (Angular servido por
nginx, que hace de proxy hacia el backend en `/api/`, mismo origen, sin CORS):

```bash
docker compose up --build -d
```

- Web: http://localhost:8081
- Backend (API directa, opcional): http://localhost:8080
- Variables ajustables copiando `.env.example` a `.env` (puertos, credenciales de Postgres,
  `HUATA_SEED_DEMO`). `docker compose down` detiene todo; `docker compose down -v` además borra
  el volumen de PostgreSQL (datos).
- Si el build de la imagen del backend necesita el mismo ajuste de certificados de la nota de
  abajo: `docker build --build-arg MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT ./huata`
  (en esta sesión el build en Docker funcionó sin necesitarlo).

### Opción B — desarrollo local sin Docker (hot-reload)

```bash
# Backend (perfil dev con H2, arranca sin dependencias externas) → http://localhost:8080
cd huata
./mvnw spring-boot:run

# Web (ng serve usa proxy.conf.json para reenviar /api al backend) → http://localhost:4200
cd huata-web
npm start
```

Usuarios DEMO sembrados (contraseña `Huata2026*` para los tres):

- `admin@huata.pe` — ADMINISTRADOR
- `acopiador.demo@huata.pe` — ACOPIADOR
- `calidad.demo@huata.pe` — CALIDAD

Portal público de un proveedor (sin login): http://localhost:8081/portal (o `:4200/portal` en
desarrollo), probar con el código `DEMO-001`.

### Nota de este equipo: Maven y certificados

En esta máquina, el JDK de línea de comandos (`C:\Program Files\Java\jdk-21`) no confía en la
cadena de certificados que intercepta el tráfico HTTPS hacia Maven Central (típico de un
antivirus/EDR corporativo), aunque Windows y el navegador sí confían en ella. Maven falla con
`PKIX path building failed`. Mientras no se importe ese certificado al almacén de confianza del
JDK (cambio de configuración que no se hizo automáticamente — es un ajuste de seguridad del
equipo), usar esta variable de entorno solo para compilar/ejecutar con Maven:

```bash
set MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT
./mvnw spring-boot:run
```

(En PowerShell: `$env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"`). Esto le dice a
esa ejecución de la JVM que use el almacén de certificados de Windows en lugar del `cacerts` propio
del JDK; no cambia nada de forma permanente. Una vez las dependencias están en `~/.m2/repository`
(ya lo están tras el primer build de esta sesión), volver a compilar/ejecutar sin conexión no
necesita la variable.

## 7. Checklist de negocio pendiente de validar con Huata (no bloquea el desarrollo, sí producción)

Copiado de `02_DECISIONES_Y_PENDIENTES.md` — mantenerlo vivo:

- [ ] Tarifas vigentes y fecha de vigencia.
- [ ] Regla de pago semanal exacta.
- [ ] Inventario real de equipos de calidad.
- [ ] Rangos de calidad operativos definitivos.
- [ ] Procedimiento para confirmar agua añadida.
- [ ] Política de proveedor observado/reincidente.
- [ ] Lista de movilidades/capacidades.
- [ ] Límites de tiempo/temperatura de transporte.
- [ ] Centros/rutas/zonas definitivos.
- [ ] Roles y permisos definitivos.
- [ ] Canal de notificaciones (email/push/ambos).

## 8. Siguiente prompt (cuando se retome el móvil, Fase 2)

"Implementa `huata_movil` en Flutter siguiendo el mismo contrato de API que `huata` (Fase 1),
con SQLite para persistencia offline, cola de sincronización por `clientUuid`, captura de
foto/firma/evidencia y sincronización automática al recuperar conexión. No dupliques modelos:
reusa los DTOs/contratos ya definidos en `huata` y documentados en este archivo."
