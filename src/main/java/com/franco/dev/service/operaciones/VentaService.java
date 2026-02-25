package com.franco.dev.service.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.FacturaLegal;
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
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import com.franco.dev.service.financiero.VentaCreditoService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.sifen.SifenEventoService;

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

    @Autowired
    private FacturaLegalService facturaLegalService;

    @Autowired
    private SifenEventoService sifenEventoService;

    @Override
    public VentaRepository getRepository() {
        return repository;
    }

    // public List<Venta> findByAll(String texto){
    // texto = texto.replace(' ', '%');
    // return repository.findByProveedor(texto.toLowerCase());
    // }

    public Venta findByIdAndSucursalId(Long id, Long sucId) {
        return repository.findByIdAndSucursalId(id, sucId);
    }

    public Page<Venta> findByCajaId(EmbebedPrimaryKey id, Integer page, Integer size, Boolean asc, Long formaPago,
            VentaEstado estado, Boolean isDelivery, Long monedaId) {
        Pageable pagina = PageRequest.of(page, size);
        return findWithFiltersCriteria(null, id.getId(), id.getSucursalId(), formaPago, estado, pagina, isDelivery,
                monedaId, asc, null, null);
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
            List<Venta> ventaList = repository.ventaPorPeriodo(ventaPorPeriodo.getCreadoEn(),
                    ventaPorPeriodo.getCreadoEn().plusDays(1));
            ventaPorPeriodo.setCantVenta(ventaList.size());
            for (Venta venta : ventaList) {
                if (venta.getEstado() != VentaEstado.CANCELADA || venta.getEstado() != VentaEstado.ABIERTA) {
                    List<CobroDetalle> cobroDetalleList = cobroDetalleService.findByCobroId(venta.getCobro().getId(),
                            venta.getSucursalId());
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
            if (venta.getEstado() == VentaEstado.CANCELADA) {
                venta.setEstado(VentaEstado.CONCLUIDA);
            } else {
                venta.setEstado(VentaEstado.CANCELADA);
            }
            venta = this.save(venta);
            List<MovimientoCaja> movimientoCajaList = movimientoCajaService.findByTipoMovimientoAndReferencia(
                    PdvCajaTipoMovimiento.VENTA, venta.getCobro().getId(), venta.getSucursalId());
            for (MovimientoCaja mov : movimientoCajaList) {
                if (venta.getEstado() == VentaEstado.CANCELADA) {
                    mov.setActivo(false);
                } else {
                    mov.setActivo(true);
                }
                movimientoCajaService.save(mov);
            }
            List<VentaItem> ventaItemList = ventaItemService.findByVentaId(venta.getId(), venta.getSucursalId());
            for (VentaItem vi : ventaItemList) {
                MovimientoStock movStock = movimientoStockService
                        .findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento.VENTA, vi.getId(),
                                vi.getSucursalId(), vi.getProducto().getId());
                if (movStock != null) {
                    if (venta.getEstado() == VentaEstado.CANCELADA) {
                        movStock.setEstado(false);
                    } else {
                        movStock.setEstado(true);
                    }
                    movStock = movimientoStockService.save(movStock);
                }
            }
            Delivery delivery = venta.getDelivery();
            if (delivery != null) {
                if (venta.getEstado() == VentaEstado.CANCELADA) {
                    delivery.setEstado(DeliveryEstado.CANCELADO);
                } else {
                    delivery.setEstado(DeliveryEstado.CONCLUIDO);
                }
                deliveryService.save(delivery);
            }
            VentaCredito ventaCredito = ventaCreditoService.findByVentaIdAndSucId(venta.getId(), venta.getSucursalId());
            if (ventaCredito != null) {
                ventaCreditoService.cancelarVentaCredito(ventaCredito.getId(), ventaCredito.getSucursalId(), venta);
            }

            log.info("Buscando factura legal para venta ID: " + venta.getId() + ", Sucursal: " + venta.getSucursalId());
            FacturaLegal facturaLegal = facturaLegalService.findByVentaIdAndSucursalId(venta.getId(),
                    venta.getSucursalId());
            if (facturaLegal != null) {
                log.info("Factura legal encontrada - ID: " + facturaLegal.getId() + ", CDC: "
                        + (facturaLegal.getCdc() != null ? facturaLegal.getCdc() : "null"));
                // Si la factura es electrónica, cancelar el documento electrónico
                if (facturaLegal.getCdc() != null && !facturaLegal.getCdc().isEmpty()) {
                    log.info("Iniciando cancelación de documento electrónico con CDC: " + facturaLegal.getCdc());
                    try {
                        sifenEventoService.cancelarDE(facturaLegal.getCdc(), "Cancelación de venta");
                        log.info("✅ Documento electrónico cancelado exitosamente para venta ID: "
                                + venta.getId().toString());
                    } catch (Exception e) {
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        log.warning("⚠️ Error al cancelar documento electrónico para venta ID: "
                                + venta.getId().toString());
                        log.warning("   Tipo de error: " + e.getClass().getName());
                        log.warning("   Mensaje: " + errorMsg);
                        e.printStackTrace();
                        // No lanzamos excepción para no impedir la cancelación de la venta
                        // El evento de cancelación se guardará en BD y podrá ser procesado
                        // posteriormente
                        log.info(
                                "La venta se cancelará de todas formas. El evento puede ser procesado posteriormente.");
                    }
                } else {
                    log.info("Factura legal no tiene CDC (no es electrónica)");
                }
                // Marcar factura como inactiva
                log.info("Marcando factura legal como inactiva");
                facturaLegal.setActivo(false);
                facturaLegalService.save(facturaLegal);
                log.info("✅ Factura legal marcada como inactiva");
            } else {
                log.info("No se encontró factura legal para esta venta");
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

    public Page<Venta> onSearch(Long idVenta, Long idCaja, Pageable pageable, Boolean asc, Long sucId, Long formaPago,
            VentaEstado estado, Boolean isDelivery, Long monedaId, Boolean conDescuento, Boolean conAumento) {
        return findWithFiltersCriteria(idVenta, idCaja, sucId, formaPago, estado, pageable, isDelivery, monedaId, asc,
                conDescuento, conAumento);
    }

    public Page<Venta> findWithFiltersCriteria(Long idVenta, Long id, Long sucId, Long formaPagoId, VentaEstado estado,
            Pageable pageable, Boolean isDelivery, Long monedaId, Boolean isAsc, Boolean conDescuento,
            Boolean conAumento) {
        Sort sort = isAsc == false ? Sort.by("id").descending() : Sort.by("id").ascending();
        Pageable newPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        return this.repository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Venta, PdvCaja> cajaJoin = root.join("caja", JoinType.INNER);

            // Add the predicates
            predicates.add(cb.equal(cajaJoin.get("id"), id));
            predicates.add(cb.equal(root.get("sucursalId"), sucId));

            if (idVenta != null) {
                predicates.add(cb.equal(root.get("id"), idVenta));
            }

            if (formaPagoId != null || monedaId != null || (conDescuento != null && conDescuento)
                    || (conAumento != null && conAumento)) {
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

                if (conDescuento != null && conDescuento == true) {
                    subqueryPredicates.add(cb.isTrue(cobroDetalleRoot.get("descuento")));
                }

                if (conAumento != null && conAumento == true) {
                    subqueryPredicates.add(cb.isTrue(cobroDetalleRoot.get("aumento")));
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

    public Page<Venta> onGenericSearch(Long idVenta, Long idCaja, Pageable pageable, Boolean asc, Long sucId, Long formaPago,
            VentaEstado estado, Boolean isDelivery, Long monedaId, Boolean conDescuento, Boolean conAumento, Boolean conObservacion, Long clienteId, String fechaInicio, String fechaFin) {
        return findWithGenericFiltersCriteria(idVenta, idCaja, sucId, formaPago, estado, pageable, isDelivery, monedaId, asc,
                conDescuento, conAumento, conObservacion, clienteId, fechaInicio, fechaFin);
    }

    public Page<Venta> findWithGenericFiltersCriteria(Long idVenta, Long id, Long sucId, Long formaPagoId, VentaEstado estado,
            Pageable pageable, Boolean isDelivery, Long monedaId, Boolean isAsc, Boolean conDescuento,
            Boolean conAumento, Boolean conObservacion, Long clienteId, String fechaInicio, String fechaFin) {
        Sort sort = isAsc == false ? Sort.by("id").descending() : Sort.by("id").ascending();
        Pageable newPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        return this.repository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Add the predicates
            if(id != null){
                Join<Venta, PdvCaja> cajaJoin = root.join("caja", JoinType.INNER);
                predicates.add(cb.equal(cajaJoin.get("id"), id));
            }
            
            if(sucId != null){
                predicates.add(cb.equal(root.get("sucursalId"), sucId));
            }

            if (idVenta != null) {
                predicates.add(cb.equal(root.get("id"), idVenta));
            }

            if(clienteId != null){
                predicates.add(cb.equal(root.get("cliente").get("id"), clienteId));
            }

            if (fechaInicio != null && fechaFin != null) {
                LocalDateTime start = stringToDate(fechaInicio);
                LocalDateTime end = stringToDate(fechaFin);
                predicates.add(cb.between(root.get("creadoEn"), start, end));
            }

            if (formaPagoId != null || monedaId != null || (conDescuento != null && conDescuento)
                    || (conAumento != null && conAumento)) {
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

                if (conDescuento != null && conDescuento == true) {
                    subqueryPredicates.add(cb.isTrue(cobroDetalleRoot.get("descuento")));
                }

                if (conAumento != null && conAumento == true) {
                    subqueryPredicates.add(cb.isTrue(cobroDetalleRoot.get("aumento")));
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

            if (conObservacion != null && conObservacion == true) {
                Subquery<Long> ventaObservacionSubquery = query.subquery(Long.class);
                Root<VentaObservacion> ventaObservacionRoot = ventaObservacionSubquery.from(VentaObservacion.class);

                ventaObservacionSubquery.select(ventaObservacionRoot.get("id"))
                        .where(cb.equal(ventaObservacionRoot.get("venta"), root));

                predicates.add(cb.exists(ventaObservacionSubquery));
            }

            // Combine predicates with AND
            return cb.and(predicates.toArray(new Predicate[0]));
        }, newPageable);
    }
}

// dropdown de moneda no aparece (revisar porque)