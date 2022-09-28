package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.apache.tomcat.jni.Local;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MovimientoStockService extends CrudService<MovimientoStock, MovimientoStockRepository, EmbebedPrimaryKey> {
    private final MovimientoStockRepository repository;

    @Override
    public MovimientoStockRepository getRepository() {
        return repository;
    }

    public Float stockByProductoIdAndSucursalId(Long proId, Long sucId){
        return  repository.stockByProductoIdAndSucursalId(proId, sucId) != null ? repository.stockByProductoIdAndSucursalId(proId, sucId) : 0;
    }

    public Float stockByProductoId(Long proId){
        return repository.stockByProductoId(proId) != null ? repository.stockByProductoId(proId) : 0;
    }

    @Override
    public MovimientoStock save(MovimientoStock entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        MovimientoStock e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public List<MovimientoStock> ultimosMovimientos(Long proId, TipoMovimiento tm, Integer limit){
        if(tm!=null){
            tm = TipoMovimiento.COMPRA;
        }
        if(limit<1){
            limit = 1;
        }
        return repository.ultimosMovimientosPorProductoId(proId, tm.toString(), limit);
    }

    public MovimientoStock findByTipoMovimientoAndReferencia(TipoMovimiento tipoMovimiento, Long referencia){
        return repository.findByTipoMovimientoAndReferencia(tipoMovimiento, referencia);
    }

    public List<MovimientoStock> findListByTipoMovimientoAndReferencia(TipoMovimiento tipoMovimiento, Long referencia){
        return repository.findByTipoMovimientoAndReferenciaAndEstadoTrue(tipoMovimiento, referencia);
    }

    public List<MovimientoStock> findByDate(String inicio, String fin){
        return repository.findByDate(inicio, fin);
    }
}