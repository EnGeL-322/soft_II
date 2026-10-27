package org.ramiro.huata.usuarios;

import java.util.List;

/**
 * Catalogo modulo -> vista -> acciones, en Java.
 *
 * <p>Mismo motivo y misma vigilancia que {@link MatrizPermisos}: V46 lo siembra en PostgreSQL, pero
 * el perfil "test" corre sobre H2 sin Flyway y necesita los mismos datos (los siembra
 * DemoDataSeeder). {@code MigracionesFlywayIT} compara ambos fila por fila.
 *
 * <p><b>Anadir una vista</b> es anadir una pantalla: va junto al codigo que la implementa (componente
 * Angular + ruta en navegacion.ts + permisos que exigen sus endpoints), en una migracion nueva y
 * aqui. Lo que es dato editable desde la aplicacion es quien la ve y que puede hacer en ella.
 */
public final class CatalogoVistas {

    private CatalogoVistas() {}

    public record DefModulo(String codigo, String nombre, int orden) {}

    public record DefAccion(String permiso, String etiqueta, int orden) {}

    /** ruta == null: funcion transversal sin pantalla propia (no sale en el menu). */
    public record DefVista(
            String codigo,
            String nombre,
            String descripcion,
            String modulo,
            String ruta,
            String icono,
            int orden,
            String permisoAcceso,
            List<DefAccion> acciones) {}

    public static final List<DefModulo> MODULOS = List.of(
            new DefModulo("ACOPIO", "Acopio", 10),
            new DefModulo("PERSONAS_RUTAS", "Personas y rutas", 20),
            new DefModulo("PRODUCCION", "Producción", 30),
            new DefModulo("VENTAS_PAGOS", "Ventas y pagos", 40),
            new DefModulo("CONFIGURACION", "Configuración", 50),
            new DefModulo("GENERAL", "Funciones generales", 60));

    private static DefAccion ver(String permiso) {
        return new DefAccion(permiso, "Ver", 10);
    }

    private static DefAccion accion(String permiso, String etiqueta, int orden) {
        return new DefAccion(permiso, etiqueta, orden);
    }

