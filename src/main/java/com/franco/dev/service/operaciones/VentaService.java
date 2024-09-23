package com.franco.dev.service.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.dto.VentaPorPeriodoV1Dto;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import com.franco.dev.service.financiero.VentaCreditoService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.*;
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

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private VentaCreditoService ventaCreditoService;

    @Override
    public VentaRepository getRepository() {
        return repository;
    }


//    public List<Venta> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//    }

    public Venta findByIdAndSucursalId(Long id, Long sucId) {
        return repository.findByIdAndSucursalId(id, sucId);
    }

    public Page<Venta> findByCajaId(EmbebedPrimaryKey id, Integer page, Integer size, Boolean asc, Long formaPago, VentaEstado estado, Boolean isDelivery, Long monedaId) {
        Pageable pagina = PageRequest.of(page, size);
        return findWithFiltersCriteria(id.getId(), id.getSucursalId(), formaPago, estado, pagina, isDelivery, monedaId, asc);
    }

    public List<Venta> findAllByCajaId(EmbebedPrimaryKey id) {
        List<Venta> aux = repository.findByCajaIdAndCajaSucursalId(id.getId(), id.getSucursalId());
        return aux;
    }

    @Override
    public Venta save(Venta entity) {
        Venta e = super.save(entity);
        return e;
    }

    @Override
    public Venta saveAndSend(Venta entity, Boolean recibir) {
        Venta e = super.save(entity);
        return e;
    }

    @Override
    public Venta saveAndSend(Venta entity, Long sucId) {
        Venta e = super.save(entity);
        return e;
    }

    public List<Venta> ventaPorPeriodoAndSucursal(String inicio, String fin, Long sucId) {
        LocalDateTime fechaInicio = stringToDate(inicio);
        LocalDateTime fechaFin = stringToDate(fin);
        List<Venta> ventaList = null;
        ventaList = repository.findBySucursalIdAndCreadoEnBetweenOrderByIdDesc(sucId, fechaInicio, fechaFin);
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
            List<Venta> ventaList = repository.ventaPorPeriodo(ventaPorPeriodo.getCreadoEn(), ventaPorPeriodo.getCreadoEn().plusDays(1));
            ventaPorPeriodo.setCantVenta(ventaList.size());
            for (Venta venta : ventaList) {
                if (venta.getEstado() != VentaEstado.CANCELADA || venta.getEstado() != VentaEstado.ABIERTA) {
                    List<CobroDetalle> cobroDetalleList = cobroDetalleService.findByCobroId(venta.getCobro().getId(), venta.getSucursalId());
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

    @Transactional()
    public Boolean cancelarVenta(Venta venta) {
        try {
            venta.setEstado(VentaEstado.CANCELADA);
            venta = this.save(venta);
            List<MovimientoCaja> movimientoCajaList = movimientoCajaService.findByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento.VENTA, venta.getCobro().getId(), venta.getSucursalId());
            for (MovimientoCaja mov : movimientoCajaList) {
                mov.setActivo(false);
                movimientoCajaService.save(mov);
            }
            List<VentaItem> ventaItemList = ventaItemService.findByVentaId(venta.getId(), venta.getSucursalId());
            for (VentaItem vi : ventaItemList) {
                MovimientoStock movStock = movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento.VENTA, vi.getId(), vi.getSucursalId(), vi.getProducto().getId());
                if (movStock != null) {
                    movStock.setEstado(false);
                    movStock = movimientoStockService.save(movStock);
                }
            }
            Delivery delivery = venta.getDelivery();
            if (delivery != null) {
                delivery.setEstado(DeliveryEstado.CANCELADO);
                deliveryService.save(delivery);
            }
            VentaCredito ventaCredito = ventaCreditoService.findByVentaIdAndSucId(venta.getId(), venta.getSucursalId());
            if (ventaCredito != null) {
                ventaCreditoService.cancelarVentaCredito(ventaCredito.getId(), ventaCredito.getSucursalId());
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("No se pudo cancelar la venta");
        }
    }

    public List<VentaPorSucursal> ventaPorSucursal(String fechaInicio, String fechaFin) {
        LocalDateTime inicio = stringToDate(fechaInicio);
        LocalDateTime fin = stringToDate(fechaFin);
        return null;
    }

    public Page<Venta> findWithFiltersCriteria(Long id, Long sucId, Long formaPagoId, VentaEstado estado, Pageable pageable, Boolean isDelivery, Long monedaId, Boolean isAsc) {
        Sort sort = isAsc == false ? Sort.by("id").descending() : Sort.by("id").ascending();
        Pageable newPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        return this.repository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Venta, PdvCaja> cajaJoin = root.join("caja", JoinType.INNER);

            // Add the predicates
            predicates.add(cb.equal(cajaJoin.get("id"), id));
            predicates.add(cb.equal(root.get("sucursalId"), sucId));

            if (formaPagoId != null || monedaId != null) {
                Subquery<Long> cobroDetalleSubquery = query.subquery(Long.class);
                Root<CobroDetalle> cobroDetalleRoot = cobroDetalleSubquery.from(CobroDetalle.class);

                List<Predicate> subqueryPredicates = new ArrayList<>();

                subqueryPredicates.add(cb.equal(cobroDetalleRoot.get("cobro"), root.get("cobro")));
                subqueryPredicates.add(cb.equal(cobroDetalleRoot.get("sucursalId"), root.get("sucursalId")));

                if (formaPagoId != null) {
                    subqueryPredicates.add(cb.equal(cobroDetalleRoot.get("formaPago").get("id"), formaPagoId));
                }

                if (monedaId != null) {
                    subqueryPredicates.add(cb.equal(cobroDetalleRoot.get("moneda").get("id"), monedaId));
                }

                cobroDetalleSubquery.select(cobroDetalleRoot.get("id"))
                        .where(subqueryPredicates.toArray(new Predicate[0]));

                predicates.add(cb.exists(cobroDetalleSubquery));
            }

            if (estado != null) {
                predicates.add(cb.equal(root.get("estado"), estado));
            }

            if (isDelivery != null) {
                Join<Venta, Delivery> deliveryJoin = root.join("delivery", JoinType.LEFT);
                if (isDelivery == true) {
                    predicates.add(cb.isNotNull(deliveryJoin.get("id")));
                } else {
                    predicates.add(cb.isNull(deliveryJoin.get("id")));
                }
            }

            // Combine predicates with AND
            return cb.and(predicates.toArray(new Predicate[0]));
        }, newPageable);
    }
}

// dropdown de moneda no aparece (revisar porque)