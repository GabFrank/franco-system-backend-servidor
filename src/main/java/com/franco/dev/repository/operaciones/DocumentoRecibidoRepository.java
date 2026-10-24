package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.DocumentoRecibido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoRecibidoRepository extends JpaRepository<DocumentoRecibido, Long> {

    Optional<DocumentoRecibido> findByCdc(String cdc);

    Optional<DocumentoRecibido> findFirstByRucEmisorAndTimbradoAndEstablecimientoAndPuntoExpedicionAndNumero(
            String rucEmisor, Integer timbrado, String establecimiento, String puntoExpedicion, String numero);

    Optional<DocumentoRecibido> findByNotaRecepcionId(Long notaRecepcionId);

    List<DocumentoRecibido> findByNotaRecepcionIdIn(List<Long> notaRecepcionIds);
}