    public static final List<DefVista> VISTAS = List.of(
            new DefVista("dashboard", "Inicio", "Panel del día y lo que requiere atención.",
                    "ACOPIO", "dashboard", "◆", 10, Permiso.PANEL_READ,
                    List.of(ver(Permiso.PANEL_READ))),
            new DefVista("calidad", "Calidad", "Controles, evidencias e incidencias de calidad.",
                    "ACOPIO", "calidad", "◇", 20, Permiso.CALIDAD_READ,
                    List.of(ver(Permiso.CALIDAD_READ),
                            accion(Permiso.CALIDAD_WRITE, "Registrar análisis y evidencias", 20),
                            accion(Permiso.CALIDAD_APROBAR, "Aprobar análisis", 30))),
            new DefVista("recojo", "Recojos en ruta", "Entregas de leche registradas por ruta y proveedor.",
                    "ACOPIO", "recojo", "▤", 30, Permiso.ACOPIO_READ,
                    List.of(ver(Permiso.ACOPIO_READ),
                            accion(Permiso.ACOPIO_WRITE, "Registrar y corregir recojos", 20))),
            new DefVista("conciliacion", "Recepción de rutas", "Litros recibidos en planta y cierre de jornada.",
                    "ACOPIO", "conciliacion", "⇄", 40, Permiso.ACOPIO_READ,
                    List.of(ver(Permiso.ACOPIO_READ),
                            accion(Permiso.PLANTA_RECEPCION, "Registrar litros recibidos", 20),
                            accion(Permiso.ACOPIO_CERRAR_JORNADA, "Cerrar y reabrir jornada", 30))),
            new DefVista("rutas", "Rutas y mapa", "Rutas, asignaciones y seguimiento GPS.",
                    "PERSONAS_RUTAS", "rutas", "⇢", 10, Permiso.RUTAS_READ,
                    List.of(ver(Permiso.RUTAS_READ), accion(Permiso.RUTAS_WRITE, "Crear y editar rutas", 20))),
            new DefVista("zonas", "Zonas", "Personas y rutas de cada zona.",
                    "PERSONAS_RUTAS", "zonas", "◎", 20, Permiso.ZONAS_READ,
                    List.of(ver(Permiso.ZONAS_READ))),
            // V55: Usuarios reune en una pantalla las cuentas internas, los acopiadores y los
            // proveedores; cada pestana conserva su vista y su permiso.
            new DefVista("usuarios", "Usuarios", "Cuentas internas, roles y permisos.",
                    "PERSONAS_RUTAS", "usuarios", "⚇", 5, Permiso.USUARIOS_READ,
                    List.of(ver(Permiso.USUARIOS_READ),
                            accion(Permiso.USUARIOS_WRITE, "Crear usuarios y asignar permisos", 20))),
            new DefVista("productores", "Proveedores", "Padrón de proveedores de leche (pestaña de Usuarios).",
                    "PERSONAS_RUTAS", "usuarios", "◍", 6, Permiso.PROVEEDORES_READ,
                    List.of(ver(Permiso.PROVEEDORES_READ),
                            accion(Permiso.PROVEEDORES_WRITE, "Crear, aprobar e inactivar", 20))),
            new DefVista("solicitudes-zona", "Cambios de zona", "Solicitudes de cambio de zona de los proveedores.",
                    "PERSONAS_RUTAS", "solicitudes-zona", "⇋", 40, Permiso.PROVEEDORES_READ,
                    List.of(ver(Permiso.PROVEEDORES_READ),
                            accion(Permiso.PROVEEDORES_WRITE, "Aprobar o rechazar", 20))),
            new DefVista("acopiadores", "Acopiadores", "Personas que realizan el recojo (pestaña de Usuarios).",
                    "PERSONAS_RUTAS", "usuarios", "☺", 7, Permiso.ACOPIADORES_READ,
                    List.of(ver(Permiso.ACOPIADORES_READ), accion(Permiso.ACOPIADORES_WRITE, "Crear y editar", 20))),
            new DefVista("recetas", "Recetas y productos", "Productos y sus recetas.",
                    "PRODUCCION", "recetas", "▧", 10, Permiso.RECETAS_READ,
                    List.of(ver(Permiso.RECETAS_READ),
                            accion(Permiso.RECETAS_WRITE, "Crear y versionar recetas", 20))),
            new DefVista("produccion-queso", "Producción", "Lotes de producción, consumo y costos.",
                    "PRODUCCION", "produccion-queso", "◲", 20, Permiso.PRODUCCION_READ,
                    List.of(ver(Permiso.PRODUCCION_READ),
                            accion(Permiso.PRODUCCION_WRITE, "Crear y cerrar lotes", 20),
                            // V54: quesos.write ya no vende; solo registra lotes por el camino de ventas.
                            accion(Permiso.QUESOS_WRITE, "Registrar lotes de queso", 30))),
            new DefVista("compras", "Compras", "Compras de insumos y materias primas.",
                    "PRODUCCION", "compras", "▣", 30, Permiso.COMPRAS_READ,
                    List.of(ver(Permiso.COMPRAS_READ), accion(Permiso.COMPRAS_WRITE, "Registrar compras", 20))),
            new DefVista("inventario", "Inventario", "Existencias, vencimientos y movimientos.",
                    "PRODUCCION", "inventario", "▥", 40, Permiso.INVENTARIO_READ,
                    List.of(ver(Permiso.INVENTARIO_READ),
                            accion(Permiso.INVENTARIO_WRITE, "Ajustar stock y materias primas", 20))),
            new DefVista("venta-queso", "Ventas", "Ventas y cobros.",
                    "VENTAS_PAGOS", "venta-queso", "◳", 10, Permiso.QUESOS_READ,
                    List.of(ver(Permiso.QUESOS_READ),
                            accion(Permiso.VENTAS_REGISTRAR, "Registrar ventas", 20),
                            accion(Permiso.VENTAS_COBRAR, "Marcar como cobradas", 30),
                            accion(Permiso.VENTAS_ANULAR, "Anular ventas", 40))),
            new DefVista("pagos", "Pagos", "Liquidaciones y pagos a proveedores.",
                    "VENTAS_PAGOS", "pagos", "$", 20, Permiso.PAGOS_READ,
                    List.of(ver(Permiso.PAGOS_READ),
                            accion(Permiso.PAGOS_WRITE, "Generar y pagar liquidaciones", 20),
                            accion(Permiso.PAGOS_CERRAR_CICLO, "Cerrar ciclo semanal", 30),
                            accion(Permiso.PAGOS_REABRIR, "Reabrir un pago (con código)", 40))),
            new DefVista("contabilidad", "Contabilidad", "Plan de cuentas, libro diario y mayor.",
                    "VENTAS_PAGOS", "contabilidad", "▤", 30, Permiso.CONTABILIDAD_READ,
                    List.of(ver(Permiso.CONTABILIDAD_READ))),
            new DefVista("reglas-leche", "Precios y calidad", "Precios por litro y criterios de calidad.",
                    "CONFIGURACION", "reglas-leche", "⚙", 20, Permiso.PARAMETROS_READ,
                    List.of(ver(Permiso.PARAMETROS_READ),
                            accion(Permiso.PARAMETROS_WRITE, "Modificar precios y reglas", 20))),
            new DefVista("reportes", "Reportes", "Reportes y exportaciones de consulta.",
                    "GENERAL", null, null, 10, Permiso.REPORTES_READ,
                    List.of(accion(Permiso.REPORTES_READ, "Generar reportes", 10))),
            new DefVista("trazabilidad", "Trazabilidad", "Seguimiento de producción y búsqueda global.",
                    "GENERAL", null, null, 20, Permiso.TRAZABILIDAD_READ,
                    List.of(accion(Permiso.TRAZABILIDAD_READ, "Consultar", 10))),
            new DefVista("mensajeria", "Mensajería", "Conversaciones con proveedores.",
                    "GENERAL", null, null, 30, Permiso.MENSAJERIA_READ,
                    List.of(accion(Permiso.MENSAJERIA_READ, "Ver conversaciones", 10),
                            accion(Permiso.MENSAJERIA_WRITE, "Enviar mensajes", 20))),
            new DefVista("respaldo", "Respaldo de datos", "Exportar e importar datos del sistema.",
                    "GENERAL", null, null, 40, Permiso.RESPALDO_READ,
                    // V53: importar el padron desde Excel. La restauracion completa es de servidor.
                    List.of(accion(Permiso.RESPALDO_READ, "Exportar datos", 10),
                            accion(Permiso.RESPALDO_WRITE, "Importar proveedores (Excel)", 20))),
            new DefVista("auditoria", "Auditoría", "Registro de acciones del sistema.",
                    "GENERAL", null, null, 50, Permiso.AUDITORIA_READ,
                    List.of(accion(Permiso.AUDITORIA_READ, "Consultar", 10))));
}
