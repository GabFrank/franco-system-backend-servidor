package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.DocumentoRecibido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentoRecibidoRepository extends JpaRepository<DocumentoRecibido, Long> {

    Optional<DocumentoRecibido> findByCdc(String cdc);

    /** La misma clave que el índice único uk_documento_recibido_numero (tipo null = 1). */
    @Query("SELECT d FROM DocumentoRecibido d WHERE d.rucEmisor = :ruc AND COALESCE(d.tipoDocumento, 1) = :tipo "
            + "AND d.timbrado = :timbrado AND d.establecimiento = :est AND d.puntoExpedicion = :pto "
            + "AND d.numero = :numero ORDER BY d.id")
    List<DocumentoRecibido> buscarMismaFactura(@Param("ruc") String rucEmisor, @Param("tipo") Integer tipoDocumento,
                                               @Param("timbrado") Integer timbrado, @Param("est") String establecimiento,
                                               @Param("pto") String puntoExpedicion, @Param("numero") String numero);

    Optional<DocumentoRecibido> findByNotaRecepcionId(Long notaRecepcionId);

    List<DocumentoRecibido> findByNotaRecepcionIdIn(List<Long> notaRecepcionIds);
}
