package org.ramiro.huata.inventario;

import org.ramiro.huata.common.Paginas;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Maestro de materias primas/insumos y consulta del kardex de inventario. */
@RestController
@RequestMapping("/api/v1/inventario")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.INVENTARIO_READ + "')")
public class MateriaPrimaController {

    private final MateriaPrimaService materiaPrimaService;
    private final AjusteInventarioService ajusteInventarioService;

    @GetMapping("/materias-primas")
    public List<MateriaPrimaResponse> listar() {
        return materiaPrimaService.listar().stream()
                .map(c -> MateriaPrimaResponse.from(c.materiaPrima(), c.stock()))
                .toList();
    }

    @PostMapping("/materias-primas")
    @PreAuthorize("hasAuthority('" + Permiso.INVENTARIO_WRITE + "')")
    public ResponseEntity<MateriaPrimaResponse> crear(@Valid @RequestBody MateriaPrimaRequest request) {
        var m = materiaPrimaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MateriaPrimaResponse.from(m, materiaPrimaService.saldoDe(m.getId())));
    }

    @PutMapping("/materias-primas/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.INVENTARIO_WRITE + "')")
    public MateriaPrimaResponse actualizar(@PathVariable Long id, @Valid @RequestBody MateriaPrimaRequest request) {
        var m = materiaPrimaService.actualizar(id, request);
        return MateriaPrimaResponse.from(m, materiaPrimaService.saldoDe(id));
    }

    @DeleteMapping("/materias-primas/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.INVENTARIO_WRITE + "')")
    public ResponseEntity<Void> inactivar(@PathVariable Long id) {
        materiaPrimaService.inactivar(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Ajuste manual (V59) ----

    /** Merma, conteo fisico o leche descartada: exige motivo y deja su movimiento en el kardex. */
    @PostMapping("/ajustes")
    @PreAuthorize("hasAuthority('" + Permiso.INVENTARIO_WRITE + "')")
    public ResponseEntity<AjusteInventarioResponse> ajustar(@Valid @RequestBody AjusteInventarioRequest request) {
        AjusteInventario a = ajusteInventarioService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AjusteInventarioResponse(
                        a.getId(),
                        a.getMateriaPrima().getId(),
                        a.getMateriaPrima().getNombre(),
                        a.getTipo(),
                        a.getCantidad(),
                        a.getFecha(),
                        a.getMotivo(),
                        materiaPrimaService.saldoDe(a.getMateriaPrima().getId())));
    }

    // ---- Kardex ----

    @GetMapping("/kardex")
    public PageResponse<MovimientoInventarioResponse> kardex(
            @RequestParam(required = false) Long materiaPrimaId,
            @RequestParam(required = false) Long loteQuesoId,
            Pageable pedido) {
        Pageable pageable = Paginas.sinOrdenDelCliente(pedido);
        var page = materiaPrimaId != null
                ? materiaPrimaService.kardexDeMateriaPrima(materiaPrimaId, pageable)
                : loteQuesoId != null
                        ? materiaPrimaService.kardexDeLote(loteQuesoId, pageable)
                        : materiaPrimaService.kardex(pageable);
        return PageResponse.from(page);
    }
}
