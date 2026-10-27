package org.ramiro.huata.ivr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.liquidaciones.EstadoLiquidacion;
import org.ramiro.huata.liquidaciones.LiquidacionRepository;
import org.ramiro.huata.liquidaciones.LiquidacionService;
import org.ramiro.huata.mensajeria.MensajeriaService;
import org.ramiro.huata.notificaciones.NotificacionService;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.EstadoSolicitudZona;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.productores.SolicitudCambioZona;
import org.ramiro.huata.productores.SolicitudCambioZonaService;
import org.ramiro.huata.recojo.EstadoRecojo;
import org.ramiro.huata.recojo.EstadoVisita;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que el IVR puede consultar y pedir en nombre de un proveedor que llama por telefono.
 *
 * <p>Todas las reglas viven aqui y no en el IVR (Asterisk hoy, RapidPro manana): el IVR solo pregunta y
 * reproduce. Asi migrar de canal no obliga a reescribir ninguna decision de negocio.
 */
@Service
@RequiredArgsConstructor
public class IvrService {

    static final ZoneId ZONA_HUATA = ZoneId.of("America/Lima");
    /** Por telefono no se leen veinte avisos: los mas importantes y la persona puede volver a llamar. */
    static final int MAXIMO_AVISOS = 5;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ProductorRepository productores;
    private final RecojoRepository recojos;
    private final LiquidacionService liquidacionService;
    private final LiquidacionRepository liquidaciones;
    private final MensajeriaService mensajeria;
    private final SolicitudCambioZonaService solicitudesZona;
    private final NotificacionService notificaciones;

    // ------------------------------------------------------------------------------ Identificar

    /**
     * Por los ultimos 9 digitos. Si el numero esta en mas de una ficha no se adivina cual llama: se le
     * pide acercarse a la planta, porque responder con los datos de otro seria peor que no responder.
     */
    @Transactional(readOnly = true)
    public IvrDtos.IdentificarResponse identificar(String telefono) {
        String numero = TelefonoPeru.normalizar(telefono);
        List<Productor> coinciden = numero == null ? List.of() : productores.findByActivoTrueOrderByNombresAscApellidosAsc().stream()
                .filter(p -> numero.equals(TelefonoPeru.normalizar(p.getTelefono())))
                .toList();
        if (coinciden.size() != 1) {
            String motivo = coinciden.isEmpty() ? "NO_REGISTRADO" : "VARIOS_PROVEEDORES";
            String texto = coinciden.isEmpty()
                    ? "Su número no está registrado en Huata Lácteos. Acérquese a la planta o hable con su acopiador."
                    : "Su número está registrado para más de una persona. Por favor, acérquese a la planta para corregirlo.";
            return new IvrDtos.IdentificarResponse(false, motivo, null, null, null, null, null, null, texto);
        }
        Productor p = coinciden.get(0);
        String nombre = primerNombre(p.getNombres());
        return new IvrDtos.IdentificarResponse(true, null, p.getId(), nombre, p.getNombres() + " " + p.getApellidos(),
                p.getEstado().name(), p.getZonaPrincipal(), puedeSolicitarZona(p),
                "Hola, " + nombre + ". Bienvenido a la línea de Huata Lácteos.");
    }

    // ------------------------------------------------------------------------------- Litros hoy

    @Transactional(readOnly = true)
    public IvrDtos.LitrosHoyResponse litrosHoy(Long proveedorId) {
        Productor p = proveedor(proveedorId);
        LocalDate hoy = LocalDate.now(ZONA_HUATA);
        Instant inicio = hoy.atStartOfDay(ZONA_HUATA).toInstant();
        Instant fin = hoy.plusDays(1).atStartOfDay(ZONA_HUATA).toInstant().minusNanos(1);
        // Solo lo que de verdad se recogio: un "no entrego" o un recojo anulado no son leche entregada.
        List<Recojo> entregas = recojos
                .findByProductorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(p.getId(), inicio, fin, EstadoRecojo.CANCELLED)
                .stream()
                .filter(r -> r.getEstadoVisita() != EstadoVisita.NO_ENTREGADO)
                .filter(r -> r.getLitros() != null && r.getLitros() > 0)
                .toList();
        if (entregas.isEmpty()) {
            return new IvrDtos.LitrosHoyResponse(hoy, 0, 0, null, null, "Hoy todavía no tiene entregas registradas.");
        }
        double litros = redondear(entregas.stream().mapToDouble(Recojo::getLitros).sum());
        Recojo ultima = entregas.get(entregas.size() - 1);
        String acopiador = ultima.getAcopiador() == null ? null
                : ultima.getAcopiador().getNombres() + " " + ultima.getAcopiador().getApellidos();
        String texto = "Hoy entregó " + FrasesIvr.litros(litros) + "." + (acopiador == null ? "" : " Lo recogió " + acopiador + ".");
        return new IvrDtos.LitrosHoyResponse(hoy, litros, entregas.size(),
                ultima.getFechaHora().atZone(ZONA_HUATA).format(HORA), acopiador, texto);
    }

