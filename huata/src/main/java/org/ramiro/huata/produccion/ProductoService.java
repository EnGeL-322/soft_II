package org.ramiro.huata.produccion;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.quesos.LoteQuesoRepository;
import org.ramiro.huata.quesos.TarifaQuesoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogo de productos terminados. Es dato maestro: dos filas para el mismo producto partirian su
 * stock, sus recetas y sus precios en dos, asi que el nombre no se repite (sin distinguir mayusculas).
 */
@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final RecetaQuesoRepository recetaRepository;
    private final LoteQuesoRepository loteRepository;
    private final TarifaQuesoRepository tarifaRepository;
    private final AuditoriaService auditoriaService;

    private static final String ENTIDAD = "Productos";

    public List<Producto> listar(boolean incluirInactivos) {
        return incluirInactivos
                ? productoRepository.findAllByOrderByActivoDescNombreAsc()
                : productoRepository.findByActivoTrueOrderByNombreAsc();
    }

    @Transactional
    public Producto crear(ProductoRequest req) {
        String nombre = req.nombre().trim();
        exigirNombreLibre(nombre, null);
        Producto producto = new Producto();
        aplicar(producto, req, nombre);
        Producto guardado = productoRepository.save(producto);
        auditoriaService.registrar("Alta de producto", ENTIDAD, guardado.getNombre() + " · " + guardado.getUnidadVenta());
        return guardado;
    }

    @Transactional
    public Producto actualizar(Long id, ProductoRequest req) {
        Producto producto = buscar(id);
        String nombre = req.nombre().trim();
        exigirNombreLibre(nombre, id);
        String anterior = producto.getNombre();
        aplicar(producto, req, nombre);
        Producto guardado = productoRepository.save(producto);
        auditoriaService.registrar("Edición de producto", ENTIDAD, anterior + " → " + guardado.getNombre());
        return guardado;
    }

    /**
     * Da de baja (o reactiva) el producto. De baja deja de ofrecerse para nuevas recetas, lotes y
     * precios; no toca lo ya producido: el stock que quede en camara se sigue vendiendo.
     */
    @Transactional
    public Producto cambiarEstado(Long id, boolean activo) {
        Producto producto = buscar(id);
        producto.setActivo(activo);
        Producto guardado = productoRepository.save(producto);
        auditoriaService.registrar(activo ? "Reactivación de producto" : "Baja de producto", ENTIDAD, guardado.getNombre());
        return guardado;
    }

    /**
     * Cuantos dias dura el producto desde que se produce (2026-10-27: se configura en Configuracion).
     * Sin este dato no se puede producir: cada lote nace con su fecha de vencimiento.
     */
    @Transactional
    public Producto cambiarVidaUtil(Long id, int dias) {
        if (dias < 1 || dias > 3650) {
            throw new BusinessRuleException("Los días de vencimiento deben estar entre 1 y 3650.");
        }
        Producto producto = buscar(id);
        Integer anterior = producto.getVidaUtilDias();
        producto.setVidaUtilDias(dias);
        Producto guardado = productoRepository.save(producto);
        auditoriaService.registrar(
                "Vencimiento de producto",
                ENTIDAD,
                guardado.getNombre() + " · " + (anterior != null ? anterior + " → " : "") + dias + " días");
        return guardado;
    }

    @Transactional
    public void eliminar(Long id) {
        Producto producto = buscar(id);
        if (loteRepository.existsByProductoId(id) || tarifaRepository.existsByProductoId(id)) {
            throw new BusinessRuleException("No se puede eliminar " + producto.getNombre()
                    + " porque ya tiene producción o precios registrados. Puedes pausarlo en su lugar.");
        }
        // Las recetas sin producción son configuración del producto y se eliminan junto con él.
        // deleteAll entidad a entidad respeta el cascade hacia RecetaItem.
        recetaRepository.deleteAll(recetaRepository.findByProductoId(id));
        productoRepository.delete(producto);
        auditoriaService.registrar("Eliminación de producto", ENTIDAD, producto.getNombre());
    }

    /** El producto, solo si sigue activo: lo que usan receta, lote y precio para aceptar un alta. */
    public Producto activo(Long id) {
        Producto producto = buscar(id);
        if (!producto.isActivo()) {
            throw new BusinessRuleException("El producto " + producto.getNombre() + " está dado de baja.");
        }
        return producto;
    }

    /**
     * El producto aunque este dado de baja. Lo usa la consulta de precio: el stock que quedo en
     * camara de un producto discontinuado se sigue vendiendo, y para eso hace falta su precio.
     */
    public Producto activoOHistorico(Long id) {
        return buscar(id);
    }

    private Producto buscar(Long id) {
        return productoRepository.findById(id).orElseThrow(() -> NotFoundException.of("Producto", id));
    }

    private void exigirNombreLibre(String nombre, Long idPropio) {
        productoRepository.findByNombreIgnoreCase(nombre)
                .filter(p -> !p.getId().equals(idPropio))
                .ifPresent(p -> {
                    throw new BusinessRuleException("Ya existe un producto llamado " + p.getNombre()
                            + (p.isActivo() ? "." : " (dado de baja): reactívalo en lugar de crear otro."));
                });
    }

    private static void aplicar(Producto producto, ProductoRequest req, String nombre) {
        producto.setNombre(nombre);
        producto.setUnidadVenta(req.unidadVenta().trim());
        producto.setDescripcion(
                req.descripcion() != null && !req.descripcion().isBlank() ? req.descripcion().trim() : null);
        producto.setVidaUtilDias(req.vidaUtilDias());
    }
}
