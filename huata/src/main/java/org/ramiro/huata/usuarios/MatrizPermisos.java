package org.ramiro.huata.usuarios;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Matriz rol -> permisos, en Java.
 *
 * <p><b>Por que existe si la migracion V8 ya la siembra.</b> Hay dos entornos que necesitan estos
 * datos y solo uno de ellos ejecuta Flyway: dev, prod y los tests de integracion con Testcontainers
 * corren las migraciones; el perfil "test" corre sobre H2 con el esquema de Hibernate y Flyway
 * apagado (las migraciones estan en dialecto PostgreSQL). Sin esta clase, en el perfil "test" los
 * roles existirian sin ningun permiso y cualquier prueba de controller recibiria 403.
 *
 * <p><b>La duplicacion es deliberada y esta vigilada.</b> Duplicar un criterio es justo lo que hay
 * que evitar, asi que {@code MigracionesFlywayIT} compara fila por fila lo que siembra V8 contra
 * esta matriz sobre PostgreSQL real: si alguien toca una y no la otra, ese test falla. La
 * alternativa —escribir V8 como migracion Java para tener una sola fuente— haria que el esquema
 * dejara de ser legible como SQL versionado, que es lo que pide CLAUDE.md.
 *
 * <p>Quien manda en un entorno real es la base de datos: la asignacion rol -> permiso es dato
 * editable, precisamente para poder ajustar responsabilidades sin desplegar codigo. Esta matriz es
 * el estado inicial, no una regla en tiempo de ejecucion.
 */
public final class MatrizPermisos {

    private MatrizPermisos() {}

    /** Catalogo completo: codigo -> descripcion. Mismo contenido que el INSERT de permisos en V8. */
    public static final Map<String, String> CATALOGO = Map.ofEntries(
            Map.entry(Permiso.PANEL_READ, "Ver el centro de operacion"),
            Map.entry(Permiso.ACOPIO_READ, "Consultar recepciones y recojos"),
            Map.entry(Permiso.ACOPIO_WRITE, "Registrar y corregir recepciones y recojos"),
            Map.entry(Permiso.ACOPIO_CERRAR_JORNADA, "Cerrar y reabrir la jornada de acopio en planta"),
            Map.entry(Permiso.PLANTA_RECEPCION, "Registrar los litros recibidos en planta de cada ruta"),
            Map.entry(Permiso.CALIDAD_READ, "Consultar controles de calidad e incidencias"),
            Map.entry(Permiso.CALIDAD_WRITE, "Registrar analisis, evidencia e incidencias"),
            Map.entry(Permiso.CALIDAD_APROBAR, "Aprobar desde la app movil los analisis de calidad"),
            Map.entry(Permiso.RUTAS_READ, "Consultar rutas, asignaciones y seguimiento GPS"),
            Map.entry(Permiso.RUTAS_WRITE, "Administrar el catalogo de rutas y asignaciones"),
            Map.entry(Permiso.PROVEEDORES_READ, "Consultar el padron de proveedores"),
            Map.entry(Permiso.PROVEEDORES_WRITE, "Alta, aprobacion e inactivacion de proveedores"),
            Map.entry(Permiso.ACOPIADORES_READ, "Consultar acopiadores"),
            Map.entry(Permiso.ACOPIADORES_WRITE, "Administrar acopiadores"),
            Map.entry(Permiso.PAGOS_READ, "Consultar liquidaciones y comprobantes"),
            Map.entry(Permiso.PAGOS_WRITE, "Generar y ejecutar liquidaciones y pagos"),
            Map.entry(Permiso.PAGOS_CERRAR_CICLO, "Cerrar el ciclo de pago semanal (\"Pagar a todos\")"),
            Map.entry(Permiso.PAGOS_REABRIR, "Reabrir una liquidación ya pagada, con código de verificación"),
            Map.entry(Permiso.QUESOS_READ, "Consultar produccion y venta de quesos"),
            Map.entry(Permiso.QUESOS_WRITE, "Registrar lotes de produccion de quesos"),
            Map.entry(Permiso.VENTAS_REGISTRAR, "Registrar ventas de productos"),
            Map.entry(Permiso.VENTAS_COBRAR, "Marcar ventas como cobradas"),
            Map.entry(Permiso.VENTAS_ANULAR, "Anular ventas y devolver las unidades al stock"),
            Map.entry(Permiso.ZONAS_READ, "Ver zonas y rendimiento"),
            Map.entry(Permiso.TRAZABILIDAD_READ, "Seguimiento de produccion y busqueda global"),
            Map.entry(Permiso.REPORTES_READ, "Generar y consultar reportes"),
            Map.entry(Permiso.USUARIOS_READ, "Consultar usuarios y roles"),
            Map.entry(Permiso.USUARIOS_WRITE, "Crear usuarios y asignar roles"),
            Map.entry(Permiso.PARAMETROS_READ, "Consultar parametros de negocio y calidad"),
            Map.entry(Permiso.PARAMETROS_WRITE, "Modificar parametros de negocio y calidad"),
            Map.entry(Permiso.AUDITORIA_READ, "Consultar el registro de auditoria"),
            Map.entry(Permiso.RESPALDO_READ, "Exportar datos y respaldos"),
            Map.entry(Permiso.RESPALDO_WRITE, "Importar el padron de proveedores desde Excel (alta o actualizacion, nunca reemplazo)"),
            Map.entry(Permiso.PRODUCCION_READ, "Consultar lotes de produccion, consumo y costos"),
            Map.entry(Permiso.PRODUCCION_WRITE, "Crear y cerrar lotes de produccion"),
            Map.entry(Permiso.RECETAS_READ, "Consultar recetas/BOM de queso"),
            Map.entry(Permiso.RECETAS_WRITE, "Crear y versionar recetas/BOM de queso"),
            Map.entry(Permiso.COMPRAS_READ, "Consultar compras de materia prima e insumos"),
            Map.entry(Permiso.COMPRAS_WRITE, "Registrar compras y mantener proveedores de compra"),
            Map.entry(Permiso.INVENTARIO_READ, "Consultar materias primas, stock y kardex"),
            Map.entry(Permiso.INVENTARIO_WRITE, "Mantener el maestro de materias primas y ajustar stock"),
            Map.entry(Permiso.CONTABILIDAD_READ, "Consultar plan de cuentas, libro diario y libro mayor"),
            Map.entry(Permiso.MENSAJERIA_READ, "Consultar conversaciones con proveedores"),
            Map.entry(Permiso.MENSAJERIA_WRITE, "Enviar mensajes a proveedores"));

