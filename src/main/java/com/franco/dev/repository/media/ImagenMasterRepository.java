package com.franco.dev.repository.media;

import com.franco.dev.domain.media.ImagenMaster;
import com.franco.dev.domain.media.enums.TipoReferencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImagenMasterRepository extends JpaRepository<ImagenMaster, Long> {

    @Query("select i from ImagenMaster i where i.tipoReferencia = ?1 and i.referenciaId = ?2")
    List<ImagenMaster> findByTipoReferenciaAndReferenciaId(TipoReferencia tipoReferencia, Long referenciaId);

    @Query("select i from ImagenMaster i where i.tipoReferencia = ?1 and i.referenciaId = ?2 and i.principal = true")
    ImagenMaster findPrincipalByTipoReferenciaAndReferenciaId(TipoReferencia tipoReferencia, Long referenciaId);

    @Query("select i from ImagenMaster i where i.usuario.id = ?1")
    List<ImagenMaster> findByUsuarioId(Long id);
} 