package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.PedidoFechaEntrega;
import com.franco.dev.repository.operaciones.PedidoFechaEntregaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PedidoFechaEntregaService extends CrudService<PedidoFechaEntrega, PedidoFechaEntregaRepository, Long> {
    private final PedidoFechaEntregaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public PedidoFechaEntregaRepository getRepository() {
        return repository;
    }

    @Override
    public PedidoFechaEntrega save(PedidoFechaEntrega entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        PedidoFechaEntrega e = super.save(entity);
        return e;
    }

    public List<PedidoFechaEntrega> findByPedido(Long id) {
        return repository.findByPedidoIdOrderByFechaEntregaDesc(id);
    }

    public void deleteAll(List<PedidoFechaEntrega> toDelete) {
        repository.deleteAll(toDelete);
    }
}