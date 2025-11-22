package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EventoInutilizacionDEDocumentoElectronico;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventoInutilizacionDEDocumentoElectronicoRepository extends HelperRepository<EventoInutilizacionDEDocumentoElectronico, Long> {

    default Class<EventoInutilizacionDEDocumentoElectronico> getEntityClass() {
        return EventoInutilizacionDEDocumentoElectronico.class;
    }

    /**
     * Buscar todas las relaciones para un evento de inutilización
     */
    @Query("SELECT r FROM EventoInutilizacionDEDocumentoElectronico r WHERE " +
           "r.eventoInutilizacionDeId = :eventoId AND " +
           "r.eventoInutilizacionDeSucursalId = :sucursalId")
    List<EventoInutilizacionDEDocumentoElectronico> findByEventoInutilizacionDeIdAndSucursalId(
            @Param("eventoId") Long eventoId,
            @Param("sucursalId") Long sucursalId
    );

    /**
     * Buscar todas las relaciones para un documento electrónico
     */
    @Query("SELECT r FROM EventoInutilizacionDEDocumentoElectronico r WHERE " +
           "r.documentoElectronicoId = :documentoId AND " +
           "r.documentoElectronicoSucursalId = :sucursalId")
    List<EventoInutilizacionDEDocumentoElectronico> findByDocumentoElectronicoIdAndSucursalId(
            @Param("documentoId") Long documentoId,
            @Param("sucursalId") Long sucursalId
    );

    /**
     * Verificar si existe una relación entre un evento y un documento
     */
    @Query("SELECT COUNT(r) > 0 FROM EventoInutilizacionDEDocumentoElectronico r WHERE " +
           "r.eventoInutilizacionDeId = :eventoId AND " +
           "r.eventoInutilizacionDeSucursalId = :eventoSucursalId AND " +
           "r.documentoElectronicoId = :documentoId AND " +
           "r.documentoElectronicoSucursalId = :documentoSucursalId")
    boolean existsByEventoAndDocumento(
            @Param("eventoId") Long eventoId,
            @Param("eventoSucursalId") Long eventoSucursalId,
            @Param("documentoId") Long documentoId,
            @Param("documentoSucursalId") Long documentoSucursalId
    );
}

