package org.ramiro.huata.ubicaciones;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UbicacionGpsService {

    private final UbicacionGpsRepository ubicacionGpsRepository;
    private final AcopiadorRepository acopiadorRepository;

    @Transactional
    public UbicacionGps registrar(String username, UbicacionRequest req) {
        Acopiador acopiador = acopiadorRepository
                .findByUsuario_UsernameIgnoreCase(username)
                .orElseThrow(() -> new BusinessRuleException(
                        "Tu usuario no está vinculado a un acopiador; no se puede reportar ubicación."));

        UbicacionGps ubicacion = new UbicacionGps();
        ubicacion.setAcopiador(acopiador);
        ubicacion.setLatitud(req.latitud());
        ubicacion.setLongitud(req.longitud());
        ubicacion.setPrecisionMetros(req.precisionMetros());
        ubicacion.setVelocidadMs(req.velocidadMs());
        return ubicacionGpsRepository.save(ubicacion);
    }

    /** Última posición reportada por cada acopiador (sin ventana de tiempo: el front muestra "hace X" para ver si está vigente). */
    public List<UbicacionResponse> ultimas() {
        List<UbicacionGps> todas = ubicacionGpsRepository.findAll(Sort.by(Sort.Direction.DESC, "fecha"));
        Map<Long, UbicacionGps> masReciente = new LinkedHashMap<>();
        for (UbicacionGps u : todas) {
            masReciente.putIfAbsent(u.getAcopiador().getId(), u);
        }
        return masReciente.values().stream().map(UbicacionResponse::from).toList();
    }

    public List<UbicacionResponse> historial(Long acopiadorId, Instant desde, Instant hasta) {
        Instant fin = hasta != null ? hasta : Instant.now();
        Instant inicio = desde != null ? desde : fin.minus(12, ChronoUnit.HOURS);
        return ubicacionGpsRepository.findByAcopiadorIdAndFechaBetweenOrderByFechaAsc(acopiadorId, inicio, fin).stream()
                .map(UbicacionResponse::from)
                .toList();
    }
}
