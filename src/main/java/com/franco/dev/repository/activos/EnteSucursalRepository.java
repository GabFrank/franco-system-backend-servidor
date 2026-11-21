package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.EnteSucursal;
import com.franco.dev.repository.HelperRepository;
import com.franco.dev.domain.activos.enums.TipoEnte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnteSucursalRepository extends HelperRepository<EnteSucursal, Long> {

    default Class<EnteSucursal> getEntityClass() {
        return EnteSucursal.class;
    }

    List<EnteSucursal> findByEnteId(Long enteId);

    List<EnteSucursal> findBySucursalId(Long sucursalId);

    Optional<EnteSucursal> findByEnteIdAndSucursalId(Long enteId, Long sucursalId);

    EnteSucursal findFirstByEnteIdOrderByIdDesc(Long enteId);

    /**
     * Joins explicitos y left: `es.responsable.persona.nombre` en el where hacia un join obligatorio y las
     * asignaciones sin responsable no aparecian nunca. Orden fijo por id para que las paginas no se pisen (#772).
     */
    @Query(value = "select es from EnteSucursal es " +
            "left join es.ente e left join es.sucursal s left join es.responsable r left join r.persona rp where " +
            "(:sucursalId is null or s.id = :sucursalId) and " +
            "(:tipoEnte is null or e.tipoEnte = :tipoEnte) and " +
            "(:responsableId is null or r.id = :responsableId) and " +
            "(:texto is null or cast(es.id as text) like :texto or " +
            "lower(e.descripcion) like :texto or " +
            "lower(s.nombre) like :texto or " +
            "lower(rp.nombre) like :texto) " +
            "order by es.id",
            countQuery = "select count(es) from EnteSucursal es " +
            "left join es.ente e left join es.sucursal s left join es.responsable r left join r.persona rp where " +
            "(:sucursalId is null or s.id = :sucursalId) and " +
            "(:tipoEnte is null or e.tipoEnte = :tipoEnte) and " +
            "(:responsableId is null or r.id = :responsableId) and " +
            "(:texto is null or cast(es.id as text) like :texto or " +
            "lower(e.descripcion) like :texto or " +
            "lower(s.nombre) like :texto or " +
            "lower(rp.nombre) like :texto)")
    Page<EnteSucursal> findAllWithFilters(@Param("texto") String texto,
                                          @Param("sucursalId") Long sucursalId,
                                          @Param("tipoEnte") TipoEnte tipoEnte,
                                          @Param("responsableId") Long responsableId,
                                          Pageable pageable);
}