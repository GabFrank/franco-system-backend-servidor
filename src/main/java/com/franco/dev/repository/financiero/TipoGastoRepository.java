package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TipoGastoRepository extends HelperRepository<TipoGasto, Long> {

    default Class<TipoGasto> getEntityClass() {
        return TipoGasto.class;
    }

    @Query(value = "select * from financiero.tipo_gasto tg " +
            "where tg.clasificacion_gasto_id is null order by tg.id asc", nativeQuery = true)
    public List<TipoGasto> findRoot();

    @Query(value = "select * from financiero.tipo_gasto tg " +
            "where CAST(tg.id as text) like concat('%', ?1, '%') or upper(tg.descripcion) like concat('%', ?1, '%')", nativeQuery = true)
    public List<TipoGasto> findByAll(String texto);

//    Moneda findByPaisId(Long id);

    List<TipoGasto> findByClasificacionGastoId(Long id);

    @Query(value = "SELECT tg.* FROM financiero.tipo_gasto tg " +
            "WHERE (CAST(:naturaleza AS text) IS NULL OR cast(tg.tipo_naturaleza as text) = CAST(:naturaleza AS text)) " +
            "AND (CAST(:texto AS text) IS NULL OR UPPER(tg.descripcion) LIKE CONCAT('%', UPPER(CAST(:texto AS text)), '%') " +
            "OR CAST(tg.id AS text) LIKE CONCAT('%', CAST(:texto AS text), '%')) " +
            "ORDER BY tg.id ASC",
            countQuery = "SELECT count(*) FROM financiero.tipo_gasto tg " +
            "WHERE (CAST(:naturaleza AS text) IS NULL OR cast(tg.tipo_naturaleza as text) = CAST(:naturaleza AS text)) " +
            "AND (CAST(:texto AS text) IS NULL OR UPPER(tg.descripcion) LIKE CONCAT('%', UPPER(CAST(:texto AS text)), '%') " +
            "OR CAST(tg.id AS text) LIKE CONCAT('%', CAST(:texto AS text), '%'))",
            nativeQuery = true)
    Page<TipoGasto> filterTipoGastos(@Param("naturaleza") String naturaleza,
                                    @Param("texto") String texto,
                                    Pageable pageable);

}