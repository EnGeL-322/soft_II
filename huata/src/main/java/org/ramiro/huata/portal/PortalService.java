package org.ramiro.huata.portal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.calidad.ControlCalidad;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.IncidenciaCalidad;
import org.ramiro.huata.calidad.IncidenciaCalidadRepository;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.common.DatosPersonales;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.Recojo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta publica de calidad: por codigo de proveedor (historial propio), por zona (RF-48) y las
 * incidencias nombradas del dia (RF-49). La decision de que se publica y que no vive aqui, no en
 * el controlador: el controlador solo habla HTTP (DESARROLLO.md seccion 5.1).
 *
 * <p>PENDIENTE Fase 2: este servicio se expone sin autenticacion, asi que necesita rate limiting y
 * cache antes de salir a produccion (seccion 8.3). Hoy un tercero puede enumerar codigos.
 */
@Service
@RequiredArgsConstructor
public class PortalService {

    /** Cuantas entregas recientes se publican. Suficiente para que el proveedor se reconozca. */
    private static final int MAX_HISTORIAL = 10;

    /**
     * El "dia" del portal es el de la planta, en hora de Lima, igual que la jornada de acopio y el
     * resto del sistema. Antes se cortaba en UTC: lo analizado despues de las 19:00 en Lima salia
     * publicado como del dia siguiente.
     */
    public static final ZoneId ZONA_HORARIA = ZoneId.of("America/Lima");

    private final ProductorRepository productorRepository;
    private final ControlCalidadRepository controlCalidadRepository;
    private final IncidenciaCalidadRepository incidenciaCalidadRepository;

    @Transactional(readOnly = true)
    public ConsultaPublica consultarPorCodigo(String codigo) {
        Productor productor = productorRepository
                .findByCodigoIgnoreCase(codigo)
                .filter(Productor::isActivo)
                .orElseThrow(() -> new NotFoundException("No se encontró el código " + codigo));

        List<HistorialPublico> historial = controlCalidadRepository
                .findByRecojo_ProductorIdOrderByCreatedAtDesc(productor.getId())
                .stream()
                .limit(MAX_HISTORIAL)
                .map(c -> new HistorialPublico(
                        c.getCreatedAt() != null ? c.getCreatedAt().toString() : null,
                        c.getResultado() != null ? c.getResultado().name() : null,
                        c.getRecojo() != null ? c.getRecojo().getLitros() : null))
                .toList();

        // Se devuelve el codigo ENMASCARADO. Desde que el codigo del proveedor es su DNI, devolverlo
        // entero convertiria esta respuesta publica en una confirmacion de documento (seccion 30,
        // RNF-12). Quien consulta ya sabe el codigo que escribio; no necesita que se lo repitamos.
        return new ConsultaPublica(
                DatosPersonales.enmascarar(productor.getCodigo()),
                zonaDe(productor),
                productor.getEstado().name(),
                historial);
    }

    /**
     * Estado de calidad por zona en una fecha (RF-48). Se agrupa por la zona vigente del proveedor
     * (no de la ruta guardada en el recojo): asi una zona efectiva aprobada por RF-07/RF-58 se
     * refleja el mismo dia, sin esperar a que se re-planifique la ruta.
     *
     * <p>Solo aparecen zonas con al menos un control ese dia; el resto no se inventa (regla 0).
     */
    @Transactional(readOnly = true)
    public List<ZonaCalidadPublica> zonasDelDia(LocalDate fecha) {
        Map<String, long[]> porZona = new LinkedHashMap<>(); // [controles, aceptadas, observadas, rechazadas]
        for (ControlCalidad control : controlCalidadRepository.findByRecojo_FechaHoraBetween(inicioDe(fecha), finDe(fecha))) {
            String zona = zonaDe(control.getRecojo());
            if (zona == null) {
                continue;
            }
            long[] fila = porZona.computeIfAbsent(zona, z -> new long[4]);
            fila[0]++;
            switch (control.getResultado()) {
                case ACEPTADA -> fila[1]++;
                case OBSERVADA -> fila[2]++;
                case RECHAZADA -> fila[3]++;
            }
        }
        return porZona.entrySet().stream()
                .map(e -> new ZonaCalidadPublica(
                        e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3], estadoDeZona(e.getValue())))
                .sorted(Comparator.comparing(ZonaCalidadPublica::zona))
                .toList();
    }

    /** ADULTERACION si hubo al menos una RECHAZADA; OBSERVADA si no hubo RECHAZADA pero si OBSERVADA; APTA en el resto. */
    private static String estadoDeZona(long[] conteos) {
        if (conteos[3] > 0) {
            return "ADULTERACION";
        }
        if (conteos[2] > 0) {
            return "OBSERVADA";
        }
        return "APTA";
    }

    /**
     * Proveedores con una incidencia de calidad registrada ese dia (RF-49). Fuente:
     * {@code IncidenciaCalidad}, no cualquier control con resultado distinto de ACEPTADA — ver nota
     * en {@link IncidenciaPublica}. Solo se publica el codigo del proveedor, nunca su nombre
     * completo ni otro dato personal (RNF-12); la exposicion nominal completa sigue sujeta a
     * aprobacion legal/contractual (RNF-19, CLAUDE.md seccion 33).
     */
    @Transactional(readOnly = true)
    public List<IncidenciaPublica> incidenciasDelDia(LocalDate fecha) {
        return incidenciaCalidadRepository.findByFechaBetweenOrderByFechaDesc(inicioDe(fecha), finDe(fecha)).stream()
                .map(this::aIncidenciaPublica)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumenPublicoHoy resumenDelDia(LocalDate fecha) {
        return new ResumenPublicoHoy(fecha, zonasDelDia(fecha), incidenciasDelDia(fecha));
    }

    /**
     * El codigo va <b>enmascarado</b> ({@code ****4040}). Es obligatorio desde que el codigo del
     * proveedor es su DNI: publicar el codigo entero aqui seria publicar el documento de la persona en
     * una pantalla sin login, justo lo que prohiben la seccion 30 y RNF-12. La incidencia sigue siendo
     * identificable para quien ya conoce al proveedor, que es lo que pedia RF-49, sin repartir DNIs.
     */
    private IncidenciaPublica aIncidenciaPublica(IncidenciaCalidad incidencia) {
        Productor productor = incidencia.getProductor();
        return new IncidenciaPublica(
                productor != null ? DatosPersonales.enmascarar(productor.getCodigo()) : null,
                productor != null ? zonaDe(productor) : null,
                incidencia.getTipo(),
                incidencia.getFecha() != null ? incidencia.getFecha().toString() : null);
    }

    /** Zona vigente del proveedor: primero la zona efectiva/principal (RF-07); si no existe, la de su ruta. */
    private String zonaDe(Productor productor) {
        if (productor.getZonaPrincipal() != null && !productor.getZonaPrincipal().isBlank()) {
            return productor.getZonaPrincipal();
        }
        if (productor.getRuta() == null) {
            return null;
        }
        String zona = productor.getRuta().getZona();
        return (zona != null && !zona.isBlank()) ? zona : productor.getRuta().getNombre();
    }

    /** Zona del recojo: la de su proveedor: el recojo no tiene zona propia. */
    private String zonaDe(Recojo recojo) {
        return recojo.getProductor() != null ? zonaDe(recojo.getProductor()) : null;
    }

    private static Instant inicioDe(LocalDate fecha) {
        return fecha.atStartOfDay(ZONA_HORARIA).toInstant();
    }

    private static Instant finDe(LocalDate fecha) {
        return fecha.plusDays(1).atStartOfDay(ZONA_HORARIA).toInstant();
    }
}
