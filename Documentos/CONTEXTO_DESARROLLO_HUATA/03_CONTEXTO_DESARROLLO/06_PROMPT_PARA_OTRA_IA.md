# 06 — PROMPT PARA ENTREGAR ESTE ZIP A OTRA IA

Copia este texto junto con el ZIP:

---

Vas a trabajar sobre el proyecto **Huata Lácteos — sistema web y móvil para gestión de acopio de leche**.

Lee primero `00_LEEME_PRIMERO.md` y respeta la jerarquía de fuentes. La fuente principal de implementación es `01_FUENTE_PRINCIPAL/Huata_Cronograma_y_Requerimientos_FINAL.xlsx`.

Tareas obligatorias antes de programar:

1. Revisa todas las hojas del Excel final: Interesados, Reglas de Negocio, Matriz de Requerimientos, Casos de Prueba, Matriz de Trazabilidad y Cronograma.
2. Cruza cada funcionalidad con su HU/EDT/Sprint.
3. No inventes datos internos de Huata.
4. Todo valor marcado como pendiente debe ser configurable.
5. El móvil es offline-first; no diseñes suponiendo Internet continuo.
6. No mezcles funcionalidades históricas con el alcance oficial sin indicar que requieren validación.
7. Si una regla operativa contradice el Project Charter, identifica el conflicto antes de implementar.
8. Mantén trazabilidad entre requisito → HU/EDT → implementación → prueba.

Tecnologías esperadas:
- Angular web.
- Flutter Android.
- Spring Boot Java 21.
- PostgreSQL.
- SQLite offline.
- JWT/HTTPS.

Prioridades funcionales:
- autenticación;
- productores/acopiadores/rutas/centros;
- recojo;
- offline + sincronización;
- calidad;
- evidencias;
- liquidaciones;
- reportes/dashboard;
- usuarios/roles/notificaciones.

Antes de generar código, produce:
1. arquitectura propuesta;
2. modelo de datos;
3. endpoints/API;
4. permisos por rol;
5. estrategia offline/sync;
6. plan de implementación por Sprint;
7. lista de datos que faltan confirmar con Huata.

---
