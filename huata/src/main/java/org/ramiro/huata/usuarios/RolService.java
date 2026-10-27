package org.ramiro.huata.usuarios;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roles como plantillas editables de permisos (V46). Lo que no se puede hacer, y por que:
 * <ul>
 *   <li>cambiar el codigo de un rol: la app movil y los paneles lo usan como identidad;</li>
 *   <li>eliminar un rol predefinido: se puede ajustar y restaurar, no perder;</li>
 *   <li>editar los permisos de PROVEEDOR: es una cuenta externa (ver {@link Rol#PERMISOS_BLOQUEADOS});</li>
 *   <li>dejar el sistema sin nadie que administre usuarios (ver {@link ReglasDeAcceso}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RolService {

    /** Los roles personalizados se listan despues de los predefinidos. */
    private static final int ORDEN_PERSONALIZADOS = 500;

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final VistaRepository vistaRepository;
    private final ReglasDeAcceso reglas;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<RolResponse> listar() {
        return rolRepository.findByActivoTrue().stream()
                .sorted(Comparator.comparing((Rol r) -> r.getOrden() == null ? Integer.MAX_VALUE : r.getOrden())
                        .thenComparing(Rol::getEtiqueta, String.CASE_INSENSITIVE_ORDER))
                .map(r -> RolResponse.from(r, usuarioRepository.countByActivoTrueAndRoles_Id(r.getId())))
                .toList();
    }

    /** Codigos de los roles que se pueden asignar desde "Usuarios y roles". */
    public List<String> asignablesEnUsuarios() {
        return listar().stream()
                .filter(r -> !r.gestionadoEnSuModulo())
                .map(RolResponse::nombre)
                .toList();
    }

    public Rol obtener(Long id) {
        return rolRepository.findById(id).filter(Rol::isActivo).orElseThrow(() -> NotFoundException.of("Rol", id));
    }

    @Transactional
    public RolResponse crear(RolRequest req) {
        String etiqueta = req.etiqueta().trim();
        validarEtiquetaLibre(etiqueta, null);
        Rol rol = new Rol(generarCodigo(etiqueta), etiqueta, limpiar(req.descripcion()), null);
        rol.setSistema(false);
        rol.setOrden(ORDEN_PERSONALIZADOS);
        rol.setPermisos(reglas.resolver(req.permisos()));
        Rol guardado = rolRepository.save(rol);
        auditoriaService.registrar("Alta de rol", "Roles", resumen(guardado));
        return RolResponse.from(guardado, 0);
    }

    @Transactional
    public RolResponse actualizar(Long id, RolRequest req) {
        Rol rol = obtener(id);
        String etiqueta = req.etiqueta().trim();
        validarEtiquetaLibre(etiqueta, rol.getId());
        rol.setEtiqueta(etiqueta);
        rol.setDescripcion(limpiar(req.descripcion()));
        if (req.permisos() != null) {
            Set<String> pedidos = new TreeSet<>(req.permisos());
            if (rol.isPermisosBloqueados() && !pedidos.equals(codigos(rol))) {
                throw new BusinessRuleException("Los permisos del rol " + rol.getEtiqueta()
                        + " no se editan: es una cuenta externa y veria datos de otros proveedores");
            }
            cambiarPermisos(rol, pedidos);
        }
        Rol guardado = rolRepository.save(rol);
        auditoriaService.registrar("Edicion de rol", "Roles", resumen(guardado));
        return RolResponse.from(guardado, usuarioRepository.countByActivoTrueAndRoles_Id(id));
    }

    /** Vuelve un rol predefinido a su nombre, descripcion y permisos estandar (MatrizPermisos). */
    @Transactional
    public RolResponse restaurarEstandar(Long id) {
        Rol rol = obtener(id);
        MatrizPermisos.DefinicionRol definicion = MatrizPermisos.definicion(rol.getNombre())
                .filter(d -> rol.isSistema())
                .orElseThrow(() -> new BusinessRuleException("Solo los roles predefinidos tienen una configuracion estandar"));
        rol.setEtiqueta(definicion.etiqueta());
        rol.setDescripcion(definicion.descripcion());
        rol.setAccesos(definicion.accesos());
        cambiarPermisos(rol, new TreeSet<>(MatrizPermisos.POR_ROL.getOrDefault(rol.getNombre(), Set.of())));
        Rol guardado = rolRepository.save(rol);
        auditoriaService.registrar("Restauracion de rol", "Roles", resumen(guardado));
        return RolResponse.from(guardado, usuarioRepository.countByActivoTrueAndRoles_Id(id));
    }

    /** Baja logica de un rol personalizado sin usuarios activos. */
    @Transactional
    public void eliminar(Long id) {
        Rol rol = obtener(id);
        if (rol.isSistema()) {
            throw new BusinessRuleException("Los roles predefinidos no se eliminan; puedes ajustar sus permisos o restaurarlos");
        }
        long usuarios = usuarioRepository.countByActivoTrueAndRoles_Id(id);
        if (usuarios > 0) {
            throw new BusinessRuleException("El rol tiene " + usuarios
                    + " usuario(s) activo(s); asígnales otro rol antes de eliminarlo");
        }
        rol.setActivo(false);
        rolRepository.save(rol);
        auditoriaService.registrar("Baja de rol", "Roles", rol.getNombre());
    }

    /** Modulos -> vistas -> acciones, en el orden del menu. */
    @Transactional(readOnly = true)
    public List<ModuloAccesoResponse> catalogo() {
        List<Vista> vistas = vistaRepository.findByActivoTrueOrderByOrdenAsc().stream()
                .filter(v -> v.getModulo().isActivo())
                .toList();
        Map<String, List<String>> vistasPorPermiso = new HashMap<>();
        vistas.forEach(v -> v.getAcciones().forEach(a -> vistasPorPermiso
                .computeIfAbsent(a.getPermiso().getCodigo(), k -> new ArrayList<>())
                .add(v.getNombre())));

        Map<Modulo, List<Vista>> porModulo = vistas.stream()
                .sorted(Comparator.comparingInt((Vista v) -> v.getModulo().getOrden()).thenComparingInt(Vista::getOrden))
                .collect(Collectors.groupingBy(Vista::getModulo, LinkedHashMap::new, Collectors.toList()));
        return porModulo.entrySet().stream()
                .map(e -> new ModuloAccesoResponse(
                        e.getKey().getCodigo(),
                        e.getKey().getNombre(),
                        e.getValue().stream().map(v -> aRespuesta(v, vistasPorPermiso)).toList()))
                .toList();
    }

    private static VistaAccesoResponse aRespuesta(Vista v, Map<String, List<String>> vistasPorPermiso) {
        return new VistaAccesoResponse(
                v.getCodigo(),
                v.getNombre(),
                v.getDescripcion(),
                v.getRuta(),
                v.getIcono(),
                v.getPermisoAcceso().getCodigo(),
                v.getAcciones().stream()
                        .map(a -> new AccionAccesoResponse(
                                a.getPermiso().getCodigo(),
                                a.getEtiqueta(),
                                a.getPermiso().getDescripcion(),
                                vistasPorPermiso.getOrDefault(a.getPermiso().getCodigo(), List.of()).stream()
                                        .filter(nombre -> !nombre.equals(v.getNombre()))
                                        .toList()))
                        .toList());
    }

    private void cambiarPermisos(Rol rol, Set<String> codigos) {
        boolean concediaAdministrar = codigos(rol).contains(Permiso.USUARIOS_WRITE);
        rol.getPermisos().clear();
        rol.getPermisos().addAll(reglas.resolver(codigos));
        if (concediaAdministrar && !codigos.contains(Permiso.USUARIOS_WRITE)) {
            rolRepository.saveAndFlush(rol);
            reglas.exigirQueAlguienAdministreUsuarios();
        }
    }

    private void validarEtiquetaLibre(String etiqueta, Long propio) {
        boolean repetida = rolRepository.findByActivoTrue().stream()
                .anyMatch(r -> !r.getId().equals(propio) && r.getEtiqueta().equalsIgnoreCase(etiqueta));
        if (repetida) {
            throw new BusinessRuleException("Ya existe un rol llamado " + etiqueta);
        }
    }

    /** "Jefe de turno" -> "JEFE_DE_TURNO"; si ya existe (incluso dado de baja), "JEFE_DE_TURNO_2". */
    String generarCodigo(String etiqueta) {
        String base = Normalizer.normalize(etiqueta, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "ROL";
        }
        if (base.length() > 34) {
            base = base.substring(0, 34);
        }
        String codigo = base;
        for (int i = 2; rolRepository.existsByNombre(codigo); i++) {
            codigo = base + "_" + i;
        }
        return codigo;
    }

    private static Set<String> codigos(Rol rol) {
        return rol.getPermisos().stream().map(Permiso::getCodigo).collect(Collectors.toCollection(TreeSet::new));
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static String resumen(Rol rol) {
        String detalle = rol.getNombre() + " permisos=[" + String.join(",", codigos(rol)) + "]";
        return detalle.length() > 500 ? detalle.substring(0, 497) + "..." : detalle;
    }
}
