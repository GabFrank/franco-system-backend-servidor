package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.NotaRemisionItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotaRemisionItemRepository extends HelperRepository<NotaRemisionItem, EmbebedPrimaryKey> {

    default Class<NotaRemisionItem> getEntityClass() {
        return NotaRemisionItem.class;
    }

    @Query("SELECT i FROM NotaRemisionItem i WHERE i.notaRemisionId = :notaRemisionId "
            + "AND i.sucursalId = :sucursalId ORDER BY i.id ASC")
    List<NotaRemisionItem> findByNotaRemision(@Param("notaRemisionId") Long notaRemisionId,
                                              @Param("sucursalId") Long sucursalId);
}