    // ------------------------------------------------------------------------------------ Cobro

    /** Ciclo jueves-miercoles, pago el viernes siguiente (RN-04, RN-05). Usa el mismo calculo que la app. */
    @Transactional(readOnly = true)
    public IvrDtos.CobroResponse cobro(Long proveedorId) {
        Productor p = proveedor(proveedorId);
        LiquidacionService.FilaPlanilla fila = liquidacionService.estimadoActual(p.getId());
        LocalDate[] ciclo = LiquidacionService.cicloActual();
        LocalDate fechaPago = ciclo[1].plusDays(2);
        double monto = redondear(fila.importePendiente());
        String estado = fila.litrosEntregados() <= 0 ? "SIN_ENTREGAS"
                : fila.tieneLiquidacionCerrada() ? "PAGADO"
                : fila.liquidado() ? "LIQUIDADO" : "ESTIMADO";

        IvrDtos.UltimoCiclo ultimo = liquidaciones
                .findByProductorIdAndPeriodoInicioAndPeriodoFinAndActivoTrue(p.getId(), ciclo[0].minusDays(7), ciclo[1].minusDays(7))
                .map(l -> new IvrDtos.UltimoCiclo(redondear(l.getMontoNeto()), l.getPeriodoFin().plusDays(2),
                        l.getEstado() == EstadoLiquidacion.CLOSED ? "PAGADO" : "POR_PAGAR"))
                .orElse(null);

        StringBuilder texto = new StringBuilder();
        switch (estado) {
            case "SIN_ENTREGAS" -> texto.append("Esta semana todavía no tiene monto por cobrar.");
            case "PAGADO" -> texto.append("Lo de esta semana ya está pagado.");
            default -> {
                texto.append("Por esta semana lleva ").append(FrasesIvr.soles(monto)).append(". Se paga ")
                        .append(FrasesIvr.fecha(fechaPago)).append(".");
                if (estado.equals("ESTIMADO")) texto.append(" Este monto puede cambiar hasta el cierre de la semana.");
            }
        }
        if (ultimo != null) {
            texto.append(" La semana pasada fueron ").append(FrasesIvr.soles(ultimo.monto()))
                    .append(ultimo.estado().equals("PAGADO") ? " y ya está pagado." : " y se paga " + FrasesIvr.fecha(ultimo.fechaPago()) + ".");
        }
        return new IvrDtos.CobroResponse(ciclo[0], ciclo[1], redondear(fila.litrosEntregados()), monto, "PEN",
                fechaPago, estado, ultimo, texto.toString());
    }

    // ------------------------------------------------------------------------------- Comunicados

    @Transactional(readOnly = true)
    public IvrDtos.ComunicadosResponse comunicados(Long proveedorId, int dias) {
        Productor p = proveedor(proveedorId);
        int ventana = Math.max(1, Math.min(dias, 30));
        Instant desde = LocalDate.now(ZONA_HUATA).minusDays(ventana - 1L).atStartOfDay(ZONA_HUATA).toInstant();
        List<IvrDtos.Comunicado> lista = mensajeria.avisosRecientes(p.getId(), desde, MAXIMO_AVISOS).stream()
                .map(a -> new IvrDtos.Comunicado(a.id(), a.fecha().atZone(ZONA_HUATA).toLocalDate(), a.texto(), a.leido()))
                .toList();
        String texto = lista.isEmpty() ? "No tiene avisos nuevos."
                : "Tiene " + lista.size() + (lista.size() == 1 ? " aviso." : " avisos.");
        return new IvrDtos.ComunicadosResponse(lista.size(), lista, texto);
    }

    @Transactional
    public void marcarLeidos(Long proveedorId, List<Long> ids) {
        Productor p = proveedor(proveedorId);
        mensajeria.marcarLeidosPorTelefono(p.getId(), ids);
    }

    // ------------------------------------------------------------------------------------- Zonas

    /** Las zonas que tienen al menos una ruta activa: pedir otra no tendria a donde llevar al proveedor. */
    @Transactional(readOnly = true)
    public IvrDtos.ZonasResponse zonas() {
        List<IvrDtos.Zona> zonas = new ArrayList<>();
        int opcion = 1;
        for (String zona : solicitudesZona.zonasDisponibles()) {
            // El teclado tiene 9 opciones utiles; con mas zonas haria falta un submenu.
            if (opcion > 9) break;
            zonas.add(new IvrDtos.Zona(opcion++, zona, audioDeZona(zona)));
        }
        return new IvrDtos.ZonasResponse(zonas);
    }

