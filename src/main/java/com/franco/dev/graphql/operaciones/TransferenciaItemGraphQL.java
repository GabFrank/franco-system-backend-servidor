package com.franco.dev.graphql.operaciones;

import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.operaciones.TransferenciaRoles;
import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaAsignacionLote;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.dto.TransferenciaItemAlertaDTO;
import com.franco.dev.graphql.operaciones.input.TransferenciaItemInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.ControlStockNegativoService;
import com.franco.dev.service.operaciones.ConversionPresentacion;
import com.franco.dev.service.operaciones.MovimientoStockLoteService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaItemAlertaService;
import com.franco.dev.service.operaciones.TransferenciaItemLoteService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.PresentacionService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class TransferenciaItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TesoreriaSecurityService seg;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TransferenciaItemGraphQL.class);

    @Autowired
    private TransferenciaItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private TransferenciaService transferenciaService;

    @Autowired
    private TransferenciaItemLoteService transferenciaItemLoteService;

    @Autowired
    private PresentacionService presentacionService;


    @Autowired
    private CostosPorProductoService costosPorProductoService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private TransferenciaItemAlertaService transferenciaItemAlertaService;

    @Autowired
    private ControlStockNegativoService controlStockNegativoService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MovimientoStockLoteService movimientoStockLoteService;

    public Optional<TransferenciaItem> transferenciaItem(Long id) {
        return service.findById(id);
    }

    public List<TransferenciaItem> transferenciaItems(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Page<TransferenciaItem> transferenciaItensPorTransferenciaId(Long id, Integer page, Integer size) {
        Page<TransferenciaItem> res = service.findByTransferenciaItemId(id, page, size);
        return res;
    }

    public Page<TransferenciaItem> transferenciaItensPorTransferenciaIdWithFilter(Long id, String name, Integer page,
            Integer size) {
        Page<TransferenciaItem> res = service.findByTransferenciaItemIdWithFilter(id, name, page, size);
        return res;
    }

    public List<TransferenciaItemAlertaDTO> alertasTransferenciaItems(Long transferenciaId, List<Long> itemIds) {
        return transferenciaItemAlertaService.calcularAlertas(transferenciaId, itemIds);
    }

    /**
     * Copia de {@code prev} todo campo que {@code e} no traiga, para que el save sea un PATCH.
     *
     * Sin esto el merge guarda como null cada columna ausente en el input. Es lo que borro las tres
     * etapas y el creado_en del item 65830 de la transferencia 6290, y lo que mantiene
     * vencimiento_verificado en false en toda la tabla: el desktop nunca manda ese campo.
     *
     * Para vaciar una etapa a proposito existe {@link #desconfirmarTransferenciaItem}: la ausencia
     * de un campo significa "no lo toques", nunca "borralo".
     */
    private void preservarCamposAusentes(TransferenciaItem e, TransferenciaItem prev) {
        if (e.getTransferencia() == null) e.setTransferencia(prev.getTransferencia());
        if (e.getUsuario() == null) e.setUsuario(prev.getUsuario());

        if (e.getPresentacionPreTransferencia() == null) e.setPresentacionPreTransferencia(prev.getPresentacionPreTransferencia());
        if (e.getPresentacionPreparacion() == null) e.setPresentacionPreparacion(prev.getPresentacionPreparacion());
        if (e.getPresentacionTransporte() == null) e.setPresentacionTransporte(prev.getPresentacionTransporte());
        if (e.getPresentacionRecepcion() == null) e.setPresentacionRecepcion(prev.getPresentacionRecepcion());

        if (e.getCantidadPreTransferencia() == null) e.setCantidadPreTransferencia(prev.getCantidadPreTransferencia());
        if (e.getCantidadPreparacion() == null) e.setCantidadPreparacion(prev.getCantidadPreparacion());
        if (e.getCantidadTransporte() == null) e.setCantidadTransporte(prev.getCantidadTransporte());
        if (e.getCantidadRecepcion() == null) e.setCantidadRecepcion(prev.getCantidadRecepcion());

        if (e.getObservacionPreTransferencia() == null) e.setObservacionPreTransferencia(prev.getObservacionPreTransferencia());
        if (e.getObservacionPreparacion() == null) e.setObservacionPreparacion(prev.getObservacionPreparacion());
        if (e.getObservacionTransporte() == null) e.setObservacionTransporte(prev.getObservacionTransporte());
        if (e.getObservacionRecepcion() == null) e.setObservacionRecepcion(prev.getObservacionRecepcion());

        if (e.getVencimientoPreTransferencia() == null) e.setVencimientoPreTransferencia(prev.getVencimientoPreTransferencia());
        if (e.getVencimientoPreparacion() == null) e.setVencimientoPreparacion(prev.getVencimientoPreparacion());
        if (e.getVencimientoTransporte() == null) e.setVencimientoTransporte(prev.getVencimientoTransporte());
        if (e.getVencimientoRecepcion() == null) e.setVencimientoRecepcion(prev.getVencimientoRecepcion());

        if (e.getMotivoModificacionPreTransferencia() == null) e.setMotivoModificacionPreTransferencia(prev.getMotivoModificacionPreTransferencia());
        if (e.getMotivoModificacionPreparacion() == null) e.setMotivoModificacionPreparacion(prev.getMotivoModificacionPreparacion());
        if (e.getMotivoModificacionTransporte() == null) e.setMotivoModificacionTransporte(prev.getMotivoModificacionTransporte());
        if (e.getMotivoModificacionRecepcion() == null) e.setMotivoModificacionRecepcion(prev.getMotivoModificacionRecepcion());

        if (e.getMotivoRechazoPreTransferencia() == null) e.setMotivoRechazoPreTransferencia(prev.getMotivoRechazoPreTransferencia());
        if (e.getMotivoRechazoPreparacion() == null) e.setMotivoRechazoPreparacion(prev.getMotivoRechazoPreparacion());
        if (e.getMotivoRechazoTransporte() == null) e.setMotivoRechazoTransporte(prev.getMotivoRechazoTransporte());
        if (e.getMotivoRechazoRecepcion() == null) e.setMotivoRechazoRecepcion(prev.getMotivoRechazoRecepcion());

        if (e.getActivo() == null) e.setActivo(prev.getActivo());
        if (e.getPoseeVencimiento() == null) e.setPoseeVencimiento(prev.getPoseeVencimiento());
        if (e.getVencimientoVerificado() == null) e.setVencimientoVerificado(prev.getVencimientoVerificado());
        if (e.getCreadoEn() == null) e.setCreadoEn(prev.getCreadoEn());
    }

    /**
     * Corre en una transaccion que bloquea primero la transferencia y despues el item, en el mismo
     * orden que el avance (#468). Sin transaccion, el save SERIALIZABLE del servicio corria solo y,
     * si un avance de la misma transferencia ya habia tocado el item, fallaba con un 40001 crudo.
     * Ahora espera al avance, relee y {@link #validarEtapasCerradas} rechaza lo que pisaria.
     */
    public TransferenciaItem saveTransferenciaItem(TransferenciaItemInput input, Double precioCosto) {
        seg.requireAnyRole(TransferenciaRoles.ROLES);
        // usuario_id es NOT NULL en la base. Sin este chequeo, un input sin usuarioId reventaba con
        // NullPointerException porque CrudService.findById devuelve null crudo cuando el id es null.
        if (input.getUsuarioId() == null) {
            throw new GraphQLException(
                    "No se puede guardar el item de transferencia sin usuarioId: falta el responsable.");
        }
        TransferenciaItem e = new TransactionTemplate(transactionManager).execute(status -> guardarItem(input));
        // Despues del commit: registrarCostoCompraManual es @Transactional y, si lanzara adentro, dejaria
        // la transaccion rollback-only y se perderia el item aunque el catch se trague el error.
        registrarCostoSiEsCompra(e, precioCosto);
        return e;
    }

    private TransferenciaItem guardarItem(TransferenciaItemInput input) {
        ModelMapper m = new ModelMapper();
        TransferenciaItem e = m.map(input, TransferenciaItem.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));

        // La transferencia que se bloquea y contra la que se valida es la del item guardado, no la del
        // input. Es la primera carga de la transferencia en el request: lockCompartidoById no refresca.
        Long itemTransferenciaId = input.getId() != null ? service.transferenciaIdDe(input.getId()) : null;
        Long transferenciaId = itemTransferenciaId != null ? itemTransferenciaId : input.getTransferenciaId();
        Transferencia transferencia = transferenciaId != null
                ? transferenciaService.lockCompartidoById(transferenciaId).orElse(null)
                : null;
        // Sin transferenciaId en el input queda null y preservarCamposAusentes pone la del item.
        if (input.getTransferenciaId() != null) {
            e.setTransferencia(input.getTransferenciaId().equals(transferenciaId)
                    ? transferencia
                    : transferenciaService.findById(input.getTransferenciaId()).orElse(null));
        }
        if (input.getVencimientoPreTransferencia() != null)
            e.setVencimientoPreTransferencia(stringToDate(input.getVencimientoPreTransferencia()));
        if (input.getVencimientoPreparacion() != null)
            e.setVencimientoPreparacion(stringToDate(input.getVencimientoPreparacion()));
        if (input.getVencimientoTransporte() != null)
            e.setVencimientoTransporte(stringToDate(input.getVencimientoTransporte()));
        if (input.getVencimientoRecepcion() != null)
            e.setVencimientoRecepcion(stringToDate(input.getVencimientoRecepcion()));
        if (input.getPresentacionPreTransferenciaId() != null)
            e.setPresentacionPreTransferencia(
                    presentacionService.findById(input.getPresentacionPreTransferenciaId()).orElse(null));
        if (input.getPresentacionPreparacionId() != null)
            e.setPresentacionPreparacion(
                    presentacionService.findById(input.getPresentacionPreparacionId()).orElse(null));
        if (input.getPresentacionTransporteId() != null)
            e.setPresentacionTransporte(presentacionService.findById(input.getPresentacionTransporteId()).orElse(null));
        if (input.getPresentacionRecepcionId() != null)
            e.setPresentacionRecepcion(presentacionService.findById(input.getPresentacionRecepcionId()).orElse(null));
        if (input.getCreadoEn() != null)
            e.setCreadoEn(stringToDate(input.getCreadoEn()));

        // El save es un PATCH: lo que el input no trae se conserva de la fila existente. Con lock y
        // despues de la transferencia: si un avance la tenia, esto ya lee lo que el avance dejo.
        TransferenciaItem existente = input.getId() != null
                ? service.lockById(input.getId()).orElse(null)
                : null;
        if (existente != null) {
            validarEtapasCerradas(transferencia, e, existente);
            preservarCamposAusentes(e, existente);
        } else if (e.getVencimientoVerificado() == null) {
            e.setVencimientoVerificado(false);
        }
        e = service.save(e);
        // Solo items nuevos, y no los que salen de COMPRAS: ahi el stock es negativo por diseno.
        if (existente == null && !esTransferenciaDesdeCompras(e)) {
            registrarEnControlDeStock(e);
        }
        // Antes de generar el movimiento: el desglose por lote lee esta asignacion para decidir
        // de que lotes sale la mercaderia. Si se guardara despues, la primera vez saldria por FEFO.
        guardarAsignacionDeLotes(input, e);
        movimientoStockService.createMovimientoFromTransferenciaItem(e);
        return e;
    }

    /**
     * Un item existente no cambia un grupo de una etapa que la transferencia ya cerro (#468).
     *
     * Al avanzar, cada etapa copia el grupo anterior al siguiente. Un save con la pantalla vieja que
     * cambie el grupo anterior despues de esa copia deja las dos etapas desalineadas y el movimiento
     * de stock calculado con la cantidad pisada. Los clientes reenvian el item entero con lo guardado:
     * solo cuenta como cambio un campo que viene y difiere. Los items nuevos no pasan por aca: nacen
     * en creacion y frc-mobile los manda con ceros en las etapas siguientes.
     */
    private void validarEtapasCerradas(Transferencia transferencia, TransferenciaItem e, TransferenciaItem prev) {
        if (transferencia == null || transferencia.getEtapa() == null) return;
        validarGrupo(transferencia, prev, EtapaTransferencia.PRE_TRANSFERENCIA_ORIGEN, "pre-transferencia",
                cambia(e.getCantidadPreTransferencia(), prev.getCantidadPreTransferencia())
                        || cambia(e.getPresentacionPreTransferencia(), prev.getPresentacionPreTransferencia())
                        || cambia(e.getVencimientoPreTransferencia(), prev.getVencimientoPreTransferencia())
                        || cambia(e.getMotivoModificacionPreTransferencia(), prev.getMotivoModificacionPreTransferencia())
                        || cambia(e.getMotivoRechazoPreTransferencia(), prev.getMotivoRechazoPreTransferencia()));
        validarGrupo(transferencia, prev, EtapaTransferencia.PREPARACION_MERCADERIA, "preparacion",
                cambia(e.getCantidadPreparacion(), prev.getCantidadPreparacion())
                        || cambia(e.getPresentacionPreparacion(), prev.getPresentacionPreparacion())
                        || cambia(e.getVencimientoPreparacion(), prev.getVencimientoPreparacion())
                        || cambia(e.getMotivoModificacionPreparacion(), prev.getMotivoModificacionPreparacion())
                        || cambia(e.getMotivoRechazoPreparacion(), prev.getMotivoRechazoPreparacion()));
        validarGrupo(transferencia, prev, EtapaTransferencia.TRANSPORTE_VERIFICACION, "transporte",
                cambia(e.getCantidadTransporte(), prev.getCantidadTransporte())
                        || cambia(e.getPresentacionTransporte(), prev.getPresentacionTransporte())
                        || cambia(e.getVencimientoTransporte(), prev.getVencimientoTransporte())
                        || cambia(e.getMotivoModificacionTransporte(), prev.getMotivoModificacionTransporte())
                        || cambia(e.getMotivoRechazoTransporte(), prev.getMotivoRechazoTransporte()));
        validarGrupo(transferencia, prev, EtapaTransferencia.RECEPCION_EN_VERIFICACION, "recepcion",
                cambia(e.getCantidadRecepcion(), prev.getCantidadRecepcion())
                        || cambia(e.getPresentacionRecepcion(), prev.getPresentacionRecepcion())
                        || cambia(e.getVencimientoRecepcion(), prev.getVencimientoRecepcion())
                        || cambia(e.getMotivoModificacionRecepcion(), prev.getMotivoModificacionRecepcion())
                        || cambia(e.getMotivoRechazoRecepcion(), prev.getMotivoRechazoRecepcion()));
    }

    private void validarGrupo(Transferencia transferencia, TransferenciaItem prev, EtapaTransferencia hasta,
            String grupo, boolean cambio) {
        if (cambio && transferencia.getEtapa().getOrden() > hasta.getOrden()) {
            throw new GraphQLException("La transferencia " + transferencia.getId() + " ya esta en etapa "
                    + transferencia.getEtapa() + ": el item " + prev.getId() + " no se puede cambiar en "
                    + grupo + " porque esa etapa ya se cerro (la avanzo otro usuario mientras guardabas)."
                    + " Volve a abrirla para ver como quedo.");
        }
    }

    private static boolean cambia(Object nuevo, Object guardado) {
        return nuevo != null && !nuevo.equals(guardado);
    }

    private static boolean cambia(Presentacion nueva, Presentacion guardada) {
        return nueva != null && !Objects.equals(nueva.getId(), guardada != null ? guardada.getId() : null);
    }

    /**
     * Por dia: el vencimiento es una fecha, y el desktop puede reenviar una fecha pura corrida una hora
     * (00:00 del dia del cambio de horario no existe en hora local).
     */
    private static boolean cambia(LocalDateTime nuevo, LocalDateTime guardado) {
        LocalDate dia = guardado != null ? guardado.toLocalDate() : null;
        return nuevo != null && !nuevo.toLocalDate().equals(dia);
    }

    /**
     * El costo SOLO se actualiza cuando la transferencia proviene de la sucursal COMPRAS:
     * es una vía alternativa (temporal) para cargar una compra cuando el módulo de compras falla.
     * Cualquier otra transferencia es un simple movimiento de stock y NO altera el costo del producto.
     */
    private void registrarCostoSiEsCompra(TransferenciaItem e, Double precioCosto) {
        if (e != null && precioCosto != null && esTransferenciaDesdeCompras(e)) {
            try {
                if (e.getPresentacionPreTransferencia() != null
                        && e.getPresentacionPreTransferencia().getProducto() != null) {
                    Producto producto = e.getPresentacionPreTransferencia().getProducto();
                    Moneda guarani = null;
                    try {
                        guarani = monedaService.findByDescripcion("GUARANI");
                    } catch (Exception ex) {
                        System.err.println("Moneda GUARANI no encontrada: " + ex.getMessage());
                    }
                    costosPorProductoService.registrarCostoCompraManual(
                            producto, precioCosto, guarani,
                            e.getTransferencia().getSucursalOrigen(), e.getUsuario(), e.getCreadoEn());
                }
            } catch (Exception ex) {
                System.err.println("Error al actualizar CostoPorProducto en saveTransferenciaItem: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    /**
     * Control de stock negativo: un item nuevo cuyo origen ya estaba en 0 o negativo queda
     * registrado para inventario. El servicio ya no lanza; el try/catch es la segunda red, para
     * que ni un servicio sin inyectar (tests viejos) pueda impedir que el item se guarde.
     */
    private void registrarEnControlDeStock(TransferenciaItem item) {
        try {
            controlStockNegativoService.registrarTransferencia(item);
        } catch (Exception ex) {
            log.warn("Control de stock negativo: no se pudo registrar el item {} de transferencia: {}",
                    item != null ? item.getId() : null, ex.getMessage());
        }
    }

    /**
     * Persiste los lotes que el operador eligio a mano, si es que los mando.
     *
     * La semantica de {@code lotesAsignados} es la que mantiene la compatibilidad hacia atras:
     * cuando viene null no se toca nada, que es lo que hace todo cliente que no conoce esta
     * funcionalidad. Una lista vacia si borra la asignacion, para poder volver a FEFO.
     *
     * La etapa por defecto es PRE_TRANSFERENCIA, que es donde se cargan los items.
     */
    private void guardarAsignacionDeLotes(TransferenciaItemInput input, TransferenciaItem e) {
        if (input.getLotesAsignados() == null || e == null || e.getId() == null) {
            return;
        }
        EtapaAsignacionLote etapa = input.getEtapaAsignacionLote() != null
                ? input.getEtapaAsignacionLote()
                : EtapaAsignacionLote.PRE_TRANSFERENCIA;

        // Las cantidades llegan en PRESENTACIONES, que es lo que carga el operador, y el ledger
        // las guarda en UNIDADES. La conversion se hace aca para que sea la misma regla con la
        // que se le mostro el saldo disponible (stockPorLoteEnPresentacion).
        double unidadesPorPresentacion = ConversionPresentacion.unidadesPorPresentacion(
                presentacionDeLaEtapa(e, etapa));

        List<TransferenciaItemLoteService.AsignacionSolicitada> solicitadas = input.getLotesAsignados()
                .stream()
                .filter(l -> l != null && l.getLoteId() != null)
                .map(l -> new TransferenciaItemLoteService.AsignacionSolicitada(
                        l.getLoteId(),
                        ConversionPresentacion.aUnidades(l.getCantidad(), unidadesPorPresentacion)))
                .collect(Collectors.toList());
        transferenciaItemLoteService.reemplazarAsignacion(e, etapa, solicitadas, e.getUsuario());
    }

    /**
     * Presentacion contra la que se expresaron las cantidades elegidas. En preparacion puede
     * diferir de la de creacion, porque el item se prepara con otra presentacion.
     */
    private Presentacion presentacionDeLaEtapa(TransferenciaItem e, EtapaAsignacionLote etapa) {
        if (etapa == EtapaAsignacionLote.PREPARACION && e.getPresentacionPreparacion() != null) {
            return e.getPresentacionPreparacion();
        }
        return e.getPresentacionPreTransferencia();
    }

    /** True si la transferencia sale de la pseudo-sucursal COMPRAS (carga manual de compra). */
    private boolean esTransferenciaDesdeCompras(TransferenciaItem e) {
        if (e.getTransferencia() == null || e.getTransferencia().getSucursalOrigen() == null) return false;
        return CostosPorProductoService.SUCURSAL_COMPRAS
                .equalsIgnoreCase(e.getTransferencia().getSucursalOrigen().getNombre());
    }

    public Boolean deleteTransferenciaItem(Long id) {
        seg.requireAnyRole(TransferenciaRoles.ROLES);
        TransferenciaItem ti = service.findById(id).orElse(null);
        Boolean ok = service.deleteById(id);
        return ok;
    }

    /**
     * Vacia las columnas de una etapa de un item: es el "des-verificar" de la grilla.
     *
     * Existe como mutation propia porque en {@link #saveTransferenciaItem} la ausencia de un campo
     * significa "no lo toques". Sin esta separacion, limpiar y preservar serian el mismo pedido y no
     * habria forma de distinguirlos.
     *
     * El movimiento de stock de la etapa se desactiva en lugar de borrarse, igual que hace el flujo
     * cuando un item se rechaza.
     *
     * Corre en una transaccion con la transferencia y el item bloqueados, en el orden del avance
     * (#471): espera a un avance en curso en vez de fallar con un 40001, valida contra lo que dejo, y
     * el movimiento no queda desactivado si falla el save del item.
     */
    public TransferenciaItem desconfirmarTransferenciaItem(Long id, EtapaTransferencia etapa) {
        seg.requireAnyRole(TransferenciaRoles.ROLES);
        if (id == null) {
            throw new GraphQLException("No existe el item de transferencia " + id);
        }
        return new TransactionTemplate(transactionManager).execute(status -> desconfirmar(id, etapa));
    }

    private TransferenciaItem desconfirmar(Long id, EtapaTransferencia etapa) {
        // Primeras cargas del request: los lock no refrescan una instancia ya cargada.
        Long transferenciaId = service.transferenciaIdDe(id);
        Transferencia transferencia = transferenciaId != null
                ? transferenciaService.lockCompartidoById(transferenciaId).orElse(null)
                : null;
        TransferenciaItem ti = service.lockById(id).orElse(null);
        if (ti == null) {
            throw new GraphQLException("No existe el item de transferencia " + id);
        }
        // La PWA des-verifica y despues guarda. Con la pantalla vieja el save ya se rechaza (#468): sin
        // esto quedaria vaciada una etapa cerrada, con su movimiento de stock desactivado.
        if (transferencia != null && transferencia.getEtapa() != null && etapa != null
                && transferencia.getEtapa().getOrden() > etapa.getOrden()) {
            throw new GraphQLException("La transferencia " + transferencia.getId() + " ya esta en etapa "
                    + transferencia.getEtapa() + ": el item " + id + " no se puede des-verificar en " + etapa
                    + " porque esa etapa ya se cerro (la avanzo otro usuario). Volve a abrirla para ver como quedo.");
        }

        Long sucursalDelMovimiento;
        switch (etapa) {
            case PREPARACION_MERCADERIA:
                ti.setCantidadPreparacion(null);
                ti.setPresentacionPreparacion(null);
                ti.setVencimientoPreparacion(null);
                ti.setMotivoModificacionPreparacion(null);
                ti.setMotivoRechazoPreparacion(null);
                sucursalDelMovimiento = sucursalOrigenId(ti);
                break;
            case TRANSPORTE_VERIFICACION:
                ti.setCantidadTransporte(null);
                ti.setPresentacionTransporte(null);
                ti.setVencimientoTransporte(null);
                ti.setMotivoModificacionTransporte(null);
                ti.setMotivoRechazoTransporte(null);
                sucursalDelMovimiento = sucursalOrigenId(ti);
                break;
            case RECEPCION_EN_VERIFICACION:
                ti.setCantidadRecepcion(null);
                ti.setPresentacionRecepcion(null);
                ti.setVencimientoRecepcion(null);
                ti.setMotivoModificacionRecepcion(null);
                ti.setMotivoRechazoRecepcion(null);
                sucursalDelMovimiento = sucursalDestinoId(ti);
                break;
            default:
                throw new GraphQLException("En la etapa " + etapa
                        + " no se verifican items, asi que no hay nada que des-verificar.");
        }

        desactivarMovimiento(ti, sucursalDelMovimiento);
        return service.save(ti);
    }

    private Long sucursalOrigenId(TransferenciaItem ti) {
        return ti.getTransferencia() != null && ti.getTransferencia().getSucursalOrigen() != null
                ? ti.getTransferencia().getSucursalOrigen().getId()
                : null;
    }

    private Long sucursalDestinoId(TransferenciaItem ti) {
        return ti.getTransferencia() != null && ti.getTransferencia().getSucursalDestino() != null
                ? ti.getTransferencia().getSucursalDestino().getId()
                : null;
    }

    /**
     * Deja inactivo el movimiento de stock del item en esa sucursal, si existe.
     *
     * La busqueda usa el producto de la presentacion de pre-transferencia, que es la misma clave con
     * la que {@code createMovimientoFromTransferenciaItem} localiza sus movimientos.
     */
    private void desactivarMovimiento(TransferenciaItem ti, Long sucursalId) {
        if (sucursalId == null
                || ti.getPresentacionPreTransferencia() == null
                || ti.getPresentacionPreTransferencia().getProducto() == null) {
            return;
        }
        MovimientoStock ms = movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(
                TipoMovimiento.TRANSFERENCIA, ti.getId(), sucursalId,
                ti.getPresentacionPreTransferencia().getProducto().getId());
        if (ms != null && Boolean.TRUE.equals(ms.getEstado())) {
            ms.setEstado(false);
            movimientoStockService.save(ms);
            // Sin esto las filas por lote quedaban activas y el saldo por lote seguia contando la salida.
            movimientoStockLoteService.sincronizarEstado(ms);
        }
    }

    public TransferenciaItem verificarProducto(Long id, Boolean vencimientoVerificado) {
        seg.requireSuperusuario(); // configuracion o sin pantalla que la llame
        System.out.println(
                "Received request to verify product: ID=" + id + ", vencimientoVerificado=" + vencimientoVerificado);
        return service.verificarProducto(id, vencimientoVerificado);
    }

}
