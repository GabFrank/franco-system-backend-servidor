package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.repository.operaciones.NotaPedidoRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class NotaRecepcionService extends CrudService<NotaRecepcion, NotaRecepcionRepository, Long> {

    private final NotaRecepcionRepository repository;

    @Override
    public NotaRecepcionRepository getRepository() {
        return repository;
    }

    public List<NotaRecepcion> findByPedidoId(Long id){
        return  repository.findByPedidoId(id);
    }

    public Page<NotaRecepcion> findByPedidoId(Long id, Pageable page){
        return repository.findByPedidoId(id, page);
    }

    public Page<NotaRecepcion> findByPedidoIdAndNumero(Long id, String texto, Pageable page){
        return repository.findByPedidoIdAndNumero(id, texto, page);
    }

    @Override
    public NotaRecepcion save(NotaRecepcion entity) {
        NotaRecepcion e = super.save(entity);
        return e;
    }
}