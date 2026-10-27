package org.ramiro.huata.exportacion;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exportaciones")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.RESPALDO_READ + "')")
public class ExportacionController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportacionService exportacionService;

    @GetMapping
    public List<String> hojasDisponibles() {
        return ExportacionService.HOJAS_DISPONIBLES;
    }

    @GetMapping("/{hoja}")
    public ResponseEntity<byte[]> exportar(@PathVariable String hoja) {
        byte[] archivo = exportacionService.exportar(hoja);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Huata_" + hoja + ".xlsx")
                .contentType(XLSX)
                .body(archivo);
    }
}
