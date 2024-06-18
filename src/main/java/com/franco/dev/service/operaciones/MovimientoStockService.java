package com.franco.dev.service.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.dto.MovimientoStockResumenDto;
import com.franco.dev.domain.dto.StockPorTipoMovimientoDto;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MovimientoStockService extends CrudService<MovimientoStock, MovimientoStockRepository, EmbebedPrimaryKey> {
    private final MovimientoStockRepository repository;

    @Autowired
    private MultiTenantService multiTenantService;

    @Override
    public MovimientoStockRepository getRepository() {
        return repository;
    }

    public Float stockByProductoIdAndSucursalId(Long proId, Long sucId) {
        Float stock = repository.stockByProductoIdAndSucursalId(proId, sucId);
        return stock != null ? stock : 0;
    }

    public Float stockByProductoId(Long proId) {
        Float stock = repository.stockByProductoId(proId);
        return stock != null ? stock : 0;
    }

    public Page<MovimientoStock> findMovimientoStockWithFilters(LocalDateTime inicio,
                                                                LocalDateTime fin,
                                                                List<Long> sucursalList,
                                                                Long productoId,
                                                                List<TipoMovimiento> tipoMovimientoList,
                                                                Long usuarioId,
                                                                Pageable pageable) {
        List<String> stringEnum = null;
        if (tipoMovimientoList != null) {
            stringEnum = tipoMovimientoList.stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }

        return repository.findByFilters(inicio, fin, sucursalList, productoId, stringEnum, usuarioId, pageable);
    }

    @Override
    public MovimientoStock save(MovimientoStock entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        MovimientoStock e = super.save(entity);
        multiTenantService.compartir("filial"+entity.getSucursalId()+"_bkp", (MovimientoStock s) -> super.save(s), e);
//        personaPublisher.publish(p);
        return e;
    }

    @Override
    public Boolean delete(MovimientoStock entity) {
        Boolean ok = super.delete(entity);
        if (ok) {
            multiTenantService.compartir(null, (MovimientoStock s) -> super.delete(s), entity);
        }
        return ok;
    }

    public List<MovimientoStock> ultimosMovimientos(Long proId, TipoMovimiento tm, Integer limit) {
        if (tm != null) {
            tm = TipoMovimiento.COMPRA;
        }
        if (limit < 1) {
            limit = 1;
        }
        return repository.ultimosMovimientosPorProductoId(proId, tm.toString(), limit);
    }

    public MovimientoStock findByTipoMovimientoAndReferenciaAndSucursalId(TipoMovimiento tipoMovimiento, Long referencia, Long sucId) {
        return repository.findByTipoMovimientoAndReferenciaAndSucursalId(tipoMovimiento, referencia, sucId);
    }

    public List<MovimientoStock> findListByTipoMovimientoAndReferencia(TipoMovimiento tipoMovimiento, Long referencia) {
        return repository.findByTipoMovimientoAndReferenciaAndEstadoTrue(tipoMovimiento, referencia);
    }

    public List<MovimientoStock> findByDate(String inicio, String fin) {
        return repository.findByDate(inicio, fin);
    }

    public Double findStockWithFilters(LocalDateTime inicio,
                                       LocalDateTime fin,
                                       List<Long> sucursalList,
                                       Long productoId,
                                       List<TipoMovimiento> tipoMovimientoList,
                                       Long usuarioId){
        List<String> stringEnum = null;
        if (tipoMovimientoList != null) {
            stringEnum = tipoMovimientoList.stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }
        Double stock = repository.findStockWithFilters(inicio, fin, sucursalList, productoId, stringEnum, usuarioId);
        return stock == null ? 0 : stock;
    }

    public List<StockPorTipoMovimientoDto> findStockPorTipoMovimiento(LocalDateTime inicio,
                                                                      LocalDateTime fin,
                                                                      List<Long> sucursalList,
                                                                      Long productoId,
                                                                      List<TipoMovimiento> tipoMovimientoList,
                                                                      Long usuarioId){
        List<String> stringEnum = null;
        if (tipoMovimientoList != null) {
            stringEnum = tipoMovimientoList.stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }
        return repository.findStockPorTipoMovimiento(inicio, fin, sucursalList, productoId, stringEnum, usuarioId);
    }

}