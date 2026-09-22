package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.NotaCreditoItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotaCreditoItemRepository extends HelperRepository<NotaCreditoItem, EmbebedPrimaryKey> {

    default Class<NotaCreditoItem> getEntityClass() {
        return NotaCreditoItem.class;
    }

    /** Siguiente id de la secuencia (ver NotaCreditoRepository.siguienteId). */
    @Query(value = "SELECT nextval('financiero.nota_credito_item_id_seq')", nativeQuery = true)
    Long siguienteId();

    @Query("SELECT i FROM NotaCreditoItem i WHERE i.notaCreditoId = :notaCreditoId "
            + "AND i.sucursalId = :sucursalId ORDER BY i.id ASC")
    List<NotaCreditoItem> findByNotaCredito(@Param("notaCreditoId") Long notaCreditoId,
                                            @Param("sucursalId") Long sucursalId);
}
