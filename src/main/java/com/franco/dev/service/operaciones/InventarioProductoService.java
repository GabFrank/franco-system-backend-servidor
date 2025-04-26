package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.repository.operaciones.InventarioProductoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class InventarioProductoService extends CrudService<InventarioProducto, InventarioProductoRepository, Long> {
    private final InventarioProductoRepository repository;

    @Override
    public InventarioProductoRepository getRepository() {
        return repository;
    }

    public List<InventarioProducto> findByInventarioId(Long id) {
        return repository.findByInventarioId(id);
    }

    public Boolean verificarUsuarioZonaAbierta(Long invId, Long usuId){
        return  repository.verificarUsuarioZona(invId, usuId).size() != 0;
    }

    @Override
    public InventarioProducto save(InventarioProducto entity) {
        entity = super.save(entity);
        return entity;
    }
}