package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoCajaVirtualRepository extends JpaRepository<MovimientoCajaVirtual, Long> {

    Page<MovimientoCajaVirtual> findByCajaVirtualIdOrderByCreadoEnDesc(Long cajaVirtualId, Pageable pageable);

    Page<MovimientoCajaVirtual> findByCajaVirtualIdAndCreadoEnBetweenOrderByCreadoEnDesc(
            Long cajaVirtualId, LocalDateTime inicio, LocalDateTime fin, Pageable pageable);

    List<MovimientoCajaVirtual> findByCajaVirtualIdAndActivoTrue(Long cajaVirtualId);

    /** Patas de caja activas de una operación dueña (para revertir todas al anularla). */
    List<MovimientoCajaVirtual> findByOrigenTipoAndOrigenIdAndActivoTrue(OrigenMovimientoTipo origenTipo, Long origenId);

    /**
     * Filtro combinado de movimientos (todos opcionales salvo la caja). soloActivos=true oculta anulados.
     * {@code tipo} llega como String (name del enum) y se compara contra la columna casteada a texto:
     * el enum es nativo de Postgres y un bind param nulo de enum rompe con 42P18. Castear a texto lo evita.
     */
    @Query("select m from MovimientoCajaVirtual m where m.cajaVirtual.id = :cajaId "
            + "and (cast(:desde as timestamp) is null or m.creadoEn >= :desde) "
            + "and (cast(:fin as timestamp) is null or m.creadoEn <= :fin) "
            + "and (:tipo is null or cast(m.tipoMovimiento as string) = :tipo) "
            + "and (:monedaId is null or m.moneda.id = :monedaId) "
            + "and (:soloActivos = false or m.activo = true) "
            + "order by m.creadoEn desc")
    Page<MovimientoCajaVirtual> filter(@Param("cajaId") Long cajaId,
                                       @Param("desde") LocalDateTime desde,
                                       @Param("fin") LocalDateTime fin,
                                       @Param("tipo") String tipo,
                                       @Param("monedaId") Long monedaId,
                                       @Param("soloActivos") boolean soloActivos,
                                       Pageable pageable);
}