    /**
     * Rol -> permisos concedidos. PROVEEDOR solo tiene mensajeria (V40): el resto de su superficie
     * son los endpoints "/me" y el portal publico, que no pasan por este modelo. Ningun permiso de
     * modulo interno, o veria datos de otros proveedores.
     *
     * <p>{@code Map.ofEntries} y no {@code Map.of}: son 13 roles y {@code Map.of} solo tiene
     * sobrecargas directas hasta 10 pares clave-valor.
     */
    public static final Map<String, Set<String>> POR_ROL = Map.ofEntries(
            // V52 (decision del cliente, 2026-10-20): el administrador tiene TODOS los permisos del
            // catalogo. Antes no registraba recepciones, calidad, pagos, lotes ni ventas (separacion
            // de funciones) y la consecuencia era que "darle todo" no le daba acceso. La auditoria
            // sigue registrando quien hace cada escritura. Se deriva del catalogo para que un permiso
            // nuevo no quede fuera por olvido.
            Map.entry(Rol.ADMINISTRADOR, Set.copyOf(CATALOGO.keySet())),

            // Analiza muestras. Lee acopio para ubicar la recepcion de la que vino cada muestra.
            // V15: rutas.read para que el testeador movil pueda usar "ruta planificada" ademas de
            // recorrido libre (RF-86) — antes solo el ACOPIADOR podia leer la geometria de la ruta.
            Map.entry(
                    Rol.CALIDAD,
                    Set.of(
                            Permiso.PANEL_READ, Permiso.CALIDAD_READ, Permiso.ACOPIO_READ,
                            // V32: esta en planta recibiendo y analizando la leche de cada ruta.
                            Permiso.PLANTA_RECEPCION,
                            Permiso.PROVEEDORES_READ, Permiso.TRAZABILIDAD_READ, Permiso.REPORTES_READ,
                            Permiso.RUTAS_READ, Permiso.CALIDAD_WRITE,
                            // V48: aprueba los analisis (desde la app desde V49).
                            Permiso.CALIDAD_APROBAR)),

            // Pagos, quesos y compras de insumos. Lee acopio y proveedores porque son la base de la
    // liquidacion.
            Map.entry(
                    Rol.SECRETARIA,
                    Set.of(
                            Permiso.PANEL_READ, Permiso.PAGOS_READ, Permiso.QUESOS_READ, Permiso.ACOPIO_READ,
                            Permiso.PROVEEDORES_READ, Permiso.REPORTES_READ,
                            Permiso.PAGOS_WRITE, Permiso.QUESOS_WRITE, Permiso.PAGOS_CERRAR_CICLO,
                            Permiso.PAGOS_REABRIR, Permiso.CONTABILIDAD_READ,
                            // V54: lo que antes daba quesos.write en la pantalla de ventas.
                            Permiso.VENTAS_REGISTRAR, Permiso.VENTAS_COBRAR, Permiso.VENTAS_ANULAR,
                            // V29: tambien registra las compras de insumos; para eso tiene que ver
                            // las compras y la lista de materias primas.
                            Permiso.COMPRAS_READ, Permiso.COMPRAS_WRITE, Permiso.INVENTARIO_READ,
                            Permiso.MENSAJERIA_READ, Permiso.MENSAJERIA_WRITE)),

            // Recojo en ruta desde la aplicacion. NO cierra la jornada: eso es de planta (regla 7.8)
            // y antes podia hacerlo solo por tener acopio.write.
            Map.entry(
                    Rol.ACOPIADOR,
                    Set.of(
                            Permiso.PANEL_READ, Permiso.ACOPIO_READ, Permiso.RUTAS_READ,
                            Permiso.PROVEEDORES_READ,
                            Permiso.ACOPIO_WRITE)),

            Map.entry(Rol.PROVEEDOR, Set.of(Permiso.MENSAJERIA_READ, Permiso.MENSAJERIA_WRITE)),

            // V13: crea/cierra lotes (produccion.write); NO recetas.write (no toca la receta maestra
            // ni sus costos), NO compras.write, NO cambia precios de venta.
            Map.entry(
                    Rol.PRODUCTOR_QUESO,
                    Set.of(
                            Permiso.PANEL_READ, Permiso.PRODUCCION_READ, Permiso.PRODUCCION_WRITE,
                            Permiso.RECETAS_READ, Permiso.INVENTARIO_READ)),

            // V46: los seis perfiles del cliente que faltaban. Cada conjunto sale de los "must_see"
            // del rol en el contexto maestro 2026-09-15; es el estandar al que vuelve "Restaurar".
            Map.entry(
                    Rol.SUPERVISOR_PLANTA,
                    Set.of(
                            Permiso.PANEL_READ, Permiso.ACOPIO_READ, Permiso.RUTAS_READ, Permiso.ZONAS_READ,
                            Permiso.PROVEEDORES_READ, Permiso.ACOPIADORES_READ, Permiso.CALIDAD_READ,
                            Permiso.TRAZABILIDAD_READ, Permiso.REPORTES_READ, Permiso.PLANTA_RECEPCION,
                            Permiso.CALIDAD_APROBAR)),
            // "Cierre operativo" es suyo: el administrador no debe cerrar como tarea normal.
            Map.entry(
                    Rol.OPERADOR_PLANTA,
                    Set.of(
                            Permiso.PANEL_READ, Permiso.ACOPIO_READ, Permiso.PLANTA_RECEPCION,
                            Permiso.ACOPIO_CERRAR_JORNADA, Permiso.PROVEEDORES_READ, Permiso.TRAZABILIDAD_READ)),
            // Sin pagos.reabrir: reabrir un pago entregado es una excepcion que se concede aparte.
            Map.entry(
                    Rol.CAJA_CONTABILIDAD,
                    Set.of(
                            Permiso.PANEL_READ, Permiso.PAGOS_READ, Permiso.PAGOS_WRITE, Permiso.PAGOS_CERRAR_CICLO,
                            Permiso.ACOPIO_READ, Permiso.PROVEEDORES_READ, Permiso.REPORTES_READ,
                            Permiso.CONTABILIDAD_READ)),
            // Testeo en campo: CALIDAD sin la recepcion de planta ni los reportes.
            Map.entry(
                    Rol.TESTEADOR,
                    Set.of(
                            Permiso.PANEL_READ, Permiso.CALIDAD_READ, Permiso.CALIDAD_WRITE, Permiso.ACOPIO_READ,
                            Permiso.RUTAS_READ, Permiso.PROVEEDORES_READ,
                            // V48: tambien recibe en planta entregas directas con su analisis (app).
                            Permiso.PLANTA_RECEPCION,
                            // V49: aprueba los analisis desde la app.
                            Permiso.CALIDAD_APROBAR)),
            Map.entry(
                    Rol.COMPRAS_ALMACEN,
                    Set.of(
                            Permiso.PANEL_READ, Permiso.COMPRAS_READ, Permiso.COMPRAS_WRITE,
                            Permiso.INVENTARIO_READ, Permiso.INVENTARIO_WRITE)),
            Map.entry(
                    Rol.VENTAS,
                    Set.of(
                            Permiso.PANEL_READ, Permiso.QUESOS_READ, Permiso.QUESOS_WRITE, Permiso.INVENTARIO_READ,
                            Permiso.VENTAS_REGISTRAR, Permiso.VENTAS_COBRAR, Permiso.VENTAS_ANULAR)),
            // Consulta ejecutiva: solo lectura, nunca escritura.
            Map.entry(
                    Rol.GERENCIA,
                    Set.of(
                            Permiso.PANEL_READ, Permiso.ZONAS_READ, Permiso.ACOPIO_READ, Permiso.CALIDAD_READ,
                            Permiso.RUTAS_READ, Permiso.PROVEEDORES_READ, Permiso.PAGOS_READ,
                            Permiso.PRODUCCION_READ, Permiso.INVENTARIO_READ, Permiso.COMPRAS_READ,
                            Permiso.QUESOS_READ, Permiso.CONTABILIDAD_READ, Permiso.TRAZABILIDAD_READ,
                            Permiso.REPORTES_READ)));

