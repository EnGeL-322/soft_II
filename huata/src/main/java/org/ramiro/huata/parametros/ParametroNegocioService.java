package org.ramiro.huata.parametros;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParametroNegocioService {

    private final ParametroNegocioRepository repository;

    public List<ParametroNegocio> listar() {
        return repository.findAll();
    }

    @Transactional
    public ParametroNegocio guardar(ParametroNegocioRequest req) {
        ParametroNegocio parametro = repository.findByClave(req.clave()).orElseGet(ParametroNegocio::new);
        parametro.setClave(req.clave());
        parametro.setValor(req.valor());
        parametro.setDescripcion(req.descripcion());
        return repository.save(parametro);
    }
}
