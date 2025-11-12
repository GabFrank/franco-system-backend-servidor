package com.franco.dev.repository.financiero;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.repository.HelperRepository;

public interface TimbradoRepository extends HelperRepository<Timbrado, Long> {

    default Class<Timbrado> getEntityClass() {
        return Timbrado.class;
    }

    List<Timbrado> findAllByOrderByIdAsc();    

    @Query(value = "SELECT * FROM financiero.timbrado t WHERE t.id != 0 AND (?1 IS NULL OR " + 
    "(CAST(t.id AS text) LIKE CONCAT('%', ?1, '%') OR UPPER(t.razon_social) LIKE UPPER(CONCAT('%', ?1, '%')) OR UPPER(t.ruc) " + 
    "LIKE UPPER(CONCAT('%', ?1, '%')) OR UPPER(t.numero) LIKE UPPER(CONCAT('%', ?1, '%')))) ORDER BY t.id ASC", nativeQuery = true)
    public List<Timbrado> findByAll(String texto);

    @Query(value = "SELECT * FROM financiero.timbrado t WHERE t.id != 0 AND (?1 IS NULL OR UPPER(t.numero) LIKE UPPER(CONCAT('%', ?1, '%'))) ORDER BY t.id ASC", 
           countQuery = "SELECT count(*) FROM financiero.timbrado t WHERE t.id != 0 AND (?1 IS NULL OR UPPER(t.numero) LIKE UPPER(CONCAT('%', ?1, '%')))", 
           nativeQuery = true)
    public Page<Timbrado> findByNumeroLike(String numero, Pageable page);

    @Query("SELECT COUNT(t) > 0 FROM Timbrado t WHERE t.activo = true AND (:excludeId IS NULL OR t.id != :excludeId)")
    public Boolean existeTimbradoActivo(@Param("excludeId") Long excludeId);
}