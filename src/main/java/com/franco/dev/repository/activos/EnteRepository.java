package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EnteRepository extends HelperRepository<Ente, Long> {

    default Class<Ente> getEntityClass() {
        return Ente.class;
    }

    List<Ente> findByTipoEnte(TipoEnte tipoEnte);

    @Query("select e from Ente e where e.tipoEnte = ?1 and e.referenciaId = ?2")
    Optional<Ente> findByTipoEnteAndReferenciaId(TipoEnte tipoEnte, Long referenciaId);

    @Query("select e from Ente e where e.activo = true")
    List<Ente> findAllActivos();

    @Query("select e from Ente e where " +
            "(:texto is null or trim(:texto) = '' or cast(e.tipoEnte as text) = :texto or upper(e.descripcion) like upper(concat('%', :texto, '%'))) " +
            "and (:sucursalId is null or " +
            "exists (select es from EnteSucursal es where es.ente.id = e.id and es.sucursal.id = :sucursalId)) " +
            "and e.activo = true " +
            "order by e.id desc")
    Page<Ente> findAllWithFilters(
            @org.springframework.data.repository.query.Param("texto") String texto,
            @org.springframework.data.repository.query.Param("sucursalId") Long sucursalId,
            Pageable pageable);
}
