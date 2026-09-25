package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.MovimientoBancario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoBancarioRepository extends JpaRepository<MovimientoBancario, Long> {
    Page<MovimientoBancario> findByCuentaBancariaIdOrderByCreadoEnDesc(Long cuentaBancariaId, Pageable pageable);

    /**
     * Filtro combinado de movimientos de una cuenta (todos opcionales salvo la cuenta).
     * soloActivos=true oculta los anulados; {@code anulado} null cuenta como vigente.
     * {@code tipo} llega como String (name del enum), igual que en MovimientoCajaVirtualRepository.
     */
    String FILTER_JPQL = "select m from MovimientoBancario m where m.cuentaBancaria.id = :cuentaId "
            + "and (cast(:desde as timestamp) is null or m.creadoEn >= :desde) "
            + "and (cast(:fin as timestamp) is null or m.creadoEn <= :fin) "
            + "and (:tipo is null or cast(m.tipoMovimiento as string) = :tipo) "
            + "and (:soloActivos = false or m.anulado is null or m.anulado = false) "
            + "order by m.creadoEn desc";

    @Query(FILTER_JPQL)
    Page<MovimientoBancario> filter(@Param("cuentaId") Long cuentaId,
                                    @Param("desde") LocalDateTime desde,
                                    @Param("fin") LocalDateTime fin,
                                    @Param("tipo") String tipo,
                                    @Param("soloActivos") boolean soloActivos,
                                    Pageable pageable);

    /** Mismo filtro sin paginar, para el reporte. */
    @Query(FILTER_JPQL)
    List<MovimientoBancario> filterList(@Param("cuentaId") Long cuentaId,
                                        @Param("desde") LocalDateTime desde,
                                        @Param("fin") LocalDateTime fin,
                                        @Param("tipo") String tipo,
                                        @Param("soloActivos") boolean soloActivos);

    /** Patas bancarias no anuladas de una operación dueña (para revertir todas al anularla). */
    List<MovimientoBancario> findByOrigenTipoAndOrigenIdAndAnuladoFalse(String origenTipo, Long origenId);
}
