package org.ramiro.huata.reportes;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.liquidaciones.LiquidacionRepository;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.quesos.QuesoRepository;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReporteEventosService {
    private final RecojoRepository recojos;
    private final ControlCalidadRepository controles;
    private final LiquidacionRepository liquidaciones;
    private final QuesoRepository quesos;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    public record Evento(String id, LocalDate fecha, String tipo, String referencia, String codigo,
            String zona, Double litros, Double monto, String estado, String detalle) {}

    private String nombre(Productor p) { return p.getNombres() + " " + p.getApellidos(); }
    private String zona(Productor p) { return p.getRuta() == null ? "Sin zona" : p.getRuta().getZona(); }

    @Transactional(readOnly = true)
    public List<Evento> listar(LocalDate desde, LocalDate hasta) {
        var fin = hasta == null ? LocalDate.now(LIMA) : hasta;
        var inicio = desde == null ? fin.minusDays(29) : desde;
        if (inicio.isAfter(fin)) throw new BusinessRuleException("La fecha inicial debe ser anterior o igual a la final.");
        var eventos = new ArrayList<Evento>();
        recojos.findAll().stream().filter(r -> r.isActivo()).forEach(r -> eventos.add(new Evento(
                "recepcion-" + r.getId(), r.getFechaHora().atZone(LIMA).toLocalDate(), "Recepción",
                nombre(r.getProductor()), r.getProductor().getCodigo(),
                r.getRuta() == null ? zona(r.getProductor()) : r.getRuta().getZona(), r.getLitros(), null,
                r.getEstado().name(), "Recepción #" + r.getId())));
        controles.findAll().stream().filter(c -> c.isActivo()).forEach(c -> {
            var r = c.getRecojo();
            eventos.add(new Evento("calidad-" + c.getId(), r.getFechaHora().atZone(LIMA).toLocalDate(), "Calidad",
                    nombre(r.getProductor()), r.getProductor().getCodigo(),
                    r.getRuta() == null ? zona(r.getProductor()) : r.getRuta().getZona(), null, null,
                    c.getResultado().name(), c.getMotivoRechazo() != null ? c.getMotivoRechazo() : c.getRevisionVisual()));
        });
        liquidaciones.findAll().stream().filter(l -> l.isActivo()).forEach(l -> eventos.add(new Evento(
                "pago-" + l.getId(), l.getPeriodoFin(), "Pago", nombre(l.getProductor()), l.getProductor().getCodigo(),
                zona(l.getProductor()), null, l.getMontoTotal(), l.getEstado().name(),
                "Liquidación " + l.getPeriodoInicio() + " — " + l.getPeriodoFin())));
        quesos.findAll().stream().filter(q -> q.isActivo()).forEach(q -> eventos.add(new Evento(
                "queso-" + q.getId(), q.getFecha(), "Quesos", q.getComprador() == null ? q.getFuente() : q.getComprador(),
                q.getNumeroBoleta(), "—", null, q.getTotalVenta(), q.getVendidosKg() > 0 ? "Venta" : "Producción",
                "Producido: " + q.getProducidosKg() + " kg · Vendido: " + q.getVendidosKg() + " kg")));
        return eventos.stream().filter(e -> !e.fecha().isBefore(inicio) && !e.fecha().isAfter(fin))
                .sorted(Comparator.comparing(Evento::fecha).reversed().thenComparing(Evento::id)).toList();
    }
}
