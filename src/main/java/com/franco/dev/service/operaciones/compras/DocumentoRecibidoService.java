package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.repository.operaciones.DocumentoRecibidoItemRepository;
import com.franco.dev.repository.operaciones.DocumentoRecibidoRepository;
import com.franco.dev.service.operaciones.ProcesoEtapaService;
import com.franco.dev.service.sifen.SifenService;
import com.roshka.sifen.core.beans.response.RespuestaConsultaDE;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Trae la factura de un proveedor de SIFEN y la guarda. Un CDC que ya respondió 0422 no se vuelve a consultar.
 *
 * ⚠️ {@code consultarDocumentoElectronico} (la mutation de facturación) no sirve acá: marca {@code exitoso=false}
 * con 0422 porque compara contra 0300, y no lee el contenido de la factura. Se usa {@link SifenService#consultarDE}
 * directo, que para un CDC ajeno no toca nada nuestro (busca el DE en la base, no lo encuentra y sigue).
 */
@Service
public class DocumentoRecibidoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoRecibidoService.class);
    static final String ENCONTRADO = "0422";

    /** Solo existe con {@code sifen.enabled=true}: sin él el central arranca igual y Compras avisa al escanear. */
    @Autowired(required = false)
    private SifenService sifenService;
    @Autowired
    private DocumentoRecibidoRepository repository;
    @Autowired
    private DocumentoRecibidoItemRepository itemRepository;
    @Autowired
    private ProcesoEtapaService procesoEtapaService;

    public DocumentoRecibido obtener(String cdc) {
        DocumentoRecibido doc = repository.findByCdc(cdc).orElse(null);
        if (doc != null && ENCONTRADO.equals(doc.getCodigoRespuesta()) && doc.getXml() != null) {
            completarCodigos(doc);
            return doc;
        }
        if (sifenService == null) {
            throw new GraphQLException("SIFEN está desactivado en este servidor (sifen.enabled=false): no se puede"
                    + " consultar la factura. Cargala desde el desktop.");
        }
        RespuestaConsultaDE respuesta;
        try {
            respuesta = sifenService.consultarDE(cdc);
        } catch (Exception e) {
            log.warn("No se pudo consultar el CDC {} en SIFEN: {}", cdc, e.getMessage());
            throw new GraphQLException("No se pudo consultar la factura en SIFEN. Probá de nuevo en unos minutos"
                    + " o cargala desde el desktop.");
        }
        String codigo = respuesta != null ? respuesta.getdCodRes() : null;
        if (!ENCONTRADO.equals(codigo)) {
            throw new GraphQLException(mensajeDeCodigo(codigo, respuesta != null ? respuesta.getdMsgRes() : null));
        }
        FacturaSifenXml.Cabecera leida;
        try {
            leida = FacturaSifenXml.leer(respuesta.getRespuestaBruta());
        } catch (IllegalArgumentException e) {
            log.warn("La factura {} vino de SIFEN pero no se pudo leer: {}", cdc, e.getMessage());
            throw new GraphQLException("SIFEN devolvió la factura pero no se pudo leer: " + e.getMessage());
        }
        // Antes de tocar nada: la misma factura pudo entrar antes por foto
        exigirFacturaLibre(leida.rucEmisor, leida.tipoDocumento, leida.timbrado, leida.establecimiento,
                leida.puntoExpedicion, leida.numero, doc != null ? doc.getId() : null, false);
        if (doc == null) {
            doc = new DocumentoRecibido();
            doc.setCreadoEn(LocalDateTime.now());
        } else {
            itemRepository.deleteByDocumentoRecibidoId(doc.getId());
        }
        doc.setCdc(cdc);
        copiarCabecera(leida, doc);
        doc.setCodigoRespuesta(codigo);
        doc.setMensajeRespuesta(respuesta.getdMsgRes());
        doc.setXml(respuesta.getRespuestaBruta());
        doc.setFechaConsulta(LocalDateTime.now());
        doc = repository.save(doc);
        for (FacturaSifenXml.Renglon r : leida.renglones) {
            itemRepository.save(renglon(doc, r));
        }
        return doc;
    }

    /**
     * Una factura guardada con una versión anterior del lector puede no tener el código de barras que el proveedor
     * escribió fuera de dGtin. Se relee del XML guardado (no se vuelve a consultar SIFEN).
     */
    public void completarCodigos(DocumentoRecibido doc) {
        if (doc.getXml() == null) return; // la factura en papel no tiene XML
        List<DocumentoRecibidoItem> items = renglones(doc.getId());
        if (items.stream().noneMatch(i -> i.getGtin() == null)) return;
        FacturaSifenXml.Cabecera leida;
        try {
            leida = FacturaSifenXml.leer(doc.getXml());
        } catch (IllegalArgumentException e) {
            return;
        }
        for (DocumentoRecibidoItem i : items) {
            if (i.getGtin() != null || i.getOrden() == null) continue;
            leida.renglones.stream().filter(r -> r.orden == i.getOrden()).findFirst().ifPresent(r -> {
                if (r.gtin != null) {
                    i.setGtin(r.gtin);
                    itemRepository.save(i);
                }
            });
        }
    }

    /**
     * La misma factura (emisor sin ceros, tipo, timbrado y número: la clave de uk_documento_recibido_numero) en otro
     * documento. Si ya tiene nota o espera otra compra, se rechaza; si es una lectura en papel que quedó a medias, se
     * libera y sigue la nueva; si ya entró por el QR, la foto se rechaza. Se llama ANTES de modificar un documento
     * gestionado: la consulta hace flush y un UPDATE con la clave repetida chocaría con el índice.
     *
     * @param propioId el documento que se está guardando (no cuenta como repetido), o null si es nuevo
     * @param desdeFoto quien llama es la factura en papel
     */
    public void exigirFacturaLibre(String rucEmisor, Integer tipoDocumento, Integer timbrado, String establecimiento,
                                   String puntoExpedicion, String numero, Long propioId, boolean desdeFoto) {
        String ruc = FacturaSifenXml.sinCeros(rucEmisor);
        if (ruc == null || timbrado == null || numero == null) return;
        DocumentoRecibido otro = repository.buscarMismaFactura(ruc, tipoDocumento != null ? tipoDocumento : 1, timbrado,
                        establecimiento, puntoExpedicion, numero).stream()
                .filter(d -> propioId == null || !propioId.equals(d.getId()))
                .findFirst().orElse(null);
        if (otro == null) return;
        if (otro.getNotaRecepcion() != null) {
            NotaRecepcion nota = otro.getNotaRecepcion();
            throw new GraphQLException("Esta factura ya está cargada: nota " + nota.getNumero() + " del pedido "
                    + (nota.getPedido() != null ? nota.getPedido().getId() : "?") + ".");
        }
        exigirQueNoEspereOtroPedido(otro, null);
        if (DocumentoRecibido.ORIGEN_FOTO.equals(otro.getOrigen())) {
            liberar(otro);
            return;
        }
        throw new GraphQLException(desdeFoto
                ? "Esta factura ya se escaneó con el QR del KuDE: cargala escaneando el QR."
                : "Ya hay otra factura electrónica con este número (CDC " + otro.getCdc() + ").");
    }

    /**
     * Una lectura en papel abandonada deja de ocupar el número: sin timbrado sale del índice único, y sin cabecera
     * confirmada no puede crear la compra ni cargar la nota.
     */
    public void liberar(DocumentoRecibido doc) {
        doc.setTimbrado(null);
        doc.setCabeceraConfirmada(false);
        repository.saveAndFlush(doc);
    }

    /**
     * Una factura que creó una compra (todavía en planificación) no se usa para otra: se carga sola en esa al
     * finalizarla. Si esa compra se canceló, la factura queda libre.
     */
    public void exigirQueNoEspereOtroPedido(DocumentoRecibido doc, Long pedidoPermitido) {
        if (!Boolean.TRUE.equals(doc.getCargarAlFinalizar()) || doc.getPedido() == null) return;
        Long otro = doc.getPedido().getId();
        if (otro.equals(pedidoPermitido) || pedidoCancelado(otro)) return;
        throw new GraphQLException("Esta factura ya creó la compra " + otro + ": se carga sola cuando se finalice "
                + "su planificación en el desktop.");
    }

    private boolean pedidoCancelado(Long pedidoId) {
        return procesoEtapaService.getEtapaByPedidoAndTipo(pedidoId, ProcesoEtapaTipo.CREACION)
                .map(e -> e.getEstadoEtapa() == ProcesoEtapaEstado.CANCELADA).orElse(false);
    }

    public List<DocumentoRecibidoItem> renglones(Long documentoId) {
        return itemRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(documentoId);
    }

    /** ¿La factura guardada tiene un evento de cancelación? Se mira en el XML para no sumar columnas. */
    public boolean cancelada(DocumentoRecibido doc) {
        return doc.getXml() != null && doc.getXml().contains("rGeVeCan");
    }

    static String mensajeDeCodigo(String codigo, String mensaje) {
        if ("0420".equals(codigo)) {
            return "SIFEN no encuentra esa factura (0420). Puede que el proveedor todavía no la haya enviado o que"
                    + " se la hayan rechazado: pedile la factura aprobada o cargala desde el desktop.";
        }
        if ("0421".equals(codigo)) {
            return "SIFEN no nos deja ver esa factura (0421): el certificado de la empresa no tiene permiso para"
                    + " consultarla.";
        }
        return "SIFEN respondió " + (codigo != null ? codigo : "sin código")
                + (mensaje != null ? ": " + mensaje : "") + ".";
    }

    private static void copiarCabecera(FacturaSifenXml.Cabecera c, DocumentoRecibido d) {
        d.setTipoDocumento(c.tipoDocumento);
        d.setRucEmisor(c.rucEmisor);
        d.setDvEmisor(c.dvEmisor);
        d.setRazonSocialEmisor(c.razonSocialEmisor);
        d.setRucReceptor(c.rucReceptor);
        d.setDvReceptor(c.dvReceptor);
        d.setRazonSocialReceptor(c.razonSocialReceptor);
        d.setTimbrado(c.timbrado);
        d.setEstablecimiento(c.establecimiento);
        d.setPuntoExpedicion(c.puntoExpedicion);
        d.setNumero(c.numero);
        d.setFechaEmision(c.fechaEmision);
        d.setCondicionOperacion(c.condicionOperacion);
        d.setMoneda(c.moneda);
        d.setCotizacion(c.cotizacion);
        d.setSubtotalExenta(c.subtotalExenta);
        d.setSubtotalIva5(c.subtotalIva5);
        d.setSubtotalIva10(c.subtotalIva10);
        d.setTotalDescuento(c.totalDescuento);
        d.setTotalGeneral(c.totalGeneral);
        d.setTotalIva5(c.totalIva5);
        d.setTotalIva10(c.totalIva10);
        d.setTotalIva(c.totalIva);
    }

    private static DocumentoRecibidoItem renglon(DocumentoRecibido doc, FacturaSifenXml.Renglon r) {
        DocumentoRecibidoItem i = new DocumentoRecibidoItem();
        i.setDocumentoRecibido(doc);
        i.setOrden(r.orden);
        i.setCodigoProveedor(r.codigoProveedor);
        i.setGtin(r.gtin);
        i.setGtinPaquete(r.gtinPaquete);
        i.setDescripcion(r.descripcion);
        i.setUnidadMedida(r.unidadMedida);
        i.setCantidad(r.cantidad);
        i.setPrecioUnitario(r.precioUnitario);
        i.setDescuentoUnitario(r.descuentoUnitario);
        i.setPorcentajeDescuento(r.porcentajeDescuento);
        i.setTotal(r.total);
        i.setAfectacionIva(r.afectacionIva);
        i.setTasaIva(r.tasaIva);
        i.setLote(r.lote);
        i.setVencimiento(r.vencimiento);
        i.setEsBonificacion(false);
        return i;
    }
}
