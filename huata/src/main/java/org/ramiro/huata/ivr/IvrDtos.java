package org.ramiro.huata.ivr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Contrato del IVR (ivr/README.md, seccion 5). Neutral al canal: lo consume Asterisk hoy y RapidPro
 * mas adelante. Cada respuesta trae los datos estructurados y un {@code texto} listo para voz.
 */
public final class IvrDtos {
    private IvrDtos() {}

    public record IdentificarRequest(
            @NotBlank @Size(max = 20) String telefono, @Size(max = 20) String canal, @Size(max = 64) String llamadaId) {}

    /** {@code motivo}: NO_REGISTRADO o VARIOS_PROVEEDORES cuando {@code encontrado} es falso. */
    public record IdentificarResponse(
            boolean encontrado,
            String motivo,
            Long proveedorId,
            String nombre,
            String nombreCompleto,
            String estado,
            String zonaActual,
            Boolean puedeSolicitarZona,
            String texto) {}

    public record LitrosHoyResponse(
            LocalDate fecha, double litros, int entregas, String ultimaEntrega, String acopiador, String texto) {}

    public record UltimoCiclo(double monto, LocalDate fechaPago, String estado) {}

    /** {@code estado}: ESTIMADO, LIQUIDADO, PAGADO o SIN_ENTREGAS. */
    public record CobroResponse(
            LocalDate cicloDesde,
            LocalDate cicloHasta,
            double litros,
            double monto,
            String moneda,
            LocalDate fechaPago,
            String estado,
            UltimoCiclo ultimoCiclo,
            String texto) {}

    public record Comunicado(Long id, LocalDate fecha, String texto, boolean leido) {}

    public record ComunicadosResponse(int cantidad, List<Comunicado> comunicados, String texto) {}

    public record LeidosRequest(@NotNull @Size(max = 50) List<Long> ids, @Size(max = 64) String llamadaId) {}

    public record Zona(int opcion, String zona, String audio) {}

    public record ZonasResponse(List<Zona> zonas) {}

    public record SolicitudZonaRequest(@NotBlank @Size(max = 120) String zona, @Size(max = 64) String llamadaId) {}

    public record SolicitudZonaResponse(Long solicitudId, String estado, String texto) {}

    public record PedidoLlamadaRequest(@Size(max = 64) String llamadaId) {}

    public record TextoResponse(String texto) {}

    /** 409 del IVR: {@code codigo} es lo que el flujo usa para elegir el audio. */
    public record ConflictoResponse(String codigo, String texto) {}

    public record SaludResponse(String estado, OffsetDateTime hora) {}
}
