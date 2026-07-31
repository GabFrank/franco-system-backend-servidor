package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
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

    /** Filtro combinado de movimientos (todos opcionales salvo la caja). soloActivos=true oculta anulados/anulaciones. */
    @Query("select m from MovimientoCajaVirtual m where m.cajaVirtual.id = :cajaId "
            + "and (:desde is null or m.creadoEn >= :desde) "
            + "and (:fin is null or m.creadoEn <= :fin) "
            + "and (:tipo is null or m.tipoMovimiento = :tipo) "
            + "and (:soloActivos = false or m.activo = true) "
            + "order by m.creadoEn desc")
    Page<MovimientoCajaVirtual> filter(@Param("cajaId") Long cajaId,
                                       @Param("desde") LocalDateTime desde,
                                       @Param("fin") LocalDateTime fin,
                                       @Param("tipo") CajaVirtualTipoMovimiento tipo,
                                       @Param("soloActivos") boolean soloActivos,
                                       Pageable pageable);
}
