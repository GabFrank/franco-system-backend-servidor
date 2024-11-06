package com.franco.dev.service.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.repository.operaciones.TransferenciaItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class TransferenciaItemService extends CrudService<TransferenciaItem, TransferenciaItemRepository, Long> {

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    private final TransferenciaItemRepository repository;

    @Override
    public TransferenciaItemRepository getRepository() {
        return repository;
    }


    public List<TransferenciaItem> findByTransferenciaIdAndSucursalId(Long id){
        return repository.findByTransferenciaId(id);
    }

    public Page<TransferenciaItem> findByTransferenciaItemId(Long id, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByTransferenciaIdOrderByIdDesc(id, pageable);
    }

    public Page<TransferenciaItem> findByTransferenciaItemIdWithFilter(Long id, String name, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByTransferenciaIdWithFilters(id, name != null ? "%"+name.toUpperCase()+"%" : null, pageable);
    }

    public List<TransferenciaItem> findByTransferenciaId(Long id) {
        return repository.findByTransferenciaIdOrderByIdDesc(id);
    }

    public List<TransferenciaItem> findByTransferenciaItemIdAsc(Long id) {
        return repository.findByTransferenciaIdOrderByIdAsc(id);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransferenciaItem save(TransferenciaItem entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        TransferenciaItem e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }


}