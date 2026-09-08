# 01 — CONTEXTO COMPLETO PARA DESARROLLO

## 1. Problema que resuelve

Huata gestiona la cadena de acopio de leche con procesos manuales, papel, libretas y hojas de cálculo. Esto provoca pérdida de información, errores de liquidación y poca trazabilidad. El sistema debe digitalizar el flujo desde el recojo en campo hasta el control de calidad, consulta de liquidaciones y reportes.

## 2. Objetivo del producto

Implementar una solución integral web + móvil para:

- administrar productores, acopiadores, rutas y centros de acopio;
- registrar recojos de leche;
- funcionar en campo sin Internet y sincronizar automáticamente al recuperar conexión;
- registrar controles de calidad;
- calcular/consultar liquidaciones;
- mostrar reportes y dashboards;
- administrar usuarios y roles;
- capacitar a usuarios y brindar soporte inicial.

El objetivo formal del Charter incluye reducir al menos 74% el uso de procesos manuales en registro y liquidación.

## 3. Usuarios y responsabilidades

### Administrador / gerencia

- Gestiona productores, rutas, centros, usuarios y roles.
- Consulta recojos, liquidaciones, reportes y dashboard.
- Configura parámetros de negocio que correspondan.
- Visualiza incidencias de calidad, seguimiento y adulteraciones.
- Preferencia de UI levantada: **el administrador no debe duplicar el registro que realizan acopiadores o personal de planta; principalmente supervisa y consulta**.

### Acopiador

- Usa principalmente la aplicación móvil.
- Consulta productores asignados y ruta del día.
- Registra recojos en campo.
- Trabaja offline mediante SQLite.
- Al recuperar Internet, la sincronización debe ser automática.
- Puede capturar foto/firma/evidencia.
- Consulta historial personal y recibe notificaciones.

### Personal de planta / responsable de calidad

- Registra o valida resultados de calidad.
- Revisa densidad, acidez, temperatura y otros parámetros disponibles.
- Cuando se implemente control de agua añadida, registra punto de congelación y evidencia del análisis.
- Debe poder identificar el equipo utilizado y el responsable del análisis.

### Productor / proveedor de leche

- Es la entidad a la que se asocian recojos, resultados de calidad, historial de incidencias y liquidaciones.
- No se asume necesariamente que tenga acceso directo al sistema en la primera versión.

## 4. Flujo operativo principal

1. El administrador registra/configura productores, acopiadores, rutas y centros de acopio.
2. Se asigna una ruta/productores al acopiador.
3. El acopiador inicia la ruta y registra la movilidad/vehículo cuando aplique.
4. En cada productor registra litros, fecha/hora y datos básicos del recojo.
5. Si no hay señal, el registro queda local en SQLite.
6. Se captura evidencia cuando corresponda.
7. En planta/calidad se registran resultados del análisis.
8. La leche queda en estado ACEPTADA, OBSERVADA o RECHAZADA/NO CONFORME según política.
9. El sistema conserva historial de calidad por productor.
10. Se calcula/consulta la liquidación con la tarifa configurada y la condición de calidad.
11. Administración consulta reportes, dashboard, incidencias y notificaciones.
12. Los registros offline se sincronizan sin duplicarse al recuperar conectividad.

## 5. Calidad de leche — interpretación funcional

El sistema debe usar lenguaje comprensible para usuarios operativos.

- **Densidad:** “qué tan pesada es la leche para su volumen”. Una lectura fuera de rango genera observación; por sí sola no confirma agua añadida.
- **Punto de congelación:** temperatura a la que la leche comienza a congelarse. Si se agrega agua, el valor se mueve hacia 0 °C. El equipo típico es el crioscopio.
- **Revisión visual/olor:** color, olor, aspecto, consistencia, grumos o suciedad. Ejemplo: “Leche con olor agrio y presencia de grumos”.
- **Evidencia:** foto, firma o comprobante/voucher del equipo de análisis asociado al recojo correcto.
- **Equipo:** registrar tipo de máquina, serie/código cuando exista, operador y estado de verificación/calibración.

Referencias actualmente colocadas en el Excel final:

- densidad de referencia: 1.0296–1.0340 g/mL a 15 °C;
- punto de congelación de referencia: alrededor de -0.540 °C;
- grasa ≥ 3.2%; sólidos no grasos ≥ 8.2%; sólidos totales ≥ 11.4%; acidez 0.13–0.17%.

**Estos valores técnicos deben permanecer configurables y documentados; no dispersarlos como números mágicos en código.**

## 6. Reglas de pago actualmente consideradas

- Precio normal usado en el levantamiento: S/ 1.75/L.
- Precio observado/baja calidad usado en el levantamiento: S/ 1.30/L.
- Pago/cierre operativo referido por el negocio: semanal, viernes.

IMPORTANTE: el Charter original indica que los precios no forman parte de la versión inicial. Por eso la implementación debe usar una tabla/configuración de tarifas y no constantes. Confirmar con Administración Huata antes de producción.

## 7. Conectividad y modo offline

La falta de señal en Huata es un riesgo explícito del proyecto. La app móvil debe:

- guardar recojos localmente;
- mostrar qué registros están pendientes de sincronizar;
- sincronizar automáticamente al recuperar red;
- evitar duplicados mediante identificadores idempotentes/UUID;
- manejar reintentos y conflictos;
- conservar evidencia aun cuando se capture offline;
- informar al usuario si la sincronización terminó o falló.

No diseñar el flujo suponiendo conexión permanente.

## 8. Reportes y seguimiento

Debe poder consultarse como mínimo:

- producción total;
- producción por ruta;
- producción por productor;
- historial de recojos;
- liquidaciones por productor/fecha/semana;
- incidencias de calidad;
- lotes observados/rechazados;
- historial de evidencias;
- estado de sincronización móvil;
- usuarios/roles.

Contexto UI adicional solicitado durante el proyecto:

- Botón **Historial de calidad** → abre tabla del historial.
- Botón **Seguimiento** → abre tabla de adulteraciones/incidencias por proveedor.
- Una notificación de adulteración debe llevar directamente al registro/tabla correspondiente.
- Incluir botón **Ver foto** / **Ver evidencia** donde exista una evidencia de calidad.
- Tablas largas con buscador, filtros y paginación.

## 9. Contexto de zonas y cobertura — validar antes de convertir en requisito oficial

En materiales de campo anteriores aparecieron estas zonas: Quillallaka, Santa Barbara, I Collana, II Collana, Moro Viejo, Yasin Uchuy, Faon y Yasin, Pancha Pampa y Planta.

La versión oficial actual prioriza **rutas y centros de acopio**, no una lista fija de zonas. Por lo tanto:

- modelar zonas/sectores como datos configurables si se necesitan;
- no codificar las nueve zonas en el frontend/backend;
- mantener compatibilidad con futuras reasignaciones de productor/ruta/zona.

## 10. Datos que no se deben inventar

- modelos exactos de máquinas de Huata;
- números de serie de equipos;
- límites propios de temperatura/transporte no documentados;
- placas o cantidad de movilidades;
- tarifas definitivas mientras Administración no las confirme;
- reglas automáticas de retiro/inactivación que no estén aprobadas formalmente.

## 11. Resultado esperado de desarrollo

La entrega debe ser mantenible, trazable y auditable. Cada cambio de estado relevante debe conservar quién, cuándo y qué cambió. Evitar eliminaciones físicas de información ya liquidada o asociada a incidencias; preferir inactivación y auditoría.
