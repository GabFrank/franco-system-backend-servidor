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
import com.franco.dev.service.empresarial.SucursalService;

import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import static java.time.temporal.ChronoUnit.DAYS;

@Service
@AllArgsConstructor
public class VentaService extends CrudService<Venta, VentaRepository, EmbebedPrimaryKey> {
    private final VentaRepository repository;

    @Autowired
    private EntityManager em;

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

    @Autowired
    private SucursalService sucursalService;

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

    public List<VentaPorPeriodoV1Dto> ventaPorPeriodo(String inicio, String fin, Long sucId) {
        LocalDateTime fechaInicio = stringToDate(inicio);
        LocalDateTime fechaFin = stringToDate(fin);
        if (fechaInicio == null || fechaFin == null)
            return new ArrayList<>();

        List<VentaPorPeriodoV1Dto> res = new ArrayList<>();
        Map<String, VentaPorPeriodoV1Dto> map = new HashMap<>();
        long days = DAYS.between(fechaInicio, fechaFin);
        for (int i = 0; i < days; i++) {
            LocalDateTime d = fechaInicio.plusDays(i);
            VentaPorPeriodoV1Dto dto = new VentaPorPeriodoV1Dto();
            dto.setCreadoEn(d);
            dto.setCantVenta(0);
            String key = d.toLocalDate().toString();
            map.put(key, dto);
            res.add(dto);
        }

        // OPTIMIZACIÓN: Usar JOIN FETCH para traer CobroDetalle en una sola consulta
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Venta> cq = cb.createQuery(Venta.class);
        Root<Venta> root = cq.from(Venta.class);

        // Hacer JOIN FETCH con cobro
        Fetch<Venta, Cobro> cobroFetch = root.fetch("cobro", JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.between(root.get("creadoEn"), fechaInicio, fechaFin));
        if (sucId != null)
            predicates.add(cb.equal(root.get("sucursalId"), sucId));
        predicates.add(cb.notEqual(root.get("estado"), VentaEstado.CANCELADA));
        predicates.add(cb.notEqual(root.get("estado"), VentaEstado.ABIERTA));
        cq.where(predicates.toArray(new Predicate[0]));
        cq.distinct(true); // Evitar duplicados por el JOIN

        List<Venta> allVentas = em.createQuery(cq).getResultList();

        // Obtener todos los IDs de cobros para hacer una sola consulta de CobroDetalle
        List<Long> cobroIds = new ArrayList<>();
        for (Venta v : allVentas) {
            if (v.getCobro() != null && v.getCobro().getId() != null) {
                cobroIds.add(v.getCobro().getId());
            }
        }

        // OPTIMIZACIÓN: Una sola consulta para todos los CobroDetalle
        Map<Long, List<CobroDetalle>> cobroDetalleMap = new HashMap<>();
        if (!cobroIds.isEmpty()) {
            // Dividir en lotes de 1000 para evitar problemas con IN clause muy grandes
            int batchSize = 1000;
            for (int i = 0; i < cobroIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, cobroIds.size());
                List<Long> batch = cobroIds.subList(i, end);

                CriteriaBuilder cb2 = em.getCriteriaBuilder();
                CriteriaQuery<CobroDetalle> cq2 = cb2.createQuery(CobroDetalle.class);
                Root<CobroDetalle> cdRoot = cq2.from(CobroDetalle.class);
                cdRoot.fetch("moneda", JoinType.LEFT); // Traer moneda también

                List<Predicate> cdPredicates = new ArrayList<>();
                cdPredicates.add(cdRoot.get("cobroId").in(batch));
                if (sucId != null) {
                    cdPredicates.add(cb2.equal(cdRoot.get("sucursalId"), sucId));
                }
                cq2.where(cdPredicates.toArray(new Predicate[0]));

                List<CobroDetalle> batchDetalles = em.createQuery(cq2).getResultList();

                // Agrupar por cobroId
                for (CobroDetalle cd : batchDetalles) {
                    Long cobroId = cd.getCobro().getId();
                    cobroDetalleMap.computeIfAbsent(cobroId, k -> new ArrayList<>()).add(cd);
                }
            }
        }

