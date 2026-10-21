package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.PedidoItemDistribucion;
import com.franco.dev.domain.operaciones.PedidoSucursalEntrega;
import com.franco.dev.domain.operaciones.ProcesoEtapa;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionEstado;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionItemEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.domain.productos.ProveedorCodigoProducto;
import com.franco.dev.domain.productos.dto.BuscadorProductoFiltros;
import com.franco.dev.domain.productos.dto.BuscadorProductoResultado;
import com.franco.dev.graphql.operaciones.compras.AvisoFacturaMobile;
import com.franco.dev.graphql.operaciones.compras.CompraNotaMobile;
import com.franco.dev.graphql.operaciones.compras.CompraPedidoItemMobile;
import com.franco.dev.graphql.operaciones.compras.CompraPedidoMobile;
import com.franco.dev.graphql.operaciones.compras.CompraPedidoResumenMobile;
import com.franco.dev.graphql.operaciones.compras.FacturaProveedorMobile;
import com.franco.dev.graphql.operaciones.compras.OpcionMobile;
import com.franco.dev.graphql.operaciones.compras.OpcionesNuevaCompraMobile;
import com.franco.dev.graphql.operaciones.GuardadoPedidoItemGraphQL;
import com.franco.dev.graphql.operaciones.PedidoGraphQL;
import com.franco.dev.graphql.operaciones.input.PedidoInput;
import com.franco.dev.graphql.operaciones.input.PedidoItemDistribucionInput;
import com.franco.dev.graphql.operaciones.input.PedidoItemInput;
import com.franco.dev.domain.operaciones.enums.PedidoItemEstado;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.graphql.operaciones.compras.PedidosParaFacturaMobile;
import com.franco.dev.graphql.operaciones.compras.RenglonFacturaMobile;
import com.franco.dev.graphql.operaciones.compras.ResumenFinalizarNotasMobile;
import com.franco.dev.graphql.operaciones.compras.SugerenciaProductoMobile;
import com.franco.dev.repository.operaciones.DocumentoRecibidoItemRepository;
import com.franco.dev.repository.operaciones.DocumentoRecibidoRepository;
import com.franco.dev.repository.operaciones.PedidoSucursalEntregaRepository;
import com.franco.dev.repository.productos.CodigoRepository;
import com.franco.dev.repository.productos.ProveedorCodigoProductoRepository;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.NotaRecepcionItemDistribucionService;
import com.franco.dev.service.operaciones.NotaRecepcionItemService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.PedidoItemDistribucionService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.operaciones.ProcesoEtapaService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoProveedorService;
import com.franco.dev.service.productos.search.BuscadorProductoInteligenteService;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compras desde el celular: la etapa «Recepción documental» (RECEPCION_NOTA) del pedido, hecha escaneando el KuDE
 * de la factura del proveedor.
 *
 * Es la misma lógica que el desktop ({@code gestion-compras}, tab 3), con las reglas que allá viven en la pantalla
 * aplicadas acá, en el servidor: el celular solo muestra. Las mutations del desktop no se tocan (memoria «Compras:
 * refactor sin cambiar lógica»): esto son operaciones paralelas con sufijo {@code Mobile}.
 *
 * Qué queda en el desktop: dividir un ítem entre notas, rechazar ítems, editar a mano la distribución por sucursal
 * y la nota cargada a mano para un proveedor sin factura electrónica.
 */
@Service
public class ComprasMobileService {

    private static final Logger log = LoggerFactory.getLogger(ComprasMobileService.class);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** El formato que lee {@code DateUtils.stringToDate} en los inputs del pedido. */
    private static final DateTimeFormatter FECHA_HORA_SEG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String GUARANI = "GUARANI";
    private static final double TOLERANCIA_TOTAL = 1.0;

    /** Código ISO 4217 de SIFEN → denominación de financiero.moneda. */
    private static final Map<String, String> MONEDAS = new HashMap<>();

    static {
        MONEDAS.put("PYG", GUARANI);
        MONEDAS.put("USD", "DOLAR");
        MONEDAS.put("BRL", "REAL");
        MONEDAS.put("ARS", "PESO ARG");
    }

    @Autowired private ComprasSecurityService seguridad;
    @Autowired private DocumentoRecibidoService documentoRecibidoService;
    @Autowired private DocumentoRecibidoRepository documentoRepository;
    @Autowired private DocumentoRecibidoItemRepository renglonRepository;
    @Autowired private ProveedorCodigoProductoRepository aprendizajeRepository;
    @Autowired private PedidoService pedidoService;
    @Autowired private PedidoItemService pedidoItemService;
    @Autowired private PedidoItemDistribucionService pedidoItemDistribucionService;
    @Autowired private PedidoSucursalEntregaRepository pedidoSucursalEntregaRepository;
    @Autowired private NotaRecepcionService notaRecepcionService;
    @Autowired private NotaRecepcionItemService notaRecepcionItemService;
    @Autowired private NotaRecepcionItemDistribucionService distribucionService;
    @Autowired private ProcesoEtapaService procesoEtapaService;
    @Autowired private MonedaService monedaService;
    @Autowired private CambioService cambioService;
    @Autowired private CodigoRepository codigoRepository;
    @Autowired private PresentacionService presentacionService;
    @Autowired private ProductoProveedorService productoProveedorService;
    @Autowired private BuscadorProductoInteligenteService buscadorProductoService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PedidoGraphQL pedidoGraphQL;
    @Autowired private GuardadoPedidoItemGraphQL guardadoPedidoItemGraphQL;
    @Autowired private SucursalService sucursalService;
    @Autowired private FormaPagoService formaPagoService;
    @Autowired private ProveedorService proveedorService;

    // =================================================================================== Pedidos

