package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaNota;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecepcionMercaderiaNotaRepository extends HelperRepository<RecepcionMercaderiaNota, Long> {
    
    default Class<RecepcionMercaderiaNota> getEntityClass() {
        return RecepcionMercaderiaNota.class;
    }

    @Query("SELECT rmn FROM RecepcionMercaderiaNota rmn WHERE rmn.recepcionMercaderia.id = :recepcionId")
    List<RecepcionMercaderiaNota> findByRecepcionMercaderiaId(@Param("recepcionId") Long recepcionId);

    @Query("SELECT rmn FROM RecepcionMercaderiaNota rmn WHERE rmn.notaRecepcion.id = :notaId")
    List<RecepcionMercaderiaNota> findByNotaRecepcionId(@Param("notaId") Long notaId);

    /**
     * Verifica si existe una asociación entre una recepción y una nota
     */
    @Query("SELECT COUNT(rmn) > 0 FROM RecepcionMercaderiaNota rmn " +
           "WHERE rmn.recepcionMercaderia.id = :recepcionId " +
           "AND rmn.notaRecepcion.id = :notaId")
    boolean existeAsociacion(@Param("recepcionId") Long recepcionId, @Param("notaId") Long notaId);

    /**
     * Busca asociaciones por recepción y nota
     */
    @Query("SELECT rmn FROM RecepcionMercaderiaNota rmn " +
           "WHERE rmn.recepcionMercaderia.id = :recepcionId " +
           "AND rmn.notaRecepcion.id = :notaId")
    List<RecepcionMercaderiaNota> findByRecepcionAndNota(@Param("recepcionId") Long recepcionId, @Param("notaId") Long notaId);
} 