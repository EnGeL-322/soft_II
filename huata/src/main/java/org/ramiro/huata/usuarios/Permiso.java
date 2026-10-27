package org.ramiro.huata.usuarios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Permiso por recurso y accion ("acopio.write"). Es la unidad de autorizacion del sistema: los
 * controllers se anotan con hasAuthority(codigo) y el frontend arma su menu con la misma lista, de
 * modo que no existen dos mecanismos de acceso que se puedan desincronizar (DESARROLLO.md 8.1).
 *
 * <p>La mayoria son read/write sobre un recurso. Unos pocos son <b>permisos de operacion</b>
 * ({@link #ACOPIO_CERRAR_JORNADA}, {@link #PAGOS_CERRAR_CICLO}): conceden una accion concreta sin
 * conceder la escritura del modulo entero, para poder dar a alguien la potestad de cerrar un
 * periodo sin darle la de registrar movimientos dentro de el.
 *
 * <p>El catalogo lo siembra la migracion V8 y no se edita desde la aplicacion: anadir un permiso
 * implica anotar un endpoint nuevo, asi que va junto al codigo. Lo que si es dato es la asignacion
 * rol -> permiso (tabla rol_permisos), pensada para poder ajustar responsabilidades sin desplegar.
 */
@Entity
@Table(name = "permisos")
@Getter
@Setter
@NoArgsConstructor
public class Permiso {

    public static final String PANEL_READ = "panel.read";
    public static final String ACOPIO_READ = "acopio.read";
    public static final String ACOPIO_WRITE = "acopio.write";
    /** Accion, no escritura del modulo: cerrar la jornada no permite registrar una recepcion. */
    public static final String ACOPIO_CERRAR_JORNADA = "acopio.cerrar-jornada";
    /**
     * V32: registrar los litros que llegan a planta de cada ruta. Separado de acopio.write porque el
     * acopiador (que tiene acopio.write) no puede ser quien anota cuanto recibio planta de SU propia
     * ruta: la conciliacion existe justamente para controlarlo. Y desde V31 ese numero es lo que
     * entra al inventario como leche.
     */
    public static final String PLANTA_RECEPCION = "planta.recepcion";
    public static final String CALIDAD_READ = "calidad.read";
    public static final String CALIDAD_WRITE = "calidad.write";
    /**
     * V48/V49: aprobar desde la app movil los analisis de calidad. Lo tiene la testeadora y se puede
     * conceder a cualquier otro rol o usuario desde Usuarios; la web solo consulta lo pendiente.
     */
    public static final String CALIDAD_APROBAR = "calidad.aprobar";
    public static final String RUTAS_READ = "rutas.read";
    public static final String RUTAS_WRITE = "rutas.write";
    public static final String PROVEEDORES_READ = "proveedores.read";
    public static final String PROVEEDORES_WRITE = "proveedores.write";
    public static final String ACOPIADORES_READ = "acopiadores.read";
    public static final String ACOPIADORES_WRITE = "acopiadores.write";
    public static final String PAGOS_READ = "pagos.read";
    public static final String PAGOS_WRITE = "pagos.write";
    /** Accion, no escritura del modulo: cerrar el ciclo no permite ejecutar ni ajustar un pago. */
    public static final String PAGOS_CERRAR_CICLO = "pagos.cerrar-ciclo";
    /**
     * Reabrir una liquidacion ya CERRADA. Separado de pagos.cerrar-ciclo a proposito: cerrar un
     * ciclo es una operacion rutinaria semanal, reabrir un pago ya entregado es una excepcion que
     * ademas exige un codigo de verificacion por correo (ver VerificacionService); no todo el que
     * cierra ciclos deberia poder deshacerlo sin ese control aparte.
     */
    public static final String PAGOS_REABRIR = "pagos.reabrir";
    public static final String QUESOS_READ = "quesos.read";
    /** Desde V54 solo lotes y el registro historico de quesos; vender ya no va por aqui. */
    public static final String QUESOS_WRITE = "quesos.write";
    /**
     * V54: cada operacion de venta es un permiso propio para poder conceder, por rol o por usuario,
     * solo vender, solo cobrar o solo anular. Anular devuelve unidades al stock: no tiene por que
     * tenerlo quien registra.
     */
    public static final String VENTAS_REGISTRAR = "ventas.registrar";
    public static final String VENTAS_COBRAR = "ventas.cobrar";
    public static final String VENTAS_ANULAR = "ventas.anular";
    public static final String ZONAS_READ = "zonas.read";
    public static final String TRAZABILIDAD_READ = "trazabilidad.read";
    public static final String REPORTES_READ = "reportes.read";
    public static final String USUARIOS_READ = "usuarios.read";
    public static final String USUARIOS_WRITE = "usuarios.write";
    public static final String PARAMETROS_READ = "parametros.read";
    public static final String PARAMETROS_WRITE = "parametros.write";
    public static final String AUDITORIA_READ = "auditoria.read";
    public static final String RESPALDO_READ = "respaldo.read";
    public static final String RESPALDO_WRITE = "respaldo.write";
    // V13: produccion, receta separada de produccion para que Productor de queso pueda crear/cerrar
    // lotes sin poder tocar la receta maestra ni sus costos.
    public static final String PRODUCCION_READ = "produccion.read";
    public static final String PRODUCCION_WRITE = "produccion.write";
    public static final String RECETAS_READ = "recetas.read";
    public static final String RECETAS_WRITE = "recetas.write";
    public static final String COMPRAS_READ = "compras.read";
    public static final String COMPRAS_WRITE = "compras.write";
    public static final String INVENTARIO_READ = "inventario.read";
    public static final String INVENTARIO_WRITE = "inventario.write";
    // V21: plan de cuentas, libro diario y libro mayor. Sin .write: los asientos se generan
    // automaticamente desde compras/produccion/venta, no hay registro manual todavia.
    public static final String CONTABILIDAD_READ = "contabilidad.read";
    /** Bandeja de conversaciones entre la planta y sus proveedores. */
    public static final String MENSAJERIA_READ = "mensajeria.read";
    public static final String MENSAJERIA_WRITE = "mensajeria.write";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Forma "<recurso>.<accion>"; es la autoridad que ve Spring Security. */
    @Column(nullable = false, unique = true, length = 60)
    private String codigo;

    @Column(nullable = false, length = 40)
    private String recurso;

    /** 30 y no 10: los permisos de operacion llevan acciones como "cerrar-jornada". */
    @Column(nullable = false, length = 30)
    private String accion;

    @Column(length = 200)
    private String descripcion;
}
