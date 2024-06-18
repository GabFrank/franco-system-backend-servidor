package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.CompraItemEstado;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.domain.operaciones.enums.PedidoItemEstado;
import com.franco.dev.repository.operaciones.PedidoItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PedidoItemService extends CrudService<PedidoItem, PedidoItemRepository, Long> {
    private final PedidoItemRepository repository;

    @Override
    public PedidoItemRepository getRepository() {
        return repository;
    }

    @Autowired
    public CompraItemService compraItemService;

    //public List<PedidoItem> findByAll(String texto){
    //    texto = texto.replace(' ', '%');
    //    return  repository.findByAll(texto);
    //}

    public List<PedidoItem> findByProductoId(Long id) { return repository.findByProductoId(id); }

    public Page<PedidoItem> findByPedidoId(Long id, Pageable page) { return repository.findByPedidoIdOrderByIdDesc(id, page); }

    public List<PedidoItem> findByPedidoId(Long id) { return repository.findByPedidoId(id); }

    public Page<PedidoItem> findByPedidoIdSobrantes(Long id, Pageable page){
        return repository.findByPedidoIdAndNotaRecepcionIdIsNull(id, page);
    }

    public Page<PedidoItem> findByNotaRecepcionId(Long id, Pageable page) {
        return repository.findByNotaRecepcionId(id, page);
    }

    public List<PedidoItem> findByNotaRecepcionId(Long id) {
        return repository.findByNotaRecepcionId(id);
    }

    public Integer countByNotaRecepcionId(Long id) {
        return repository.countByNotaRecepcionId(id);
    }

    @Override
    public PedidoItem save(PedidoItem entity) {
        PedidoItem e = null;
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
            e = super.save(entity);
        return e;
    }
}