# HUATA LÁCTEOS — CONTEXTO PARA CLAUDE CODE

## 1. PROYECTO
Sistema web + móvil para gestionar:
- Proveedores y acopiadores.
- Recolección de leche.
- Rutas y GPS.
- Calidad y evidencias.
- Planta y conciliación.
- Liquidaciones y pagos.
- Compras e inventario.
- Producción de lácteos.
- Ventas, precios y promociones.

El sistema es productivo, no un simple prototipo.

## 2. REGLA PRINCIPAL
Antes de modificar código:
1. Revisar `DESARROLLO.md`.
2. Revisar la implementación existente.
3. No duplicar funcionalidades.
4. Mantener compatibilidad con lo existente.
5. No inventar reglas de negocio.
6. Las decisiones más recientes tienen prioridad.

## 3. TECNOLOGÍA
- Backend: Java + Spring Boot + Spring Security/JWT + JPA.
- BD: PostgreSQL + Flyway.
- Web: Angular standalone.
- Móvil: Flutter + almacenamiento local/offline.
- Archivos: MinIO/S3.
- Infraestructura: Docker.
- Tests: JUnit/Mockito.

## 4. ROLES
ADMIN, SUPERVISOR_PLANTA, OPERADOR_PLANTA, CALIDAD,
CAJA/CONTABILIDAD, ACOPIADOR, PROVEEDOR, TESTEADOR,
PRODUCCION, COMPRAS/ALMACEN, VENTAS y GERENCIA.

Cada rol solo debe acceder a sus recursos autorizados.

## 5. ACOPIO
- Cada proveedor pertenece a una zona y acopiador.
- El acopiador trabaja por ruta y zona.
- Estados de visita: pendiente, recolectado, derramado/no recolectado.
- Registrar litros recolectados y recibidos en planta.
- Registrar diferencias sin asumir automáticamente robo o pérdida.
- La app móvil debe funcionar offline.
- Los datos se sincronizan automáticamente al recuperar conexión.
- No crear botón manual de sincronización.

## 6. GPS
- Registrar ubicación durante la ruta.
- Aproximadamente cada 10 m cuando corresponda.
- Soportar Puntos, Trazado o Ambos.
- Debe funcionar con mala cobertura.
- Sincronización automática, reintentos e idempotencia.
- GPS no constituye por sí solo prueba absoluta.

## 7. CALIDAD
Registrar:
- proveedor
- fecha
- operador/dispositivo
- parámetros
- resultado
- evidencia

Conservar el voucher/foto original.
OCR solo propone datos; una persona debe confirmarlos.

Las reglas de mala calidad, descuentos y retiro de proveedores deben ser configurables.

## 8. TARIFAS Y PAGOS
Valores vigentes definidos en `DESARROLLO.md`.
No modificar tarifas por suposición.

Liquidación semanal:
- jueves → miércoles
- pago: viernes

Las reglas de descuento y retiro deben respetar la configuración vigente.

## 9. PRODUCTOS
No limitar el sistema a queso.
Debe permitir productos lácteos configurables, por ejemplo:
- queso
- yogurt
- manjar
- otros

Productos, precios, promociones, recetas, producción, lotes e inventario deben ser configurables.

## 10. SEGURIDAD
- JWT/autorización por rol y recurso.
- HTTPS en producción.
- Nunca guardar secretos en código.
- No exponer DNI, firmas, vouchers ni GPS privado en el panel público.
- Validar permisos también en backend.

## 11. PANEL PÚBLICO
Sin login:
- calidad por zona
- indicadores permitidos
- información pública de proveedores únicamente cuando corresponda

Nunca mostrar información privada.

## 12. REGLAS DE DESARROLLO
- Preferir reutilizar componentes y servicios existentes.
- No eliminar funcionalidades vigentes sin autorización.
- No crear pantallas duplicadas.
- Mantener trazabilidad e historial.
- Probar cambios antes de afirmar que funcionan.
- Si algo queda pendiente, indicarlo claramente.

## 13. PRIORIDAD
1. Seguridad y reglas de negocio.
2. Backend y BD.
3. Web.
4. Móvil/offline.
5. GPS.
6. Calidad.
7. Inventario/producción.
8. Ventas.
9. UI y mejoras visuales.

Para detalles específicos, consultar `DESARROLLO.md`.