        // Procesar ventas con los datos ya cargados
        for (Venta v : allVentas) {
            String key = v.getCreadoEn().toLocalDate().toString();
            VentaPorPeriodoV1Dto dto = map.get(key);
            if (dto == null)
                continue;

            dto.setCantVenta(dto.getCantVenta() + 1);

            if (v.getCobro() != null && v.getCobro().getId() != null) {
                List<CobroDetalle> detalles = cobroDetalleMap.get(v.getCobro().getId());
                if (detalles != null) {
                    for (CobroDetalle cd : detalles) {
                        procesarCobroDetalle(dto, cd);
                    }
                }
            }
        }
        return res;
    }

    private void procesarCobroDetalle(VentaPorPeriodoV1Dto dto, CobroDetalle cd) {
        Double cambio = cd.getCambio() != null ? cd.getCambio() : 1.0;
        double valor = cd.getValor() != null ? cd.getValor() : 0.0;
        if (cd.getDescuento() != null && cd.getDescuento())
            valor *= -1;

        String den = cd.getMoneda().getDenominacion();
        if (den.contains("GUARANI")) {
            dto.addGs(valor);
            dto.addTotalGs(valor);
        } else if (den.contains("REAL")) {
            dto.addRs(valor);
            dto.addTotalGs(valor * cambio);
        } else if (den.contains("DOLAR")) {
            dto.addDs(valor);
            dto.addTotalGs(valor * cambio);
        }
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
        return repository.getVentasPorSucursal(inicio, fin);
    }

    public List<VentaPorSucursal> ventaPorSucursalAndUsuario(Long usuarioId, String inicio, String fin) {
        LocalDateTime fechaInicio = stringToDate(inicio);
        LocalDateTime fechaFin = stringToDate(fin);
        List<VentaPorSucursal> ventaPorSucursales = new ArrayList<>();
        List<Venta> ventas = repository.findByUsuarioIdAndCreadoEnBetweenOrderByIdDesc(usuarioId, fechaInicio,
                fechaFin);
        for (Venta v : ventas) {
            VentaPorSucursal vps = null;
            for (VentaPorSucursal vpsAux : ventaPorSucursales) {
                if (vpsAux.getSucId().equals(v.getSucursalId())) {
                    vps = vpsAux;
                }
            }
            if (vps == null) {
                vps = new VentaPorSucursal();
                vps.setSucId(v.getSucursalId());
                vps.setNombre("Sucursal " + v.getSucursalId());
                if (sucursalService != null) {
                    java.util.Optional<com.franco.dev.domain.empresarial.Sucursal> s = sucursalService
                            .findById(v.getSucursalId());
                    if (s.isPresent())
                        vps.setNombre(s.get().getNombre());
                }
                vps.setTotal(0.0);
                ventaPorSucursales.add(vps);
            }
            if (v.getEstado().equals(VentaEstado.CONCLUIDA)) {
                vps.setTotal(vps.getTotal() + v.getTotalGs());
            }
        }

        return ventaPorSucursales;
    }

    public List<VentaPorFuncionario> ventasPorFuncionario(String inicio, String fin, Long sucId) {
        LocalDateTime fechaInicio = stringToDate(inicio);
        LocalDateTime fechaFin = stringToDate(fin);
        List<VentaPorFuncionario> list = repository.getVentasPorFuncionario(fechaInicio, fechaFin, sucId,
                PageRequest.of(0, 20));

        for (VentaPorFuncionario vpf : list) {

            // Fetch Sucursales
            if (sucId != null) {
                sucursalService.findById(sucId).ifPresent(s -> vpf.setSucursales(s.getNombre()));
            } else {
                List<String> sucursalIds = repository.findSucursalesByUsuario(vpf.getId(), fechaInicio, fechaFin);
                if (sucursalIds != null && !sucursalIds.isEmpty()) {
                    List<String> nombres = new ArrayList<>();
                    for (String sId : sucursalIds) {
                        try {
                            Long susIdLong = Long.parseLong(sId);
                            sucursalService.findById(susIdLong).ifPresent(s -> nombres.add(s.getNombre()));
                        } catch (Exception e) {
                        }
                    }
                    vpf.setSucursales(String.join(", ", nombres));
                }
            }
        }
        return list;
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

    public List<com.franco.dev.domain.operaciones.VentaPorHora> ventasPorHora(String fecha, Long sucId) {
        java.time.LocalDate date = java.time.LocalDate.parse(fecha);
        LocalDateTime inicio = date.atStartOfDay();
        LocalDateTime fin = date.atTime(java.time.LocalTime.MAX);
        return repository.ventasPorHora(inicio, fin, sucId);
    }

    public List<com.franco.dev.domain.operaciones.VentaPorMes> ventasPorMes(Integer anio, Long sucId) {
        LocalDateTime inicio = LocalDateTime.of(anio, 1, 1, 0, 0);
        LocalDateTime fin = LocalDateTime.of(anio, 12, 31, 23, 59, 59);
        return repository.ventasPorMes(inicio, fin, sucId);
    }
}

// dropdown de moneda no aparece (revisar porque)