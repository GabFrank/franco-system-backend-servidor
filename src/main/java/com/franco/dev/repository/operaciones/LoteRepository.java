package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoteRepository extends HelperRepository<Lote, Long> {

    /**
     * Busca por la clave natural del lote. El número ya debe venir normalizado (trim + mayúsculas).
     */
    @Query("select e from Lote e where e.producto.id = :productoId and e.numeroLote = :numeroLote")
    Optional<Lote> findByProductoIdAndNumeroLote(@Param("productoId") Long productoId,
                                                 @Param("numeroLote") String numeroLote);

    /**
     * Lotes de un producto, ordenados por FEFO: primero los que hay que retirar antes.
     * Los lotes sin fecha conocida quedan al final.
     */
    @Query("select e from Lote e where e.producto.id = :productoId " +
            "order by case when coalesce(e.fechaRetiro, e.fechaVencimiento) is null then 1 else 0 end, " +
            "coalesce(e.fechaRetiro, e.fechaVencimiento) asc, e.id asc")
    List<Lote> findByProductoId(@Param("productoId") Long productoId);
}
