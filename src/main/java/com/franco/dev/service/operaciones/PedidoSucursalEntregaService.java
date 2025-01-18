package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.PedidoSucursalEntrega;
import com.franco.dev.repository.operaciones.PedidoSucursalEntregaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PedidoSucursalEntregaService extends CrudService<PedidoSucursalEntrega, PedidoSucursalEntregaRepository, Long> {
    private final PedidoSucursalEntregaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public PedidoSucursalEntregaRepository getRepository() {
        return repository;
    }

    @Override
    public PedidoSucursalEntrega save(PedidoSucursalEntrega entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        PedidoSucursalEntrega e = super.save(entity);
        return e;
    }

    public List<PedidoSucursalEntrega> findByPedidoId(Long id) {
        return repository.findByPedidoIdOrderBySucursalIdAsc(id);
    }

    public void deleteAll(List<PedidoSucursalEntrega> toDelete) {
        for(PedidoSucursalEntrega pedidoSucursalEntrega: toDelete){
            repository.deleteById(pedidoSucursalEntrega.getId());
        }
    }
}