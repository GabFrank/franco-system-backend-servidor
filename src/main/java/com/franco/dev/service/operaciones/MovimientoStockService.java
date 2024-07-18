package com.franco.dev.service.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.dto.StockPorTipoMovimientoDto;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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
        multiTenantService.compartir("filial" + entity.getSucursalId() + "_bkp", (MovimientoStock s) -> super.save(s), e);
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
        MovimientoStock movimientoStockSalida = multiTenantService.compartir("filial" + e.getTransferencia().getSucursalOrigen().getId() + "_bkp", (params) -> findByTipoMovimientoAndReferenciaAndSucursalId(TipoMovimiento.TRANSFERENCIA, finalE.getId(), finalE.getTransferencia().getSucursalOrigen().getId()), TipoMovimiento.TRANSFERENCIA, e.getId(), e.getTransferencia().getSucursalOrigen().getId());
        MovimientoStock movimientoStockEntrada = multiTenantService.compartir("filial" + e.getTransferencia().getSucursalDestino().getId() + "_bkp", (params) -> findByTipoMovimientoAndReferenciaAndSucursalId(TipoMovimiento.TRANSFERENCIA, finalE.getId(), finalE.getTransferencia().getSucursalDestino().getId()), TipoMovimiento.TRANSFERENCIA, e.getId(), e.getTransferencia().getSucursalDestino().getId());
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
                ms.setCantidad(e.getCantidadPreparacion() * e.getPresentacionPreparacion().getCantidad());
                ms.setProducto(e.getPresentacionPreparacion().getProducto());
                ms.setReferencia(e.getId());
                ms.setTipoMovimiento(TipoMovimiento.TRANSFERENCIA);
                ms.setCreadoEn(LocalDateTime.now());
                ms.setUsuario(e.getUsuario());
                movimientoStockSalida = multiTenantService.compartir("filial" + e.getTransferencia().getSucursalOrigen().getId() + "_bkp", (MovimientoStock s) -> save(s), ms);
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
                movimientoStockEntrada = multiTenantService.compartir("filial" + e.getTransferencia().getSucursalDestino().getId() + "_bkp", (MovimientoStock s) -> save(s), ms);
                ms = movimientoStockSalida;
                ms.setEstado(!esRechazado);
                movimientoStockSalida = multiTenantService.compartir("filial" + e.getTransferencia().getSucursalOrigen().getId() + "_bkp", (MovimientoStock s) -> save(s), ms);
                break;
            case TRANSPORTE_VERIFICACION:
                if (movimientoStockSalida != null) {
                    ms = movimientoStockSalida;
                    ms.setEstado(!esRechazado);
                    movimientoStockSalida = multiTenantService.compartir("filial" + e.getTransferencia().getSucursalOrigen().getId() + "_bkp", (MovimientoStock s) -> save(s), ms);
                }
                break;
        }

        if (e.getTransferencia().getEstado() == TransferenciaEstado.CANCELADA) {
            if (movimientoStockSalida != null) {
                movimientoStockSalida.setEstado(false);
                multiTenantService.compartir("filial" + e.getTransferencia().getSucursalOrigen().getId() + "_bkp", (MovimientoStock s) -> save(s), movimientoStockSalida);
            }
            if (movimientoStockEntrada != null) {
                movimientoStockEntrada.setEstado(false);
                multiTenantService.compartir("filial" + e.getTransferencia().getSucursalDestino().getId() + "_bkp", (MovimientoStock s) -> save(s), movimientoStockEntrada);
            }
        }
        List<MovimientoStock> res = new ArrayList<>();
        res.add(movimientoStockSalida);
        res.add(movimientoStockEntrada);
        return res;
    }

}