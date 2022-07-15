package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.enums.InventarioEstado;
import com.franco.dev.print.operaciones.MovimientoPrintService;
import com.franco.dev.repository.operaciones.InventarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class InventarioService extends CrudService<Inventario, InventarioRepository> {

    private final Logger log = LoggerFactory.getLogger(InventarioService.class);

    private final InventarioRepository repository;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Override
    public InventarioRepository getRepository() {
        return repository;
    }

    @Autowired
    private MovimientoPrintService movimientoPrintService;

    @Override
    public Inventario save(Inventario entity) {
        if(entity.getFechaInicio()==null) entity.setFechaInicio(LocalDateTime.now());
        Inventario e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public List<Inventario> findByDate(String inicio, String fin){
        return repository.findByDate(inicio, fin);
    }
    public List<Inventario> findByUsuario(Long id){
        return repository.findByUsuarioId(id);
    }

    public List<Inventario> findInventarioAbiertoPorSucursal(Long id){
        return repository.findBySucursalIdAndEstado(id, InventarioEstado.ABIERTO);
    }
}