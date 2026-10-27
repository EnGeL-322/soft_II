package org.ramiro.huata.reportes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.compras.Compra;
import org.ramiro.huata.compras.CompraItem;
import org.ramiro.huata.compras.CompraRepository;
import org.ramiro.huata.compras.ProveedorCompra;
import org.ramiro.huata.compras.ProveedorCompraRepository;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.UnidadMedida;
import org.ramiro.huata.liquidaciones.EstadoLiquidacion;
import org.ramiro.huata.liquidaciones.Liquidacion;
import org.ramiro.huata.liquidaciones.LiquidacionRepository;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.quesos.CondicionVenta;
import org.ramiro.huata.quesos.EstadoCobro;
import org.ramiro.huata.quesos.LoteQueso;
import org.ramiro.huata.quesos.LoteQuesoRepository;
import org.ramiro.huata.quesos.MedioCobro;
import org.ramiro.huata.quesos.OrigenLeche;
import org.ramiro.huata.quesos.TipoComprador;
import org.ramiro.huata.produccion.ProductoRepository;
import org.ramiro.huata.quesos.VentaQueso;
import org.ramiro.huata.quesos.VentaQuesoRepository;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReporteQuesoServiceTests {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Autowired private ReporteQuesoService reporteQuesoService;
    @Autowired private RutaRepository rutaRepository;
    @Autowired private ProductorRepository productorRepository;
    @Autowired private RecojoRepository recojoRepository;
    @Autowired private LoteQuesoRepository loteQuesoRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private VentaQuesoRepository ventaQuesoRepository;
    @Autowired private ProveedorCompraRepository proveedorCompraRepository;
    @Autowired private MateriaPrimaRepository materiaPrimaRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private LiquidacionRepository liquidacionRepository;

    @Test
    void calculaVentaRendimientoYRegistroDeUnDiaCompleto() {
        LocalDate fecha = LocalDate.of(2026, 6, 4); // jueves

        Ruta ruta = new Ruta();
        ruta.setNombre("Ruta test");
        ruta.setZona("ZONA-TEST-" + UUID.randomUUID());
        rutaRepository.save(ruta);

        Productor productor = new Productor();
        productor.setCodigo("PR-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        productor.setNombres("Prueba");
        productor.setApellidos("Reporte");
        productor.setEstado(EstadoProductor.ACTIVO);
        productor.setRuta(ruta);
        productorRepository.save(productor);

        Recojo r1 = new Recojo();
        r1.setClientUuid(UUID.randomUUID().toString());
        r1.setProductor(productor);
        r1.setRuta(ruta);
        r1.setFechaHora(fecha.atTime(6, 0).atZone(ZONA).toInstant());
        r1.setLitros(60.0);
        recojoRepository.save(r1);

        Recojo r2 = new Recojo();
        r2.setClientUuid(UUID.randomUUID().toString());
        r2.setProductor(productor);
        r2.setRuta(ruta);
        r2.setFechaHora(fecha.atTime(7, 0).atZone(ZONA).toInstant());
        r2.setLitros(40.0);
        recojoRepository.save(r2);
        // 100 litros acopiados en total ese dia en esa zona

        LoteQueso lote = new LoteQueso();
        lote.setCodigo("L-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        lote.setFecha(fecha);
        lote.setProducto(productoRepository.findByNombreIgnoreCase("Queso fresco").orElseThrow());
        lote.setOrigenLeche(OrigenLeche.ACOPIO_DEL_DIA);
        lote.setLitrosDestinados(100.0);
        lote.setUnidadesProducidas(10); // rendimiento esperado: 100L / 10 quesos = 10.0
        lote.setResponsable("Responsable test");
        loteQuesoRepository.save(lote);

        VentaQueso venta = new VentaQueso();
        venta.setLote(lote);
        venta.setFecha(fecha);
        venta.setCliente("Comprador test");
        venta.setUnidades(6);
        venta.setTipoComprador(TipoComprador.PUBLICO_GENERAL);
        venta.setPrecioUnitario(15.0);
        venta.setCondicion(CondicionVenta.CONTADO);
        venta.setMedioCobro(MedioCobro.EFECTIVO);
        venta.setEstado(EstadoCobro.COBRADO);
        ventaQuesoRepository.save(venta);
        // ingresos del dia: 6 * 15.0 = 90.0; diferencia producido-vendido: 10 - 6 = 4

        ProveedorCompra proveedorCompra = new ProveedorCompra();
        proveedorCompra.setCodigo("PC-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        proveedorCompra.setNombre("Ferreteria test");
        proveedorCompraRepository.save(proveedorCompra);

        MateriaPrima insumo = new MateriaPrima();
        insumo.setNombre("Diesel test " + UUID.randomUUID());
        insumo.setUnidadMedida(UnidadMedida.LITRO);
        materiaPrimaRepository.save(insumo);

        Compra compra = new Compra();
        compra.setProveedor(proveedorCompra);
        compra.setFecha(fecha);
        compra.setDocumento("F001-001");
        CompraItem item = new CompraItem();
        item.setCompra(compra);
        item.setMateriaPrima(insumo);
        item.setCantidad(new BigDecimal("20"));
        item.setCostoUnitario(new BigDecimal("3.00"));
        compra.setItems(List.of(item));
        compraRepository.save(compra);
        // egreso del ciclo: 20 * 3.00 = 60.0

        Liquidacion liquidacion = new Liquidacion();
        liquidacion.setProductor(productor);
        liquidacion.setPeriodoInicio(fecha);
        liquidacion.setPeriodoFin(fecha);
        liquidacion.setLitrosTotales(100.0);
        liquidacion.setMontoTotal(170.0);
        liquidacion.setAjustesTotal(0.0);
        liquidacion.setMontoNeto(170.0);
        liquidacion.setEstado(EstadoLiquidacion.CLOSED);
        liquidacionRepository.save(liquidacion);
        // pago a proveedores de leche del ciclo: 170.0

        ReporteQuesoSemanalResponse reporte = reporteQuesoService.semanal(fecha, fecha);

        assertEquals(1, reporte.ventaPorDia().size());
        VentaQuesoDiaRow dia = reporte.ventaPorDia().get(0);
        assertEquals(10, dia.producidos());
        assertEquals(6, dia.vendidos());
        assertEquals(6, dia.despachados(), "despachado debe reportarse igual a vendido");
        assertEquals(4, dia.diferencia());
        assertEquals(4, reporte.totalQuesosEnDescuento());

        assertEquals(1, reporte.rendimientoPorDia().size());
        RendimientoDiaRow rendimientoDia = reporte.rendimientoPorDia().get(0);
        assertEquals(100.0, rendimientoDia.totalAcopio());
        assertEquals(10, rendimientoDia.produccion());
        assertEquals(10.0, rendimientoDia.rendimiento());

        assertEquals(90.0, reporte.totalIngresos());
        assertEquals(170.0, reporte.totalPagoProveedoresLeche());
        assertEquals(60.0, reporte.totalEgresos());
        assertEquals(90.0 - 170.0 - 60.0, reporte.totalSaldo(), 0.001);

        assertTrue(reporte.zonas().contains(ruta.getZona()));
        int idxZona = reporte.zonas().indexOf(ruta.getZona());
        assertEquals(100.0, rendimientoDia.litrosPorZona().get(idxZona));
    }

    @Test
    void usaElCicloJuevesMiercolesVigenteCuandoNoSeIndicanFechas() {
        ReporteQuesoSemanalResponse reporte = reporteQuesoService.semanal(null, null);
        assertEquals(6, reporte.desde().until(reporte.hasta()).getDays());
    }

    @Test
    void rechazaRangoInvertido() {
        LocalDate a = LocalDate.of(2026, 1, 10);
        LocalDate b = LocalDate.of(2026, 1, 5);
        assertThrows(BusinessRuleException.class, () -> reporteQuesoService.semanal(a, b));
    }
}
