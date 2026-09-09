package org.ramiro.huata.reportes;

import java.util.List;
import org.ramiro.huata.calidad.ResultadoCalidad;

record ProduccionPorClaveResponse(String clave, Double litros, long recojos) {}

record TendenciaCalidadResponse(ResultadoCalidad resultado, long cantidad) {}

/** Punto de la serie diaria de acopio: alimenta los gráficos de tendencia de Reportes y Dashboard. */
record PuntoSerieDiaria(java.time.LocalDate fecha, double litros, long recepciones, double litrosObservados) {}

/** Fila de "Seguimiento de producción": busqueda libre por codigo de proveedor, zona, fecha o id de recepción. */
record TrazabilidadItem(
        Long recojoId,
        String clientUuid,
        String productorCodigo,
        String productorNombre,
        String zona,
        String rutaNombre,
        java.time.Instant fechaHora,
        Double litros,
        String estado) {}

record DashboardResponse(
        double litrosTotales,
        long recojosTotales,
        List<ProduccionPorClaveResponse> produccionPorRuta,
        List<ProduccionPorClaveResponse> produccionPorProductor,
        List<TendenciaCalidadResponse> tendenciaCalidad,
        long incidenciasAbiertas,
        long liquidacionesPendientes) {}
