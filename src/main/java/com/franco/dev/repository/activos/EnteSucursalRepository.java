package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.EnteSucursal;
import com.franco.dev.repository.HelperRepository;
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

    @Query("select es from EnteSucursal es where " +
            "(:sucursalId is null or es.sucursal.id = :sucursalId) and " +
            "(:texto is null or cast(es.id as text) like concat('%', :texto, '%') or " +
            "cast(es.ente.id as text) like concat('%', :texto, '%'))")
    Page<EnteSucursal> findAllWithFilters(@Param("texto") String texto,
                                          @Param("sucursalId") Long sucursalId,
                                          Pageable pageable);
}