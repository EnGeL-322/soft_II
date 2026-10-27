package org.ramiro.huata.ingesta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.productores.ProductorService;
import org.ramiro.huata.recojo.FuenteRecojo;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrada automatica de recepciones desde el equipo de domotica.
 *
 * <p>Se autentica con la cabecera {@code X-Api-Key} (ver IngestaApiKeyFilter), no con el JWT de una
 * persona, y queda fuera del resto de la API a proposito: es la unica puerta abierta a un aparato y
 * conviene que se vea de un vistazo cual es.
 *
 * <p>NO DICTAMINA CALIDAD. Registra el recojo y las lecturas del instrumento; decidir si la entrega
 * es apta sigue siendo del responsable de calidad, que abrira su control con estos valores ya
 * cargados. El aparato mide, la persona confirma.
 */
@RestController
@RequestMapping("/api/v1/ingesta")
@RequiredArgsConstructor
public class IngestaController {

    private final InstrumentoService instrumentoService;
    private final RecojoRepository recojoRepository;
    private final ProductorRepository productorRepository;
    private final ProductorService productorService;
    private final AuditoriaService auditoriaService;

    /**
     * Lectura instantanea del equipo, para el boton "Llamar datos" del formulario de recepcion.
     *
     * <p>La usa una PERSONA desde el navegador, asi que va con el JWT normal y no con la clave de
     * API del aparato: aqui el sentido de la comunicacion es el contrario, es el sistema el que
     * pregunta al equipo.
     */
    /**
     * Si hay un equipo configurado, para que la web muestre "Llamar datos" habilitado o explique que
     * falta configurarlo, en vez de dejar pulsar un boton que siempre falla.
     */
    @GetMapping("/estado")
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIO_READ + "')")
    public java.util.Map<String, Boolean> estado() {
        return java.util.Map.of("configurado", instrumentoService.configurado());
    }

    @GetMapping("/lectura")
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIO_READ + "')")
    public LecturaEvaluada lectura() {
        return instrumentoService.leerYEvaluar();
    }

    /**
     * Una recepcion medida por el equipo.
     *
     * <p>{@code clientUuid} lo genera el aparato y es la clave de idempotencia: si reintenta por un
     * corte de red, la segunda llamada devuelve el recojo que ya existe en vez de duplicarlo. Sin
     * esto, cada reintento seria leche fantasma que alguien acabaria pagando.
     */
    public record RecepcionMedidaRequest(
            @NotBlank @Size(max = 64) String clientUuid,
            @NotBlank @Size(max = 30) String productorCodigo,
            @NotNull Instant fechaHora,
            @NotNull @Positive Double litros,
            FuenteRecojo fuente,
            /** Ticket del analizador, opcional. */
            LecturaInstrumento medicion) {}

    public record RecepcionMedidaResponse(Long recojoId, String clientUuid, boolean yaExistia) {}

    @PostMapping("/recepciones")
    @PreAuthorize("hasAuthority('" + IngestaApiKeyFilter.ROL + "')")
    @Transactional
    public ResponseEntity<RecepcionMedidaResponse> registrarRecepcion(@Valid @RequestBody RecepcionMedidaRequest req) {
        var existente = recojoRepository.findByClientUuid(req.clientUuid());
        if (existente.isPresent()) {
            // Reintento del aparato: se devuelve 200 con lo que ya hay, no un error.
            return ResponseEntity.ok(new RecepcionMedidaResponse(existente.get().getId(), req.clientUuid(), true));
        }

        // El aparato identifica al proveedor por su codigo, que es lo que lleva impreso el porongo;
        // pedirle un id interno de la base seria acoplarlo a algo que no puede ver.
        Productor productor = productorRepository
                .findByCodigoIgnoreCase(req.productorCodigo())
                .orElseThrow(() -> NotFoundException.of("Productor", req.productorCodigo()));
        // La misma regla que en el registro manual, y llamada a la misma definicion: un proveedor
        // suspendido no entrega leche tampoco porque la recepcion venga del analizador.
        //
        // DEUDA CONOCIDA: este endpoint construye el Recojo aqui, en la capa de presentacion, en vez
        // de delegar en RecojoService. Por eso hay que acordarse de llamar a esta guardia a mano.
        // Unificar ambos caminos en el service es lo correcto y esta pendiente.
        productorService.exigirQuePuedeEntregar(productor);

        Recojo recojo = new Recojo();
        recojo.setClientUuid(req.clientUuid());
        recojo.setProductor(productor);
        recojo.setRuta(productor.getRuta());
        recojo.setFechaHora(req.fechaHora());
        recojo.setLitros(req.litros());
        recojo.setTemperatura(req.medicion() == null ? null : req.medicion().temperatura());
        recojo.setFuente(req.fuente() == null ? FuenteRecojo.PLANTA : req.fuente());
        Recojo guardado = recojoRepository.save(recojo);
        instrumentoService.guardarMedicion(guardado, req.medicion());

        auditoriaService.registrar(
                "Ingesta automática",
                "Recepción",
                productor.getCodigo() + " · " + req.litros() + " L · equipo de domótica");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RecepcionMedidaResponse(guardado.getId(), req.clientUuid(), false));
    }
}