    /** Datos de presentacion de un rol predefinido. Mismos textos que siembran V2/V8/V25/V46. */
    public record DefinicionRol(String codigo, String etiqueta, String descripcion, String accesos, int orden) {}

    /** Roles predefinidos en el orden en que se muestran. */
    public static final List<DefinicionRol> ROLES = List.of(
            new DefinicionRol(
                    Rol.ADMINISTRADOR,
                    "Administrador",
                    "Administra el sistema con acceso total: configuracion, datos maestros y todas las"
                            + " operaciones (acopio, calidad, pagos, produccion y ventas).",
                    "Todos los permisos",
                    10),
            new DefinicionRol(
                    Rol.SUPERVISOR_PLANTA,
                    "Supervisor de planta",
                    "Supervisa acopio y planta, concilia volúmenes y revisa incidencias.",
                    "Acopio, rutas, llegada a planta, calidad, trazabilidad y reportes",
                    20),
            new DefinicionRol(
                    Rol.OPERADOR_PLANTA,
                    "Operador de planta",
                    "Registra la llegada a planta y los datos operativos de cierre.",
                    "Recepción en planta y cierre operativo",
                    30),
            new DefinicionRol(
                    Rol.CALIDAD,
                    "Analista de calidad",
                    "Registra densidad, adulteracion, resultado y observaciones; consulta historial.",
                    "Calidad y consulta",
                    40),
            new DefinicionRol(
                    Rol.TESTEADOR,
                    "Testeador",
                    "Ejecuta el testeo de leche en campo, captura el voucher y registra el resultado.",
                    "Testeo de leche en ruta",
                    45),
            new DefinicionRol(
                    Rol.CAJA_CONTABILIDAD,
                    "Caja / Contabilidad",
                    "Liquidaciones semanales, pagos, comprobantes y consulta de proveedores.",
                    "Liquidaciones, pagos y reportes",
                    50),
            new DefinicionRol(
                    Rol.SECRETARIA,
                    "Secretaría y finanzas",
                    "Ejecuta pagos y liquidaciones, y registra produccion y venta de quesos.",
                    "Pagos, quesos y comprobantes",
                    55),
            new DefinicionRol(
                    Rol.ACOPIADOR,
                    "Acopiador / Conductor",
                    "Registra recojos en ruta desde la aplicacion (movil en Fase 2).",
                    "Recojo en ruta",
                    60),
            new DefinicionRol(
                    Rol.PROVEEDOR,
                    "Proveedor de leche",
                    "Consulta sus propias entregas, calidad y liquidaciones desde el portal publico.",
                    "Portal publico",
                    70),
            new DefinicionRol(
                    Rol.PRODUCTOR_QUESO,
                    "Producción",
                    "Ejecuta lotes de produccion segun la receta vigente; no administra precios ni recetas maestras.",
                    "Lotes de produccion",
                    90),
            new DefinicionRol(
                    Rol.COMPRAS_ALMACEN,
                    "Compras / Almacén",
                    "Gestiona compras, existencias y movimientos físicos.",
                    "Compras e inventario",
                    100),
            new DefinicionRol(
                    Rol.VENTAS,
                    "Ventas",
                    "Registra ventas de productos y consulta el stock disponible.",
                    "Ventas y stock",
                    110),
            new DefinicionRol(
                    Rol.GERENCIA,
                    "Gerencia / Consulta",
                    "Consulta ejecutiva de toda la operación, sin registrar operaciones diarias.",
                    "Todo en solo lectura",
                    120));

    public static java.util.Optional<DefinicionRol> definicion(String codigo) {
        return ROLES.stream().filter(d -> d.codigo().equals(codigo)).findFirst();
    }
}
