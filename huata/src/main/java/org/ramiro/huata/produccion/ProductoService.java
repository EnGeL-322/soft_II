package org.ramiro.huata.produccion;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NombresParecidos;
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
        String nombre = NombresParecidos.limpiar(req.nombre());
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
        String nombre = NombresParecidos.limpiar(req.nombre());
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

    /** Lotes producidos del producto (anulados incluidos). Con alguno, ya no se puede eliminar. */
    public long lotesRegistrados(Long id) {
        return loteRepository.countByProductoId(id);
    }

    /**
     * Elimina un producto que nunca se produjo (2026-10-27). Lo que lo impide es el historial de
     * produccion: un lote, aun anulado, tiene movimientos de inventario y asientos que lo nombran, y
     * las ventas cuelgan de los lotes. Precios y recetas sin lotes son solo configuracion: no se
     * vendio nada con ellos, asi que se eliminan junto con el producto (antes un precio cargado
     * bloqueaba la eliminacion y el mensaje no decia donde quitarlo).
     */
    @Transactional
    public void eliminar(Long id) {
        Producto producto = buscar(id);
        long lotes = loteRepository.countByProductoId(id);
        if (lotes > 0) {
            throw new BusinessRuleException("«" + producto.getNombre() + "» no se puede eliminar porque ya se "
                    + (lotes == 1 ? "registró 1 producción" : "registraron " + lotes + " producciones")
                    + " con él y ese historial se conserva. Si ya no lo hacen, usa «Pausar»: deja de aparecer"
                    + " al producir y lo que quede guardado se puede seguir vendiendo.");
        }
        // deleteAll entidad a entidad respeta el cascade hacia RecetaItem.
        recetaRepository.deleteAll(recetaRepository.findByProductoId(id));
        var precios = tarifaRepository.findByProductoId(id);
        tarifaRepository.deleteAll(precios);
        productoRepository.delete(producto);
        auditoriaService.registrar(
                "Eliminación de producto",
                ENTIDAD,
                producto.getNombre() + (precios.isEmpty() ? "" : " · con " + precios.size() + " precio(s) de venta"));
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

    /**
     * Ni igual (sin mayusculas, tildes ni espacios de mas) ni casi igual a otro producto, activo o
     * pausado. Regla del cliente (2026-10-27): "yogurd" junto a "yogurt" se bloquea siempre.
     */
    private void exigirNombreLibre(String nombre, Long idPropio) {
        var otros = productoRepository.findAll().stream().filter(p -> !p.getId().equals(idPropio)).toList();
        NombresParecidos.buscarChoque(nombre, otros, Producto::getNombre).ifPresent(p -> {
            String estado = p.isActivo() ? "" : " (pausado: reanúdalo en lugar de crear otro)";
            if (NombresParecidos.iguales(nombre, p.getNombre())) {
                throw new BusinessRuleException("Ya existe un producto llamado «" + p.getNombre() + "»" + estado + ".");
            }
            throw new BusinessRuleException("«" + nombre + "» es casi igual a «" + p.getNombre() + "», que ya existe"
                    + estado + ". Si es el mismo producto, usa «" + p.getNombre() + "»; si es otro, ponle un nombre"
                    + " que se distinga (por ejemplo, con el sabor o el tamaño).");
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
