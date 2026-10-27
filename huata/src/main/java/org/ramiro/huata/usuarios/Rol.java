package org.ramiro.huata.usuarios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Rol de negocio: una plantilla de permisos con nombre. Se modela como tabla (no enum fijo) para
 * poder crear roles nuevos y ajustar los existentes desde "Usuarios y roles" sin desplegar codigo.
 *
 * <p>Hay dos clases de rol:
 * <ul>
 *   <li><b>Predefinidos</b> ({@link #sistema} = true): los doce perfiles del cliente (contexto maestro
 *       2026-09-15) mas SECRETARIA, que ya tenia cuentas. No se eliminan; sus permisos se pueden
 *       ajustar y volver al estandar de {@link MatrizPermisos}.</li>
 *   <li><b>Personalizados</b>: los crea el administrador. Se dan de baja logica.</li>
 * </ul>
 *
 * <p>El rol por si solo no autoriza nada: lo que autoriza es el conjunto de {@link Permiso} que
 * tiene asociado. Las constantes de abajo existen para las pocas reglas que dependen del rol como
 * identidad (la app movil elige su experiencia por rol, los paneles de inicio), no para escribir
 * hasRole(...) en controllers.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Rol extends BaseEntity {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String SUPERVISOR_PLANTA = "SUPERVISOR_PLANTA";
    public static final String OPERADOR_PLANTA = "OPERADOR_PLANTA";
    public static final String CALIDAD = "CALIDAD";
    public static final String TESTEADOR = "TESTEADOR";
    public static final String CAJA_CONTABILIDAD = "CAJA_CONTABILIDAD";
    public static final String SECRETARIA = "SECRETARIA";
    public static final String ACOPIADOR = "ACOPIADOR";
    public static final String PROVEEDOR = "PROVEEDOR";
    // V13: rol del CLAUDE.md para la produccion; desde V46 se muestra como "Produccion".
    public static final String PRODUCTOR_QUESO = "PRODUCTOR_QUESO";
    public static final String COMPRAS_ALMACEN = "COMPRAS_ALMACEN";
    public static final String VENTAS = "VENTAS";
    public static final String GERENCIA = "GERENCIA";

    /**
     * Roles que NO se asignan desde "Usuarios y roles" sino al dar de alta el registro en su propio
     * modulo: un acopiador se crea en Acopiadores (donde ademas se elige la zona y la ruta en la que
     * acopiara) y un proveedor en Proveedores.
     *
     * <p>El motivo no es de interfaz: la cuenta de estos dos roles no tiene sentido por separado del
     * registro de negocio. Un usuario con rol ACOPIADOR pero sin fila en {@code acopiadores} no sabe
     * a que proveedores visitar —{@code GET /acopiadores/me} le responde 422— y la app movil se
     * queda en blanco. Creandolos siempre desde su modulo, cuenta y registro nacen juntos.
     */
    public static final List<String> GESTIONADOS_EN_SU_MODULO = List.of(ACOPIADOR, PROVEEDOR);

    /**
     * Roles cuyos permisos no se editan desde la aplicacion. PROVEEDOR es una cuenta externa: con
     * cualquier permiso de modulo interno (acopio.read, pagos.read...) veria datos de los demas
     * proveedores, que es justo lo que CLAUDE.md seccion 10 prohibe.
     */
    public static final List<String> PERMISOS_BLOQUEADOS = List.of(PROVEEDOR);

    /** Codigo inmutable ("VENTAS"). La app movil y los paneles lo usan como identidad. */
    @Column(nullable = false, unique = true, length = 40)
    private String nombre;

    /** Nombre visible y editable ("Ventas"). */
    @Column(nullable = false, length = 80)
    private String etiqueta;

    @Column(length = 200)
    private String descripcion;

    /**
     * Etiqueta corta de presentacion (rol / responsabilidad / acceso). NO es una regla: quien decide
     * el acceso es {@link #permisos}.
     */
    @Column(length = 200)
    private String accesos;

    /** Predefinido: no se elimina y se puede restaurar a su configuracion estandar. */
    @Column(nullable = false)
    private boolean sistema;

    private Integer orden;

    /**
     * EAGER a proposito: SecurityUserDetailsService necesita los permisos en cada peticion para
     * construir las autoridades, y la sesion de Hibernate ya esta cerrada cuando Spring Security
     * los lee. Con una docena de roles y 40 permisos el coste es despreciable.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "rol_permisos",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<Permiso> permisos = new LinkedHashSet<>();

    public Rol(String nombre, String etiqueta, String descripcion, String accesos) {
        this.nombre = nombre;
        this.etiqueta = etiqueta;
        this.descripcion = descripcion;
        this.accesos = accesos;
    }

    public boolean isGestionadoEnSuModulo() {
        return GESTIONADOS_EN_SU_MODULO.contains(nombre);
    }

    public boolean isPermisosBloqueados() {
        return PERMISOS_BLOQUEADOS.contains(nombre);
    }
}
