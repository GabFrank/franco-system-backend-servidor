package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPagoRecepcion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SolicitudPagoRecepcionRepository extends HelperRepository<SolicitudPagoRecepcion, Long> {
    
    default Class<SolicitudPagoRecepcion> getEntityClass() {
        return SolicitudPagoRecepcion.class;
    }

    @Query("SELECT spr FROM SolicitudPagoRecepcion spr WHERE spr.solicitudPago.id = :solicitudPagoId")
    List<SolicitudPagoRecepcion> findBySolicitudPagoId(@Param("solicitudPagoId") Long solicitudPagoId);

    @Query("SELECT spr FROM SolicitudPagoRecepcion spr WHERE spr.recepcionMercaderia.id = :recepcionId")
    List<SolicitudPagoRecepcion> findByRecepcionMercaderiaId(@Param("recepcionId") Long recepcionId);

    @Query("SELECT spr FROM SolicitudPagoRecepcion spr WHERE spr.solicitudPago.id = :solicitudPagoId AND spr.recepcionMercaderia.id = :recepcionId")
    List<SolicitudPagoRecepcion> findBySolicitudPagoAndRecepcion(@Param("solicitudPagoId") Long solicitudPagoId, @Param("recepcionId") Long recepcionId);
} 