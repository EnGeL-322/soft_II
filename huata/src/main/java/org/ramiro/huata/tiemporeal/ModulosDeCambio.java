package org.ramiro.huata.tiemporeal;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.ramiro.huata.usuarios.Permiso;

/**
 * A que modulo pertenece cada entidad y quien puede enterarse de que cambio.
 *
 * <p>Se decide por el paquete de la entidad: el proyecto ya esta organizado por modulo de negocio,
 * asi que una entidad nueva queda cubierta sin tocar esta clase. Un paquete que no figura aqui no se
 * difunde (fallo cerrado): es preferible que una pantalla tarde en enterarse a avisar a quien no debe.
 *
 * <p>Los permisos son de LECTURA y basta con uno. Son los mismos que protegen los endpoints REST del
 * modulo: el aviso solo dice "cambio algo en pagos"; los datos los vuelve a pedir la pantalla, y la
 * API vuelve a validar el permiso.
 */
public final class ModulosDeCambio {

    /** Un modulo del canal: el nombre que reciben los clientes y los permisos que lo dejan ver. */
    public record Modulo(String nombre, Set<String> permisos) {}

    private static final Map<String, Modulo> POR_PAQUETE = Map.ofEntries(
            Map.entry("acopiadores", new Modulo("acopiadores", Set.of(Permiso.ACOPIADORES_READ))),
            Map.entry("calidad", new Modulo("calidad", Set.of(Permiso.CALIDAD_READ, Permiso.CALIDAD_APROBAR))),
            Map.entry("ingesta", new Modulo("calidad", Set.of(Permiso.CALIDAD_READ))),
            Map.entry("compras", new Modulo("compras", Set.of(Permiso.COMPRAS_READ))),
            Map.entry("contabilidad", new Modulo("contabilidad", Set.of(Permiso.CONTABILIDAD_READ))),
            Map.entry("inventario", new Modulo("inventario", Set.of(Permiso.INVENTARIO_READ))),
            Map.entry("liquidaciones", new Modulo("pagos", Set.of(Permiso.PAGOS_READ))),
            Map.entry("parametros", new Modulo("parametros", Set.of(Permiso.PARAMETROS_READ))),
            Map.entry("produccion", new Modulo("produccion",
                    Set.of(Permiso.PRODUCCION_READ, Permiso.RECETAS_READ, Permiso.QUESOS_READ))),
            Map.entry("productores", new Modulo("proveedores", Set.of(Permiso.PROVEEDORES_READ))),
            Map.entry("quesos", new Modulo("ventas", Set.of(Permiso.QUESOS_READ, Permiso.PRODUCCION_READ))),
            Map.entry("recojo", new Modulo("acopio", Set.of(Permiso.ACOPIO_READ))),
            Map.entry("reportes", new Modulo("acopio", Set.of(Permiso.ACOPIO_READ, Permiso.REPORTES_READ))),
            Map.entry("rutas", new Modulo("rutas", Set.of(Permiso.RUTAS_READ, Permiso.ZONAS_READ))),
            Map.entry("usuarios", new Modulo("usuarios", Set.of(Permiso.USUARIOS_READ))));

    /**
     * Entidades que no se difunden aunque su paquete si:
     * <ul>
     *   <li>los puntos GPS del trazado: llegan cada ~10 m y ya tienen su canal (ubicaciones/puntos);
     *   <li>la mensajeria: tiene su propio canal con entrega y leido.
     * </ul>
     * Auditoria, codigos de verificacion y notificaciones no estan en {@link #POR_PAQUETE}.
     */
    private static final Set<String> EXCLUIDAS = Set.of("TrazadoAcopiadorPunto", "UbicacionGps");

    private ModulosDeCambio() {}

    public static Optional<Modulo> de(Class<?> entidad) {
        if (EXCLUIDAS.contains(entidad.getSimpleName())) return Optional.empty();
        String paquete = entidad.getPackageName();
        String ultimo = paquete.substring(paquete.lastIndexOf('.') + 1);
        if (!paquete.startsWith("org.ramiro.huata.")) return Optional.empty();
        return Optional.ofNullable(POR_PAQUETE.get(ultimo));
    }
}
