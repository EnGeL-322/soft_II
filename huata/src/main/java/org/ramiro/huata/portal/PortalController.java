package org.ramiro.huata.portal;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal público (sin autenticación): permite a un proveedor consultar su propia calidad con solo
 * su código, igual que "Calidad visible para la comunidad" del prototipo HTML. No expone datos
 * personales (documento, teléfono) ni de otros proveedores: solo código, zona y su historial de
 * calidad.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PortalController {

    private final ProductorRepository productorRepository;
    private final ControlCalidadRepository controlCalidadRepository;

    public record HistorialPublico(String fecha, String resultado, Double litros) {}

    public record ConsultaPublica(String codigo, String zona, String estado, List<HistorialPublico> historial) {}

    @GetMapping("/productores/{codigo}/calidad")
    public ConsultaPublica consultar(@PathVariable String codigo) {
        Productor productor = productorRepository
                .findByCodigoIgnoreCase(codigo)
                .filter(Productor::isActivo)
                .orElseThrow(() -> new NotFoundException("No se encontró el código " + codigo));

        List<HistorialPublico> historial = controlCalidadRepository
                .findByRecojo_ProductorIdOrderByCreatedAtDesc(productor.getId())
                .stream()
                .limit(10)
                .map(c -> new HistorialPublico(
                        c.getCreatedAt() != null ? c.getCreatedAt().toString() : null,
                        c.getResultado().name(),
                        c.getRecojo().getLitros()))
                .toList();

        String zona = productor.getRuta() == null
                ? null
                : (productor.getRuta().getZona() != null && !productor.getRuta().getZona().isBlank()
                        ? productor.getRuta().getZona()
                        : productor.getRuta().getNombre());

        return new ConsultaPublica(productor.getCodigo(), zona, productor.getEstado().name(), historial);
    }
}
