package com.franco.dev.graphql.operaciones.compras;

import com.franco.dev.service.operaciones.compras.ComprasMobileService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Compras desde el celular (PWA): cargar la factura del proveedor al pedido escaneando el KuDE.
 *
 * Usado en: Desktop no · Mobile sí (módulo Compras). Las operaciones del desktop no se tocan.
 */
@Component
public class ComprasMobileGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ComprasMobileService service;

    public Page<CompraPedidoResumenMobile> pedidosEsperandoFacturaMobile(String texto, Long proveedorId, Integer page, Integer size) {
        return service.pedidosEsperandoFactura(texto, proveedorId, page != null ? page : 0, size != null ? size : 20);
    }

    public Page<CompraPedidoResumenMobile> comprasMobile(String texto, Boolean soloEsperandoFactura, Integer page, Integer size) {
        return service.compras(texto, null, Boolean.TRUE.equals(soloEsperandoFactura), page != null ? page : 0, size != null ? size : 20);
    }

    public CompraPedidoMobile pedidoComprasMobile(Long pedidoId) {
        return service.pedido(pedidoId);
    }

    public PedidosParaFacturaMobile pedidosParaFacturaMobile(String qr) {
        return service.pedidosParaFactura(qr);
    }

    public FacturaProveedorMobile facturaProveedorMobile(Long id, Long pedidoId) {
        return service.factura(id, pedidoId);
    }

    public ResumenFinalizarNotasMobile resumenFinalizarNotasMobile(Long pedidoId) {
        return service.resumenFinalizar(pedidoId);
    }

    public FacturaProveedorMobile facturaNuevaCompraMobile(Long id) {
        return service.facturaNuevaCompra(id);
    }

    public java.util.List<PresentacionCompraMobile> presentacionesCompraMobile(Long productoId, Long renglonId) {
        return service.presentacionesParaRenglon(productoId, renglonId);
    }

    public OpcionesNuevaCompraMobile opcionesNuevaCompraMobile() {
        return service.opcionesNuevaCompra();
    }

    public FacturaProveedorMobile leerFacturaNuevaCompraMobile(String qr) {
        return service.leerFacturaNuevaCompra(qr);
    }

    public CompraPedidoMobile crearCompraDesdeFacturaMobile(Long documentoId, Long formaPagoId, Integer plazoCredito,
                                                           Long sucursalId, java.util.List<Long> sucursalIds,
                                                           java.util.List<RepartoRenglonMobileInput> repartos, Long monedaId) {
        return service.crearCompraDesdeFactura(documentoId, formaPagoId, plazoCredito, sucursalId, sucursalIds, repartos, monedaId);
    }

    public KudeProveedorMobile kudeFacturaProveedorMobile(String qr) {
        return service.kudeProveedor(qr);
    }

    public ConversionMonedaMobile conversionMonedaCompraMobile(Long documentoId, Long monedaId) {
        return service.conversionMoneda(documentoId, monedaId);
    }

    public FacturaProveedorMobile leerFacturaProveedorMobile(Long pedidoId, String qr) {
        return service.leerFactura(pedidoId, qr);
    }

    public FacturaProveedorMobile vincularRenglonFacturaMobile(Long renglonId, Long presentacionId, Double factor, Boolean recordar) {
        return service.vincularRenglon(renglonId, presentacionId, factor, recordar);
    }

    public FacturaProveedorMobile observarRenglonFacturaMobile(Long renglonId, String observacion) {
        return service.observarRenglon(renglonId, observacion);
    }

    public CompraNotaMobile confirmarFacturaMobile(Long documentoId, Long pedidoId) {
        return service.confirmarFactura(documentoId, pedidoId);
    }

    public CompraPedidoMobile eliminarNotaComprasMobile(Long notaId) {
        return service.eliminarNota(notaId);
    }

    public CompraPedidoMobile finalizarRecepcionNotasMobile(Long pedidoId) {
        return service.finalizar(pedidoId);
    }
}
