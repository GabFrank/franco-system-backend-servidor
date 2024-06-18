package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.PedidoSucursalInfluencia;
import com.franco.dev.repository.operaciones.PedidoSucursalInfluenciaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PedidoSucursalInfluenciaService extends CrudService<PedidoSucursalInfluencia, PedidoSucursalInfluenciaRepository, Long> {
    private final PedidoSucursalInfluenciaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public PedidoSucursalInfluenciaRepository getRepository() {
        return repository;
    }

    @Override
    public PedidoSucursalInfluencia save(PedidoSucursalInfluencia entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        PedidoSucursalInfluencia e = super.save(entity);
        return e;
    }

    public List<PedidoSucursalInfluencia> findByPedidoId(Long id) {
        return repository.findByPedidoIdOrderBySucursalIdAsc(id);
    }

    public void deleteAll(List<PedidoSucursalInfluencia> toDelete) {
        repository.deleteAll(toDelete);
    }

    @Override
    public Boolean delete(PedidoSucursalInfluencia entity) {
        return super.delete(entity);
    }
}