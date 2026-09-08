# 02 — DECISIONES, CONFLICTOS Y DATOS PENDIENTES

## Confirmado por fuentes oficiales / cronograma

- Solución web + móvil + backend API REST.
- Angular, Flutter, Spring Boot, PostgreSQL y SQLite.
- Autenticación JWT y seguridad HTTPS.
- Modo offline-first para recojo en campo.
- Gestión de productores, acopiadores, rutas y centros de acopio.
- Registro de recojo, control de calidad, liquidaciones, reportes y usuarios/roles.
- Control de calidad mínimo del Charter: densidad, acidez y temperatura.
- Android vía APK.
- Capacitación y despliegue en Sprint 8.
- Cronograma de 8 Sprints.

## Decisiones operativas acumuladas del proyecto

- Administrador: principalmente supervisa/consulta; no debe duplicar el registro de campo/planta.
- Historial de calidad visible por proveedor.
- Seguimiento de incidencias/adulteraciones con evidencia.
- Evidencia mediante foto/voucher/archivo.
- Notificaciones enlazadas al detalle de la incidencia.
- Búsqueda, filtros y paginación en listados grandes.
- Sincronización móvil automática al recuperar conectividad.
- Lenguaje de calidad amigable para usuario no técnico.

## Conflictos que el equipo debe resolver con el cliente

### C-01 — Precio de leche

Contexto operativo trabajado:
- S/ 1.75/L leche normal.
- S/ 1.30/L leche observada/baja calidad.

Pero el Project Charter original indica que “los precios de leche no serán incluidos en esta versión”.

**Decisión técnica:** implementar tarifas como configuración opcional y no como constantes. Activar su uso solo después de aprobación de Huata.

### C-02 — Equipos reales de calidad

Se han definido conceptualmente:
- crioscopio;
- medidor de densidad/lactodensímetro;
- analizador de leche;
- termómetro/sensor.

No está documentado qué equipos/marcas/modelos posee Huata.

**Pendiente:** inventario real de equipos, serie, precisión, impresión de voucher y procedimiento de calibración.

### C-03 — Temperatura de transporte

El sistema debe poder registrar temperatura y alertar por límite, pero el límite operativo específico de Huata no está formalizado.

**Decisión técnica:** parámetro configurable por Administración; no hardcodear un valor.

### C-04 — Movilidad

Se requiere trazabilidad de ruta/vehículo, pero falta confirmar:
- cuántos vehículos existen;
- placa/código;
- capacidad;
- tanque/bidones;
- chofer/acopiador asignado;
- si existe GPS.

### C-05 — Zonas vs rutas

Materiales históricos incluyen 9 zonas. El alcance oficial actual se expresa en términos de rutas y centros de acopio.

**Decisión técnica:** no usar enums fijos para zonas; crear entidades/configuración flexible.

### C-06 — Reincidencia de mala calidad

En levantamientos previos se planteó historial de reincidencia e inactivación de proveedor. Falta una política formal que indique exactamente cuántas incidencias y qué tipos provocan retiro.

**Decisión técnica:** guardar todas las incidencias y permitir decisión administrativa; no automatizar el retiro hasta aprobación formal.

## Checklist que debe validar el cliente antes de producción

- [ ] Tarifas vigentes y fechas de vigencia.
- [ ] Regla de pago semanal exacta.
- [ ] Inventario de máquinas/equipos.
- [ ] Rangos de calidad que Huata usará operativamente.
- [ ] Procedimiento para confirmar agua añadida.
- [ ] Política de proveedor observado/reincidente.
- [ ] Lista de movilidades y capacidades.
- [ ] Límites de tiempo/temperatura de transporte.
- [ ] Centros/rutas/zonas definitivos.
- [ ] Roles y permisos definitivos.
- [ ] Canal de notificaciones: email, push o ambos.
