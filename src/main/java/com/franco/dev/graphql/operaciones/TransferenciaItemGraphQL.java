package com.franco.dev.graphql.operaciones;

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
import com.franco.dev.service.operaciones.ConversionPresentacion;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class TransferenciaItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

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

    public TransferenciaItem saveTransferenciaItem(TransferenciaItemInput input, Double precioCosto) {
        // usuario_id es NOT NULL en la base. Sin este chequeo, un input sin usuarioId reventaba con
        // NullPointerException porque CrudService.findById devuelve null crudo cuando el id es null.
        if (input.getUsuarioId() == null) {
            throw new GraphQLException(
                    "No se puede guardar el item de transferencia sin usuarioId: falta el responsable.");
        }
        ModelMapper m = new ModelMapper();
        TransferenciaItem e = m.map(input, TransferenciaItem.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setTransferencia(transferenciaService.findById(input.getTransferenciaId()).orElse(null));
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

        // El save es un PATCH: lo que el input no trae se conserva de la fila existente.
        TransferenciaItem existente = input.getId() != null
                ? service.findById(input.getId()).orElse(null)
                : null;
        if (existente != null) {
            preservarCamposAusentes(e, existente);
        } else if (e.getVencimientoVerificado() == null) {
            e.setVencimientoVerificado(false);
        }
        e = service.save(e);
        // Antes de generar el movimiento: el desglose por lote lee esta asignacion para decidir
        // de que lotes sale la mercaderia. Si se guardara despues, la primera vez saldria por FEFO.
        guardarAsignacionDeLotes(input, e);
        movimientoStockService.createMovimientoFromTransferenciaItem(e);

        // El costo SOLO se actualiza cuando la transferencia proviene de la sucursal COMPRAS:
        // es una vía alternativa (temporal) para cargar una compra cuando el módulo de compras falla.
        // Cualquier otra transferencia es un simple movimiento de stock y NO altera el costo del producto.
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
        return e;
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
     */
    public TransferenciaItem desconfirmarTransferenciaItem(Long id, EtapaTransferencia etapa) {
        Optional<TransferenciaItem> encontrado = id != null ? service.findById(id) : null;
        TransferenciaItem ti = encontrado != null ? encontrado.orElse(null) : null;
        if (ti == null) {
            throw new GraphQLException("No existe el item de transferencia " + id);
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
        }
    }

    public TransferenciaItem verificarProducto(Long id, Boolean vencimientoVerificado) {
        System.out.println(
                "Received request to verify product: ID=" + id + ", vencimientoVerificado=" + vencimientoVerificado);
        return service.verificarProducto(id, vencimientoVerificado);
    }

}
