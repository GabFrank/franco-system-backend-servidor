package com.franco.dev.service.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.config.multitenant.TenantContext;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.dto.VentaPorPeriodoV1Dto;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import static java.time.temporal.ChronoUnit.DAYS;

@Service
@AllArgsConstructor
public class VentaService extends CrudService<Venta, VentaRepository, EmbebedPrimaryKey> {
    private final VentaRepository repository;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private CobroDetalleService cobroDetalleService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private VentaItemService ventaItemService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Override
    public VentaRepository getRepository() {
        return repository;
    }


//    public List<Venta> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//    }

    public Page<Venta> findByCajaId(EmbebedPrimaryKey id, Integer page, Integer size, Boolean asc, Long formaPago, VentaEstado estado, Boolean isDelivery) {
        setTenant("filial"+id.getSucursalId()+"_bkp");
        Pageable pagina = PageRequest.of(page, size);
        if (formaPago != null || estado != null || isDelivery != null)
            if (isDelivery == null || isDelivery == false) {
                return repository.findWithFilters(id.getId(), id.getSucursalId(), formaPago, estado, pagina);
//                return multiTenantService.compartir("filial"+id.getSucursalId()+"_bkp", (params) -> repository.findWithFilters(id.getId(), id.getSucursalId(), formaPago, estado, pagina), id.getId(), id.getSucursalId(), formaPago, estado, pagina);
            } else {
                return repository.findWithFilters(id.getId(), id.getSucursalId(), formaPago, estado, pagina, isDelivery);
//                return multiTenantService.compartir("filial"+id.getSucursalId()+"_bkp", (params) -> repository.findWithFilters(id.getId(), id.getSucursalId(), formaPago, estado, pagina, isDelivery), id.getId(), id.getSucursalId(), formaPago, estado, pagina, isDelivery);

            }
        if (asc == true)
            return repository.findAllByCajaIdAndSucursalIdOrderByIdAsc(id.getId(), id.getSucursalId(), pagina);
//        return multiTenantService.compartir("filial"+id.getSucursalId()+"_bkp", (params) -> repository.findAllByCajaIdAndSucursalIdOrderByIdAsc(id.getId(), id.getSucursalId(), pagina), id.getId(), id.getSucursalId(), pagina);

        if (asc != true)
            return repository.findAllByCajaIdAndSucursalIdOrderByIdDesc(id.getId(), id.getSucursalId(), pagina);
//        return multiTenantService.compartir("filial"+id.getSucursalId()+"_bkp", (params) -> repository.findAllByCajaIdAndSucursalIdOrderByIdDesc(id.getId(), id.getSucursalId(), pagina), id.getId(), id.getSucursalId(), pagina);
        clearTenant();
        return null;
    }

    public List<Venta> findAllByCajaId(EmbebedPrimaryKey id) {
        setTenant("filial"+id.getSucursalId()+"_bkp");
        List<Venta> aux = repository.findByCajaIdAndCajaSucursalId(id.getId(), id.getSucursalId());
        clearTenant();
        return aux;
//        return multiTenantService.compartir("filial"+id.getSucursalId()+"_bkp", (params) -> repository.findByCajaIdAndCajaSucursalId(id.getId(), id.getSucursalId()), id.getId(), id.getSucursalId());
    }

    @Override
    public Venta save(Venta entity) {
        Venta e = super.save(entity);
        return e;
    }

    @Override
    public Venta saveAndSend(Venta entity, Boolean recibir) {
        Venta e = super.save(entity);
        propagacionService.propagarEntidad(e, TipoEntidad.VENTA, entity.getSucursalId());
        return e;
    }

    @Override
    public Venta saveAndSend(Venta entity, Long sucId) {
        Venta e = super.save(entity);
        super.<VentaService>setTenant("filial"+sucId+"_bkp").save(entity);
        super.clearTenant();
//        propagacionService.propagarEntidad(e, TipoEntidad.VENTA, entity.getSucursalId());
//        multiTenantService.compartir("filial"+sucId+"_bkp", (Venta s) -> super.save(s), e);
        return e;
    }

    public List<Venta> ventaPorPeriodoAndSucursal(String inicio, String fin, Long sucId) {
        LocalDateTime fechaInicio = stringToDate(inicio);
        LocalDateTime fechaFin = stringToDate(fin);
        List<Venta> ventaList = null;
        if (repository != null) {
            setTenant("filial"+sucId+"_bkp");
            ventaList = repository.findBySucursalIdAndCreadoEnBetweenOrderByIdDesc(sucId, fechaInicio, fechaFin);
            clearTenant();
//            ventaList = multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> repository.findBySucursalIdAndCreadoEnBetweenOrderByIdDesc(sucId, fechaInicio, fechaFin), sucId, fechaInicio, fechaFin);
        }
        return ventaList;
    }

