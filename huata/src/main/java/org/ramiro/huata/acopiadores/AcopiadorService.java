package org.ramiro.huata.acopiadores;

import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.common.SearchSpecifications;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AcopiadorService {

    private final AcopiadorRepository acopiadorRepository;
    private final UsuarioRepository usuarioRepository;

    public PageResponse<Acopiador> buscar(String q, Pageable pageable) {
        Specification<Acopiador> spec = SearchSpecifications
                .<Acopiador>textSearch(q, "nombres", "apellidos", "documentoIdentidad")
                .and(SearchSpecifications.onlyActive());
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
        return acopiadorRepository.save(acopiador);
    }

    @Transactional
    public Acopiador actualizar(Long id, AcopiadorRequest req) {
        Acopiador acopiador = obtener(id);
        aplicarDatos(acopiador, req);
        return acopiadorRepository.save(acopiador);
    }

    @Transactional
    public void inactivar(Long id) {
        Acopiador acopiador = obtener(id);
        acopiador.setActivo(false);
        acopiadorRepository.save(acopiador);
    }

    private void aplicarDatos(Acopiador acopiador, AcopiadorRequest req) {
        acopiador.setNombres(req.nombres());
        acopiador.setApellidos(req.apellidos());
        acopiador.setDocumentoIdentidad(req.documentoIdentidad());
        acopiador.setTelefono(req.telefono());
        if (req.usuarioId() != null) {
            Usuario usuario = usuarioRepository
                    .findById(req.usuarioId())
                    .orElseThrow(() -> NotFoundException.of("Usuario", req.usuarioId()));
            acopiador.setUsuario(usuario);
        } else {
            acopiador.setUsuario(null);
        }
    }
}
