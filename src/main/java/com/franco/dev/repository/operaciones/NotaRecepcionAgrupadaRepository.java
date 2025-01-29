package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionAgrupada;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotaRecepcionAgrupadaRepository extends HelperRepository<NotaRecepcionAgrupada, Long> {
    default Class<NotaRecepcionAgrupada> getEntityClass() {
        return NotaRecepcionAgrupada.class;
    }

    public Page<NotaRecepcionAgrupada> findByUsuarioIdOrderByIdDesc(Long id, Pageable page);

    public Page<NotaRecepcionAgrupada> findByProveedorId(Long id, Pageable page);
}