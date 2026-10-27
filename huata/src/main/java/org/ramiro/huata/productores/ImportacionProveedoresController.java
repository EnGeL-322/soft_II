package org.ramiro.huata.productores;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importar el padron desde Excel. Exige los dos permisos: respaldo.write ("Importar datos") y
 * proveedores.write, porque cada fila crea o modifica un proveedor igual que el formulario.
 * {@code confirmar=false} (por defecto) es la vista previa: no guarda nada.
 */
@RestController
@RequestMapping("/api/v1/importaciones")
@RequiredArgsConstructor
public class ImportacionProveedoresController {

    private final ImportacionProveedoresService importacionService;

    @PostMapping(path = "/proveedores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Permiso.RESPALDO_WRITE + "') and hasAuthority('" + Permiso.PROVEEDORES_WRITE + "')")
    public ImportacionProveedoresService.ResultadoImportacion importar(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean confirmar) {
        if (archivo.isEmpty()) {
            throw new BusinessRuleException("Selecciona un archivo Excel");
        }
        try {
            return importacionService.procesar(archivo.getBytes(), confirmar);
        } catch (IOException e) {
            throw new BusinessRuleException("No se pudo leer el archivo subido");
        }
    }
}
