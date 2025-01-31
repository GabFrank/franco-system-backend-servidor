package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionAgrupada;
import com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto;
import com.franco.dev.repository.operaciones.NotaRecepcionAgrupadaRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class NotaRecepcionAgrupadaService extends CrudService<NotaRecepcionAgrupada, NotaRecepcionAgrupadaRepository, Long> {

    private final NotaRecepcionAgrupadaRepository repository;

    @Override
    public NotaRecepcionAgrupadaRepository getRepository() {
        return repository;
    }

    public Page<PedidoRecepcionProductoDto> findRecepcionProductoByNotaRecepcionAgrupada(Long id, Pageable page){
        return repository.findRecepcionProductoByRecepcionByNotaAgrupada(id, page);
    }

    public PedidoRecepcionProductoDto findRecepcionProductoByNotaRecepcionAgrupadaAndProducto(Long notaRecepcionAgrupadaId, Long productoId){
        return repository.findRecepcionProductoByRecepcionByNotaAgrupadaAndProducto(notaRecepcionAgrupadaId, productoId);
    }

    @Override
    public NotaRecepcionAgrupada save(NotaRecepcionAgrupada entity) {
        NotaRecepcionAgrupada e = super.save(entity);
        return e;
    }
}