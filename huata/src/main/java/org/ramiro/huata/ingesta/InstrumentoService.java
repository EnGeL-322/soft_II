package org.ramiro.huata.ingesta;

import java.time.Duration;
import org.ramiro.huata.common.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.ramiro.huata.recojo.Recojo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Consulta la lectura actual del equipo de medicion (ESP32) para el boton "Llamar datos".
 *
 * <p>POR QUE PASA POR EL BACKEND Y NO LO PIDE EL NAVEGADOR: si el navegador llamara al ESP32
 * directamente harian falta cabeceras CORS en el microcontrolador, que quien usa la web estuviera
 * en la misma red que el equipo, y ademas dejaria de funcionar en cuanto la aplicacion se sirva por
 * HTTPS (contenido mixto: una pagina segura no puede pedir datos a un http:// de la LAN). Haciendolo
 * desde el servidor, el ESP32 se queda en la red local y no se expone a nadie.
 */
@Service
public class InstrumentoService {

    /** Corto a proposito: es una lectura en vivo con alguien esperando delante de la pantalla. */
    private static final Duration ESPERA = Duration.ofSeconds(4);

    private final String urlEquipo;
    private final RestClient http;
    private final MedicionInstrumentoRepository mediciones;
    private final org.ramiro.huata.calidad.CorreccionDensidadService correccionDensidad;
    private final org.ramiro.huata.liquidaciones.TarifaLecheRepository tarifas;

    /**
     * Se construye el cliente con la fabrica estatica en vez de inyectar RestClient.Builder: en
     * Spring Boot 4 ese bean vive en un modulo de autoconfiguracion aparte y no esta en el contexto,
     * asi que inyectarlo impedia arrancar la aplicacion. Aqui no hace falta nada del contexto.
     */
    public InstrumentoService(
            @Value("${huata.instrumento.url:}") String urlEquipo,
            MedicionInstrumentoRepository mediciones,
            org.ramiro.huata.calidad.CorreccionDensidadService correccionDensidad,
            org.ramiro.huata.liquidaciones.TarifaLecheRepository tarifas) {
        this.mediciones = mediciones;
        this.correccionDensidad = correccionDensidad;
        this.tarifas = tarifas;
        this.urlEquipo = urlEquipo == null ? "" : urlEquipo.trim();
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) ESPERA.toMillis());
        factory.setReadTimeout((int) ESPERA.toMillis());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configurado() {
        return !urlEquipo.isBlank();
    }

    /**
     * Pide al equipo su medicion actual.
     *
     * <p>Los fallos se traducen a mensajes de negocio y no a un 500: que el equipo este apagado o
     * fuera de red es una situacion corriente en planta, no un error del sistema, y quien esta en la
     * pantalla necesita entender que hacer.
     */
    public LecturaInstrumento leer() {
        if (!configurado()) {
            throw new BusinessRuleException(
                    "No hay un equipo de medición configurado. Define HUATA_INSTRUMENTO_URL para usar "
                            + "\"Llamar datos\", o completa los valores a mano.");
        }
        try {
            LecturaInstrumento lectura = http.get().uri(urlEquipo).retrieve().body(LecturaInstrumento.class);
            if (lectura == null) {
                throw new BusinessRuleException("El equipo de medición respondió vacío. Intenta de nuevo.");
            }
            return lectura;
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException(
                    "No se pudo leer el equipo de medición (" + urlEquipo + "). Comprueba que esté encendido "
                            + "y en la misma red que el servidor.");
        }
    }

    /**
     * Lee el equipo y aplica la regla 7.5 sobre lo que devuelva.
     *
     * <p>Sin densidad o sin temperatura no se clasifica: se devuelve el ticket con la evaluacion en
     * null. Suponer una categoria a partir de datos incompletos seria peor que no dar ninguna,
     * porque de ella cuelga el precio que se le paga al proveedor.
     */
    public LecturaEvaluada leerYEvaluar() {
        LecturaInstrumento lectura = leer();
        if (lectura.densidadLactometrica() == null || lectura.temperatura() == null) {
            return new LecturaEvaluada(lectura, null);
        }
        double corregida = correccionDensidad.corregirA15C(lectura.densidadLactometrica(), lectura.temperatura());
        var categoria = org.ramiro.huata.calidad.CategoriaLeche.de(corregida);
        var resultado = correccionDensidad.resultadoDe(categoria);
        Double precio = tarifas
                .findTopByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(java.time.LocalDate.now())
                .map(t -> resultado == org.ramiro.huata.calidad.ResultadoCalidad.ACEPTADA
                        ? t.getPrecioNormal()
                        : t.getPrecioObservada())
                .orElse(null);
        return new LecturaEvaluada(
                lectura,
                new LecturaEvaluada.Evaluacion(corregida, categoria, categoria.etiqueta(), resultado, precio));
    }

    /**
     * Guarda el ticket del analizador asociado a una recepcion.
     *
     * <p>Si la lectura viene vacia no se crea nada: una fila con todos los valores en null solo
     * ensucia y hace creer que hubo medicion. Es idempotente por recojo, porque el analizador emite
     * un ticket por muestra y la tabla lo impone con una restriccion unica.
     */
    @Transactional
    public void guardarMedicion(Recojo recojo, LecturaInstrumento lectura) {
        if (lectura == null || vacia(lectura)) {
            return;
        }
        MedicionInstrumento medicion = mediciones.findByRecojoId(recojo.getId()).orElseGet(MedicionInstrumento::new);
        medicion.setRecojo(recojo);
        medicion.setAnalizadorSn(lectura.analizadorSn());
        medicion.setModo(lectura.modo());
        medicion.setMedidoEn(lectura.medidoEn());
        medicion.setTemperatura(lectura.temperatura());
        medicion.setGrasaPct(lectura.grasaPct());
        medicion.setSolidosNoGrasosPct(lectura.solidosNoGrasosPct());
        medicion.setDensidadLactometrica(lectura.densidadLactometrica());
        medicion.setProteinaPct(lectura.proteinaPct());
        medicion.setLactosaPct(lectura.lactosaPct());
        medicion.setSalesPct(lectura.salesPct());
        medicion.setSolidosTotalesPct(lectura.solidosTotalesPct());
        medicion.setAguaAnadidaPct(lectura.aguaAnadidaPct());
        medicion.setPuntoCongelacion(lectura.puntoCongelacion());
        medicion.setPh(lectura.ph());
        mediciones.save(medicion);
    }

    /** Sin ningun valor util: solo cabecera o todo en null. */
    private boolean vacia(LecturaInstrumento l) {
        return l.temperatura() == null && l.grasaPct() == null && l.solidosNoGrasosPct() == null
                && l.densidadLactometrica() == null && l.proteinaPct() == null && l.lactosaPct() == null
                && l.salesPct() == null && l.solidosTotalesPct() == null && l.aguaAnadidaPct() == null
                && l.puntoCongelacion() == null && l.ph() == null;
    }
}
