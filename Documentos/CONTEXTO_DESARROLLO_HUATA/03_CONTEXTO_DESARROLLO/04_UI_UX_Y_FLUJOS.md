# 04 — UI/UX Y FLUJOS SOLICITADOS

## Estilo visual

- Paleta preferida: crema / amarillo pastel + verde.
- Diseño limpio, profesional y fácil de usar en campo.
- Evitar pantallas saturadas o “blanco muerto”.
- Responsive en web.

## Principios

- El administrador supervisa; acopiadores y personal de planta registran lo operativo.
- Evitar paneles duplicados.
- Las alertas deben llevar al dato que las originó.
- Lenguaje sencillo en calidad: “olor agrio”, “grumos”, “agua añadida”, “fuera del rango”.

## Calidad

- Botón `Historial de calidad` abre tabla histórica del proveedor.
- Botón `Seguimiento` abre tabla de incidencias/adulteraciones.
- Si una notificación informa adulteración, al hacer clic debe abrir el detalle correspondiente.
- Cada incidencia con evidencia debe tener `Ver foto` / `Ver comprobante`.
- Distinguir claramente:
  - ACEPTADA
  - OBSERVADA
  - RECHAZADA

## Listados

En productores, recojos, calidad, reportes, liquidaciones y usuarios:
- buscador;
- filtros relevantes;
- selector de cantidad por página;
- paginación;
- ordenamiento cuando aporte valor.

## Móvil

- Offline-first.
- No usar botón manual de sincronización como flujo principal: sincronizar automáticamente al detectar conexión.
- Mostrar estado de sincronización de forma discreta y clara.
- Permitir capturar voucher/foto/firma.
- Vista rápida de productores asignados y ruta del día.

## Dashboard / reportes

Como mínimo:
- litros totales;
- producción por productor y ruta;
- tendencia de calidad;
- incidencias;
- liquidaciones;
- filtros por periodo.

## Mejoras históricas a validar antes de construir

Durante prototipos anteriores también se solicitaron:
- ticket/impresión para administrador y trabajador;
- vistas por zonas/sectores;
- mapa de recorrido y pérdida de señal;
- módulo relacionado con producción/venta de quesos;
- importación/exportación Excel.

Estas funciones **no aparecen todas en el alcance oficial actual**. No implementarlas automáticamente sin validación/Change Request.