    /** Pedidos con RECEPCION_NOTA en PENDIENTE o EN_PROCESO, del más nuevo al más viejo. */
    @Transactional(readOnly = true)
    public Page<CompraPedidoResumenMobile> pedidosEsperandoFactura(String texto, Long proveedorId, int page, int size) {
        seguridad.exigirCompras();
        int tamano = size > 0 ? Math.min(size, 50) : 20;
        int pagina = Math.max(page, 0);
        String filtro = texto != null ? texto.trim().toUpperCase() : "";
        long prov = proveedorId != null ? proveedorId : 0L;
        String desde = "FROM operaciones.pedido p "
                + "LEFT JOIN personas.proveedor pr ON pr.id = p.proveedor_id "
                + "LEFT JOIN personas.persona per ON per.id = pr.persona_id "
                + "LEFT JOIN financiero.moneda m ON m.id = p.moneda_id "
                + "WHERE EXISTS (SELECT 1 FROM operaciones.proceso_etapa pe WHERE pe.pedido_id = p.id "
                + "  AND pe.tipo_etapa::text = 'RECEPCION_NOTA' AND pe.estado_etapa::text IN ('PENDIENTE','EN_PROCESO')) "
                + "AND (? = 0 OR p.proveedor_id = ?) "
                + "AND (? = '' OR UPPER(per.nombre) LIKE ? OR CAST(p.id AS text) = ? OR per.documento LIKE ?) ";
        Object[] filtros = {prov, prov, filtro, "%" + filtro + "%", filtro, filtro + "%"};
        Integer total = jdbc.queryForObject("SELECT COUNT(*) " + desde, Integer.class, filtros);
        List<Object> args = new ArrayList<>(Arrays.asList(filtros));
        args.add(tamano);
        args.add(pagina * tamano);
        List<CompraPedidoResumenMobile> filas = jdbc.query(
                "SELECT p.id, p.proveedor_id, per.nombre, per.documento, p.creado_en, m.simbolo, "
                        + "(SELECT pe.estado_etapa::text FROM operaciones.proceso_etapa pe WHERE pe.pedido_id = p.id "
                        + "   AND pe.tipo_etapa::text = 'RECEPCION_NOTA' ORDER BY pe.id DESC LIMIT 1) AS estado_etapa, "
                        + "(SELECT COALESCE(SUM(pi.cantidad_solicitada * COALESCE(pi.precio_unitario_solicitado, 0)), 0) "
                        + "   FROM operaciones.pedido_item pi WHERE pi.pedido_id = p.id "
                        + "   AND COALESCE(pi.es_bonificacion, false) = false) AS total, "
                        + "(SELECT COUNT(*) FROM operaciones.pedido_item pi WHERE pi.pedido_id = p.id) AS items, "
                        + "(SELECT COUNT(*) FROM operaciones.pedido_item pi WHERE pi.pedido_id = p.id AND pi.cantidad_solicitada "
                        + "   - COALESCE((SELECT SUM(nri.cantidad_en_nota) FROM operaciones.nota_recepcion_item nri "
                        + "     WHERE nri.pedido_item_id = pi.id), 0) > 0.001) AS pendientes, "
                        + "(SELECT COUNT(*) FROM operaciones.nota_recepcion nr WHERE nr.pedido_id = p.id) AS notas "
                        + desde + "ORDER BY p.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    CompraPedidoResumenMobile r = new CompraPedidoResumenMobile();
                    r.setPedidoId(rs.getLong("id"));
                    long pid = rs.getLong("proveedor_id");
                    r.setProveedorId(rs.wasNull() ? null : pid);
                    r.setProveedorNombre(rs.getString("nombre"));
                    r.setProveedorRuc(rs.getString("documento"));
                    java.sql.Timestamp ts = rs.getTimestamp("creado_en");
                    r.setFecha(ts != null ? ts.toLocalDateTime().format(FECHA_HORA) : null);
                    r.setMonedaSimbolo(rs.getString("simbolo"));
                    r.setEstadoEtapa(rs.getString("estado_etapa"));
                    r.setTotalPedido(rs.getDouble("total"));
                    r.setCantidadItems(rs.getInt("items"));
                    r.setItemsPendientes(rs.getInt("pendientes"));
                    r.setCantidadNotas(rs.getInt("notas"));
                    return r;
                }, args.toArray());
        return new PageImpl<>(filas, PageRequest.of(pagina, tamano), total != null ? total : 0);
    }

    @Transactional(readOnly = true)
    public CompraPedidoMobile pedido(Long pedidoId) {
        seguridad.exigirCompras();
        return armarPedido(pedidoObligatorio(pedidoId));
    }

    /** El QR escaneado desde el botón universal: de qué proveedor es y qué pedidos suyos esperan factura. */
    @Transactional(readOnly = true)
    public PedidosParaFacturaMobile pedidosParaFactura(String qr) {
        seguridad.exigirCompras();
        KudeQr kude = KudeQr.leer(qr);
        if (!kude.isOk()) {
            throw new GraphQLException(kude.getMensaje());
        }
        PedidosParaFacturaMobile r = new PedidosParaFacturaMobile();
        r.setCdc(kude.getCdc());
        r.setTipoDocumentoDescripcion(KudeQr.descripcionTipo(kude.getTipoDocumento()));
        r.setNumeroCompleto(kude.getEstablecimiento() + "-" + kude.getPuntoExpedicion() + "-" + kude.getNumero());
        r.setProveedorRuc(kude.getRucEmisor() + "-" + kude.getDvEmisor());
        List<Map<String, Object>> proveedores = jdbc.queryForList(
                "SELECT pr.id, per.nombre, per.documento FROM personas.proveedor pr "
                        + "JOIN personas.persona per ON per.id = pr.persona_id WHERE per.documento IS NOT NULL");
        for (Map<String, Object> p : proveedores) {
            if (mismoRuc((String) p.get("documento"), kude.getRucEmisor())) {
                Long id = ((Number) p.get("id")).longValue();
                Page<CompraPedidoResumenMobile> pedidos = pedidosEsperandoFactura(null, id, 0, 20);
                if (r.getProveedorId() == null || !pedidos.isEmpty()) {
                    r.setProveedorId(id);
                    r.setProveedorNombre((String) p.get("nombre"));
                    r.setPedidos(new ArrayList<>(pedidos.getContent()));
                }
                if (!pedidos.isEmpty()) break;
            }
        }
        return r;
    }

    // =================================================================================== Factura

    /** Escanear el KuDE: trae la factura de SIFEN (o de la caché), valida y la cruza con el pedido. */
    @Transactional
    public FacturaProveedorMobile leerFactura(Long pedidoId, String qr) {
        seguridad.exigirCompras();
        Pedido pedido = pedidoObligatorio(pedidoId);
        exigirEtapaAbierta(pedido);
        KudeQr kude = KudeQr.leer(qr);
        if (!kude.isOk()) {
            throw new GraphQLException(kude.getMensaje());
        }
        if (kude.getTipoDocumento() != 1 && kude.getTipoDocumento() != 4) {
            throw new GraphQLException("Ese documento es una " + KudeQr.descripcionTipo(kude.getTipoDocumento()).toLowerCase()
                    + ", no una factura.");
        }
        // Antes de ir a SIFEN: el CDC ya dice el RUC del emisor
        String rucPedido = rucDe(pedido.getProveedor());
        if (rucPedido != null && !mismoRuc(rucPedido, kude.getRucEmisor())) {
            throw new GraphQLException("Esta factura es del RUC " + kude.getRucEmisor() + "-" + kude.getDvEmisor()
                    + " y el pedido es de " + nombreDe(pedido.getProveedor()) + " (" + rucPedido + ").");
        }
        DocumentoRecibido doc = documentoRecibidoService.obtener(kude.getCdc());
        if (doc.getNotaRecepcion() != null) {
            NotaRecepcion nota = doc.getNotaRecepcion();
            throw new GraphQLException("Esta factura ya está cargada: nota " + nota.getNumero() + " del pedido "
                    + (nota.getPedido() != null ? nota.getPedido().getId() : "?") + ".");
        }
        Set<String> nuestros = rucsPropios();
        if (!nuestros.isEmpty() && doc.getRucReceptor() != null
                && nuestros.stream().noneMatch(r -> mismoRuc(r, doc.getRucReceptor()))) {
            throw new GraphQLException("La factura está a nombre de " + texto(doc.getRazonSocialReceptor())
                    + " (RUC " + doc.getRucReceptor() + "), no de la empresa.");
        }
        doc.setPedido(pedido);
        doc.setProveedor(pedido.getProveedor());
        if (doc.getUsuario() == null) doc.setUsuario(seguridad.usuarioActual());
        documentoRepository.save(doc);
        return armarFactura(doc, pedido, true);
    }

    @Transactional
    public FacturaProveedorMobile factura(Long documentoId, Long pedidoId) {
        seguridad.exigirCompras();
        DocumentoRecibido doc = documentoObligatorio(documentoId);
        Pedido pedido = pedidoObligatorio(pedidoId != null ? pedidoId
                : doc.getPedido() != null ? doc.getPedido().getId() : null);
        return armarFactura(doc, pedido, doc.getNotaRecepcion() == null);
    }

    /**
     * Vincula a mano un renglón con una presentación nuestra. Con {@code recordar}, las próximas facturas del
     * proveedor reconocen ese código solas.
     */
    @Transactional
    public FacturaProveedorMobile vincularRenglon(Long renglonId, Long presentacionId, Double factor, Boolean recordar) {
        seguridad.exigirCompras();
        DocumentoRecibidoItem renglon = renglonObligatorio(renglonId);
        DocumentoRecibido doc = renglon.getDocumentoRecibido();
        exigirSinNota(doc);
        Presentacion presentacion = presentacionId != null ? presentacionService.findById(presentacionId).orElse(null) : null;
        if (presentacion == null || presentacion.getProducto() == null) {
            throw new GraphQLException("Elegí la presentación del producto.");
        }
        double f = factor != null ? factor : presentacion.getCantidad() != null ? presentacion.getCantidad() : 1.0;
        if (!(f > 0)) {
            throw new GraphQLException("Las unidades por cada unidad de la factura tienen que ser más que cero.");
        }
        renglon.setProducto(presentacion.getProducto());
        renglon.setPresentacion(presentacion);
        renglon.setFactor(f);
        renglon.setTipoMatch(TipoMatch.MANUAL.name());
        renglonRepository.save(renglon);
        if (Boolean.TRUE.equals(recordar) && doc.getProveedor() != null && !vacio(renglon.getCodigoProveedor())) {
            aprender(doc.getProveedor(), renglon, presentacion, f);
        }
        return armarSegunModo(doc);
    }

    /** La observación que justifica una diferencia o un producto fuera del pedido. */
    @Transactional
    public FacturaProveedorMobile observarRenglon(Long renglonId, String observacion) {
        seguridad.exigirCompras();
        DocumentoRecibidoItem renglon = renglonObligatorio(renglonId);
        exigirSinNota(renglon.getDocumentoRecibido());
        renglon.setObservacion(vacio(observacion) ? null : observacion.trim());
        renglonRepository.save(renglon);
        return armarSegunModo(renglon.getDocumentoRecibido());
    }

    /**
     * Crea la nota de recepción con sus ítems y distribuciones, en una sola transacción. Es lo que en el desktop son
     * «Crear nota» + «Editar ítem» por cada renglón + «Distribuir».
     */
    @Transactional
    public CompraNotaMobile confirmarFactura(Long documentoId, Long pedidoId) {
        seguridad.exigirCompras();
        return cargarNota(documentoObligatorio(documentoId), pedidoObligatorio(pedidoId));
    }

    private CompraNotaMobile cargarNota(DocumentoRecibido doc, Pedido pedido) {
        exigirSinNota(doc);
        exigirEtapaAbierta(pedido);
        FacturaProveedorMobile factura = armarFactura(doc, pedido, true);
        if (!Boolean.TRUE.equals(factura.getPuedeConfirmar())) {
            throw new GraphQLException(factura.getMotivoNoConfirma());
        }
        Moneda moneda = monedaDe(doc.getMoneda());
        Usuario usuario = seguridad.usuarioActual();

        NotaRecepcion nota = new NotaRecepcion();
        nota.setPedido(pedido);
        nota.setNumero(numeroNota(doc));
        nota.setTimbrado(doc.getTimbrado());
        nota.setTipoBoleta("FACTURA");
        nota.setFecha(doc.getFechaEmision() != null ? doc.getFechaEmision() : LocalDateTime.now());
        nota.setMoneda(moneda);
        nota.setCotizacion(cotizacion(moneda, doc, pedido));
        nota.setEstado(NotaRecepcionEstado.PENDIENTE_CONCILIACION);
        nota.setPagado(false);
        nota.setEsNotaRechazo(false);
        nota.setCreadoEn(LocalDateTime.now());
        nota.setUsuario(usuario);
        // save() pasa la etapa a EN_PROCESO si es la primera nota del pedido
        nota = notaRecepcionService.save(nota);

        Map<Long, RenglonFacturaMobile> porId = factura.getRenglones().stream()
                .collect(Collectors.toMap(RenglonFacturaMobile::getId, r -> r));
        List<PedidoItem> itemsPedido = pedidoItemService.findByPedidoId(pedido.getId());
        for (DocumentoRecibidoItem renglon : documentoRecibidoService.renglones(doc.getId())) {
            RenglonFacturaMobile cruce = porId.get(renglon.getId());
            NotaRecepcionItem item = new NotaRecepcionItem();
            item.setNotaRecepcion(nota);
            item.setPedidoItem(renglon.getPedidoItem());
            item.setProducto(renglon.getProducto());
            item.setPresentacionEnNota(renglon.getPresentacion());
            item.setCantidadEnNota(cruce.getCantidadBase());
            item.setPrecioUnitarioEnNota(cruce.getPrecioUnitarioBase());
            item.setEsBonificacion(Boolean.TRUE.equals(renglon.getEsBonificacion()));
            item.setVencimientoEnNota(renglon.getVencimiento());
            item.setObservacion(observacionDeNota(renglon, cruce));
            item.setEstado(NotaRecepcionItemEstado.PENDIENTE_CONCILIACION);
            item.setCreadoEn(LocalDateTime.now());
            item.setUsuario(usuario);
            item = notaRecepcionItemService.save(item);
            distribuir(item, fuenteDeDistribucion(renglon, itemsPedido), pedido, usuario);
            renglon.setNotaRecepcionItem(item);
            renglonRepository.save(renglon);
            if (renglon.getProducto() != null && !vacio(renglon.getCodigoProveedor()) && doc.getProveedor() != null
                    && renglon.getPresentacion() != null) {
                aprender(doc.getProveedor(), renglon, renglon.getPresentacion(),
                        renglon.getFactor() != null ? renglon.getFactor() : 1.0);
            }
        }
        // Concilia los ítems con la distribución completa, la nota si quedaron todos, y recalcula el valor
        notaRecepcionService.actualizarEstadosDespuesDeDistribucion(nota.getId());
        notaRecepcionService.actualizarValorNota(nota.getId());
        vincularProductosConProveedor(nota, usuario);

        doc.setNotaRecepcion(nota);
        doc.setPedido(pedido);
        documentoRepository.save(doc);
        notaRecepcionItemService.getRepository().flush();
        NotaRecepcion guardada = notaRecepcionService.findById(nota.getId()).orElse(nota);
        return armarNota(guardada, Collections.singletonMap(guardada.getId(), doc), etapaAbierta(pedido));
    }

    /** Borra una nota que todavía no entró a recepción, como «Eliminar» del desktop. */
    @Transactional
    public CompraPedidoMobile eliminarNota(Long notaId) {
        seguridad.exigirCompras();
        NotaRecepcion nota = notaRecepcionService.findById(notaId)
                .orElseThrow(() -> new GraphQLException("La nota ya no existe. Recargá el pedido."));
        Pedido pedido = nota.getPedido();
        if (pedido == null) {
            throw new GraphQLException("La nota no tiene pedido.");
        }
        exigirEtapaAbierta(pedido);
        if (!eliminable(nota)) {
            throw new GraphQLException("La nota está " + etiquetaNota(nota.getEstado()).toLowerCase()
                    + ": ya no se puede eliminar desde acá.");
        }
        Long pedidoId = pedido.getId();
        if (!Boolean.TRUE.equals(notaRecepcionService.deleteById(notaId))) {
            throw new GraphQLException("No se pudo eliminar la nota " + nota.getNumero() + ".");
        }
        // Los totales del pedido se leen por JDBC: el borrado tiene que estar en la base, no solo en la sesión
        notaRecepcionItemService.getRepository().flush();
        return armarPedido(pedidoObligatorio(pedidoId));
    }

    @Transactional(readOnly = true)
    public ResumenFinalizarNotasMobile resumenFinalizar(Long pedidoId) {
        seguridad.exigirCompras();
        Pedido pedido = pedidoObligatorio(pedidoId);
        CompraPedidoMobile p = armarPedido(pedido);
        ResumenFinalizarNotasMobile r = new ResumenFinalizarNotasMobile();
        r.setPedidoId(pedidoId);
        r.setPuedeFinalizar(p.getPuedeFinalizar());
        r.setCantidadNotas(p.getNotas().size());
        r.setTotalFacturado(p.getTotalFacturado());
        r.setItemsSinFacturar(p.getItems().stream()
                .filter(i -> i.getCantidadPendiente() != null && i.getCantidadPendiente() > CruceFactura.TOLERANCIA_CANTIDAD)
                .collect(Collectors.toList()));
        r.setNotasPendientes((int) p.getNotas().stream()
                .filter(n -> NotaRecepcionEstado.PENDIENTE_CONCILIACION.name().equals(n.getEstado())).count());
        if (!Boolean.TRUE.equals(p.getPuedeFinalizar())) {
            r.setMotivo(p.getNotas().isEmpty()
                    ? "Cargá al menos una factura antes de finalizar."
                    : p.getMotivoNoOpera() != null ? p.getMotivoNoOpera() : "La etapa de notas no está en proceso.");
        }
        if (!r.getItemsSinFacturar().isEmpty()) {
            r.getAvisos().add(r.getItemsSinFacturar().size() == 1
                    ? "Queda 1 ítem del pedido sin facturar: no va a entrar en la recepción de mercadería."
                    : "Quedan " + r.getItemsSinFacturar().size()
                    + " ítems del pedido sin facturar: no van a entrar en la recepción de mercadería.");
        }
        int sinDistribuir = p.getNotas().stream().mapToInt(n -> n.getItemsSinDistribuir() != null ? n.getItemsSinDistribuir() : 0).sum();
        if (sinDistribuir > 0) {
            r.getAvisos().add(sinDistribuir + " ítem(s) de las notas no tienen la distribución por sucursal completa:"
                    + " no van a llegar a ninguna sucursal. Distribuilos en el desktop antes de finalizar.");
        }
        return r;
    }

    /**
     * Finaliza la conciliación (como el desktop) y además crea los vínculos producto-proveedor de todas las notas,
     * que el finalizar del desktop se saltea cuando fuerza las notas a CONCILIADA.
     */
    @Transactional
    public CompraPedidoMobile finalizar(Long pedidoId) {
        seguridad.exigirCompras();
        Pedido pedido = pedidoObligatorio(pedidoId);
        try {
            pedidoService.finalizarRecepcionNotas(pedidoId);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new GraphQLException(e.getMessage());
        }
        Usuario usuario = seguridad.usuarioActual();
        for (NotaRecepcion nota : notaRecepcionService.findByPedidoId(pedidoId)) {
            vincularProductosConProveedor(nota, usuario);
        }
        return armarPedido(pedidoObligatorio(pedido.getId()));
    }

    // =================================================================================== Nueva compra

    /**
     * La factura <b>crea</b> la compra: desde la etapa de creación hasta la recepción documental, sin cargar el
     * pedido a mano. Trae de SIFEN la factura del proveedor (el CDC dice de quién es) y la deja lista para elegir lo
     * que la factura no trae (forma de pago si no viene, sucursal) y vincular los productos que no se reconocen.
     */
    @Transactional
    public FacturaProveedorMobile leerFacturaNuevaCompra(String qr) {
        seguridad.exigirCompras();
        KudeQr kude = KudeQr.leer(qr);
        if (!kude.isOk()) {
            throw new GraphQLException(kude.getMensaje());
        }
        if (kude.getTipoDocumento() != 1 && kude.getTipoDocumento() != 4) {
            throw new GraphQLException("Ese documento es una " + KudeQr.descripcionTipo(kude.getTipoDocumento()).toLowerCase()
                    + ", no una factura.");
        }
        Proveedor proveedor = proveedorPorRuc(kude.getRucEmisor());
        if (proveedor == null) {
            throw new GraphQLException("El RUC " + kude.getRucEmisor() + "-" + kude.getDvEmisor()
                    + " de la factura no es de ningún proveedor cargado. Dalo de alta como proveedor en el desktop.");
        }
        DocumentoRecibido doc = documentoRecibidoService.obtener(kude.getCdc());
        if (doc.getNotaRecepcion() != null) {
            NotaRecepcion nota = doc.getNotaRecepcion();
            throw new GraphQLException("Esta factura ya está cargada: nota " + nota.getNumero() + " del pedido "
                    + (nota.getPedido() != null ? nota.getPedido().getId() : "?") + ".");
        }
        Set<String> nuestros = rucsPropios();
        if (!nuestros.isEmpty() && doc.getRucReceptor() != null
                && nuestros.stream().noneMatch(r -> mismoRuc(r, doc.getRucReceptor()))) {
            throw new GraphQLException("La factura está a nombre de " + texto(doc.getRazonSocialReceptor())
                    + " (RUC " + doc.getRucReceptor() + "), no de la empresa.");
        }
        doc.setPedido(null);
        doc.setProveedor(proveedor);
        if (doc.getUsuario() == null) doc.setUsuario(seguridad.usuarioActual());
        documentoRepository.save(doc);
        return armarBorrador(doc, true);
    }

    @Transactional
    public FacturaProveedorMobile facturaNuevaCompra(Long documentoId) {
        seguridad.exigirCompras();
        DocumentoRecibido doc = documentoObligatorio(documentoId);
        return armarBorrador(doc, doc.getNotaRecepcion() == null);
    }

    @Transactional(readOnly = true)
    public OpcionesNuevaCompraMobile opcionesNuevaCompra() {
        seguridad.exigirCompras();
        OpcionesNuevaCompraMobile o = new OpcionesNuevaCompraMobile();
        jdbc.query("SELECT id, descripcion FROM financiero.forma_pago WHERE COALESCE(activo, true) = true ORDER BY id",
                rs -> {
                    o.getFormasPago().add(new OpcionMobile(rs.getLong(1), rs.getString(2)));
                });
        for (Sucursal s : sucursalService.findAllSucursalesEntrega()) {
            if (s.getId() != null && s.getId() != 0L) o.getSucursales().add(new OpcionMobile(s.getId(), s.getNombre()));
        }
        return o;
    }

    /**
     * Crea el pedido con lo que dice la factura y lo lleva hasta la recepción documental, en una sola transacción:
     * los mismos pasos que el desktop (crear el pedido con sus sucursales, cargar cada ítem con su reparto,
     * «Finalizar planificación», cargar la nota y, si se pide, «Finalizar conciliación»), con las mismas operaciones.
     *
     * Lo que la factura trae se toma de la factura (proveedor, moneda, cotización, ítems, precios con IVA, forma de
     * pago y plazo si vienen). Lo que no trae lo elige el comprador: la forma de pago si la factura no la dice, y la
     * sucursal, que recibe todo.
     */
    @Transactional
    public CompraPedidoMobile crearCompraDesdeFactura(Long documentoId, Long formaPagoId, Integer plazoCredito,
                                                       Long sucursalId, Boolean finalizarNotas) {
        seguridad.exigirCompras();
        DocumentoRecibido doc = documentoObligatorio(documentoId);
        exigirSinNota(doc);
        FacturaProveedorMobile factura = armarBorrador(doc, true);
        if (!Boolean.TRUE.equals(factura.getPuedeConfirmar())) {
            throw new GraphQLException(factura.getMotivoNoConfirma());
        }
        Long formaPago = formaPagoId != null ? formaPagoId : factura.getFormaPagoId();
        if (formaPago == null || !formaPagoService.findById(formaPago).isPresent()) {
            throw new GraphQLException("Elegí la forma de pago: la factura no la trae.");
        }
        if (plazoCredito != null && plazoCredito < 0) {
            throw new GraphQLException("El plazo de crédito no puede ser negativo.");
        }
        if (sucursalId == null || sucursalService.findAllSucursalesEntrega().stream().noneMatch(s -> sucursalId.equals(s.getId()))) {
            throw new GraphQLException("Elegí la sucursal que recibe la mercadería.");
        }
        Moneda moneda = monedaDe(doc.getMoneda());
        Usuario usuario = seguridad.usuarioActual();
        Long usuarioId = usuario != null ? usuario.getId() : null;

        // 1. El pedido, con sus sucursales y sus etapas: como «Guardar» de la creación en el desktop
        PedidoInput input = new PedidoInput();
        input.setProveedorId(doc.getProveedor().getId());
        input.setFormaPagoId(formaPago);
        input.setMonedaId(moneda.getId());
        input.setPlazoCredito(plazoCredito != null ? plazoCredito : factura.getPlazoCredito());
        input.setUsuarioId(usuarioId);
        List<Integer> sucursales = Collections.singletonList(sucursalId.intValue());
        Pedido pedido = pedidoGraphQL.savePedidoFull(input, null, sucursales, sucursales,
                usuarioId != null ? usuarioId.intValue() : null);
        pedido.setFormaPago(formaPagoService.findById(formaPago).orElse(null));
        pedido.setCotizacion(cotizacionOrNull(moneda, doc, pedido));
        pedido = pedidoService.save(pedido);

        // 2. Un ítem por renglón, con todo para la sucursal elegida: como «Agregar ítem» con una sola sucursal
        Map<Long, RenglonFacturaMobile> porId = factura.getRenglones().stream()
                .collect(Collectors.toMap(RenglonFacturaMobile::getId, r -> r));
        for (DocumentoRecibidoItem renglon : documentoRecibidoService.renglones(doc.getId())) {
            RenglonFacturaMobile cruce = porId.get(renglon.getId());
            PedidoItemInput item = new PedidoItemInput();
            item.setPedidoId(pedido.getId());
            item.setProductoId(renglon.getProducto().getId());
            item.setPresentacionCreacionId(renglon.getPresentacion() != null ? renglon.getPresentacion().getId() : null);
            item.setCantidadSolicitada(cruce.getCantidadBase());
            item.setPrecioUnitarioSolicitado(cruce.getPrecioUnitarioBase());
            item.setEsBonificacion(Boolean.TRUE.equals(cruce.getEsBonificacion()));
            item.setEstado(PedidoItemEstado.ACTIVO);
            item.setVencimientoEsperado(renglon.getVencimiento() != null ? renglon.getVencimiento().atStartOfDay().format(FECHA_HORA_SEG) : null);
            item.setCreadoEn(LocalDateTime.now().format(FECHA_HORA_SEG));
            item.setUsuarioCreacionId(usuarioId);
            PedidoItemDistribucionInput reparto = new PedidoItemDistribucionInput();
            reparto.setSucursalInfluenciaId(sucursalId);
            reparto.setSucursalEntregaId(sucursalId);
            reparto.setCantidadAsignada(cruce.getCantidadBase());
            guardadoPedidoItemGraphQL.guardarPedidoItemConDistribuciones(item, new ArrayList<>(Collections.singletonList(reparto)));
        }

        // 3. «Finalizar planificación»: la creación termina y la recepción documental queda esperando la nota
        try {
            pedidoService.finalizarCreacion(pedido.getId());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new GraphQLException(e.getMessage());
        }
        pedidoItemService.getRepository().flush();

        // 4. La nota, con la misma lógica que cargar la factura a un pedido existente
        cargarNota(doc, pedido);

        // 5. «Finalizar conciliación», si el comprador no espera más facturas para esta compra
        if (Boolean.TRUE.equals(finalizarNotas)) {
            try {
                pedidoService.finalizarRecepcionNotas(pedido.getId());
            } catch (IllegalStateException | IllegalArgumentException e) {
                throw new GraphQLException(e.getMessage());
            }
            for (NotaRecepcion nota : notaRecepcionService.findByPedidoId(pedido.getId())) {
                vincularProductosConProveedor(nota, usuario);
            }
        }
        notaRecepcionItemService.getRepository().flush();
        return armarPedido(pedidoObligatorio(pedido.getId()));
    }

    private FacturaProveedorMobile armarSegunModo(DocumentoRecibido doc) {
        return doc.getPedido() == null ? armarBorrador(doc, true) : armarFactura(doc, doc.getPedido(), true);
    }

    /** La factura que va a crear la compra: productos reconocidos, avisos de IVA y lo que trae de forma de pago. */
    private FacturaProveedorMobile armarBorrador(DocumentoRecibido doc, boolean recalcular) {
        List<DocumentoRecibidoItem> renglones = documentoRecibidoService.renglones(doc.getId());
        Proveedor proveedor = doc.getProveedor();
        if (recalcular) {
            for (DocumentoRecibidoItem r : renglones) {
                if (!TipoMatch.MANUAL.name().equals(r.getTipoMatch())) {
                    resolverProducto(r, proveedor, Collections.emptySet());
                }
            }
        }
        List<CruceFactura.Renglon> entrada = new ArrayList<>();
        for (DocumentoRecibidoItem r : renglones) entrada.add(entradaDe(r));
        List<CruceFactura.Resultado> resultados = CruceFactura.sinPedido(entrada);
        Moneda moneda = monedaDeOrNull(doc.getMoneda());
        boolean enGuaranies = moneda != null && GUARANI.equals(moneda.getDenominacion());

        FacturaProveedorMobile f = cabecera(doc, null);
        f.setModo("NUEVA_COMPRA");
        FacturaSifenXml.Cabecera leida = leerXml(doc);
        if (leida != null) {
            f.setTipoPagoFactura(leida.tipoPagoDescripcion);
            f.setPlazoCredito(leida.plazoCredito);
            Long formaPago = formaPagoDeTipo(leida.tipoPago);
            if (formaPago != null) {
                f.setFormaPagoId(formaPago);
                f.setFormaPagoDescripcion(formaPagoService.findById(formaPago).map(fp -> fp.getDescripcion()).orElse(null));
            }
        }
        int ok = 0, sinProd = 0, ivaProducto = 0, ivaUltima = 0;
        double totalNota = 0, totalRenglones = 0;
        for (int k = 0; k < renglones.size(); k++) {
            DocumentoRecibidoItem r = renglones.get(k);
            CruceFactura.Resultado res = resultados.get(k);
            if (recalcular) {
                r.setPedidoItem(null);
                r.setEsBonificacion(res.bonificacion);
                renglonRepository.save(r);
            }
            if (res.estado == CruceFactura.Estado.OK && !res.bonificacion && enGuaranies && r.getProducto() != null) {
                String aviso = CruceFactura.avisoUltimaCompra(res.precioBase, ultimoPrecioCompra(r.getProducto().getId()), r.getTasaIva());
                if (aviso != null) {
                    res.avisosIva.add(aviso.startsWith("IVA: ") ? aviso.substring(5) : aviso);
                    if (aviso.startsWith("IVA: ")) ivaUltima++;
                }
            }
            RenglonFacturaMobile rm = armarRenglon(r, res);
            if (res.estado == CruceFactura.Estado.SIN_PRODUCTO) {
                rm.setSugerencias(sugerencias(r, proveedor));
                sinProd++;
            } else {
                ok++;
            }
            if (res.avisosIva.stream().anyMatch(a -> a.startsWith("En el sistema"))) ivaProducto++;
            f.getRenglones().add(rm);
            if (!res.bonificacion) totalNota += res.cantidadBase * res.precioBase;
            totalRenglones += nz(r.getTotal());
        }
        f.setTotalNota(CruceFactura.redondear(totalNota, 2));
        f.setRenglonesOk(ok);
        f.setRenglonesConDiferencia(0);
        f.setRenglonesFueraDePedido(0);
        f.setRenglonesSinProducto(sinProd);
        f.setRenglonesSinObservacion(0);

        List<AvisoFacturaMobile> a = f.getAvisos();
        if (documentoRecibidoService.cancelada(doc)) {
            a.add(new AvisoFacturaMobile("CANCELADA", "BLOQUEO", "SIFEN informa que esta factura fue cancelada por el proveedor."));
        }
        if (moneda == null) {
            a.add(new AvisoFacturaMobile("MONEDA", "BLOQUEO", "La factura está en " + texto(doc.getMoneda())
                    + ", una moneda que el sistema no tiene."));
        } else if (!enGuaranies) {
            Double cot = doc.getCotizacion() != null && doc.getCotizacion() > 1 ? doc.getCotizacion() : null;
            if (cot == null) {
                Cambio cambio = cambioService.findLastByMonedaId(moneda.getId());
                cot = cambio != null && cambio.getValorEnGsCompraMercado() != null ? cambio.getValorEnGsCompraMercado()
                        : cambio != null ? cambio.getValorEnGs() : null;
            }
            a.add(cot == null || cot <= 1
                    ? new AvisoFacturaMobile("MONEDA", "BLOQUEO", "No hay cotización de " + moneda.getDenominacion() + ". Cargá la del día.")
                    : new AvisoFacturaMobile("MONEDA", "INFO", "Cotización " + CruceFactura.importe(cot)
                        + (doc.getCotizacion() != null && doc.getCotizacion() > 1 ? " (la de la factura)." : " (la última cargada en el sistema).")));
        }
        a.add(new AvisoFacturaMobile("IVA", "INFO", textoIva(doc)));
        if (ivaUltima > 0) {
            a.add(new AvisoFacturaMobile("IVA", "ADVERTENCIA", ivaUltima == 1
                    ? "1 producto: la última compra se cargó con otro criterio de IVA. Mirá el aviso en el renglón."
                    : ivaUltima + " productos: la última compra se cargó con otro criterio de IVA. Mirá el aviso en cada renglón."));
        }
        if (ivaProducto > 0) {
            a.add(new AvisoFacturaMobile("IVA", "ADVERTENCIA", ivaProducto == 1
                    ? "1 producto tiene en el sistema un IVA distinto al de la factura. Revisá su ficha."
                    : ivaProducto + " productos tienen en el sistema un IVA distinto al de la factura. Revisá sus fichas."));
        }
        if (sinProd > 0) {
            a.add(new AvisoFacturaMobile("PRODUCTOS", "ADVERTENCIA", (sinProd == 1
                    ? "1 renglón no se reconoció" : sinProd + " renglones no se reconocieron")
                    + ": el producto no está en el sistema con ese código. Vinculalo con uno parecido o dalo de alta."));
        }
        if (doc.getTotalGeneral() != null && Math.abs(totalRenglones - doc.getTotalGeneral()) > TOLERANCIA_TOTAL) {
            a.add(new AvisoFacturaMobile("TOTAL", "ADVERTENCIA", "La suma de los renglones (" + CruceFactura.importe(totalRenglones)
                    + ") no da el total de la factura (" + CruceFactura.importe(doc.getTotalGeneral()) + "): puede haber un descuento global."));
        }
        if (f.getFormaPagoId() == null) {
            a.add(new AvisoFacturaMobile("PAGO", "INFO", "La factura no dice la forma de pago: elegila antes de crear la compra."));
        }
        boolean bloqueada = a.stream().anyMatch(x -> "BLOQUEO".equals(x.getNivel()));
        f.setPuedeConfirmar(doc.getNotaRecepcion() == null && !bloqueada && sinProd == 0 && !renglones.isEmpty());
        if (!f.getPuedeConfirmar()) {
            f.setMotivoNoConfirma(doc.getNotaRecepcion() != null ? "La factura ya está cargada."
                    : bloqueada ? a.stream().filter(x -> "BLOQUEO".equals(x.getNivel())).findFirst().get().getMensaje()
                    : renglones.isEmpty() ? "La factura no trae renglones."
                    : sinProd == 1 ? "Falta vincular 1 producto." : "Faltan vincular " + sinProd + " productos.");
        }
        return f;
    }

    private CruceFactura.Renglon entradaDe(DocumentoRecibidoItem r) {
        CruceFactura.Renglon c = new CruceFactura.Renglon();
        c.id = r.getId();
        c.productoId = r.getProducto() != null ? r.getProducto().getId() : null;
        c.presentacionId = r.getPresentacion() != null ? r.getPresentacion().getId() : null;
        c.presentacionDescripcion = r.getPresentacion() != null ? r.getPresentacion().getDescripcion() : null;
        c.factor = r.getFactor();
        c.cantidad = nz(r.getCantidad());
        c.precioUnitario = nz(r.getPrecioUnitario());
        c.descuentoUnitario = nz(r.getDescuentoUnitario());
        c.porcentajeDescuento = r.getPorcentajeDescuento();
        c.tasaIva = r.getTasaIva();
        c.ivaProducto = r.getProducto() != null ? r.getProducto().getIva() : null;
        c.observacion = r.getObservacion();
        return c;
    }

    private static FacturaSifenXml.Cabecera leerXml(DocumentoRecibido doc) {
        try {
            return doc.getXml() != null ? FacturaSifenXml.leer(doc.getXml()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * El tipo de pago de SIFEN (iTiPago) en nuestras formas de pago. Solo los que tienen equivalente claro; el
     * resto (giro, billetera, retención…) lo elige el comprador.
     */
    private Long formaPagoDeTipo(Integer tipoPago) {
        if (tipoPago == null) return null;
        String descripcion;
        switch (tipoPago) {
            case 1: descripcion = "EFECTIVO"; break;
            case 2: descripcion = "CHEQUE"; break;
            case 3: case 4: case 8: descripcion = "TARJETA"; break;
            case 5: case 16: case 21: descripcion = "TRANSFERENCIA"; break;
            default: return null;
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM financiero.forma_pago WHERE UPPER(descripcion) = ? "
                + "AND COALESCE(activo, true) = true ORDER BY id LIMIT 1", Long.class, descripcion);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** El último precio de compra del producto (por unidad base, con lo que se cargó en la nota). */
    private Double ultimoPrecioCompra(Long productoId) {
        List<Double> precios = jdbc.queryForList("SELECT nri.precio_unitario_en_nota FROM operaciones.nota_recepcion_item nri "
                + "JOIN operaciones.nota_recepcion nr ON nr.id = nri.nota_recepcion_id "
                + "JOIN financiero.moneda m ON m.id = nr.moneda_id "
                + "WHERE nri.producto_id = ? AND COALESCE(nri.es_bonificacion, false) = false "
                + "AND COALESCE(nri.estado::text, '') <> 'RECHAZADO' AND nri.precio_unitario_en_nota > 0 "
                + "AND m.denominacion = 'GUARANI' ORDER BY nri.id DESC LIMIT 1", Double.class, productoId);
        return precios.isEmpty() ? null : precios.get(0);
    }

    private Proveedor proveedorPorRuc(String ruc) {
        List<Map<String, Object>> proveedores = jdbc.queryForList(
                "SELECT pr.id, per.documento FROM personas.proveedor pr "
                        + "JOIN personas.persona per ON per.id = pr.persona_id WHERE per.documento IS NOT NULL ORDER BY pr.id");
        for (Map<String, Object> p : proveedores) {
            if (mismoRuc((String) p.get("documento"), ruc)) {
                return proveedorService.findById(((Number) p.get("id")).longValue()).orElse(null);
            }
        }
        return null;
    }

    private static String textoIva(DocumentoRecibido doc) {
        StringBuilder iva = new StringBuilder("Los precios de la factura incluyen IVA");
        List<String> partes = new ArrayList<>();
        if (nz(doc.getTotalIva10()) > 0) partes.add("IVA 10 %: " + CruceFactura.importe(nz(doc.getTotalIva10())));
        if (nz(doc.getTotalIva5()) > 0) partes.add("IVA 5 %: " + CruceFactura.importe(nz(doc.getTotalIva5())));
        if (nz(doc.getSubtotalExenta()) > 0) partes.add("exentas: " + CruceFactura.importe(nz(doc.getSubtotalExenta())));
        if (!partes.isEmpty()) iva.append(" (").append(String.join(" · ", partes)).append(")");
        iva.append(". La compra se carga con IVA incluido, igual que en el desktop.");
        return iva.toString();
    }

    // =================================================================================== Armado

    private CompraPedidoMobile armarPedido(Pedido pedido) {
        CompraPedidoMobile p = new CompraPedidoMobile();
        p.setPedidoId(pedido.getId());
        if (pedido.getProveedor() != null) {
            p.setProveedorId(pedido.getProveedor().getId());
            p.setProveedorNombre(nombreDe(pedido.getProveedor()));
            p.setProveedorRuc(rucDe(pedido.getProveedor()));
        }
        p.setFecha(pedido.getCreadoEn() != null ? pedido.getCreadoEn().format(FECHA_HORA) : null);
        if (pedido.getMoneda() != null) {
            p.setMonedaDenominacion(pedido.getMoneda().getDenominacion());
            p.setMonedaSimbolo(pedido.getMoneda().getSimbolo());
        }
        p.setCotizacion(pedido.getCotizacion());
        ProcesoEtapa etapa = procesoEtapaService.getEtapaByPedidoAndTipo(pedido.getId(), ProcesoEtapaTipo.RECEPCION_NOTA).orElse(null);
        p.setEstadoEtapa(etapa != null ? etapa.getEstadoEtapa().name() : null);
        boolean abierta = etapaAbierta(etapa);
        p.setPuedeCargarFacturas(abierta);
        if (!abierta) {
            p.setMotivoNoOpera(etapa == null
                    ? "El pedido todavía no llegó a la etapa de notas: termine la planificación en el desktop."
                    : "La etapa de notas está " + etapa.getEstadoEtapa().name().toLowerCase().replace('_', ' ') + ".");
        }
        Map<Long, Double> facturado = facturadoPorItem(pedido.getId());
        double total = 0;
        int pendientes = 0;
        for (PedidoItem pi : pedidoItemService.findByPedidoId(pedido.getId())) {
            CompraPedidoItemMobile i = armarItem(pi, facturado.getOrDefault(pi.getId(), 0.0));
            p.getItems().add(i);
            if (!Boolean.TRUE.equals(pi.getEsBonificacion())) {
                total += nz(pi.getCantidadSolicitada()) * nz(pi.getPrecioUnitarioSolicitado());
            }
            if (i.getCantidadPendiente() > CruceFactura.TOLERANCIA_CANTIDAD) pendientes++;
        }
        // Lo pendiente primero: es lo que el comprador busca
        p.getItems().sort(Comparator.comparing((CompraPedidoItemMobile i) -> i.getCantidadPendiente() <= CruceFactura.TOLERANCIA_CANTIDAD)
                .thenComparing(i -> texto(i.getDescripcion())));
        p.setTotalPedido(total);
        p.setItemsPendientes(pendientes);
        List<NotaRecepcion> notas = notaRecepcionService.findByPedidoId(pedido.getId());
        notas.sort(Comparator.comparing(NotaRecepcion::getId).reversed());
        Map<Long, DocumentoRecibido> docs = documentosDe(notas);
        double facturadoTotal = 0;
        for (NotaRecepcion n : notas) {
            CompraNotaMobile nm = armarNota(n, docs, abierta);
            p.getNotas().add(nm);
            if (!Boolean.TRUE.equals(n.getEsNotaRechazo())) facturadoTotal += nz(nm.getValor());
        }
        p.setTotalFacturado(facturadoTotal);
        p.setPuedeFinalizar(etapa != null && etapa.getEstadoEtapa() == ProcesoEtapaEstado.EN_PROCESO && !notas.isEmpty());
        return p;
    }

    private CompraPedidoItemMobile armarItem(PedidoItem pi, double facturada) {
        CompraPedidoItemMobile i = new CompraPedidoItemMobile();
        i.setPedidoItemId(pi.getId());
        Producto producto = pi.getProducto();
        if (producto != null) {
            i.setProductoId(producto.getId());
            i.setDescripcion(producto.getDescripcion());
        }
        if (pi.getPresentacionCreacion() != null) {
            i.setPresentacionDescripcion(pi.getPresentacionCreacion().getDescripcion());
            i.setPresentacionCantidad(pi.getPresentacionCreacion().getCantidad());
        }
        double solicitada = nz(pi.getCantidadSolicitada());
        double pendiente = Math.max(0, solicitada - facturada);
        i.setCantidadSolicitada(solicitada);
        i.setCantidadFacturada(facturada);
        i.setCantidadPendiente(pendiente);
        i.setPrecioUnitario(pi.getPrecioUnitarioSolicitado());
        i.setEsBonificacion(Boolean.TRUE.equals(pi.getEsBonificacion()));
        i.setPendienteTexto(pendiente <= CruceFactura.TOLERANCIA_CANTIDAD
                ? (facturada > solicitada + CruceFactura.TOLERANCIA_CANTIDAD
                    ? "Facturado de más: " + CruceFactura.cantidad(facturada) + " de " + CruceFactura.cantidad(solicitada) + " u."
                    : "Facturado completo")
                : facturada <= CruceFactura.TOLERANCIA_CANTIDAD
                    ? "Faltan " + CruceFactura.cantidad(solicitada) + " u."
                    : "Faltan " + CruceFactura.cantidad(pendiente) + " de " + CruceFactura.cantidad(solicitada) + " u.");
        return i;
    }

    private CompraNotaMobile armarNota(NotaRecepcion n, Map<Long, DocumentoRecibido> docs, boolean etapaAbierta) {
        CompraNotaMobile nm = new CompraNotaMobile();
        nm.setId(n.getId());
        nm.setNumero(n.getNumero());
        nm.setTimbrado(n.getTimbrado());
        DocumentoRecibido doc = docs.get(n.getId());
        nm.setElectronica(doc != null);
        if (doc != null) {
            nm.setCdc(doc.getCdc());
            nm.setNumeroCompleto(numeroCompleto(doc));
        } else {
            nm.setNumeroCompleto(n.getNumero() != null ? String.valueOf(n.getNumero()) : "s/n");
        }
        nm.setFecha(n.getFecha() != null ? n.getFecha().format(FECHA_HORA) : null);
        nm.setEstado(n.getEstado() != null ? n.getEstado().name() : null);
        nm.setEstadoEtiqueta(etiquetaNota(n.getEstado()));
        nm.setValor(n.getValor() != null ? n.getValor() : 0.0);
        nm.setMonedaSimbolo(n.getMoneda() != null ? n.getMoneda().getSimbolo() : null);
        Map<String, Object> conteo = jdbc.queryForMap(
                "SELECT COUNT(*) AS items, COUNT(*) FILTER (WHERE COALESCE(nri.estado::text, '') <> 'RECHAZADO' AND "
                        + "ABS(COALESCE((SELECT SUM(d.cantidad) FROM operaciones.nota_recepcion_item_distribucion d "
                        + "WHERE d.nota_recepcion_item_id = nri.id), 0) - nri.cantidad_en_nota) > 0.001) AS sin_distribuir "
                        + "FROM operaciones.nota_recepcion_item nri WHERE nri.nota_recepcion_id = ?", n.getId());
        nm.setCantidadItems(((Number) conteo.get("items")).intValue());
        nm.setItemsSinDistribuir(((Number) conteo.get("sin_distribuir")).intValue());
        nm.setPuedeEliminar(etapaAbierta && eliminable(n));
        return nm;
    }

    /**
     * Cruza la factura con el pedido y arma lo que ve el comprador. Con {@code recalcular}, el cruce se rehace y se
     * guarda (el pedido pudo cambiar desde la última vez); sin él, se muestra lo guardado (factura ya confirmada).
     */
    private FacturaProveedorMobile armarFactura(DocumentoRecibido doc, Pedido pedido, boolean recalcular) {
        List<DocumentoRecibidoItem> renglones = documentoRecibidoService.renglones(doc.getId());
        Proveedor proveedor = pedido.getProveedor();
        Moneda moneda = monedaDeOrNull(doc.getMoneda());
        boolean mismaMoneda = moneda == null || pedido.getMoneda() == null || Objects.equals(moneda.getId(), pedido.getMoneda().getId());
        List<PedidoItem> itemsPedido = pedidoItemService.findByPedidoId(pedido.getId());
        Map<Long, Double> facturado = facturadoPorItem(pedido.getId());
        Set<Long> productosDelPedido = itemsPedido.stream().filter(i -> i.getProducto() != null)
                .map(i -> i.getProducto().getId()).collect(Collectors.toSet());

        if (recalcular) {
            for (DocumentoRecibidoItem r : renglones) {
                if (!TipoMatch.MANUAL.name().equals(r.getTipoMatch())) {
                    resolverProducto(r, proveedor, productosDelPedido);
                }
            }
        }

        List<CruceFactura.Renglon> entrada = new ArrayList<>();
        for (DocumentoRecibidoItem r : renglones) {
            CruceFactura.Renglon c = new CruceFactura.Renglon();
            c.id = r.getId();
            c.productoId = r.getProducto() != null ? r.getProducto().getId() : null;
            c.presentacionId = r.getPresentacion() != null ? r.getPresentacion().getId() : null;
            c.presentacionDescripcion = r.getPresentacion() != null ? r.getPresentacion().getDescripcion() : null;
            c.factor = r.getFactor();
            c.cantidad = nz(r.getCantidad());
            c.precioUnitario = nz(r.getPrecioUnitario());
            c.descuentoUnitario = nz(r.getDescuentoUnitario());
            c.porcentajeDescuento = r.getPorcentajeDescuento();
            c.tasaIva = r.getTasaIva();
            c.ivaProducto = r.getProducto() != null ? r.getProducto().getIva() : null;
            c.observacion = r.getObservacion();
            entrada.add(c);
        }
        List<CruceFactura.ItemPedido> items = new ArrayList<>();
        for (PedidoItem pi : itemsPedido) {
            if (pi.getProducto() == null) continue;
            CruceFactura.ItemPedido i = new CruceFactura.ItemPedido();
            i.id = pi.getId();
            i.productoId = pi.getProducto().getId();
            i.presentacionId = pi.getPresentacionCreacion() != null ? pi.getPresentacionCreacion().getId() : null;
            i.presentacionDescripcion = pi.getPresentacionCreacion() != null ? pi.getPresentacionCreacion().getDescripcion() : null;
            i.cantidadPendiente = doc.getNotaRecepcion() == null
                    ? Math.max(0, nz(pi.getCantidadSolicitada()) - facturado.getOrDefault(pi.getId(), 0.0))
                    : nz(pi.getCantidadSolicitada());
            i.precioUnitario = pi.getPrecioUnitarioSolicitado();
            i.bonificacion = Boolean.TRUE.equals(pi.getEsBonificacion());
            items.add(i);
        }
        List<CruceFactura.Resultado> resultados = CruceFactura.cruzar(entrada, items, mismaMoneda);
        Map<Long, PedidoItem> itemPorId = itemsPedido.stream().collect(Collectors.toMap(PedidoItem::getId, i -> i));

        FacturaProveedorMobile f = cabecera(doc, pedido);
        Map<Long, Double> pendienteTrasFactura = new HashMap<>();
        for (CruceFactura.ItemPedido i : items) pendienteTrasFactura.put(i.id, i.cantidadPendiente);
        int ok = 0, dif = 0, fuera = 0, sinProd = 0, sinObs = 0, sinDistribucion = 0, ivaPedido = 0, ivaProducto = 0;
        double totalNota = 0, totalRenglones = 0;
        for (int k = 0; k < renglones.size(); k++) {
            DocumentoRecibidoItem r = renglones.get(k);
            CruceFactura.Resultado res = resultados.get(k);
            if (recalcular) {
                r.setPedidoItem(res.pedidoItemId != null ? itemPorId.get(res.pedidoItemId) : null);
                r.setEsBonificacion(res.bonificacion);
                renglonRepository.save(r);
            }
            RenglonFacturaMobile rm = armarRenglon(r, res);
            if (res.estado == CruceFactura.Estado.SIN_PRODUCTO) {
                rm.setSugerencias(sugerencias(r, proveedor));
            }
            f.getRenglones().add(rm);
            switch (res.estado) {
                case OK: ok++; break;
                case DIFERENCIA: dif++; break;
                case FUERA_DE_PEDIDO: fuera++; break;
                default: sinProd++;
            }
            if (res.observacionFaltante) sinObs++;
            if (res.avisosIva.stream().anyMatch(a -> a.startsWith("El precio"))) ivaPedido++;
            if (res.avisosIva.stream().anyMatch(a -> a.startsWith("En el sistema"))) ivaProducto++;
            if (res.pedidoItemId != null) {
                pendienteTrasFactura.merge(res.pedidoItemId, -res.cantidadBase, Double::sum);
            } else if (res.estado != CruceFactura.Estado.SIN_PRODUCTO
                    && fuenteDeDistribucion(r, itemsPedido) == null && sucursalUnica(pedido) == null) {
                sinDistribucion++;
            }
            if (!res.bonificacion) totalNota += res.cantidadBase * res.precioBase;
            totalRenglones += nz(r.getTotal());
        }
        f.setTotalNota(CruceFactura.redondear(totalNota, 2));
        f.setRenglonesOk(ok);
        f.setRenglonesConDiferencia(dif);
        f.setRenglonesFueraDePedido(fuera);
        f.setRenglonesSinProducto(sinProd);
        f.setRenglonesSinObservacion(sinObs);
        for (PedidoItem pi : itemsPedido) {
            Double pendiente = pendienteTrasFactura.get(pi.getId());
            if (pendiente != null && pendiente > CruceFactura.TOLERANCIA_CANTIDAD && doc.getNotaRecepcion() == null) {
                CompraPedidoItemMobile item = armarItem(pi, Math.max(0, nz(pi.getCantidadSolicitada()) - pendiente));
                f.getItemsNoFacturados().add(item);
            }
        }

        avisos(f, doc, pedido, moneda, mismaMoneda, sinProd, ivaPedido, ivaProducto, sinDistribucion, totalRenglones);
        boolean bloqueada = f.getAvisos().stream().anyMatch(a -> "BLOQUEO".equals(a.getNivel()));
        f.setPuedeConfirmar(doc.getNotaRecepcion() == null && !bloqueada && sinProd == 0 && sinObs == 0 && !renglones.isEmpty());
        if (!f.getPuedeConfirmar()) {
            f.setMotivoNoConfirma(doc.getNotaRecepcion() != null ? "La factura ya está cargada."
                    : bloqueada ? f.getAvisos().stream().filter(a -> "BLOQUEO".equals(a.getNivel())).findFirst().get().getMensaje()
                    : renglones.isEmpty() ? "La factura no trae renglones."
                    : sinProd > 0 ? (sinProd == 1 ? "Falta vincular 1 producto." : "Faltan vincular " + sinProd + " productos.")
                    : (sinObs == 1 ? "Falta la observación de 1 renglón con diferencia."
                    : "Falta la observación de " + sinObs + " renglones con diferencia."));
        }
        return f;
    }

    private FacturaProveedorMobile cabecera(DocumentoRecibido doc, Pedido pedido) {
        FacturaProveedorMobile f = new FacturaProveedorMobile();
        f.setId(doc.getId());
        f.setModo(pedido != null ? "PEDIDO" : "NUEVA_COMPRA");
        f.setPedidoId(pedido != null ? pedido.getId() : null);
        f.setCdc(doc.getCdc());
        f.setTipoDocumento(doc.getTipoDocumento());
        f.setTipoDocumentoDescripcion(KudeQr.descripcionTipo(doc.getTipoDocumento()));
        f.setEmisorRuc(conDv(doc.getRucEmisor(), doc.getDvEmisor()));
        f.setEmisorNombre(doc.getRazonSocialEmisor());
        f.setReceptorRuc(conDv(doc.getRucReceptor(), doc.getDvReceptor()));
        f.setReceptorNombre(doc.getRazonSocialReceptor());
        f.setTimbrado(doc.getTimbrado());
        f.setEstablecimiento(doc.getEstablecimiento());
        f.setPuntoExpedicion(doc.getPuntoExpedicion());
        f.setNumero(doc.getNumero());
        f.setNumeroCompleto(numeroCompleto(doc));
        f.setFechaEmision(doc.getFechaEmision() != null ? doc.getFechaEmision().format(FECHA_HORA) : null);
        f.setCondicion(doc.getCondicionOperacion() == null ? null : doc.getCondicionOperacion() == 2 ? "Crédito" : "Contado");
        f.setMoneda(doc.getMoneda());
        Moneda moneda = monedaDeOrNull(doc.getMoneda());
        f.setMonedaSimbolo(moneda != null ? moneda.getSimbolo() : doc.getMoneda());
        f.setCotizacion(doc.getCotizacion());
        f.setSubtotalExenta(doc.getSubtotalExenta());
        f.setSubtotalIva5(doc.getSubtotalIva5());
        f.setSubtotalIva10(doc.getSubtotalIva10());
        f.setTotalDescuento(doc.getTotalDescuento());
        f.setTotalGeneral(doc.getTotalGeneral());
        f.setTotalIva5(doc.getTotalIva5());
        f.setTotalIva10(doc.getTotalIva10());
        f.setTotalIva(doc.getTotalIva());
        f.setConsultadaEn(doc.getFechaConsulta() != null ? doc.getFechaConsulta().format(FECHA_HORA) : null);
        Proveedor proveedor = pedido != null ? pedido.getProveedor() : doc.getProveedor();
        if (proveedor != null) {
            f.setProveedorId(proveedor.getId());
            f.setProveedorNombre(nombreDe(proveedor));
        }
        f.setNotaRecepcionId(doc.getNotaRecepcion() != null ? doc.getNotaRecepcion().getId() : null);
        return f;
    }

    private RenglonFacturaMobile armarRenglon(DocumentoRecibidoItem r, CruceFactura.Resultado res) {
        RenglonFacturaMobile rm = new RenglonFacturaMobile();
        rm.setId(r.getId());
        rm.setOrden(r.getOrden());
        rm.setCodigoProveedor(r.getCodigoProveedor());
        rm.setGtin(r.getGtin());
        rm.setGtinPaquete(r.getGtinPaquete());
        rm.setDescripcion(r.getDescripcion());
        rm.setUnidadMedida(r.getUnidadMedida());
        rm.setCantidad(r.getCantidad());
        rm.setPrecioUnitario(r.getPrecioUnitario());
        rm.setDescuentoUnitario(r.getDescuentoUnitario());
        rm.setPorcentajeDescuento(r.getPorcentajeDescuento());
        rm.setTotal(r.getTotal());
        rm.setTasaIva(r.getTasaIva());
        rm.setIvaTexto(r.getAfectacionIva() != null && r.getAfectacionIva() == 3 ? "Exenta" : CruceFactura.iva(r.getTasaIva()));
        rm.setLote(r.getLote());
        rm.setVencimiento(r.getVencimiento() != null ? r.getVencimiento().format(FECHA) : null);
        rm.setEsBonificacion(res.bonificacion);
        rm.setEstado(res.estado.name());
        rm.setEstadoEtiqueta(etiquetaRenglon(res));
        rm.setTipoMatch(r.getProducto() != null ? r.getTipoMatch() : null);
        rm.setTipoMatchEtiqueta(r.getProducto() != null ? TipoMatch.etiqueta(r.getTipoMatch()) : null);
        if (r.getProducto() != null) {
            rm.setProductoId(r.getProducto().getId());
            rm.setProductoDescripcion(r.getProducto().getDescripcion());
        }
        if (r.getPresentacion() != null) {
            rm.setPresentacionId(r.getPresentacion().getId());
            rm.setPresentacionDescripcion(r.getPresentacion().getDescripcion());
        }
        rm.setFactor(r.getFactor());
        rm.setPedidoItemId(res.pedidoItemId);
        rm.setCantidadBase(res.cantidadBase);
        rm.setPrecioUnitarioBase(res.precioBase);
        rm.setPedidoCantidadPendiente(res.pedidoCantidadPendiente);
        rm.setPedidoPrecioUnitario(res.pedidoPrecio);
        rm.setDiferencias(new ArrayList<>(res.diferencias));
        rm.setAvisosIva(new ArrayList<>(res.avisosIva));
        rm.setAvisos(new ArrayList<>(res.avisos));
        rm.setRequiereObservacion(res.requiereObservacion);
        rm.setObservacionFaltante(res.observacionFaltante);
        rm.setObservacion(r.getObservacion());
        return rm;
    }

    private void avisos(FacturaProveedorMobile f, DocumentoRecibido doc, Pedido pedido, Moneda moneda, boolean mismaMoneda,
                        int sinProd, int ivaPedido, int ivaProducto, int sinDistribucion, double totalRenglones) {
        List<AvisoFacturaMobile> a = f.getAvisos();
        if (documentoRecibidoService.cancelada(doc)) {
            a.add(new AvisoFacturaMobile("CANCELADA", "BLOQUEO",
                    "SIFEN informa que esta factura fue cancelada por el proveedor."));
        }
        if (doc.getNotaRecepcion() == null) {
            ProcesoEtapa etapa = procesoEtapaService.getEtapaByPedidoAndTipo(pedido.getId(), ProcesoEtapaTipo.RECEPCION_NOTA).orElse(null);
            if (!etapaAbierta(etapa)) {
                a.add(new AvisoFacturaMobile("ETAPA", "BLOQUEO", "El pedido ya no está esperando facturas."));
            }
        }
        String rucPedido = rucDe(pedido.getProveedor());
        if (rucPedido != null && !mismoRuc(rucPedido, doc.getRucEmisor())) {
            a.add(new AvisoFacturaMobile("PROVEEDOR", "BLOQUEO", "La factura es de " + texto(doc.getRazonSocialEmisor())
                    + " y el pedido es de " + nombreDe(pedido.getProveedor()) + "."));
        }
        if (moneda == null) {
            a.add(new AvisoFacturaMobile("MONEDA", "BLOQUEO", "La factura está en " + texto(doc.getMoneda())
                    + ", una moneda que el sistema no tiene."));
        } else if (!mismaMoneda) {
            a.add(new AvisoFacturaMobile("MONEDA", "ADVERTENCIA", "La factura está en " + moneda.getDenominacion()
                    + " y el pedido en " + pedido.getMoneda().getDenominacion() + ": los precios no se comparan."));
        }
        if (moneda != null && !GUARANI.equals(moneda.getDenominacion())) {
            Double cot = cotizacionOrNull(moneda, doc, pedido);
            a.add(cot == null
                    ? new AvisoFacturaMobile("MONEDA", "BLOQUEO", "No hay cotización de " + moneda.getDenominacion()
                        + " para cargar la nota. Cargá la cotización del día.")
                    : new AvisoFacturaMobile("MONEDA", "INFO", "Cotización " + CruceFactura.importe(cot)
                        + (doc.getCotizacion() != null && doc.getCotizacion() > 1 ? " (la de la factura)." : " (la del pedido o la última cargada).")));
        }
        StringBuilder iva = new StringBuilder("Los precios de la factura incluyen IVA");
        List<String> partes = new ArrayList<>();
        if (nz(doc.getTotalIva10()) > 0) partes.add("IVA 10 %: " + CruceFactura.importe(nz(doc.getTotalIva10())));
        if (nz(doc.getTotalIva5()) > 0) partes.add("IVA 5 %: " + CruceFactura.importe(nz(doc.getTotalIva5())));
        if (nz(doc.getSubtotalExenta()) > 0) partes.add("exentas: " + CruceFactura.importe(nz(doc.getSubtotalExenta())));
        if (!partes.isEmpty()) iva.append(" (").append(String.join(" · ", partes)).append(")");
        iva.append(". La nota se carga con IVA incluido, igual que en el desktop.");
        a.add(new AvisoFacturaMobile("IVA", "INFO", iva.toString()));
        if (ivaPedido > 0) {
            a.add(new AvisoFacturaMobile("IVA", "ADVERTENCIA", ivaPedido == 1
                    ? "1 producto tiene en el pedido el precio sin IVA: la factura cobra el precio más el IVA."
                    : ivaPedido + " productos tienen en el pedido el precio sin IVA: la factura cobra el precio más el IVA."));
        }
        if (ivaProducto > 0) {
            a.add(new AvisoFacturaMobile("IVA", "ADVERTENCIA", ivaProducto == 1
                    ? "1 producto tiene en el sistema un IVA distinto al de la factura. Revisá su ficha."
                    : ivaProducto + " productos tienen en el sistema un IVA distinto al de la factura. Revisá sus fichas."));
        }
        if (sinProd > 0) {
            a.add(new AvisoFacturaMobile("PRODUCTOS", "ADVERTENCIA", (sinProd == 1
                    ? "1 renglón no se reconoció" : sinProd + " renglones no se reconocieron")
                    + ": el producto no está en el sistema con ese código. Vinculalo con uno parecido o dalo de alta."));
        }
        if (doc.getTotalGeneral() != null && Math.abs(totalRenglones - doc.getTotalGeneral()) > TOLERANCIA_TOTAL) {
            a.add(new AvisoFacturaMobile("TOTAL", "ADVERTENCIA", "La suma de los renglones ("
                    + CruceFactura.importe(totalRenglones) + ") no da el total de la factura ("
                    + CruceFactura.importe(doc.getTotalGeneral()) + "): puede haber un descuento global."));
        }
        if (sinDistribucion > 0) {
            a.add(new AvisoFacturaMobile("DISTRIBUCION", "ADVERTENCIA", sinDistribucion
                    + " renglón(es) fuera del pedido van a quedar sin distribuir entre sucursales: terminalos en el desktop."));
        }
        if (doc.getNotaRecepcion() == null) {
            Integer numero = numeroNotaOrNull(doc);
            if (numero != null) {
                List<Map<String, Object>> repetidas = jdbc.queryForList(
                        "SELECT nr.id, nr.pedido_id FROM operaciones.nota_recepcion nr JOIN operaciones.pedido p ON p.id = nr.pedido_id "
                                + "WHERE nr.numero = ? AND p.proveedor_id = ? AND (nr.timbrado IS NULL OR nr.timbrado = ?)",
                        numero, pedido.getProveedor() != null ? pedido.getProveedor().getId() : -1L,
                        doc.getTimbrado() != null ? doc.getTimbrado() : -1);
                for (Map<String, Object> rep : repetidas) {
                    long pedidoRep = ((Number) rep.get("pedido_id")).longValue();
                    a.add(pedidoRep == pedido.getId()
                            ? new AvisoFacturaMobile("DUPLICADA", "BLOQUEO", "Este pedido ya tiene una nota con el número "
                                + numero + ". Si es la misma factura cargada a mano, eliminala primero.")
                            : new AvisoFacturaMobile("DUPLICADA", "ADVERTENCIA", "El proveedor ya tiene una nota número "
                                + numero + " en el pedido " + pedidoRep + ". Revisá que no sea la misma factura."));
                }
            }
        }
    }

    // =================================================================================== Reconocer productos

    enum TipoMatch {
        MANUAL, APRENDIDO, CODIGO_BARRAS, CODIGO_PAQUETE, CODIGO_INTERNO;

        static String etiqueta(String tipo) {
            if (tipo == null) return null;
            switch (tipo) {
                case "MANUAL": return "Vinculado a mano";
                case "APRENDIDO": return "Reconocido por el código del proveedor";
                case "CODIGO_BARRAS": return "Reconocido por el código de barras";
                case "CODIGO_PAQUETE": return "Reconocido por el código del paquete";
                case "CODIGO_INTERNO": return "Reconocido por el código interno";
                default: return tipo;
            }
        }
    }

    /** Aprendido (proveedor + código) → código de barras → código del paquete → código interno como barras. */
    private void resolverProducto(DocumentoRecibidoItem r, Proveedor proveedor, Set<Long> productosDelPedido) {
        r.setProducto(null);
        r.setPresentacion(null);
        r.setFactor(null);
        r.setTipoMatch(null);
        if (proveedor != null && !vacio(r.getCodigoProveedor())) {
            ProveedorCodigoProducto aprendido = aprendizajeRepository
                    .findByProveedorIdAndCodigoProveedor(proveedor.getId(), r.getCodigoProveedor().trim()).orElse(null);
            if (aprendido != null && aprendido.getPresentacion() != null && aprendido.getPresentacion().getProducto() != null) {
                asignar(r, aprendido.getPresentacion(), aprendido.getFactor(), TipoMatch.APRENDIDO);
                return;
            }
        }
        if (porCodigo(r, r.getGtin(), TipoMatch.CODIGO_BARRAS, productosDelPedido)) return;
        if (porCodigo(r, r.getGtinPaquete(), TipoMatch.CODIGO_PAQUETE, productosDelPedido)) return;
        if (r.getCodigoProveedor() != null && r.getCodigoProveedor().trim().matches("\\d{8,14}")) {
            porCodigo(r, r.getCodigoProveedor(), TipoMatch.CODIGO_INTERNO, productosDelPedido);
        }
    }

    private boolean porCodigo(DocumentoRecibidoItem r, String codigo, TipoMatch tipo, Set<Long> productosDelPedido) {
        Presentacion p = presentacionPorCodigo(codigo, productosDelPedido);
        if (p == null) return false;
        asignar(r, p, p.getCantidad(), tipo);
        return true;
    }

    /** La presentación de ese código; si hay varias, la de un producto del pedido. Prueba también sin ceros. */
    private Presentacion presentacionPorCodigo(String codigo, Set<Long> productosDelPedido) {
        if (vacio(codigo)) return null;
        String c = codigo.trim();
        LinkedHashSet<String> variantes = new LinkedHashSet<>();
        variantes.add(c);
        variantes.add(c.replaceFirst("^0+(?=.)", ""));
        if (c.matches("\\d{12}")) variantes.add("0" + c);
        List<Presentacion> encontradas = new ArrayList<>();
        for (String v : variantes) {
            for (Codigo cod : codigoRepository.findByCodigo(v)) {
                if (Boolean.FALSE.equals(cod.getActivo())) continue;
                Presentacion p = cod.getPresentacion();
                if (p != null && p.getProducto() != null && !encontradas.contains(p)) encontradas.add(p);
            }
            if (!encontradas.isEmpty()) break;
        }
        return encontradas.stream()
                .filter(p -> productosDelPedido.contains(p.getProducto().getId()))
                .findFirst()
                .orElse(encontradas.isEmpty() ? null : encontradas.get(0));
    }

    private static void asignar(DocumentoRecibidoItem r, Presentacion p, Double factor, TipoMatch tipo) {
        r.setProducto(p.getProducto());
        r.setPresentacion(p);
        r.setFactor(factor != null && factor > 0 ? factor : 1.0);
        r.setTipoMatch(tipo.name());
    }

    private void aprender(Proveedor proveedor, DocumentoRecibidoItem r, Presentacion presentacion, double factor) {
        String codigo = r.getCodigoProveedor().trim();
        ProveedorCodigoProducto a = aprendizajeRepository.findByProveedorIdAndCodigoProveedor(proveedor.getId(), codigo)
                .orElse(null);
        if (a == null) {
            a = new ProveedorCodigoProducto();
            a.setProveedor(proveedor);
            a.setCodigoProveedor(codigo);
            a.setUsos(0);
            a.setCreadoEn(LocalDateTime.now());
            a.setUsuario(seguridad.usuarioActual());
        }
        a.setDescripcionProveedor(r.getDescripcion());
        a.setPresentacion(presentacion);
        a.setFactor(factor);
        a.setUsos((a.getUsos() != null ? a.getUsos() : 0) + 1);
        a.setUltimoUso(LocalDateTime.now());
        aprendizajeRepository.save(a);
    }

    /**
     * Para un renglón que no se reconoció: productos con un código parecido (un dígito cambiado o de más) y con
     * una descripción parecida, primero entre los del proveedor.
     */
    private List<SugerenciaProductoMobile> sugerencias(DocumentoRecibidoItem r, Proveedor proveedor) {
        LinkedHashMap<Long, SugerenciaProductoMobile> porProducto = new LinkedHashMap<>();
        try {
            for (String codigo : Arrays.asList(r.getGtin(), r.getGtinPaquete(), r.getCodigoProveedor())) {
                if (codigo == null || !codigo.trim().matches("\\d{8,14}")) continue;
                String c = codigo.trim();
                List<Map<String, Object>> parecidos = jdbc.queryForList(
                        "SELECT c.codigo, c.presentacion_id FROM productos.codigo c "
                                + "WHERE LENGTH(c.codigo) BETWEEN ? AND ? AND LEFT(c.codigo, 7) = LEFT(?, 7) "
                                + "AND COALESCE(c.activo, true) = true AND c.presentacion_id IS NOT NULL LIMIT 1000",
                        c.length() - 1, c.length() + 1, c);
                List<Map<String, Object>> ordenados = parecidos.stream()
                        .filter(m -> {
                            int d = Similitud.distancia(c, (String) m.get("codigo"));
                            return d > 0 && d <= 2;
                        })
                        .sorted(Comparator.comparingInt(m -> Similitud.distancia(c, (String) m.get("codigo"))))
                        .limit(3)
                        .collect(Collectors.toList());
                for (Map<String, Object> m : ordenados) {
                    Presentacion p = presentacionService.findById(((Number) m.get("presentacion_id")).longValue()).orElse(null);
                    if (p == null || p.getProducto() == null || porProducto.containsKey(p.getProducto().getId())) continue;
                    porProducto.put(p.getProducto().getId(), sugerencia(p, (String) m.get("codigo"), "CODIGO_PARECIDO",
                            "Código parecido: " + m.get("codigo") + " (la factura dice " + c + ")"));
                }
            }
            for (String texto : Similitud.consultasDescripcion(r.getDescripcion())) {
                if (porProducto.size() >= 5) break;
                for (Long proveedorId : proveedor != null ? Arrays.asList(proveedor.getId(), null) : Collections.<Long>singletonList(null)) {
                    if (porProducto.size() >= 5) break;
                    BuscadorProductoFiltros filtros = new BuscadorProductoFiltros();
                    filtros.setTexto(texto);
                    filtros.setProveedorId(proveedorId);
                    filtros.setActivo(true);
                    filtros.setPage(0);
                    filtros.setSize(3);
                    for (BuscadorProductoResultado res : buscadorProductoService.buscar(filtros).getContent()) {
                        Producto prod = res.getProducto();
                        if (prod == null || porProducto.containsKey(prod.getId()) || porProducto.size() >= 5) continue;
                        Presentacion p = presentacionParaSugerir(prod);
                        if (p == null) continue;
                        porProducto.put(prod.getId(), sugerencia(p, null, proveedorId != null ? "PROVEEDOR" : "DESCRIPCION",
                                proveedorId != null ? "Descripción parecida, ya se le compró a este proveedor"
                                        : "Descripción parecida"));
                    }
                }
            }
        } catch (RuntimeException e) {
            // Las sugerencias ayudan; si fallan, el comprador igual puede buscar a mano
            log.warn("No se pudieron armar sugerencias para el renglón {}: {}", r.getId(), e.getMessage());
        }
        return new ArrayList<>(porProducto.values());
    }

    private Presentacion presentacionParaSugerir(Producto producto) {
        List<Presentacion> presentaciones = presentacionService.findByProductoIdOrderByCantidadAsc(producto.getId());
        return presentaciones.stream().filter(p -> !Boolean.FALSE.equals(p.getActivo())).findFirst()
                .orElse(presentaciones.isEmpty() ? null : presentaciones.get(0));
    }

    private static SugerenciaProductoMobile sugerencia(Presentacion p, String codigo, String tipo, String motivo) {
        SugerenciaProductoMobile s = new SugerenciaProductoMobile();
        s.setProductoId(p.getProducto().getId());
        s.setDescripcion(p.getProducto().getDescripcion());
        s.setPresentacionId(p.getId());
        s.setPresentacionDescripcion(p.getDescripcion());
        s.setFactor(p.getCantidad() != null && p.getCantidad() > 0 ? p.getCantidad() : 1.0);
        s.setCodigo(codigo);
        s.setTipo(tipo);
        s.setMotivo(motivo);
        return s;
    }

    // =================================================================================== Distribución

    /**
     * De dónde sale el reparto por sucursal: el ítem del pedido del renglón; si no tiene (bonificación que el pedido
     * no esperaba), el ítem cobrado del mismo producto.
     */
    private PedidoItem fuenteDeDistribucion(DocumentoRecibidoItem r, List<PedidoItem> itemsPedido) {
        if (r.getPedidoItem() != null) return r.getPedidoItem();
        if (r.getProducto() == null) return null;
        return itemsPedido.stream()
                .filter(i -> i.getProducto() != null && i.getProducto().getId().equals(r.getProducto().getId()))
                .findFirst().orElse(null);
    }

    private Sucursal sucursalUnica(Pedido pedido) {
        List<PedidoSucursalEntrega> entregas = pedidoSucursalEntregaRepository.findByPedidoIdOrderBySucursalIdAsc(pedido.getId());
        return entregas.size() == 1 ? entregas.get(0).getSucursal() : null;
    }

    /**
     * Reparte la cantidad del ítem en proporción a la distribución del pedido. A diferencia de
     * {@code asignarItemsANota}, que copia la distribución completa aunque la nota traiga menos, acá el reparto suma
     * exactamente la cantidad de la nota, así el ítem queda conciliado. Con cantidades enteras, el reparto es entero.
     */
    private void distribuir(NotaRecepcionItem item, PedidoItem fuente, Pedido pedido, Usuario usuario) {
        List<NotaRecepcionItemDistribucion> nuevas = new ArrayList<>();
        double cantidad = nz(item.getCantidadEnNota());
        if (cantidad <= 0) return;
        List<PedidoItemDistribucion> base = fuente != null
                ? pedidoItemDistribucionService.findByPedidoItemId(fuente.getId()) : Collections.emptyList();
        if (!base.isEmpty()) {
            double[] pesos = base.stream().mapToDouble(d -> nz(d.getCantidadAsignada())).toArray();
            double[] partes = Reparto.proporcional(cantidad, pesos);
            for (int i = 0; i < base.size(); i++) {
                if (partes[i] <= 0) continue;
                nuevas.add(distribucion(item, base.get(i).getSucursalInfluencia(), base.get(i).getSucursalEntrega(), partes[i], usuario));
            }
        } else {
            Sucursal unica = sucursalUnica(pedido);
            if (unica != null) {
                nuevas.add(distribucion(item, unica, unica, cantidad, usuario));
            }
        }
        if (!nuevas.isEmpty()) {
            distribucionService.saveDistribuciones(nuevas);
        }
    }

    private static NotaRecepcionItemDistribucion distribucion(NotaRecepcionItem item, Sucursal influencia, Sucursal entrega,
                                                             double cantidad, Usuario usuario) {
        NotaRecepcionItemDistribucion d = new NotaRecepcionItemDistribucion();
        d.setNotaRecepcionItem(item);
        d.setSucursalInfluencia(influencia);
        d.setSucursalEntrega(entrega);
        d.setCantidad(cantidad);
        d.setCreadoEn(LocalDateTime.now());
        d.setUsuario(usuario);
        return d;
    }

    /** Los vínculos producto-proveedor que crea la nota al conciliarse, también para las que quedan pendientes. */
    private void vincularProductosConProveedor(NotaRecepcion nota, Usuario usuario) {
        Pedido pedido = nota.getPedido();
        if (pedido == null || pedido.getProveedor() == null) return;
        Set<Long> vistos = new HashSet<>();
        for (NotaRecepcionItem item : notaRecepcionItemService.findByNotaRecepcionId(nota.getId())) {
            Producto producto = item.getProducto();
            if (producto == null || !vistos.add(producto.getId())) continue;
            ProductoProveedor existente = productoProveedorService.getRepository()
                    .findByProductoIdAndProveedorId(producto.getId(), pedido.getProveedor().getId());
            if (existente == null) {
                ProductoProveedor nuevo = new ProductoProveedor();
                nuevo.setProducto(producto);
                nuevo.setProveedor(pedido.getProveedor());
                nuevo.setPedido(pedido);
                nuevo.setCreadoEn(LocalDateTime.now());
                nuevo.setUsuario(usuario != null ? usuario : pedido.getUsuario());
                nuevo.setActivo(true);
                productoProveedorService.save(nuevo);
            } else if (!Boolean.TRUE.equals(existente.getActivo())) {
                existente.setActivo(true);
                existente.setMotivoDesvinculacion(null);
                productoProveedorService.save(existente);
            }
        }
    }

    // =================================================================================== Ayudas

    private Pedido pedidoObligatorio(Long pedidoId) {
        if (pedidoId == null) throw new GraphQLException("Falta el pedido.");
        Optional<Pedido> p = pedidoService.findById(pedidoId);
        if (p == null || !p.isPresent()) throw new GraphQLException("El pedido " + pedidoId + " no existe.");
        return p.get();
    }

    private DocumentoRecibido documentoObligatorio(Long id) {
        if (id == null) throw new GraphQLException("Falta la factura.");
        return documentoRepository.findById(id)
                .orElseThrow(() -> new GraphQLException("La factura ya no está. Escaneala de nuevo."));
    }

    private DocumentoRecibidoItem renglonObligatorio(Long id) {
        if (id == null) throw new GraphQLException("Falta el renglón.");
        return renglonRepository.findById(id)
                .orElseThrow(() -> new GraphQLException("El renglón ya no está. Escaneá la factura de nuevo."));
    }

    private Pedido pedidoDe(DocumentoRecibido doc) {
        if (doc.getPedido() == null) throw new GraphQLException("La factura no está asociada a un pedido. Escaneala desde el pedido.");
        return doc.getPedido();
    }

    private static void exigirSinNota(DocumentoRecibido doc) {
        if (doc.getNotaRecepcion() != null) {
            throw new GraphQLException("Esta factura ya está cargada en la nota " + doc.getNotaRecepcion().getNumero() + ".");
        }
    }

    private void exigirEtapaAbierta(Pedido pedido) {
        ProcesoEtapa etapa = procesoEtapaService.getEtapaByPedidoAndTipo(pedido.getId(), ProcesoEtapaTipo.RECEPCION_NOTA).orElse(null);
        if (!etapaAbierta(etapa)) {
            throw new GraphQLException(etapa == null
                    ? "El pedido " + pedido.getId() + " todavía no está esperando facturas."
                    : "El pedido " + pedido.getId() + " ya no está esperando facturas (etapa de notas "
                    + etapa.getEstadoEtapa().name().toLowerCase().replace('_', ' ') + ").");
        }
    }

    private boolean etapaAbierta(Pedido pedido) {
        return etapaAbierta(procesoEtapaService.getEtapaByPedidoAndTipo(pedido.getId(), ProcesoEtapaTipo.RECEPCION_NOTA).orElse(null));
    }

    private static boolean etapaAbierta(ProcesoEtapa etapa) {
        return etapa != null && (etapa.getEstadoEtapa() == ProcesoEtapaEstado.PENDIENTE
                || etapa.getEstadoEtapa() == ProcesoEtapaEstado.EN_PROCESO);
    }

    private static boolean eliminable(NotaRecepcion n) {
        return n.getEstado() == NotaRecepcionEstado.PENDIENTE_CONCILIACION || n.getEstado() == NotaRecepcionEstado.CONCILIADA;
    }

    private Map<Long, Double> facturadoPorItem(Long pedidoId) {
        Map<Long, Double> m = new HashMap<>();
        jdbc.query("SELECT nri.pedido_item_id, SUM(nri.cantidad_en_nota) AS c FROM operaciones.nota_recepcion_item nri "
                        + "JOIN operaciones.pedido_item pi ON pi.id = nri.pedido_item_id WHERE pi.pedido_id = ? GROUP BY nri.pedido_item_id",
                rs -> {
                    m.put(rs.getLong(1), rs.getDouble(2));
                }, pedidoId);
        return m;
    }

    private Map<Long, DocumentoRecibido> documentosDe(List<NotaRecepcion> notas) {
        if (notas.isEmpty()) return Collections.emptyMap();
        return documentoRepository.findByNotaRecepcionIdIn(notas.stream().map(NotaRecepcion::getId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(d -> d.getNotaRecepcion().getId(), d -> d, (a, b) -> a));
    }

    private Set<String> rucsPropios() {
        return new HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT ruc FROM financiero.timbrado WHERE ruc IS NOT NULL AND TRIM(ruc) <> ''", String.class));
    }

    /** Compara RUC sin DV, puntos, guiones ni ceros a la izquierda. */
    static boolean mismoRuc(String a, String b) {
        String x = rucBase(a);
        String y = rucBase(b);
        return x != null && x.equals(y);
    }

    static String rucBase(String ruc) {
        if (ruc == null) return null;
        String limpio = ruc.trim().toUpperCase().replace(".", "").replace(" ", "");
        int guion = limpio.indexOf('-');
        if (guion > 0) limpio = limpio.substring(0, guion);
        limpio = limpio.replaceFirst("^0+(?=.)", "");
        return limpio.isEmpty() ? null : limpio;
    }

    private static String rucDe(Proveedor p) {
        return p != null && p.getPersona() != null && !vacio(p.getPersona().getDocumento()) ? p.getPersona().getDocumento().trim() : null;
    }

    private static String nombreDe(Proveedor p) {
        return p != null && p.getPersona() != null ? p.getPersona().getNombre() : "el proveedor";
    }

    private Moneda monedaDeOrNull(String codigo) {
        String denominacion = MONEDAS.get(codigo != null ? codigo.trim().toUpperCase() : "PYG");
        return denominacion != null ? monedaService.findByDescripcion(denominacion) : null;
    }

    private Moneda monedaDe(String codigo) {
        Moneda m = monedaDeOrNull(codigo);
        if (m == null) throw new GraphQLException("La factura está en " + codigo + ", una moneda que el sistema no tiene.");
        return m;
    }

    /** Cotización de la nota (R3 del desktop): guaraní 1; si no, la de la factura, la del pedido o el último cambio. */
    private Double cotizacionOrNull(Moneda moneda, DocumentoRecibido doc, Pedido pedido) {
        if (moneda == null || GUARANI.equals(moneda.getDenominacion())) return 1.0;
        if (doc.getCotizacion() != null && doc.getCotizacion() > 1) return doc.getCotizacion();
        if (pedido.getMoneda() != null && Objects.equals(pedido.getMoneda().getId(), moneda.getId())
                && pedido.getCotizacion() != null && pedido.getCotizacion() > 1) {
            return pedido.getCotizacion();
        }
        Cambio cambio = cambioService.findLastByMonedaId(moneda.getId());
        Double tasa = cambio == null ? null
                : cambio.getValorEnGsCompraMercado() != null ? cambio.getValorEnGsCompraMercado()
                : cambio.getValorEnGsVentaMercado() != null ? cambio.getValorEnGsVentaMercado()
                : cambio.getValorEnGs();
        return tasa != null && tasa > 1 ? tasa : null;
    }

    private Double cotizacion(Moneda moneda, DocumentoRecibido doc, Pedido pedido) {
        Double c = cotizacionOrNull(moneda, doc, pedido);
        if (c == null) throw new GraphQLException("No hay cotización de " + moneda.getDenominacion() + " para cargar la nota.");
        return c;
    }

    private static Integer numeroNotaOrNull(DocumentoRecibido doc) {
        try {
            return doc.getNumero() != null ? Integer.valueOf(doc.getNumero().trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer numeroNota(DocumentoRecibido doc) {
        Integer n = numeroNotaOrNull(doc);
        if (n == null) throw new GraphQLException("La factura no tiene un número válido.");
        return n;
    }

    private static String numeroCompleto(DocumentoRecibido doc) {
        return texto(doc.getEstablecimiento()) + "-" + texto(doc.getPuntoExpedicion()) + "-" + texto(doc.getNumero());
    }

    private static String conDv(String ruc, String dv) {
        if (ruc == null) return null;
        return vacio(dv) ? ruc : ruc + "-" + dv;
    }

    /** La observación del comprador; si no hay, lo que se sabe del renglón (lote del proveedor). */
    private static String observacionDeNota(DocumentoRecibidoItem r, RenglonFacturaMobile cruce) {
        List<String> partes = new ArrayList<>();
        if (!vacio(r.getObservacion())) partes.add(r.getObservacion().trim());
        if (!vacio(r.getLote())) partes.add("Lote del proveedor: " + r.getLote().trim());
        return partes.isEmpty() ? null : String.join(" · ", partes);
    }

    private static String etiquetaRenglon(CruceFactura.Resultado res) {
        switch (res.estado) {
            case OK: return res.bonificacion ? "Bonificación" : "Coincide con el pedido";
            case DIFERENCIA: return "Diferencia con el pedido";
            case FUERA_DE_PEDIDO: return "No estaba en el pedido";
            default: return "Producto no reconocido";
        }
    }

    static String etiquetaNota(NotaRecepcionEstado e) {
        if (e == null) return "Sin estado";
        switch (e) {
            case PENDIENTE_CONCILIACION: return "Pendiente de conciliar";
            case CONCILIADA: return "Conciliada";
            case EN_RECEPCION: return "En recepción";
            case RECEPCION_PARCIAL: return "Recepción parcial";
            case RECEPCION_COMPLETA: return "Recibida";
            case CERRADA: return "Cerrada";
            default: return e.name();
        }
    }

    private static double nz(Double d) {
        return d != null ? d : 0.0;
    }

    private static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String texto(String s) {
        return s != null ? s : "";
    }
}
