package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.graphql.operaciones.compras.FacturaPendientePedido;
import com.franco.dev.graphql.operaciones.compras.RenglonPendienteFactura;
import com.franco.dev.repository.operaciones.DocumentoRecibidoItemRepository;
import com.franco.dev.repository.operaciones.DocumentoRecibidoRepository;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.ProcesoEtapaService;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Los renglones de la factura que quedaron afuera al crear la compra con solo los reconocidos (#124): el desktop los
 * carga como ítems del pedido y, si se borra el ítem, el renglón vuelve a quedar pendiente. El que no se va a cargar
 * se descarta con su motivo (y se puede restaurar), mientras la planificación siga en proceso.
 */
@Service
public class RenglonPendienteService {

    static final String YA_CARGADO = "Ese renglón de la factura ya está cargado.";
    static final String DE_OTRO_PEDIDO = "Ese renglón no es de una factura de este pedido.";
    static final String DESCARTADO = "Ese renglón de la factura está descartado: restauralo para cargarlo.";

    @Autowired private DocumentoRecibidoItemRepository renglonRepository;
    static final String SIN_MOTIVO = "Escribí por qué no se carga este renglón.";
    static final String PLANIFICACION_CERRADA = "Solo se puede descartar con la planificación en proceso.";
    static final String NO_DESCARTADO = "Ese renglón de la factura no está descartado.";
    static final String YA_DESCARTADO = "Ese renglón de la factura ya está descartado.";
    static final String FACTURA_NO_ESPERA = "Ese renglón no es de una factura que espera la planificación.";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired private AprendizajeProveedor aprendizaje;
    @Autowired private DocumentoRecibidoRepository documentoRepository;
    @Autowired private ProcesoEtapaService procesoEtapaService;
    @Autowired private MonedaService monedaService;

    /**
     * Vincula un renglón pendiente (sin producto, no descartado) con el ítem recién guardado, como «Vincular» +
     * «Recordar» del celular: producto y presentación del ítem, factor = unidades de la presentación y lo aprendido
     * para las próximas facturas del proveedor.
     */
    @Transactional
    public void vincularAlItem(Long renglonId, PedidoItem item) {
        DocumentoRecibidoItem renglon = renglonId != null ? renglonRepository.findById(renglonId).orElse(null) : null;
        if (renglon == null) {
            throw new GraphQLException("No se encontró el renglón de la factura.");
        }
        if (renglon.getProducto() != null) {
            throw new GraphQLException(YA_CARGADO);
        }
        if (Boolean.TRUE.equals(renglon.getDescartado())) {
            throw new GraphQLException(DESCARTADO);
        }
        DocumentoRecibido doc = renglon.getDocumentoRecibido();
        if (doc == null || doc.getPedido() == null || item.getPedido() == null
                || !doc.getPedido().getId().equals(item.getPedido().getId())
                || !Boolean.TRUE.equals(doc.getCargarAlFinalizar()) || doc.getNotaRecepcion() != null) {
            throw new GraphQLException(DE_OTRO_PEDIDO);
        }
        if (item.getProducto() == null) {
            throw new GraphQLException("Elegí el producto del ítem.");
        }
        Presentacion presentacion = item.getPresentacionCreacion();
        double factor = presentacion != null && presentacion.getCantidad() != null && presentacion.getCantidad() > 0
                ? presentacion.getCantidad() : 1.0;
        renglon.setProducto(item.getProducto());
        renglon.setPresentacion(presentacion);
        renglon.setFactor(factor);
        renglon.setTipoMatch(ComprasMobileService.TipoMatch.MANUAL.name());
        renglon.setPedidoItem(item);
        renglonRepository.save(renglon);
        if (presentacion != null && doc.getProveedor() != null && AprendizajeProveedor.sePuedeAprender(renglon)) {
            aprendizaje.aprender(doc.getProveedor(), renglon, presentacion, factor);
        }
    }

    /**
     * Antes de borrar un ítem (o de cambiarle el producto): sus renglones de facturas sin nota vuelven a pendiente.
     * Va antes del borrado porque la FK {@code pedido_item_id} es ON DELETE SET NULL y después ya no se los encuentra.
     */
    @Transactional
    public void liberarDelItem(Long pedidoItemId) {
        if (pedidoItemId == null) return;
        for (DocumentoRecibidoItem renglon : renglonRepository.findByPedidoItemId(pedidoItemId)) {
            DocumentoRecibido doc = renglon.getDocumentoRecibido();
            if (doc == null || doc.getNotaRecepcion() != null) continue;
            renglon.setProducto(null);
            renglon.setPresentacion(null);
            renglon.setFactor(null);
            renglon.setTipoMatch(null);
            renglon.setPedidoItem(null);
            renglon.setObservacion(null);
            renglonRepository.save(renglon);
        }
    }

    /** Marca un renglón pendiente como «no se carga», con el motivo, quién y cuándo. */
    @Transactional
    public void descartar(Long renglonId, String motivo, Usuario usuario) {
        String m = motivo != null ? motivo.trim() : "";
        if (m.isEmpty()) {
            throw new GraphQLException(SIN_MOTIVO);
        }
        DocumentoRecibidoItem renglon = renglonDeFacturaEnEspera(renglonId);
        if (renglon.getProducto() != null) {
            throw new GraphQLException(YA_CARGADO);
        }
        if (Boolean.TRUE.equals(renglon.getDescartado())) {
            throw new GraphQLException(YA_DESCARTADO);
        }
        exigirPlanificacionEnProceso(renglon.getDocumentoRecibido().getPedido());
        renglon.setDescartado(true);
        renglon.setMotivoDescarte(m);
        renglon.setDescartadoPor(usuario);
        renglon.setDescartadoEn(LocalDateTime.now());
        renglonRepository.save(renglon);
    }

    /** Un renglón descartado vuelve a pendiente. */
    @Transactional
    public void restaurar(Long renglonId) {
        DocumentoRecibidoItem renglon = renglonDeFacturaEnEspera(renglonId);
        if (!Boolean.TRUE.equals(renglon.getDescartado())) {
            throw new GraphQLException(NO_DESCARTADO);
        }
        exigirPlanificacionEnProceso(renglon.getDocumentoRecibido().getPedido());
        renglon.setDescartado(false);
        renglon.setMotivoDescarte(null);
        renglon.setDescartadoPor(null);
        renglon.setDescartadoEn(null);
        renglonRepository.save(renglon);
    }

    /**
     * Las facturas del pedido que esperan la planificación (con {@code cargarAlFinalizar} y sin nota), con los
     * renglones que faltan cargar y los descartados, en el orden de la factura. Las que no tienen ninguno no vienen.
     */
    @Transactional(readOnly = true)
    public List<FacturaPendientePedido> pendientesDelPedido(Long pedidoId) {
        List<FacturaPendientePedido> facturas = new ArrayList<>();
        if (pedidoId == null) return facturas;
        for (DocumentoRecibido doc : documentoRepository.findByPedidoIdOrderByIdAsc(pedidoId)) {
            if (!esperaPlanificacion(doc)) continue;
            Double factor = factorAlPedido(doc, doc.getPedido());
            FacturaPendientePedido f = new FacturaPendientePedido();
            double pendiente = 0;
            double descartado = 0;
            for (DocumentoRecibidoItem r : renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(doc.getId())) {
                boolean esDescartado = Boolean.TRUE.equals(r.getDescartado());
                if (r.getProducto() != null && !esDescartado) continue;
                if (esDescartado) descartado += importe(r);
                else pendiente += importe(r);
                f.getRenglones().add(renglonPendiente(r, factor));
            }
            if (f.getRenglones().isEmpty()) continue;
            f.setDocumentoId(doc.getId());
            f.setNumeroCompleto(ComprasMobileService.numeroCompleto(doc));
            f.setMonedaSimbolo(simboloDeFactura(doc));
            f.setMontoPendiente(CruceFactura.redondear(pendiente, 2));
            f.setMontoDescartado(CruceFactura.redondear(descartado, 2));
            facturas.add(f);
        }
        return facturas;
    }

    private DocumentoRecibidoItem renglonDeFacturaEnEspera(Long renglonId) {
        DocumentoRecibidoItem renglon = renglonId != null ? renglonRepository.findById(renglonId).orElse(null) : null;
        if (renglon == null) {
            throw new GraphQLException("No se encontró el renglón de la factura.");
        }
        if (!esperaPlanificacion(renglon.getDocumentoRecibido())) {
            throw new GraphQLException(FACTURA_NO_ESPERA);
        }
        return renglon;
    }

    private static boolean esperaPlanificacion(DocumentoRecibido doc) {
        return doc != null && doc.getPedido() != null && Boolean.TRUE.equals(doc.getCargarAlFinalizar())
                && doc.getNotaRecepcion() == null;
    }

    private void exigirPlanificacionEnProceso(Pedido pedido) {
        boolean enProceso = procesoEtapaService.getEtapaByPedidoAndTipo(pedido.getId(), ProcesoEtapaTipo.CREACION)
                .map(e -> e.getEstadoEtapa() == ProcesoEtapaEstado.EN_PROCESO).orElse(false);
        if (!enProceso) {
            throw new GraphQLException(PLANIFICACION_CERRADA);
        }
    }

    private RenglonPendienteFactura renglonPendiente(DocumentoRecibidoItem r, Double factor) {
        RenglonPendienteFactura rp = new RenglonPendienteFactura();
        rp.setId(r.getId());
        rp.setOrden(r.getOrden());
        rp.setDescripcion(r.getDescripcion());
        rp.setCantidad(r.getCantidad());
        rp.setUnidadMedida(r.getUnidadMedida());
        rp.setPrecioUnitario(r.getPrecioUnitario());
        rp.setTotal(r.getTotal());
        rp.setVencimiento(r.getVencimiento() != null ? r.getVencimiento().format(FECHA) : null);
        Double neto = precioNeto(r);
        rp.setPrecioPedido(neto == null || factor == null ? null
                : factor == 1.0 ? neto : CruceFactura.redondear(neto * factor, 4));
        rp.setDescartado(Boolean.TRUE.equals(r.getDescartado()));
        rp.setMotivoDescarte(r.getMotivoDescarte());
        rp.setDescartadoPor(nombre(r.getDescartadoPor()));
        return rp;
    }

    /** Precio neto por unidad de la factura: el total sobre la cantidad; sin total, el unitario menos el descuento. */
    static Double precioNeto(DocumentoRecibidoItem r) {
        if (r.getTotal() != null && r.getCantidad() != null && r.getCantidad() > 0) {
            return r.getTotal() / r.getCantidad();
        }
        if (r.getPrecioUnitario() == null) return null;
        return r.getPrecioUnitario() - (r.getDescuentoUnitario() != null ? r.getDescuentoUnitario() : 0);
    }

    private static double importe(DocumentoRecibidoItem r) {
        if (r.getTotal() != null) return r.getTotal();
        Double neto = precioNeto(r);
        return neto != null && r.getCantidad() != null ? neto * r.getCantidad() : 0;
    }

    /**
     * El factor de la factura a la moneda del pedido con la cotización que quedó guardada en el pedido al crearlo
     * (la misma con la que se convirtieron sus ítems), no con la del día. La de la factura: 1 el guaraní, si no la
     * que trae la factura.
     */
    static Double factorAlPedido(DocumentoRecibido doc, Pedido pedido) {
        String deFactura = ComprasMobileService.denominacionMoneda(doc.getMoneda());
        Moneda delPedido = pedido != null ? pedido.getMoneda() : null;
        if (deFactura == null || delPedido == null || delPedido.getDenominacion() == null) return null;
        boolean misma = deFactura.equalsIgnoreCase(delPedido.getDenominacion().trim());
        Double cotFactura = ComprasMobileService.GUARANI.equals(deFactura) ? Double.valueOf(1.0)
                : doc.getCotizacion() != null && doc.getCotizacion() > 1 ? doc.getCotizacion() : null;
        Double cotPedido = ComprasMobileService.GUARANI.equalsIgnoreCase(delPedido.getDenominacion().trim())
                ? Double.valueOf(1.0) : pedido.getCotizacion();
        return CruceFactura.factorFacturaAPedido(misma, cotFactura, cotPedido);
    }

    private String simboloDeFactura(DocumentoRecibido doc) {
        String denominacion = ComprasMobileService.denominacionMoneda(doc.getMoneda());
        Moneda m = denominacion != null ? monedaService.findByDescripcion(denominacion) : null;
        return m != null ? m.getSimbolo() : doc.getMoneda();
    }

    private static String nombre(Usuario u) {
        if (u == null) return null;
        if (u.getPersona() != null && u.getPersona().getNombre() != null && !u.getPersona().getNombre().trim().isEmpty()) {
            return u.getPersona().getNombre().trim();
        }
        return u.getNickname();
    }
}
