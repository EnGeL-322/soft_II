package org.ramiro.huata.compras;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.contabilidad.AsientoContableService;
import org.ramiro.huata.contabilidad.LineaContable;
import org.ramiro.huata.contabilidad.OrigenAsiento;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra una compra de materia prima/insumos: guarda la cabecera y sus items, y por cada item
 * genera un movimiento {@code ENTRADA_COMPRA} en el kardex. Todo en una sola transaccion (RNF-18):
 * si algo falla a mitad de camino, ni la compra ni los movimientos quedan a medias.
 *
 * <p>Tambien actualiza {@code MateriaPrima.costoUnitarioReferencial} al ultimo costo comprado — solo
 * informativo, para prellenar formularios; no reescribe el costo ya congelado de lotes anteriores.
 */
@Service
@RequiredArgsConstructor
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProveedorCompraRepository proveedorRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final InventarioService inventarioService;
    private final AuditoriaService auditoriaService;
    private final AsientoContableService asientoContableService;

    private static final String ENTIDAD = "Compras";

    /**
     * Mapea a DTO dentro de la transaccion: {@code Compra.items} y {@code CompraItem.materiaPrima}
     * son LAZY, y el response los recorre. Devolver la entidad para mapearla en el controller
     * revienta con LazyInitializationException porque la sesion ya se cerro.
     */
    @Transactional(readOnly = true)
    public Page<CompraResponse> listar(LocalDate desde, LocalDate hasta, Pageable pageable) {
        Page<Compra> pagina = (desde == null && hasta == null)
                ? compraRepository.findAllByOrderByFechaDescIdDesc(pageable)
                : compraRepository.findByFechaBetweenOrderByFechaDescIdDesc(
                        desde != null ? desde : LocalDate.now().minusMonths(1), hasta != null ? hasta : LocalDate.now(), pageable);
        return pagina.map(CompraResponse::from);
    }

    @Transactional
    public Compra registrar(CompraRequest req) {
        if (req.fecha().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("La compra no puede registrarse con fecha futura.");
        }
        ProveedorCompra proveedor = proveedorRepository
                .findById(req.proveedorCompraId())
                .filter(ProveedorCompra::isActivo)
                .orElseThrow(() -> NotFoundException.of("Proveedor de compra", req.proveedorCompraId()));

        Compra compra = new Compra();
        compra.setProveedor(proveedor);
        compra.setFecha(req.fecha());
        compra.setDocumento(req.documento());
        compra.setObservaciones(req.observaciones());
        Compra guardada = compraRepository.save(compra);

        for (CompraItemRequest itemReq : req.items()) {
            MateriaPrima materiaPrima = materiaPrimaRepository
                    .findById(itemReq.materiaPrimaId())
                    .filter(MateriaPrima::isActivo)
                    .orElseThrow(() -> NotFoundException.of("Materia prima", itemReq.materiaPrimaId()));
            // La leche entra al almacen al recibir rutas o entregas directas, con su liquidacion al
            // proveedor: comprarla aqui la contaria dos veces. La web ya no la ofrece; esto cubre la API.
            if (materiaPrima.isLecheAcopio()) {
                throw new BusinessRuleException(
                        "La leche no se compra: entra al almacén al recibir las rutas o una entrega directa.");
            }

            CompraItem item = new CompraItem();
            item.setCompra(guardada);
            item.setMateriaPrima(materiaPrima);
            item.setCantidad(itemReq.cantidad());
            item.setCostoUnitario(itemReq.costoUnitario());
            guardada.getItems().add(item);

            inventarioService.registrarEntradaCompra(materiaPrima, itemReq.cantidad(), req.fecha(), guardada.getId());

            // Informativo: el costo de referencia para prellenar el siguiente formulario, no la
            // fuente de verdad de ningun costo de lote ya cerrado.
            materiaPrima.setCostoUnitarioReferencial(itemReq.costoUnitario());
            materiaPrimaRepository.save(materiaPrima);
        }
        Compra resultado = compraRepository.save(guardada);

        registrarAsientoDeCompra(resultado);

        auditoriaService.registrar(
                "Compra de materia prima",
                ENTIDAD,
                proveedor.getNombre() + " · " + resultado.getItems().size() + " item(s) · S/ " + resultado.total());
        return resultado;
    }

    /** {@link #anular} mapeado dentro de la transaccion: proveedor e items son LAZY. */
    @Transactional
    public CompraResponse anularYResponder(Long id, String motivo) {
        return CompraResponse.from(anular(id, motivo));
    }

    /**
     * Anula una compra registrada por error (V59): saca del almacen lo que entro por ella y revierte
     * su asiento. Solo si todo sigue en el almacen; si parte ya se uso en produccion, la salida
     * dejaria el stock negativo y lo que corresponde es un ajuste de inventario. No se borra nada.
     */
    @Transactional
    public Compra anular(Long id, String motivoRecibido) {
        String motivo = motivoRecibido == null ? "" : motivoRecibido.trim();
        if (motivo.length() < 3) {
            throw new BusinessRuleException("La anulación de una compra exige un motivo.");
        }
        Compra compra = compraRepository.findById(id).orElseThrow(() -> NotFoundException.of("Compra", id));
        if (!compra.isActivo()) {
            throw new BusinessRuleException("La compra ya está anulada.");
        }

        // Cantidad por materia prima (una compra puede repetir un insumo en dos lineas), bloqueadas
        // en orden ascendente de id como al producir, para no cruzarse con un lote simultaneo.
        java.util.Map<Long, BigDecimal> porMateria = new java.util.TreeMap<>();
        java.util.Map<Long, MateriaPrima> materias = new java.util.HashMap<>();
        for (CompraItem item : compra.getItems()) {
            porMateria.merge(item.getMateriaPrima().getId(), item.getCantidad(), BigDecimal::add);
            materias.put(item.getMateriaPrima().getId(), item.getMateriaPrima());
        }
        LocalDate hoy = LocalDate.now();
        String nota = "Anulación de compra: " + motivo;
        for (var e : porMateria.entrySet()) {
            MateriaPrima materiaPrima = inventarioService.bloquearMateriaPrima(e.getKey());
            BigDecimal disponible = inventarioService.saldoMateriaPrima(materiaPrima.getId());
            if (disponible.compareTo(e.getValue()) < 0) {
                throw new BusinessRuleException("No se puede anular: de " + materias.get(e.getKey()).getNombre()
                        + " entraron " + e.getValue().stripTrailingZeros().toPlainString() + " y en el almacén quedan "
                        + disponible.stripTrailingZeros().toPlainString()
                        + " (el resto ya se usó en producción). Corrígelo con un ajuste de inventario.");
            }
            inventarioService.registrarAjusteSalida(
                    materiaPrima, e.getValue(), hoy, org.ramiro.huata.inventario.DocumentoOrigenTipo.COMPRA, compra.getId(), nota);
        }

        compra.setActivo(false);
        compra.setMotivoAnulacion(motivo);
        Compra guardada = compraRepository.save(compra);

        BigDecimal total = guardada.total();
        if (total.signum() > 0) {
            asientoContableService.registrar(
                    hoy,
                    "Anulación: compra a " + guardada.getProveedor().getNombre(),
                    "Por la reversión de la compra a " + guardada.getProveedor().getNombre() + ", documento "
                            + (guardada.getDocumento() != null ? guardada.getDocumento() : "s/n") + " (" + motivo + ").",
                    OrigenAsiento.ANULACION_COMPRA,
                    guardada.getId(),
                    List.of(LineaContable.debe("4212", total), LineaContable.haber("2411", total)));
        }

        auditoriaService.registrar(
                "Anulación de compra",
                ENTIDAD,
                guardada.getProveedor().getNombre() + " · S/ " + total + " · " + motivo);
        return guardada;
    }

    /**
     * Debe materia prima/materiales auxiliares, haber cuentas por pagar: se asume compra a credito
     * porque {@code Compra} no distingue medio de pago todavia (queseria.html si lo hacia para leche
     * vs. insumos, pero eso exigiria un campo que hoy no existe en este modelo).
     */
    private void registrarAsientoDeCompra(Compra compra) {
        BigDecimal total = compra.total();
        if (total.signum() <= 0) {
            return;
        }
        asientoContableService.registrar(
                compra.getFecha(),
                "Compra de materia prima e insumos, " + compra.getProveedor().getNombre(),
                "Por la compra de materia prima e insumos a " + compra.getProveedor().getNombre()
                        + " (a credito), documento " + (compra.getDocumento() != null ? compra.getDocumento() : "s/n") + ".",
                OrigenAsiento.COMPRA,
                compra.getId(),
                List.of(LineaContable.debe("2411", total), LineaContable.haber("4212", total)));
    }
}
