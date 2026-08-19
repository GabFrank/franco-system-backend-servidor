package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import javax.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LiquidacionSueldoRepository extends HelperRepository<LiquidacionSueldo, Long> {

    default Class<LiquidacionSueldo> getEntityClass() {
        return LiquidacionSueldo.class;
    }

    List<LiquidacionSueldo> findByFuncionarioIdOrderByPeriodoDesc(Long funcionarioId);

    /**
     * Liquidaciones de un funcionario EN UN ESTADO, paginadas.
     *
     * ⚠️ El estado va en la consulta, no se filtra despues en memoria. El
     * mobile solo muestra las PAGADA: si se paginara sin filtrar y el filtro
     * quedara en Java, una pagina de 10 filas podria devolver 2 —o ninguna—,
     * y el cliente no tendria forma de saber si eso significa "no hay mas".
     */
    List<LiquidacionSueldo> findByFuncionarioIdAndEstadoOrderByPeriodoDesc(
            Long funcionarioId, LiquidacionSueldoEstado estado, Pageable pageable);

    Optional<LiquidacionSueldo> findByFuncionarioIdAndPeriodo(Long funcionarioId, String periodo);

    List<LiquidacionSueldo> findByPeriodoOrderByIdAsc(String periodo);

    List<LiquidacionSueldo> findByEstadoOrderByPeriodoDesc(LiquidacionSueldoEstado estado);

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select l from LiquidacionSueldo l where " +
            "(:funcionarioId is null or l.funcionario.id = :funcionarioId) and " +
            "(:periodo is null or l.periodo = :periodo) and " +
            "(:estado is null or l.estado = :estado) " +
            "order by l.periodo desc, l.id desc")
    Page<LiquidacionSueldo> findPage(@Param("funcionarioId") Long funcionarioId,
                                     @Param("periodo") String periodo,
                                     @Param("estado") LiquidacionSueldoEstado estado,
                                     Pageable pageable);

    // Serie de nómina por período (los períodos son 'YYYY-MM', ordenan lexicográfico).
    // Solo liquidaciones APROBADA/PAGADA (lo efectivamente liquidado). Native SQL:
    // el enum se guarda como texto (@Enumerated(STRING)).
    @Query(value = "SELECT l.periodo AS periodo, COUNT(l.id) AS cantidad, " +
            "COALESCE(SUM(l.total_neto), 0) AS monto " +
            "FROM rrhh.liquidacion_sueldo l " +
            "WHERE l.periodo BETWEEN :periodoInicio AND :periodoFin " +
            "AND l.estado IN ('APROBADA', 'PAGADA') " +
            "GROUP BY l.periodo ORDER BY l.periodo",
            nativeQuery = true)
    List<Object[]> nominaSeriePorMesRaw(@Param("periodoInicio") String periodoInicio,
                                        @Param("periodoFin") String periodoFin);

    /** Documento dueno de una obligacion de pago (puente tesoreria, V199.5). */
    LiquidacionSueldo findBySolicitudPagoId(Long solicitudPagoId);

    /**
     * Toma el documento con lock pesimista antes de resolver su obligacion de pago.
     *
     * <p>Sin esto, dos pedidos de pago concurrentes sobre el mismo documento (doble click,
     * reintento de red) leen los dos {@code solicitud_pago_id == null}, crean una solicitud
     * cada uno y terminan pagando dos veces el mismo sueldo. El indice unico no lo impide:
     * evita que dos documentos compartan una solicitud, no que un documento reciba dos.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from LiquidacionSueldo e where e.id = :id")
    java.util.Optional<LiquidacionSueldo> lockById(@org.springframework.data.repository.query.Param("id") Long id);
}
