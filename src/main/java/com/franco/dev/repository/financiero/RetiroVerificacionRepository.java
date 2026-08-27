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

    /** La verificación vigente de un retiro, si tiene. Las anuladas no cuentan. */
    @Query("select v from RetiroVerificacion v where v.retiroId = :retiroId "
            + "and v.sucursalId = :sucId and v.anulada = false")
    Optional<RetiroVerificacion> findVigente(@Param("retiroId") Long retiroId, @Param("sucId") Long sucId);

    /**
     * Verificaciones con diferencia, para la bandeja.
     *
     * Se filtra por resultado y no por la existencia de un caso: un caso puede haberse
     * resuelto y la verificación sigue siendo el registro de que hubo diferencia.
     */
    List<RetiroVerificacion> findByResultadoAndAnuladaFalseOrderByCreadoEnDesc(
            ResultadoVerificacionRetiro resultado);
}
