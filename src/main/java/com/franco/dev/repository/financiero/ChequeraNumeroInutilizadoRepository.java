package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.ChequeraNumeroInutilizado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChequeraNumeroInutilizadoRepository extends JpaRepository<ChequeraNumeroInutilizado, Long> {

    /** Números inutilizados vigentes de una chequera desde {@code desde}. */
    @Query("select i.numero from ChequeraNumeroInutilizado i " +
            "where i.chequera.id = :chequeraId and i.revertidoEn is null and i.numero >= :desde")
    List<Long> numerosVigentesDesde(@Param("chequeraId") Long chequeraId, @Param("desde") Long desde);

    Optional<ChequeraNumeroInutilizado> findFirstByChequeraIdAndNumeroAndRevertidoEnIsNull(Long chequeraId, Long numero);

    /** Para la lista de la chequera: vigentes y revertidos, los más nuevos primero. */
    List<ChequeraNumeroInutilizado> findByChequeraIdOrderByIdDesc(Long chequeraId);

    /** Para el tablero de cheques: los vigentes de todas las chequeras. */
    List<ChequeraNumeroInutilizado> findByRevertidoEnIsNullOrderByIdDesc();
}
