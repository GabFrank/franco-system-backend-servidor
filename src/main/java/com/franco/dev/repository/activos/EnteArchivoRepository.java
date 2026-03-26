package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.EnteArchivo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EnteArchivoRepository extends HelperRepository<EnteArchivo, Long> {

    default Class<EnteArchivo> getEntityClass() {
        return EnteArchivo.class;
    }

    @Query("select ea from EnteArchivo ea where ea.ente.id = ?1 order by ea.id desc")
    List<EnteArchivo> findByEnteId(Long enteId);

    @Query("select ea from EnteArchivo ea where ea.ente.id = ?1 and ea.vigente = true order by ea.id desc")
    List<EnteArchivo> findByEnteIdAndVigenteTrue(Long enteId);

    @Query(value = "select ea from EnteArchivo ea where " +
            "(cast(:enteId as long) is null or ea.ente.id = :enteId) " +
            "order by ea.id desc",
            countQuery = "select count(ea) from EnteArchivo ea where " +
            "(cast(:enteId as long) is null or ea.ente.id = :enteId)")
    Page<EnteArchivo> findAllByEnteId(
            @org.springframework.data.repository.query.Param("enteId") Long enteId,
            Pageable pageable);
}
