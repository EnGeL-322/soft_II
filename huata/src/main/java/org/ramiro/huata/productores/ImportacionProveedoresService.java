package org.ramiro.huata.productores;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.DatosPersonales;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Importacion del padron de proveedores desde Excel (RF-11/RF-24, DESARROLLO.md 12).
 *
 * <p>Reglas, todas de la especificacion: la importacion va por el backend y reutiliza los MISMOS
 * services que el formulario ({@link ProductorService#crear}/{@link ProductorService#actualizar}),
 * asi que valida y audita igual; es aditiva o de actualizacion por clave natural (Codigo = DNI) y
 * nunca un reemplazo total: no borra proveedores ni cambia estados (eso tiene su propia regla, con
 * motivo). Un alta nace ACTIVO como cualquier otra (ver {@link EstadoProductor#inicial()}).
 *
 * <p>Vista previa y confirmacion ejecutan el mismo codigo dentro de una transaccion: en la vista
 * previa, o si cualquier fila falla, la transaccion se deshace entera. Asi la vista previa dice
 * exactamente lo que pasara, y un archivo con un error no deja el padron a medio cargar.
 */
@Service
@RequiredArgsConstructor
public class ImportacionProveedoresService {

    /** Mas filas que esto no es un padron, es un error de archivo; se rechaza antes de procesar. */
    static final int MAX_FILAS = 2000;

    private final ProductorService productorService;
    private final ProductorRepository productorRepository;
    private final RutaRepository rutaRepository;
    private final Validator validator;
    private final PlatformTransactionManager transactionManager;
    private final AuditoriaService auditoriaService;

    public enum Accion { CREAR, ACTUALIZAR, ERROR }

    public record FilaResultado(int fila, String codigo, String nombre, Accion accion, String mensaje) {}

    public record ResultadoImportacion(
            boolean aplicado, int crear, int actualizar, int errores, List<FilaResultado> filas) {}

    public ResultadoImportacion procesar(byte[] archivo, boolean confirmar) {
        List<Map<String, String>> filas = leer(archivo);
        return new TransactionTemplate(transactionManager).execute(status -> {
            List<FilaResultado> resultados = new ArrayList<>();
            Set<String> codigosVistos = new HashSet<>();
            for (int i = 0; i < filas.size(); i++) {
                Map<String, String> f = filas.get(i);
                int numero = i + 2; // fila 1 = encabezados, como la ve quien abre el Excel
                String codigo = f.getOrDefault("codigo", "");
                String nombre = (f.getOrDefault("nombres", "") + " " + f.getOrDefault("apellidos", "")).trim();
                try {
                    if (!codigosVistos.add(codigo.toLowerCase(Locale.ROOT))) {
                        throw new BusinessRuleException("El código " + codigo + " está repetido en el archivo");
                    }
                    resultados.add(new FilaResultado(numero, codigo, nombre, aplicarFila(f), null));
                } catch (BusinessRuleException | NotFoundException e) {
                    resultados.add(new FilaResultado(numero, codigo, nombre, Accion.ERROR, e.getMessage()));
                }
            }
            int crear = contar(resultados, Accion.CREAR);
            int actualizar = contar(resultados, Accion.ACTUALIZAR);
            int errores = contar(resultados, Accion.ERROR);
            boolean aplicado = confirmar && errores == 0;
            if (aplicado) {
                auditoriaService.registrar("Importacion de proveedores", "Proveedores",
                        crear + " altas y " + actualizar + " actualizaciones desde Excel");
            } else {
                status.setRollbackOnly();
            }
            return new ResultadoImportacion(aplicado, crear, actualizar, errores, resultados);
        });
    }

    private static int contar(List<FilaResultado> filas, Accion accion) {
        return (int) filas.stream().filter(f -> f.accion() == accion).count();
    }

    private Accion aplicarFila(Map<String, String> f) {
        String codigo = f.getOrDefault("codigo", "");
        if (codigo.isBlank()) {
            throw new BusinessRuleException("Falta el Código (DNI)");
        }
        Ruta ruta = rutaPorNombre(f.getOrDefault("ruta", ""));
        Productor existente = productorRepository.findByCodigoIgnoreCase(codigo).filter(Productor::isActivo).orElse(null);
        if (existente == null) {
            String usuario = vacioANull(f.get("usuario"));
            String clave = vacioANull(f.get("clave_inicial"));
            if (usuario == null || clave == null) {
                throw new BusinessRuleException("Proveedor nuevo: faltan Usuario y Clave_inicial para su cuenta de la app");
            }
            productorService.crear(validado(new ProductorRequest(
                    codigo, f.get("nombres"), f.get("apellidos"), vacioANull(f.get("telefono")),
                    ruta.getId(), null, null, null, null, usuario, clave)));
            return Accion.CREAR;
        }
        // Lo que el archivo no trae se conserva tal cual: notas, orden de visita, cuenta de la app y,
        // si sigue en la misma ruta, su acopiador responsable.
        String usuario = vacioANull(f.get("usuario"));
        if (usuario == null && existente.getUsuario() != null) {
            usuario = existente.getUsuario().getUsername();
        }
        Long acopiador = existente.getAcopiadorResponsable() != null
                        && existente.getRuta() != null
                        && existente.getRuta().getId().equals(ruta.getId())
                ? existente.getAcopiadorResponsable().getId()
                : null;
        String telefono = f.containsKey("telefono") ? vacioANull(f.get("telefono")) : existente.getTelefono();
        // Un proveedor sin DNI tiene codigo generado (PRV-...): ese codigo no es un DNI.
        String dni = DatosPersonales.esDniValido(codigo) ? codigo : existente.getDocumentoIdentidad();
        productorService.actualizar(existente.getId(), validado(new ProductorRequest(
                dni, f.get("nombres"), f.get("apellidos"), telefono, ruta.getId(), existente.getNotas(),
                acopiador, null, existente.getOrdenEnRuta(), usuario, null)));
        return Accion.ACTUALIZAR;
    }

    /** Las mismas validaciones de forma que el formulario (@Valid en el controller). */
    private ProductorRequest validado(ProductorRequest req) {
        Set<ConstraintViolation<ProductorRequest>> errores = validator.validate(req);
        if (!errores.isEmpty()) {
            throw new BusinessRuleException(errores.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
        return req;
    }

    private Ruta rutaPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new BusinessRuleException("Falta la Ruta");
        }
        return rutaRepository.findByActivoTrueOrderByZonaAscNombreAsc().stream()
                .filter(r -> r.getNombre().trim().equalsIgnoreCase(nombre.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("No existe una ruta activa llamada \"" + nombre.trim() + "\""));
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Primera hoja; fila 1 = encabezados (sin importar mayusculas ni el orden de las columnas). El
     * DNI guardado como numero pierde los ceros a la izquierda en Excel: se devuelven.
     */
    static List<Map<String, String>> leer(byte[] archivo) {
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(archivo))) {
            Sheet hoja = libro.getSheetAt(0);
            DataFormatter formato = new DataFormatter(Locale.ROOT);
            Row encabezado = hoja.getRow(hoja.getFirstRowNum());
            if (encabezado == null) {
                throw new BusinessRuleException("El archivo está vacío");
            }
            Map<Integer, String> columnas = new HashMap<>();
            for (Cell c : encabezado) {
                String nombre = formato.formatCellValue(c).trim().toLowerCase(Locale.ROOT);
                if (!nombre.isEmpty()) {
                    columnas.put(c.getColumnIndex(), nombre);
                }
            }
            for (String obligatoria : List.of("codigo", "nombres", "apellidos", "ruta")) {
                if (!columnas.containsValue(obligatoria)) {
                    throw new BusinessRuleException("Falta la columna \"" + obligatoria
                            + "\". Usa el Excel que exporta Proveedores como plantilla.");
                }
            }
            List<Map<String, String>> filas = new ArrayList<>();
            for (int r = encabezado.getRowNum() + 1; r <= hoja.getLastRowNum(); r++) {
                Row fila = hoja.getRow(r);
                if (fila == null) {
                    continue;
                }
                Map<String, String> valores = new HashMap<>();
                columnas.forEach((idx, nombre) -> {
                    Cell c = fila.getCell(idx);
                    String v = c == null ? "" : formato.formatCellValue(c).trim();
                    if ("codigo".equals(nombre) && c != null && c.getCellType() == CellType.NUMERIC && v.matches("\\d{1,7}")) {
                        v = "0".repeat(8 - v.length()) + v;
                    }
                    valores.put(nombre, v);
                });
                if (valores.values().stream().allMatch(String::isEmpty)) {
                    continue; // fila en blanco al final del archivo
                }
                filas.add(valores);
                if (filas.size() > MAX_FILAS) {
                    throw new BusinessRuleException("El archivo supera las " + MAX_FILAS + " filas");
                }
            }
            if (filas.isEmpty()) {
                throw new BusinessRuleException("El archivo no tiene filas de proveedores");
            }
            return filas;
        } catch (IOException | IllegalArgumentException e) { // incluye EmptyFileException y "no es Excel"
            throw new BusinessRuleException("No se pudo leer el archivo: debe ser un Excel (.xlsx)");
        }
    }
}
