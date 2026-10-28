package org.ramiro.huata.compras;

import org.ramiro.huata.common.Paginas;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/compras")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.COMPRAS_READ + "')")
public class CompraController {

    private final CompraService compraService;

    @GetMapping
    public PageResponse<CompraResponse> listar(
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            Pageable pageable) {
        return PageResponse.from(compraService.listar(desde, hasta, Paginas.sinOrdenDelCliente(pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.COMPRAS_WRITE + "')")
    public ResponseEntity<CompraResponse> registrar(@Valid @RequestBody CompraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(compraService.registrarYResponder(request));
    }

    /** Compras que todavia no llegan del todo: la bandeja de Recepciones (V61). */
    @GetMapping("/pendientes")
    public java.util.List<CompraResponse> pendientes() {
        return compraService.pendientesDeRecepcion();
    }

    /** Historial de recepciones, con lote y vencimiento de cada linea (V61). */
    @GetMapping("/recepciones")
    public java.util.List<RecepcionResponse> recepciones() {
        return compraService.recepciones();
    }

    /** Recibe lo que llego de una compra, total o en parte: es lo que mete el insumo al almacen. */
    @PostMapping("/{id}/recepciones")
    @PreAuthorize("hasAuthority('" + Permiso.COMPRAS_WRITE + "')")
    public CompraResponse recibir(@PathVariable Long id, @Valid @RequestBody RecepcionRequest request) {
        return compraService.recibirYResponder(id, request);
    }

    /** Anula una compra registrada por error (V59): exige motivo y no borra nada. */
    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('" + Permiso.COMPRAS_WRITE + "')")
    public CompraResponse anular(@PathVariable Long id, @Valid @RequestBody AnularCompraRequest request) {
        return compraService.anularYResponder(id, request.motivo());
    }
}
