package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.RetiroVerificacion;
import com.franco.dev.domain.financiero.enums.ResultadoVerificacionRetiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RetiroVerificacionRepository extends JpaRepository<RetiroVerificacion, Long> {

    /**
     * Lo contado en la verificación vigente, por retiro y moneda: {@code [retiroId, sucursalId, monedaId, suma]}.
     * Es lo que la verificación acreditó en caja mayor. El id del retiro no es global: quien llama filtra por la
     * clave (retiro, sucursal).
     */
    @Query("select v.retiroId, v.sucursalId, d.moneda.id, sum(d.contado) from RetiroVerificacionDetalle d "
            + "join d.verificacion v where v.anulada = false and v.retiroId in :retiroIds "
            + "group by v.retiroId, v.sucursalId, d.moneda.id")
    List<Object[]> sumarContadoVigente(@Param("retiroIds") java.util.Collection<Long> retiroIds);

    /** La verificación vigente de un retiro, si tiene. Las anuladas no cuentan. */
    @Query("select v from RetiroVerificacion v where v.retiroId = :retiroId "
            + "and v.sucursalId = :sucId and v.anulada = false")
    Optional<RetiroVerificacion> findVigente(@Param("retiroId") Long retiroId, @Param("sucId") Long sucId);

    /**
     * Solo si esta anulada, leido de la base y no de la instancia ya cargada. Se llama <b>despues</b> de
     * tomar el lock del retiro, que es lo que serializa las anulaciones de sus verificaciones.
     */
    @Query("select coalesce(v.anulada, false) from RetiroVerificacion v where v.id = :id")
    Optional<Boolean> findAnuladaById(@Param("id") Long id);

    /**
     * Verificaciones con diferencia, para la bandeja.
     *
     * Se filtra por resultado y no por la existencia de un caso: un caso puede haberse
     * resuelto y la verificación sigue siendo el registro de que hubo diferencia.
     */
    List<RetiroVerificacion> findByResultadoAndAnuladaFalseOrderByCreadoEnDesc(
            ResultadoVerificacionRetiro resultado);
}