    public List<VentaPorPeriodoV1Dto> ventaPorPeriodo(String inicio, String fin) {
        List<VentaPorPeriodoV1Dto> ventaPorPeriodoList = new ArrayList<>();
        LocalDateTime fechaInicio = LocalDateTime.parse(inicio);
        LocalDateTime fechaFin = LocalDateTime.parse(fin);
        Long cantDias = DAYS.between(fechaInicio, fechaFin);
        for (int i = 0; i < cantDias; i++) {
            VentaPorPeriodoV1Dto ventaPorPeriodoV1Dto = new VentaPorPeriodoV1Dto();
            ventaPorPeriodoV1Dto.setCreadoEn(fechaInicio.plusDays(i));
            ventaPorPeriodoList.add(ventaPorPeriodoV1Dto);
        }
        for (VentaPorPeriodoV1Dto ventaPorPeriodo : ventaPorPeriodoList) {
            List<Venta> ventaList = new ArrayList<>();
            for(String key: TenantContext.getAllTenantKeys()){
                List<Venta> aux = multiTenantService.compartir("filial"+key+"_bkp", (params) -> repository.ventaPorPeriodo(ventaPorPeriodo.getCreadoEn(), ventaPorPeriodo.getCreadoEn().plusDays(1)), ventaPorPeriodo.getCreadoEn(), ventaPorPeriodo.getCreadoEn().plusDays(1));
                ventaList.addAll(aux);
            }
//            List<Venta> ventaList = repository.ventaPorPeriodo(ventaPorPeriodo.getCreadoEn(), ventaPorPeriodo.getCreadoEn().plusDays(1));
            ventaPorPeriodo.setCantVenta(ventaList.size());
            for (Venta venta : ventaList) {
                if (venta.getEstado() != VentaEstado.CANCELADA || venta.getEstado() != VentaEstado.ABIERTA) {
                    List<CobroDetalle> cobroDetalleList = new ArrayList<>();
                    for(String key: TenantContext.getAllTenantKeys()){
                        List<CobroDetalle> aux = cobroDetalleService.<CobroDetalleService>setTenant(key).findByCobroId(venta.getCobro().getId(), venta.getSucursalId());
                        cobroDetalleList.addAll(aux);
                    }
                    for (CobroDetalle cobroDetalle : cobroDetalleList) {
                        if (cobroDetalle.getMoneda().getDenominacion().contains("GUARANI")) {
                            if (cobroDetalle.getPago()) {
                                ventaPorPeriodo.addGs(cobroDetalle.getValor());
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor());
                            } else if (cobroDetalle.getDescuento()) {
                                ventaPorPeriodo.addGs(cobroDetalle.getValor() * -1);
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * -1);
                            }
                        }
                        if (cobroDetalle.getMoneda().getDenominacion().contains("REAL")) {
                            if (cobroDetalle.getPago()) {
                                ventaPorPeriodo.addRs(cobroDetalle.getValor());
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * cobroDetalle.getCambio());
                            } else if (cobroDetalle.getDescuento()) {
                                ventaPorPeriodo.addRs(cobroDetalle.getValor() * -1);
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * -1 * cobroDetalle.getCambio());
                            }
                        }
                        if (cobroDetalle.getMoneda().getDenominacion().contains("DOLAR")) {
                            if (cobroDetalle.getPago()) {
                                ventaPorPeriodo.addDs(cobroDetalle.getValor());
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * cobroDetalle.getCambio());
                            } else if (cobroDetalle.getDescuento()) {
                                ventaPorPeriodo.addDs(cobroDetalle.getValor() * -1);
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * -1 * cobroDetalle.getCambio());
                            }
                        }
                    }
                }
            }
        }
        return ventaPorPeriodoList;
    }

    @Transactional
    public Boolean cancelarVenta(Venta venta) {
        venta.setEstado(VentaEstado.CANCELADA);
        setTenant("filial"+venta.getSucursalId()+"_bkp");
        this.save(venta);
        List<MovimientoCaja> movimientoCajaList = movimientoCajaService.findByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento.VENTA, venta.getCobro().getId(), venta.getSucursalId());
        for (MovimientoCaja mov : movimientoCajaList) {
            mov.setActivo(false);
            movimientoCajaService.save(mov);
        }
        List<VentaItem> ventaItemList = ventaItemService.findByVentaId(venta.getId(), venta.getSucursalId());
        for (VentaItem vi : ventaItemList) {
            MovimientoStock movStock = movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalId(TipoMovimiento.VENTA, vi.getId(), vi.getSucursalId());
            if (movStock != null) {
                movStock.setEstado(false);
                movStock = movimientoStockService.save(movStock);
            }
        }
        clearTenant();
        return true;
    }

    public List<VentaPorSucursal> ventaPorSucursal(String fechaInicio, String fechaFin) {
        LocalDateTime inicio = stringToDate(fechaInicio);
        LocalDateTime fin = stringToDate(fechaFin);
        return null;
//        return repository.ventasPorSucursal(inicio, fin);
    }
}