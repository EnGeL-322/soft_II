package org.ramiro.huata.acopiadores;

import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.common.SearchSpecifications;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.DatosPersonales;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.Rol;
import org.ramiro.huata.usuarios.UsuarioService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AcopiadorService {

    private final AcopiadorRepository acopiadorRepository;
    private final UsuarioService usuarioService;
    private final RutaRepository rutaRepository;
    private final ProductorRepository productorRepository;
    private final AuditoriaService auditoriaService;

    /** Filtros del listado de Usuarios (pestana Acopiadores). Todo es opcional. */
    public record Filtro(String q, Long rutaId, String zona) {}

    public PageResponse<Acopiador> buscar(Filtro filtro, Pageable pageable) {
        Specification<Acopiador> spec = SearchSpecifications
                .<Acopiador>buscarPalabras(filtro.q(), "nombres", "apellidos", "documentoIdentidad", "telefono",
                        "ruta.nombre", "ruta.zona", "usuario.username")
                .and(SearchSpecifications.onlyActive())
                .and(SearchSpecifications.equalsPathIfPresent(filtro.rutaId(), "ruta", "id"))
                .and(SearchSpecifications.equalsIgnoreCaseIfPresent(filtro.zona(), "ruta", "zona"));
        return PageResponse.from(acopiadorRepository.findAll(spec, pageable));
    }

    public Acopiador obtener(Long id) {
        return acopiadorRepository
                .findById(id)
                .filter(Acopiador::isActivo)
                .orElseThrow(() -> NotFoundException.of("Acopiador", id));
    }

    @Transactional
    public Acopiador crear(AcopiadorRequest req) {
        Acopiador acopiador = new Acopiador();
        aplicarDatos(acopiador, req);
        sincronizarCuenta(acopiador, req);
        return acopiadorRepository.save(acopiador);
    }

    @Transactional
    public Acopiador actualizar(Long id, AcopiadorRequest req) {
        Acopiador acopiador = obtener(id);
        aplicarDatos(acopiador, req);
        sincronizarCuenta(acopiador, req);
        return acopiadorRepository.save(acopiador);
    }

    /**
     * Baja logica: deja de listarse y su cuenta ya no entra a la web ni a la app, pero sus recojos y
     * liquidaciones siguen apuntando a el (borrarlo de verdad romperia ese historial). Los proveedores
     * que lo tenian como responsable directo pasan a "cualquier acopiador de su ruta": si siguieran
     * apuntando a alguien dado de baja, nadie los tendria asignados en la app.
     */
    @Transactional
    public void inactivar(Long id) {
        Acopiador acopiador = obtener(id);
        acopiador.setActivo(false);
        if (acopiador.getUsuario() != null) {
            usuarioService.inactivar(acopiador.getUsuario().getId());
        }
        var proveedores = productorRepository.findByAcopiadorResponsable_IdAndActivoTrue(id);
        proveedores.forEach(p -> p.setAcopiadorResponsable(null));
        productorRepository.saveAll(proveedores);
        acopiadorRepository.save(acopiador);
        auditoriaService.registrar("Baja de acopiador", "Acopiadores",
                acopiador.getNombres() + " " + acopiador.getApellidos()
                        + (proveedores.isEmpty() ? "" : " · " + proveedores.size() + " proveedor(es) sin responsable directo"));
    }

    /** El acopiador de la app movil, resuelto desde su login. */
    public Acopiador obtenerPorUsuario(String username) {
        return acopiadorRepository
                .findByUsuario_UsernameIgnoreCase(username)
                .filter(Acopiador::isActivo)
                .orElseThrow(() -> NotFoundException.of("Acopiador para el usuario", username));
    }

    private void aplicarDatos(Acopiador acopiador, AcopiadorRequest req) {
        String dni = req.documentoIdentidad() == null ? "" : req.documentoIdentidad().trim();
        // El DNI es opcional, pero si viene tiene que ser valido. Red de seguridad por si alguien llama
        // al servicio sin pasar por la validacion del controlador.
        if (!dni.isEmpty() && !DatosPersonales.esDniValido(dni)) {
            throw new BusinessRuleException(DatosPersonales.DNI_MENSAJE);
        }
        acopiador.setNombres(req.nombres().trim());
        acopiador.setApellidos(req.apellidos().trim());
        acopiador.setDocumentoIdentidad(dni.isEmpty() ? null : dni);
        acopiador.setTelefono(req.telefono() == null || req.telefono().isBlank() ? null : req.telefono().trim());
        if (req.rutaId() == null) {
            throw new BusinessRuleException("Selecciona la zona y ruta donde trabajara el acopiador");
        }
        Ruta ruta = rutaRepository
                .findById(req.rutaId())
                .filter(Ruta::isActivo)
                .orElseThrow(() -> NotFoundException.of("Ruta", req.rutaId()));
        if (ruta.getZona() == null || ruta.getZona().isBlank()) {
            throw new BusinessRuleException("La ruta seleccionada no tiene una zona configurada");
        }
        acopiador.setRuta(ruta);
    }

    private void sincronizarCuenta(Acopiador acopiador, AcopiadorRequest req) {
        Usuario usuario = usuarioService.sincronizarCuentaDeAcceso(
                acopiador.getUsuario(),
                req.nombres().trim() + " " + req.apellidos().trim(),
                req.username(),
                req.telefono(),
                req.password(),
                Rol.ACOPIADOR);
        acopiador.setUsuario(usuario);
    }
}
