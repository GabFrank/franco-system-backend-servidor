package com.franco.dev.service.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.dto.StockPorTipoMovimientoDto;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.StockPorProductoSucursal;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.dto.MovimientoStockCantidadAndIdDto;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.SucursalService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MovimientoStockService extends CrudService<MovimientoStock, MovimientoStockRepository, Long> {
    private final MovimientoStockRepository repository;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private StockPorProductoSucursalService stockPorProductoSucursalService;

    @Autowired
    private Environment env;

    @Autowired
    private SucursalService sucursalService;

    @Override
    public MovimientoStockRepository getRepository() {
        return repository;
    }

    public Double stockByProductoIdAndSucursalId(Long proId, Long sucId) {
//        StockPorProductoSucursal sps = stockPorProductoSucursalService.getRepository().findByIdAndSucursalId(proId, sucId);
//        if (sps != null) {
//            MovimientoStockCantidadAndIdDto dto = repository.stockByProductoIdAndSucursalIdAndLastId(proId, sucId, sps.getLastMovimientoStockId());
//            if (dto != null && dto.getCantidad() != null && dto.getCantidad() != 0) {
//                Double cantidadParcial = dto.getCantidad();
//                sps.sumarCantidad(Double.valueOf(cantidadParcial));
//                sps.setLastMovimientoStockId(dto.getLastId());
//                if(dto.getCantItens() > env.getProperty("calculoStockLimite", Long.class)){
//                    stockPorProductoSucursalService.save(sps);
//                }
//            }
//            return sps.getCantidad();
//        } else {
//            MovimientoStockCantidadAndIdDto dto = repository.stockByProductoIdAndSucursalIdAndLastId(proId, sucId, Long.valueOf(0));
//            if (dto != null && dto.getLastId() != null) {
//                Double cantidadParcial = dto.getCantidad() != null ? dto.getCantidad() : 0.0;
//                sps = new StockPorProductoSucursal();
//                sps.setId(proId);
//                sps.setSucursalId(sucId);
//                sps.setCantidad(cantidadParcial);
//                sps.setLastMovimientoStockId(dto.getLastId());
//                stockPorProductoSucursalService.save(sps);
//                return sps.getCantidad();
//            } else {
//                return 0.0;
//            }
//        }
        Float stock = repository.stockByProductoIdAndSucursalId(proId, sucId);
        if(stock == null) stock = Float.valueOf(0);
        return Double.valueOf(stock);
    }

    public Double stockByProductoId(Long proId) {
        Double finalStock = 0.0;
        List<Sucursal> sucursalList = sucursalService.findAll2().stream().filter(s -> s.getNombre().equals("COMPRAS") == false).collect(Collectors.toList());
        for(Sucursal s : sucursalList){
            finalStock += stockByProductoIdAndSucursalId(proId, s.getId());
        }
        return finalStock;
    }

    public Double stockByProductoIdExecptMovStockId(Long proId, Long movId, Long sucId) {
        Float stock = repository.stockByProductoIdExeptMovimientoId(proId, movId, sucId);
        return Double.valueOf(stock != null ? stock : 0);
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
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public MovimientoStock save(MovimientoStock entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            Long newId = Long.valueOf(1);
            Long lastId = repository.findMaxId(entity.getSucursalId());
            if(lastId == null) lastId = Long.valueOf(0);
            if(lastId % 2 != 0){
                newId = lastId + 2;
            } else {
                newId = lastId + 1;
            }
            entity.setId(newId);
        }
        MovimientoStock e = super.save(entity);
        return e;
    }

    @Override
    public Boolean delete(MovimientoStock entity) {
        Boolean ok = super.delete(entity);
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

    public List<MovimientoStock> findByTipoMovimientoAndReferenciaAndSucursalId(TipoMovimiento tipoMovimiento, Long referencia, Long sucId) {
        return repository.findByTipoMovimientoAndReferenciaAndSucursalId(tipoMovimiento, referencia, sucId);
    }

    public MovimientoStock findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento tipoMovimiento, Long referencia, Long sucId, Long proId) {
        return repository.findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(tipoMovimiento, referencia, sucId, proId);
    }

    public List<MovimientoStock> findListByTipoMovimientoAndReferenciaEstadoTrue(TipoMovimiento tipoMovimiento, Long referencia) {
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
                                       Long usuarioId) {
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
                                                                      Long usuarioId) {
        List<String> stringEnum = null;
        if (tipoMovimientoList != null) {
            stringEnum = tipoMovimientoList.stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }
        return repository.findStockPorTipoMovimiento(inicio, fin, sucursalList, productoId, stringEnum, usuarioId);
    }

    public List<MovimientoStock> createMovimientoFromTransferenciaItem(TransferenciaItem e) {
        TransferenciaItem finalE = e;
        MovimientoStock movimientoStockSalida = findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento.TRANSFERENCIA, finalE.getId(), finalE.getTransferencia().getSucursalOrigen().getId(), finalE.getPresentacionPreTransferencia().getProducto().getId());
        MovimientoStock movimientoStockEntrada = findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento.TRANSFERENCIA, finalE.getId(), finalE.getTransferencia().getSucursalDestino().getId(), finalE.getPresentacionPreTransferencia().getProducto().getId());
        Boolean esRechazado = false;
        Boolean esModificado = false;
        esRechazado = e.getMotivoRechazoPreparacion() != null || e.getMotivoRechazoPreTransferencia() != null || e.getMotivoRechazoRecepcion() != null || e.getMotivoRechazoTransporte() != null;
        esModificado = e.getMotivoModificacionPreparacion() != null || e.getMotivoModificacionPreTransferencia() != null || e.getMotivoModificacionRecepcion() != null || e.getMotivoModificacionTransporte() != null;
        MovimientoStock ms = null;

        switch (e.getTransferencia().getEtapa()) {
            case PREPARACION_MERCADERIA:
                ms = movimientoStockSalida != null ? movimientoStockSalida : new MovimientoStock();
                ms.setEstado(!esRechazado);
                ms.setSucursalId(e.getTransferencia().getSucursalOrigen().getId());
                ms.setCantidad(e.getCantidadPreparacion() * e.getPresentacionPreparacion().getCantidad() * -1);
                ms.setProducto(e.getPresentacionPreparacion().getProducto());
                ms.setReferencia(e.getId());
                ms.setTipoMovimiento(TipoMovimiento.TRANSFERENCIA);
                ms.setCreadoEn(LocalDateTime.now());
                ms.setUsuario(e.getUsuario());
                movimientoStockSalida = save(ms);
                break;
            case RECEPCION_EN_VERIFICACION:
                ms = movimientoStockEntrada != null ? movimientoStockEntrada : new MovimientoStock();
                ms.setEstado(!esRechazado);
                ms.setSucursalId(e.getTransferencia().getSucursalDestino().getId());
                ms.setCantidad(e.getCantidadRecepcion() * e.getPresentacionRecepcion().getCantidad());
                ms.setProducto(e.getPresentacionRecepcion().getProducto());
                ms.setReferencia(e.getId());
                ms.setTipoMovimiento(TipoMovimiento.TRANSFERENCIA);
                ms.setCreadoEn(LocalDateTime.now());
                ms.setUsuario(e.getUsuario());
                movimientoStockEntrada = save(ms);
                ms = movimientoStockSalida;
                ms.setEstado(!esRechazado);
                movimientoStockSalida = save(ms);
                break;
            case TRANSPORTE_VERIFICACION:
                if (movimientoStockSalida != null) {
                    ms = movimientoStockSalida;
                    ms.setCantidad(e.getCantidadTransporte() * e.getPresentacionTransporte().getCantidad() * -1);
                    ms = movimientoStockSalida;
                    ms.setEstado(!esRechazado);
                    movimientoStockSalida = save(ms);
                }
                break;
        }

        if (e.getTransferencia().getEstado() == TransferenciaEstado.CANCELADA) {
            if (movimientoStockSalida != null) {
                movimientoStockSalida.setEstado(false);
                save(movimientoStockSalida);
            }
            if (movimientoStockEntrada != null) {
                movimientoStockEntrada.setEstado(false);
                save(movimientoStockEntrada);
            }
        }
        List<MovimientoStock> res = new ArrayList<>();
        res.add(movimientoStockSalida);
        res.add(movimientoStockEntrada);
        return res;
    }

}