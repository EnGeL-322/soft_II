package org.ramiro.huata.inventario;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

    Page<MovimientoInventario> findByMateriaPrima_IdOrderByFechaDescIdDesc(Long materiaPrimaId, Pageable pageable);

    Page<MovimientoInventario> findByLoteQueso_IdOrderByFechaDescIdDesc(Long loteQuesoId, Pageable pageable);

    Page<MovimientoInventario> findAllByOrderByFechaDescIdDesc(Pageable pageable);

    /**
     * Saldo de una materia prima: suma de entradas menos suma de salidas, en una sola consulta
     * agregada. {@code ENTRADA_COMPRA}, {@code ENTRADA_ACOPIO} (V31) y {@code AJUSTE_ENTRADA} son las
     * unicas entradas posibles para una materia prima. La lista esta escrita a mano: KardexTests
     * comprueba que coincida con {@link TipoMovimientoInventario#esEntrada()}, porque un tipo nuevo
     * olvidado aqui RESTA en vez de sumar (paso con ENTRADA_ACOPIO) (los tipos de queso terminado nunca aparecen en una fila con materia_prima_id
     * seteado, por la restriccion de la tabla).
     */
    @Query(
            """
            select coalesce(sum(case when m.tipo in ('ENTRADA_COMPRA', 'ENTRADA_ACOPIO', 'AJUSTE_ENTRADA') then m.cantidad else -m.cantidad end), 0)
            from MovimientoInventario m
            where m.materiaPrima.id = :materiaPrimaId
            """)
    BigDecimal saldoMateriaPrima(@Param("materiaPrimaId") Long materiaPrimaId);

    /** Lo mismo para varias materias primas a la vez, para no hacer una consulta por fila de tabla. */
    @Query(
            """
            select m.materiaPrima.id as itemId,
                   coalesce(sum(case when m.tipo in ('ENTRADA_COMPRA', 'ENTRADA_ACOPIO', 'AJUSTE_ENTRADA') then m.cantidad else -m.cantidad end), 0) as saldo
            from MovimientoInventario m
            where m.materiaPrima.id in :materiaPrimaIds
            group by m.materiaPrima.id
            """)
    List<SaldoPorItem> saldosMateriasPrimas(@Param("materiaPrimaIds") Collection<Long> materiaPrimaIds);

    /**
     * Saldo (stock disponible) de un lote de queso terminado: entrada de produccion menos salidas de
     * venta, mas cualquier reversion por anulacion. Reemplaza el calculo anterior
     * (unidades_producidas - ventas activas) por la misma cuenta sobre el kardex unificado.
     */
    @Query(
            """
            select coalesce(sum(case when m.tipo in ('ENTRADA_PRODUCCION_TERMINADO', 'ENTRADA_ANULACION_VENTA', 'AJUSTE_ENTRADA') then m.cantidad else -m.cantidad end), 0)
            from MovimientoInventario m
            where m.loteQueso.id = :loteId
            """)
    BigDecimal saldoLoteQueso(@Param("loteId") Long loteId);

    @Query(
            """
            select m.loteQueso.id as itemId,
                   coalesce(sum(case when m.tipo in ('ENTRADA_PRODUCCION_TERMINADO', 'ENTRADA_ANULACION_VENTA', 'AJUSTE_ENTRADA') then m.cantidad else -m.cantidad end), 0) as saldo
            from MovimientoInventario m
            where m.loteQueso.id in :loteIds
            group by m.loteQueso.id
            """)
    List<SaldoPorItem> saldosLotesQueso(@Param("loteIds") Collection<Long> loteIds);

    /** Proyeccion del agregado: id del item (materia prima o lote) y su saldo. */
    interface SaldoPorItem {
        Long getItemId();

        BigDecimal getSaldo();
    }

    /**
     * Lo que ya entro (neto de correcciones) de una materia prima por un documento concreto. Lo usa
     * la leche del acopio para mover solo la diferencia cuando cambia lo recibido o su calidad. Misma
     * lista de entradas que {@link #saldoMateriaPrima}: KardexTests comprueba las dos.
     */
    @Query(
            """
            select coalesce(sum(case when m.tipo in ('ENTRADA_COMPRA', 'ENTRADA_ACOPIO', 'AJUSTE_ENTRADA') then m.cantidad else -m.cantidad end), 0)
            from MovimientoInventario m
            where m.materiaPrima.id = :materiaPrimaId
              and m.documentoOrigenTipo = :origen
              and m.documentoOrigenId = :origenId
            """)
    BigDecimal netoPorOrigen(
            @Param("materiaPrimaId") Long materiaPrimaId,
            @Param("origen") DocumentoOrigenTipo origen,
            @Param("origenId") Long origenId);
}
