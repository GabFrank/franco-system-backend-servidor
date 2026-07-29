package com.franco.dev.graphql.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaAsignacionLote;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.dto.TransferenciaItemAlertaDTO;
import com.franco.dev.graphql.operaciones.input.TransferenciaItemInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaItemAlertaService;
import com.franco.dev.service.operaciones.TransferenciaItemLoteService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.PresentacionService;
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

    public TransferenciaItem saveTransferenciaItem(TransferenciaItemInput input, Double precioCosto) {
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
        if (input.getVencimientoVerificado() == null)
            e.setVencimientoVerificado(false);
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
        List<TransferenciaItemLoteService.AsignacionSolicitada> solicitadas = input.getLotesAsignados()
                .stream()
                .filter(l -> l != null && l.getLoteId() != null)
                .map(l -> new TransferenciaItemLoteService.AsignacionSolicitada(l.getLoteId(), l.getCantidad()))
                .collect(Collectors.toList());
        transferenciaItemLoteService.reemplazarAsignacion(e, etapa, solicitadas, e.getUsuario());
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

    public TransferenciaItem verificarProducto(Long id, Boolean vencimientoVerificado) {
        System.out.println(
                "Received request to verify product: ID=" + id + ", vencimientoVerificado=" + vencimientoVerificado);
        return service.verificarProducto(id, vencimientoVerificado);
    }

}
