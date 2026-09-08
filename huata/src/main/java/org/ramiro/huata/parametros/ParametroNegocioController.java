package org.ramiro.huata.parametros;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parametros-negocio")
@RequiredArgsConstructor
public class ParametroNegocioController {

    private final ParametroNegocioService service;

    @GetMapping
    public List<ParametroNegocioResponse> listar() {
        return service.listar().stream().map(ParametroNegocioResponse::from).toList();
    }

    @PostMapping
    public ParametroNegocioResponse guardar(@Valid @RequestBody ParametroNegocioRequest request) {
        return ParametroNegocioResponse.from(service.guardar(request));
    }
}
