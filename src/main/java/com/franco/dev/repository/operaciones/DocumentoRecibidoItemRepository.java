package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentoRecibidoItemRepository extends JpaRepository<DocumentoRecibidoItem, Long> {

    List<DocumentoRecibidoItem> findByDocumentoRecibidoIdOrderByOrdenAsc(Long documentoRecibidoId);

    /** Los renglones de factura vinculados a un ítem del pedido. */
    List<DocumentoRecibidoItem> findByPedidoItemId(Long pedidoItemId);

    @Modifying
    @Query("delete from DocumentoRecibidoItem i where i.documentoRecibido.id = :documentoId")
    void deleteByDocumentoRecibidoId(@Param("documentoId") Long documentoId);
}