    /**
     * Crea la misma solicitud que la app, pendiente de aprobacion en la web. Repetir el pedido de la
     * misma zona (reintento tras un corte) devuelve la solicitud que ya existe en vez de duplicarla.
     */
    @Transactional
    public Resultado<IvrDtos.SolicitudZonaResponse> solicitarZona(Long proveedorId, String zonaPedida) {
        Productor p = proveedor(proveedorId);
        if (!puedeSolicitarZona(p)) {
            throw new ConflictoIvrException("NO_PERMITIDO", "Ahora no puede pedir cambio de zona. Consulte en la planta.");
        }
        String zona = zonas().zonas().stream()
                .map(IvrDtos.Zona::zona)
                .filter(z -> z.equalsIgnoreCase(zonaPedida.trim()))
                .findFirst()
                .orElseThrow(() -> new ConflictoIvrException("ZONA_NO_DISPONIBLE", "Esa zona no está disponible."));
        if (zona.equalsIgnoreCase(p.getZonaPrincipal() == null ? "" : p.getZonaPrincipal().trim())) {
            throw new ConflictoIvrException("MISMA_ZONA", "Usted ya está en esa zona.");
        }
        var pendiente = solicitudesZona.deProductor(p.getId()).stream()
                .filter(s -> s.getEstado() == EstadoSolicitudZona.PENDIENTE)
                .findFirst();
        if (pendiente.isPresent()) {
            SolicitudCambioZona s = pendiente.get();
            if (s.getZonaSolicitada() != null && s.getZonaSolicitada().trim().equalsIgnoreCase(zona)) {
                return new Resultado<>(false, respuestaSolicitud(s, zona));
            }
            throw new ConflictoIvrException("YA_PENDIENTE", "Ya tiene un pedido de cambio de zona esperando respuesta.");
        }
        SolicitudCambioZona creada = solicitudesZona.crearPorTelefono(p.getId(), zona);
        return new Resultado<>(true, respuestaSolicitud(creada, zona));
    }

    /** {@code creado} decide entre 201 y 200 (el pedido ya existia). */
    public record Resultado<T>(boolean creado, T cuerpo) {}

    // -------------------------------------------------------------------------- Pedido de llamada

    /**
     * Tecla 0: no se transfiere la llamada (ocuparia dos lineas); queda un aviso en la campana de la
     * web para que alguien de la planta le devuelva la llamada.
     */
    @Transactional
    public IvrDtos.TextoResponse pedidoLlamada(Long proveedorId) {
        Productor p = proveedor(proveedorId);
        notificaciones.crear(null, "Pidió que lo llamen",
                p.getNombres() + " " + p.getApellidos() + " (" + p.getCodigo() + ") pidió por teléfono que lo llamen"
                        + (p.getTelefono() == null ? "." : " al " + p.getTelefono() + "."),
                "PROVEEDOR", p.getId());
        return new IvrDtos.TextoResponse("Listo. Pedimos a la planta que lo llame.");
    }

    public IvrDtos.SaludResponse salud() {
        return new IvrDtos.SaludResponse("OK", OffsetDateTime.now(ZONA_HUATA));
    }

    // -------------------------------------------------------------------------------- Internos

    private Productor proveedor(Long id) {
        return productores.findById(id).filter(Productor::isActivo).orElseThrow(() -> NotFoundException.of("Proveedor", id));
    }

    /** Solo un proveedor ACTIVO pide cambio de zona; uno retirado o en revision consulta, pero no pide. */
    private static boolean puedeSolicitarZona(Productor p) {
        return p.getEstado() == EstadoProductor.ACTIVO;
    }

    private static IvrDtos.SolicitudZonaResponse respuestaSolicitud(SolicitudCambioZona s, String zona) {
        return new IvrDtos.SolicitudZonaResponse(s.getId(), s.getEstado().name(),
                "Listo. Su pedido para pasar a " + zona + " quedó registrado. La planta le avisará.");
    }

    private static String primerNombre(String nombres) {
        if (nombres == null || nombres.isBlank()) return "";
        return nombres.trim().split("\\s+")[0];
    }

    /** "COLLANA I" -> "zona_collana_i": el nombre del audio pregrabado de cada zona (ivr/audios/guion.csv). */
    static String audioDeZona(String zona) {
        String sinTildes = Normalizer.normalize(zona, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return "zona_" + slug;
    }

    private static double redondear(Double valor) {
        if (valor == null) return 0;
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
