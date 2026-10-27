package org.ramiro.huata.ingesta;

import java.time.Instant;

/**
 * Lectura del analizador de leche al pulsar "Llamar datos".
 *
 * <p>Reproduce el ticket que imprime el aparato (LACTOMAT / SmartScan AI, ver W.jpeg y W1.jpeg):
 *
 * <pre>
 * Analiz. de Leche  SmartScan AI
 * SN: 48359  Mode: 1
 * Temp. 12.2 C
 * Grasa.............  4.05%
 * SNG...............  8.95%
 * Densidad..........  31.4
 * Proteina..........  3.40%
 * Lactosa...........  4.87%
 * Sales.............  0.7%
 * Total solidos..... 13.0%
 * Agua anadida......  0.0%
 * Punto cong........ -0.562 C
 * pH................ 11.1
 * </pre>
 *
 * <p>CONTRATO PARA EL EQUIPO DE DOMOTICA. El ESP32 debe exponer un GET que devuelva:
 *
 * <pre>
 * {
 *   "analizadorSn": "48359",
 *   "modo": "1",
 *   "medidoEn": "2026-08-19T05:53:00Z",
 *   "temperatura": 12.2,
 *   "grasaPct": 4.05,
 *   "solidosNoGrasosPct": 8.95,
 *   "densidadLactometrica": 31.4,
 *   "proteinaPct": 3.40,
 *   "lactosaPct": 4.87,
 *   "salesPct": 0.7,
 *   "solidosTotalesPct": 13.0,
 *   "aguaAnadidaPct": 0.0,
 *   "puntoCongelacion": -0.562,
 *   "ph": 11.1
 * }
 * </pre>
 *
 * <p>Todos los campos son opcionales: lo que el aparato no entregue queda vacio en el formulario.
 *
 * <p>CUIDADO CON DOS UNIDADES. {@code densidadLactometrica} son GRADOS de lactometro (23.9 / 31.4 en
 * los tickets), no g/mL: enviar 1.031 aqui seria un error. Y {@code aguaAnadidaPct} es el porcentaje
 * medido, no el dictamen "sospecha / confirmada", que lo pone una persona.
 */
public record LecturaInstrumento(
        String analizadorSn,
        String modo,
        Instant medidoEn,
        Double temperatura,
        Double grasaPct,
        Double solidosNoGrasosPct,
        Double densidadLactometrica,
        Double proteinaPct,
        Double lactosaPct,
        Double salesPct,
        Double solidosTotalesPct,
        Double aguaAnadidaPct,
        Double puntoCongelacion,
        Double ph) {}
