package com.franco.dev.service.operaciones;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.FacturaProveedorImport;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.EstadoImport;
import com.franco.dev.domain.operaciones.enums.PedidoItemEstado;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.dto.FacturaImportPreviewDto;
import com.franco.dev.graphql.operaciones.input.ConfirmarFacturaImportItemInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.ia.FacturaIaResponse;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Genera la vista de revision (preview) con matching ejecutado, y maneja la confirmacion
 * que crea el Pedido + PedidoItems + persiste aliases aprendidos.
 */
@Service
public class FacturaImportConfirmService {

    private static final Logger log = LoggerFactory.getLogger(FacturaImportConfirmService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private FacturaProveedorImportService importService;
    @Autowired private FacturaImportMatcherService matcher;
    @Autowired private AliasImportService aliasService;
    @Autowired private ProveedorService proveedorService;
    @Autowired private ProductoService productoService;
    @Autowired private PresentacionService presentacionService;
    @Autowired private MonedaService monedaService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private PedidoService pedidoService;
    @Autowired private PedidoItemService pedidoItemService;

    public Optional<FacturaImportPreviewDto> preview(Long importId) {
        Optional<FacturaProveedorImport> opt = importService.findById(importId);
        if (!opt.isPresent()) return Optional.empty();
        FacturaProveedorImport imp = opt.get();
        if (imp.getJsonValidado() == null) {
            log.warn("Preview de import id={} sin jsonValidado", importId);
            return Optional.empty();
        }

        FacturaIaResponse data;
        try {
            data = MAPPER.readValue(imp.getJsonValidado(), FacturaIaResponse.class);
        } catch (Exception e) {
            log.error("Preview id={} - jsonValidado no parseable: {}", importId, e.getMessage());
            return Optional.empty();
        }

        FacturaImportPreviewDto dto = new FacturaImportPreviewDto();
        dto.setImportId(imp.getId());
        dto.setEstado(imp.getEstado() != null ? imp.getEstado().name() : null);
        dto.setEmisorRuc(data.getEmisorRuc());
        dto.setEmisorNombre(data.getEmisorNombre());
        dto.setNumeroFactura(data.getNumeroFactura());
        dto.setTimbrado(data.getTimbrado());
        dto.setFechaEmision(data.getFechaEmision());
        dto.setMoneda(data.getMoneda());
        dto.setTotalGeneral(data.getTotalGeneral());
        dto.setEsLegal(data.getEsLegal());

        FacturaImportMatcherService.MatchProveedor matchProv =
                matcher.matchProveedor(data.getEmisorRuc(), data.getEmisorNombre());
        dto.setProveedorSugerido(matchProv.proveedor);
        dto.setProveedorConfianza(matchProv.confianza.name());
        dto.setProveedorRazon(matchProv.razon);

        Long provIdParaMatching = matchProv.proveedor != null ? matchProv.proveedor.getId() : null;
        List<FacturaImportPreviewDto.ItemPreview> itemsPreview = new ArrayList<>();
        if (data.getItems() != null) {
            for (FacturaIaResponse.Item item : data.getItems()) {
                FacturaImportMatcherService.MatchProducto mp = matcher.matchProducto(
                        item.getCodigoProducto(), item.getNombreProducto(), provIdParaMatching);

                FacturaImportPreviewDto.ItemPreview ip = new FacturaImportPreviewDto.ItemPreview();
                ip.setTextoOcr(item.getNombreProducto());
                ip.setCodigoOcr(item.getCodigoProducto());
                ip.setCantidad(item.getCantidad());
                ip.setPrecioUnitario(item.getPrecioUnitario());
                ip.setDescuento(item.getDescuento());
                ip.setTotalItem(item.getTotalItem());
                ip.setProductoSugerido(mp.producto);
                ip.setProductoConfianza(mp.confianza.name());
                ip.setProductoRazon(mp.razon);
                ip.setCandidatos(mp.candidatos != null ? mp.candidatos : Collections.emptyList());
                itemsPreview.add(ip);
            }
        }
        dto.setItems(itemsPreview);
        return Optional.of(dto);
    }

    /**
     * Confirma el import: crea Pedido + PedidoItems con los items no omitidos, persiste aliases
     * y marca el import como CONFIRMADO.
     */
    @Transactional
    public FacturaProveedorImport confirmar(Long importId, Long proveedorId,
                                            List<ConfirmarFacturaImportItemInput> items,
                                            String tipoBoleta, Boolean crearPedido, Long usuarioId) {
        FacturaProveedorImport imp = importService.findById(importId)
                .orElseThrow(() -> new IllegalArgumentException("Import no encontrado: " + importId));

        Proveedor proveedor = proveedorService.findById(proveedorId)
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado: " + proveedorId));

        // Datos del payload original (para aprender aliases)
        FacturaIaResponse data;
        try {
            data = imp.getJsonValidado() != null
                    ? MAPPER.readValue(imp.getJsonValidado(), FacturaIaResponse.class)
                    : null;
        } catch (Exception e) {
            log.warn("confirmar id={} - jsonValidado no parseable, sigo sin aprender aliases", importId);
            data = null;
        }

        Pedido pedido = null;
        if (Boolean.TRUE.equals(crearPedido)) {
            pedido = crearPedidoDesdeItems(imp, proveedor, tipoBoleta, items, data, usuarioId);
            imp.setPedidoId(pedido.getId());
        }

        // Aprender alias proveedor (siempre que tengamos texto OCR o RUC)
        if (data != null && data.getEmisorNombre() != null && !data.getEmisorNombre().isEmpty()) {
            aliasService.guardarAliasProveedor(data.getEmisorNombre(), data.getEmisorRuc(), proveedor);
        }

        // Aprender alias productos
        if (items != null) {
            for (ConfirmarFacturaImportItemInput in : items) {
                if (Boolean.TRUE.equals(in.getOmitir())) continue;
                if (in.getProductoId() == null) continue;
                Producto producto = productoService.findById(in.getProductoId()).orElse(null);
                if (producto == null) continue;
                if (in.getTextoOcr() != null && !in.getTextoOcr().isEmpty()) {
                    aliasService.guardarAliasProducto(
                            in.getTextoOcr(), in.getCodigoOcr(), producto, proveedor);
                }
            }
        }

        imp.setEstado(EstadoImport.CONFIRMADO);
        return importService.save(imp);
    }

    private Pedido crearPedidoDesdeItems(FacturaProveedorImport imp, Proveedor proveedor,
                                         String tipoBoleta,
                                         List<ConfirmarFacturaImportItemInput> items,
                                         FacturaIaResponse data, Long usuarioId) {
        Pedido pedido = new Pedido();
        pedido.setProveedor(proveedor);
        pedido.setTipoBoleta(tipoBoleta);
        pedido.setCreadoEn(LocalDateTime.now());
        if (usuarioId != null) {
            Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
            pedido.setUsuario(usuario);
        }
        if (data != null && data.getMoneda() != null) {
            // Buscar moneda por denominacion ej "PYG" -> "Guarani". Si no encuentra, dejamos null.
            Moneda m = monedaService.getRepository().findByDenominacion(data.getMoneda());
            if (m == null && "PYG".equalsIgnoreCase(data.getMoneda())) {
                m = monedaService.getRepository().findByDenominacion("Guarani");
            }
            pedido.setMoneda(m);
        }
        pedido = pedidoService.save(pedido);
        log.info("Import id={} - Pedido creado id={} para proveedor={}",
                imp.getId(), pedido.getId(), proveedor.getId());

        if (items != null) {
            for (ConfirmarFacturaImportItemInput in : items) {
                if (Boolean.TRUE.equals(in.getOmitir())) continue;
                if (in.getProductoId() == null) continue;

                Producto producto = productoService.findById(in.getProductoId()).orElse(null);
                if (producto == null) continue;

                Presentacion presentacion = null;
                if (in.getPresentacionId() != null) {
                    presentacion = presentacionService.findById(in.getPresentacionId()).orElse(null);
                }

                PedidoItem item = new PedidoItem();
                item.setPedido(pedido);
                item.setProducto(producto);
                item.setPresentacionCreacion(presentacion);
                item.setCantidadSolicitada(in.getCantidad());
                item.setPrecioUnitarioSolicitado(in.getPrecioUnitario());
                item.setEsBonificacion(false);
                item.setEstado(PedidoItemEstado.ACTIVO);
                pedidoItemService.save(item);
            }
        }
        return pedido;
    }
}
