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
import com.franco.dev.domain.productos.enums.TipoCoincidenciaBuscador;
import com.franco.dev.graphql.operaciones.compras.AvisoFacturaMobile;
import com.franco.dev.graphql.operaciones.compras.CompraNotaMobile;
import com.franco.dev.graphql.operaciones.compras.CompraPedidoItemMobile;
import com.franco.dev.graphql.operaciones.compras.CompraPedidoMobile;
import com.franco.dev.graphql.operaciones.compras.CompraPedidoResumenMobile;
import com.franco.dev.graphql.operaciones.compras.ConversionMonedaMobile;
import com.franco.dev.graphql.operaciones.compras.KudeProveedorMobile;
import com.franco.dev.graphql.operaciones.compras.FacturaProveedorMobile;
import com.franco.dev.graphql.operaciones.compras.OpcionMobile;
import com.franco.dev.graphql.operaciones.compras.PresentacionCompraMobile;
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
import com.franco.dev.graphql.operaciones.compras.RepartoRenglonMobileInput;
import com.franco.dev.service.operaciones.PlanificacionFinalizadaEvent;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

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
    static final String CABECERA_SIN_CONFIRMAR = "Confirmá los datos de la factura en papel antes de seguir.";

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
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private KudeProveedorService kudeProveedorService;

    // =================================================================================== Pedidos

    /** Pedidos con RECEPCION_NOTA en PENDIENTE o EN_PROCESO, del más nuevo al más viejo. */
    @Transactional(readOnly = true)
    public Page<CompraPedidoResumenMobile> pedidosEsperandoFactura(String texto, Long proveedorId, int page, int size) {
        return compras(texto, proveedorId, true, page, size);
    }

    /**
     * Las compras, la última primero. Con {@code soloEsperandoFactura}, solo las que tienen la etapa de notas
     * pendiente o en proceso; sin él, todas menos las canceladas, con la etapa en la que está cada una (una compra
     * creada desde la factura y finalizada ya está en recepción de mercadería).
     */
    @Transactional(readOnly = true)
    public Page<CompraPedidoResumenMobile> compras(String texto, Long proveedorId, boolean soloEsperandoFactura, int page, int size) {
        seguridad.exigirCompras();
        int tamano = size > 0 ? Math.min(size, 50) : 20;
        int pagina = Math.max(page, 0);
        String filtro = texto != null ? texto.trim().toUpperCase() : "";
        long prov = proveedorId != null ? proveedorId : 0L;
        String desde = "FROM operaciones.pedido p "
                + "LEFT JOIN personas.proveedor pr ON pr.id = p.proveedor_id "
                + "LEFT JOIN personas.persona per ON per.id = pr.persona_id "
                + "LEFT JOIN financiero.moneda m ON m.id = p.moneda_id "
                + "WHERE (" + (soloEsperandoFactura
                    ? "EXISTS (SELECT 1 FROM operaciones.proceso_etapa pe WHERE pe.pedido_id = p.id "
                        + "  AND pe.tipo_etapa::text = 'RECEPCION_NOTA' AND pe.estado_etapa::text IN ('PENDIENTE','EN_PROCESO')) "
                        // La etapa de notas nace PENDIENTE junto con el pedido: espera facturas recién con la planificación terminada
                        + "AND NOT EXISTS (SELECT 1 FROM operaciones.proceso_etapa pc WHERE pc.pedido_id = p.id "
                        + "  AND pc.tipo_etapa::text = 'CREACION' AND pc.estado_etapa::text NOT IN ('COMPLETADA','OMITIDA')) "
                        // Un pedido sin ítems no espera ninguna factura: la factura crea su propia compra
                        + "AND EXISTS (SELECT 1 FROM operaciones.pedido_item pi0 WHERE pi0.pedido_id = p.id)"
                    : "NOT EXISTS (SELECT 1 FROM operaciones.proceso_etapa pe WHERE pe.pedido_id = p.id "
                        + "  AND pe.estado_etapa::text = 'CANCELADA')") + ") "
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
                        + "(SELECT pe.tipo_etapa::text FROM operaciones.proceso_etapa pe WHERE pe.pedido_id = p.id "
                        + "   AND pe.estado_etapa::text NOT IN ('COMPLETADA','OMITIDA') ORDER BY CASE pe.tipo_etapa::text "
                        + "   WHEN 'CREACION' THEN 1 WHEN 'RECEPCION_NOTA' THEN 2 WHEN 'RECEPCION_MERCADERIA' THEN 3 ELSE 4 END "
                        + "   LIMIT 1) AS etapa_actual, "
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
                    r.setEtapaActual(rs.getString("etapa_actual") != null ? rs.getString("etapa_actual") : "CONCLUIDA");
                    r.setEtapaEtiqueta(etiquetaEtapa(r.getEtapaActual(), r.getEstadoEtapa()));
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
        return abrirEnPedido(documentoRecibidoService.obtener(kude.getCdc()), pedido);
    }

    /**
     * La factura leída (del QR o de la foto ya confirmada) queda abierta en el pedido: no cargada, no esperando
     * otra compra, a nombre de la empresa, y cruzada con el pedido.
     */
    public FacturaProveedorMobile abrirEnPedido(DocumentoRecibido doc, Pedido pedido) {
        exigirNoCargada(doc);
        exigirQueNoEspereOtroPedido(doc, pedido.getId());
        exigirReceptorPropio(doc);
        doc.setPedido(pedido);
        doc.setProveedor(pedido.getProveedor());
        if (doc.getUsuario() == null) doc.setUsuario(seguridad.usuarioActual());
        documentoRepository.save(doc);
        return armarFactura(doc, pedido, true);
    }

    /** Ídem para la factura que crea la compra: queda como borrador del proveedor, sin pedido. */
    public FacturaProveedorMobile abrirNuevaCompra(DocumentoRecibido doc, Proveedor proveedor) {
        exigirNoCargada(doc);
        exigirQueNoEspereOtroPedido(doc, null);
        exigirReceptorPropio(doc);
        doc.setPedido(null);
        doc.setCargarAlFinalizar(false);
        doc.setProveedor(proveedor);
        if (doc.getUsuario() == null) doc.setUsuario(seguridad.usuarioActual());
        documentoRepository.save(doc);
        return armarBorrador(doc, true);
    }

    private static void exigirNoCargada(DocumentoRecibido doc) {
        if (doc.getNotaRecepcion() != null) {
            NotaRecepcion nota = doc.getNotaRecepcion();
            throw new GraphQLException("Esta factura ya está cargada: nota " + nota.getNumero() + " del pedido "
                    + (nota.getPedido() != null ? nota.getPedido().getId() : "?") + ".");
        }
    }

    /**
     * La factura tiene que estar a nombre de la empresa. En la de papel el RUC del cliente lo leyó la IA y no se
     * puede corregir: ahí es solo un aviso de la cabecera (ver {@link FacturaFotoService}).
     */
    private void exigirReceptorPropio(DocumentoRecibido doc) {
        if (esFoto(doc)) return;
        Set<String> nuestros = rucsPropios();
        if (!nuestros.isEmpty() && doc.getRucReceptor() != null
                && nuestros.stream().noneMatch(r -> mismoRuc(r, doc.getRucReceptor()))) {
            throw new GraphQLException("La factura está a nombre de " + texto(doc.getRazonSocialReceptor())
                    + " (RUC " + doc.getRucReceptor() + "), no de la empresa.");
        }
    }

    /** La factura en papel no sigue hasta que el comprador confirme la cabecera que leyó la IA. */
    static void exigirCabeceraConfirmada(DocumentoRecibido doc) {
        if (Boolean.FALSE.equals(doc.getCabeceraConfirmada())) {
            throw new GraphQLException(CABECERA_SIN_CONFIRMAR);
        }
    }

    static boolean esFoto(DocumentoRecibido doc) {
        return DocumentoRecibido.ORIGEN_FOTO.equals(doc.getOrigen());
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
        if (!presentacionUsable(presentacion)) {
            throw new GraphQLException("«" + presentacion.getProducto().getDescripcion()
                    + "» o esa presentación están inactivos: no se le puede cargar una compra.");
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
        if (Boolean.TRUE.equals(recordar) && doc.getProveedor() != null && sePuedeAprender(renglon)) {
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
        exigirCabeceraConfirmada(doc);
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
            if (renglon.getProducto() != null && sePuedeAprender(renglon) && doc.getProveedor() != null
                    && renglon.getPresentacion() != null) {
                aprender(doc.getProveedor(), renglon, renglon.getPresentacion(),
                        renglon.getFactor() != null ? renglon.getFactor() : 1.0);
            }
        }
        // Concilia los ítems con la distribución completa, la nota si quedaron todos, y recalcula el valor
        notaRecepcionService.actualizarEstadosDespuesDeDistribucion(nota.getId());
        notaRecepcionService.actualizarValorNota(nota.getId());
        notaRecepcionService.vincularProductosConProveedor(nota, usuario);

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
        return armarResumenFinalizar(pedidoObligatorio(pedidoId));
    }

    /** Lo que falta para finalizar la conciliación. Lo comparten el celular y la carga automática de la factura. */
    private ResumenFinalizarNotasMobile armarResumenFinalizar(Pedido pedido) {
        Long pedidoId = pedido.getId();
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
        r.setItemsSinDistribuir(sinDistribuir);
        if (sinDistribuir > 0) {
            r.getAvisos().add(sinDistribuir + " ítem(s) de las notas no tienen la distribución por sucursal completa:"
                    + " no van a llegar a ninguna sucursal. Distribuilos en el desktop antes de finalizar.");
        }
        return r;
    }

    /**
     * Finaliza la conciliación (como el desktop); {@code finalizarRecepcionNotas} ya crea los vínculos
     * producto-proveedor de todas las notas.
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
        return armarPedido(pedidoObligatorio(pedido.getId()));
    }

    /**
     * Las presentaciones del producto para vincular un renglón, con la sugerida marcada: la del bulto que dice la
     * descripción («10X15G» → la de 10), si no la que tiene el código de barras del renglón, si no la unidad.
     */
    @Transactional(readOnly = true)
    public List<PresentacionCompraMobile> presentacionesParaRenglon(Long productoId, Long renglonId) {
        seguridad.exigirCompras();
        List<Presentacion> presentaciones = presentacionService.findByProductoIdOrderByCantidadAsc(productoId).stream()
                .filter(p -> !Boolean.FALSE.equals(p.getActivo()))
                .collect(Collectors.toList());
        DocumentoRecibidoItem renglon = renglonId != null ? renglonRepository.findById(renglonId).orElse(null) : null;
        Long sugerida = null;
        String motivo = null;
        Double factorSugerido = null;
        Double ultimo = ultimoPrecioCompra(productoId);
        double precio = renglon != null ? nz(renglon.getPrecioUnitario()) - nz(renglon.getDescuentoUnitario()) : 0;
        if (renglon != null && precio > 0) {
            List<Double> candidatos = presentaciones.stream().map(p -> p.getCantidad() != null && p.getCantidad() > 0 ? p.getCantidad() : 1.0)
                    .collect(Collectors.toList());
            Double factor = CruceFactura.factorPorPrecio(precio, ultimo, candidatos, Similitud.numerosDeBulto(renglon.getDescripcion()));
            if (factor != null) {
                Presentacion p = presentacionQueDivide(presentaciones, factor);
                if (p != null) {
                    sugerida = p.getId();
                    factorSugerido = factor;
                    motivo = "Bulto de " + CruceFactura.cantidad(factor) + " u.: el precio por unidad cierra con la última compra";
                }
            }
        }
        if (sugerida == null && renglon != null) {
            Integer bulto = CruceFactura.productosDe(Similitud.numerosDeBulto(renglon.getDescripcion())).stream()
                    .max(Integer::compare).orElse(null);
            if (bulto != null && bulto > 1) {
                for (Presentacion p : presentaciones) {
                    if (p.getCantidad() != null && Math.abs(p.getCantidad() - bulto) < 0.001) {
                        sugerida = p.getId();
                        motivo = "La factura dice " + bulto + " por bulto";
                        break;
                    }
                }
            }
            if (sugerida == null) {
                for (String[] c : codigosDelRenglon(renglon)) {
                    Presentacion p = presentaciones.isEmpty() ? null : presentacionConCodigo(presentaciones, c[0]);
                    if (p != null) {
                        sugerida = p.getId();
                        motivo = "Tiene el código " + c[0];
                        break;
                    }
                }
            }
        }
        if (sugerida == null && !presentaciones.isEmpty()) {
            Presentacion unidad = presentaciones.stream()
                    .filter(p -> p.getCantidad() != null && Math.abs(p.getCantidad() - 1) < 0.001)
                    .findFirst().orElse(presentaciones.get(0));
            sugerida = unidad.getId();
            motivo = null;
        }
        List<PresentacionCompraMobile> lista = new ArrayList<>();
        for (Presentacion p : presentaciones) {
            PresentacionCompraMobile pm = new PresentacionCompraMobile();
            pm.setId(p.getId());
            pm.setDescripcion(p.getDescripcion());
            pm.setCantidad(p.getCantidad() != null && p.getCantidad() > 0 ? p.getCantidad() : 1.0);
            Codigo principal = codigoRepository.findByPresentacionId(p.getId()).stream()
                    .filter(c -> !Boolean.FALSE.equals(c.getActivo()))
                    .sorted(Comparator.comparing((Codigo c) -> !Boolean.TRUE.equals(c.getPrincipal())))
                    .findFirst().orElse(null);
            pm.setCodigo(principal != null ? principal.getCodigo() : null);
            if (precio > 0) {
                double unidades = p.getId().equals(sugerida) && factorSugerido != null ? factorSugerido : pm.getCantidad();
                double porUnidad = precio / unidades;
                pm.setPrecioPorUnidad(CruceFactura.redondear(porUnidad, 2));
                if (ultimo != null && ultimo > 0) {
                    double variacion = (porUnidad - ultimo) / ultimo;
                    pm.setComparacion(Math.abs(variacion) <= CruceFactura.TOLERANCIA_BULTO
                            ? "como la última compra (" + CruceFactura.importe(ultimo) + ")"
                            : (variacion > 0 ? "+" : "") + Math.round(variacion * 100) + " % sobre la última compra ("
                                + CruceFactura.importe(ultimo) + ")");
                    pm.setCierraConUltimaCompra(Math.abs(variacion) <= CruceFactura.TOLERANCIA_BULTO);
                }
            }
            pm.setSugerida(p.getId().equals(sugerida));
            // El bulto puede ser varias veces la presentación (12 bolsas de 67): el factor va aparte
            pm.setFactorSugerido(p.getId().equals(sugerida) && factorSugerido != null ? factorSugerido : null);
            pm.setMotivo(p.getId().equals(sugerida) ? motivo : null);
            lista.add(pm);
        }
        return lista;
    }

    private Presentacion presentacionConCodigo(List<Presentacion> presentaciones, String codigo) {
        String buscado = sinCeros(codigo);
        for (Presentacion p : presentaciones) {
            for (Codigo c : codigoRepository.findByPresentacionId(p.getId())) {
                String propio = sinCeros(c.getCodigo());
                if (propio != null && propio.length() >= 4 && (propio.equals(buscado) || buscado.contains(propio) || propio.contains(buscado))) {
                    return p;
                }
            }
        }
        return null;
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
        return abrirNuevaCompra(documentoRecibidoService.obtener(kude.getCdc()), proveedor);
    }

    /**
     * «Factura del proveedor (PDF)»: escanear el KuDE, traer la factura de SIFEN (o de la caché) y devolverla armada
     * en PDF con la plantilla de nuestras facturas electrónicas. No crea nada: solo consulta.
     */
    @Transactional
    public KudeProveedorMobile kudeProveedor(String qr) {
        seguridad.exigirCompras();
        KudeQr kude = KudeQr.leer(qr);
        if (!kude.isOk()) {
            throw new GraphQLException(kude.getMensaje());
        }
        if (kude.getTipoDocumento() != 1 && kude.getTipoDocumento() != 4) {
            throw new GraphQLException("Ese documento es una " + KudeQr.descripcionTipo(kude.getTipoDocumento()).toLowerCase()
                    + ", no una factura.");
        }
        DocumentoRecibido doc = documentoRecibidoService.obtener(kude.getCdc());
        Set<String> nuestros = rucsPropios();
        if (!nuestros.isEmpty() && doc.getRucReceptor() != null
                && nuestros.stream().noneMatch(r -> mismoRuc(r, doc.getRucReceptor()))) {
            throw new GraphQLException("La factura está a nombre de " + texto(doc.getRazonSocialReceptor())
                    + " (RUC " + doc.getRucReceptor() + "), no de la empresa.");
        }
        if (doc.getUsuario() == null) {
            doc.setUsuario(seguridad.usuarioActual());
            documentoRepository.save(doc);
        }
        byte[] pdf;
        try {
            pdf = kudeProveedorService.pdf(doc);
        } catch (RuntimeException e) {
            log.warn("KuDE del proveedor {}: {}", doc.getCdc(), e.getMessage(), e);
            throw new GraphQLException("No se pudo armar el PDF de la factura: " + e.getMessage());
        }
        KudeProveedorMobile k = new KudeProveedorMobile();
        k.setDocumentoId(doc.getId());
        k.setCdc(doc.getCdc());
        k.setTipoDocumentoDescripcion(KudeQr.descripcionTipo(doc.getTipoDocumento() != null ? doc.getTipoDocumento() : 1));
        k.setNumeroCompleto(numeroCompleto(doc));
        k.setEmisorNombre(doc.getRazonSocialEmisor());
        k.setEmisorRuc(conDv(doc.getRucEmisor(), doc.getDvEmisor()));
        k.setFechaEmision(doc.getFechaEmision() != null ? doc.getFechaEmision().format(FECHA_HORA) : null);
        k.setCondicion(doc.getCondicionOperacion() == null ? null : doc.getCondicionOperacion() == 2 ? "Crédito" : "Contado");
        Moneda moneda = monedaDeOrNull(doc.getMoneda());
        k.setMonedaSimbolo(moneda != null ? moneda.getSimbolo() : doc.getMoneda());
        k.setTotalGeneral(doc.getTotalGeneral());
        k.setCantidadRenglones(documentoRecibidoService.renglones(doc.getId()).size());
        k.setCancelada(documentoRecibidoService.cancelada(doc));
        k.setNombreArchivo("Factura " + texto(doc.getRazonSocialEmisor()).replaceAll("[^A-Za-z0-9 .-]", "").trim()
                + " " + numeroCompleto(doc) + ".pdf");
        k.setPdf(java.util.Base64.getEncoder().encodeToString(pdf));
        return k;
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
        for (Moneda m : monedaService.findAll2()) {
            if (Boolean.FALSE.equals(m.getActivo())) continue;
            o.getMonedas().add(new OpcionMobile(m.getId(), texto(m.getDenominacion())
                    + (vacio(m.getSimbolo()) ? "" : " (" + m.getSimbolo().trim() + ")")));
        }
        return o;
    }

    /**
     * Cómo quedarían los precios del pedido en otra moneda que la de la factura, para mostrarlo antes de crear la
     * compra. Con la misma moneda, el factor es 1.
     */
    @Transactional
    public ConversionMonedaMobile conversionMoneda(Long documentoId, Long monedaId) {
        seguridad.exigirCompras();
        DocumentoRecibido doc = documentoObligatorio(documentoId);
        FacturaProveedorMobile factura = armarBorrador(doc, false);
        return conversion(doc, monedaId, factura);
    }

    private ConversionMonedaMobile conversion(DocumentoRecibido doc, Long monedaId, FacturaProveedorMobile factura) {
        Moneda origen = monedaDe(doc.getMoneda());
        Moneda destino = monedaId == null ? origen : monedaService.findById(monedaId)
                .orElseThrow(() -> new GraphQLException("No existe la moneda " + monedaId + "."));
        ConversionMonedaMobile c = new ConversionMonedaMobile();
        c.setMonedaId(destino.getId());
        c.setMonedaSimbolo(destino.getSimbolo());
        boolean misma = Objects.equals(origen.getId(), destino.getId());
        c.setMismaMoneda(misma);
        Double cotOrigen = cotizacionDe(origen, doc);
        Double cotDestino = misma ? cotOrigen : cotizacionDe(destino, doc);
        if (cotOrigen == null) {
            throw new GraphQLException("No hay cotización de " + origen.getDenominacion() + " para convertir los precios.");
        }
        if (cotDestino == null) {
            throw new GraphQLException("No hay cotización de " + destino.getDenominacion() + " para convertir los precios.");
        }
        c.setCotizacionFactura(cotOrigen);
        c.setCotizacionPedido(cotDestino);
        c.setFactor(misma ? 1.0 : cotOrigen / cotDestino);
        double total = 0;
        if (factura != null) {
            for (RenglonFacturaMobile r : factura.getRenglones()) {
                if (Boolean.TRUE.equals(r.getEsBonificacion())) continue;
                total += nz(r.getCantidadBase()) * nz(r.getPrecioUnitarioBase());
            }
        }
        c.setTotalPedido(CruceFactura.redondear(total * c.getFactor(), 2));
        if (misma) {
            c.setTexto("La moneda de la factura.");
        } else {
            StringBuilder t = new StringBuilder("Los precios del pedido se convierten: ");
            if (!GUARANI.equals(destino.getDenominacion())) {
                t.append("1 ").append(texto(destino.getSimbolo())).append(" = ").append(CruceFactura.importe(cotDestino)).append(" Gs.");
            }
            if (!GUARANI.equals(origen.getDenominacion())) {
                if (!GUARANI.equals(destino.getDenominacion())) t.append(" y ");
                t.append("1 ").append(texto(origen.getSimbolo())).append(" = ").append(CruceFactura.importe(cotOrigen)).append(" Gs.");
            }
            t.append(". Total del pedido: ").append(texto(destino.getSimbolo())).append(" ")
                    .append(CruceFactura.importe(c.getTotalPedido())).append(". La nota queda en ")
                    .append(origen.getDenominacion().toLowerCase()).append(", como la factura.");
            c.setTexto(t.toString());
        }
        return c;
    }

    /** Guaraníes por unidad de la moneda: 1 el guaraní; la de la factura si es su moneda; si no, el último cambio. */
    private Double cotizacionDe(Moneda m, DocumentoRecibido doc) {
        if (m == null || GUARANI.equals(m.getDenominacion())) return 1.0;
        Moneda deFactura = monedaDeOrNull(doc.getMoneda());
        if (deFactura != null && Objects.equals(deFactura.getId(), m.getId())
                && doc.getCotizacion() != null && doc.getCotizacion() > 1) {
            return doc.getCotizacion();
        }
        Cambio cambio = cambioService.findLastByMonedaId(m.getId());
        Double tasa = cambio == null ? null
                : cambio.getValorEnGsCompraMercado() != null ? cambio.getValorEnGsCompraMercado()
                : cambio.getValorEnGsVentaMercado() != null ? cambio.getValorEnGsVentaMercado()
                : cambio.getValorEnGs();
        return tasa != null && tasa > 1 ? tasa : null;
    }

    /**
     * Crea el pedido con lo que dice la factura, en una sola transacción: los mismos pasos que el desktop (crear el
     * pedido con sus sucursales y cargar cada ítem con su reparto), con las mismas operaciones.
     *
     * ⚠️ <b>La compra queda en la planificación («Ítems del pedido»)</b>, con todos los ítems distribuidos: el
     * comprador la revisa en el desktop con el papel en la mano y la finaliza. Recién ahí el central carga la factura
     * como nota ({@link #alFinalizarPlanificacion}); hasta entonces queda vinculada al pedido con
     * {@code cargarAlFinalizar}.
     *
     * Lo que la factura trae se toma de la factura (proveedor, moneda, cotización, ítems, precios con IVA, forma de
     * pago y plazo si vienen). Lo que no trae lo elige el comprador: la forma de pago si la factura no la dice, y las
     * sucursales. Con una sola sucursal recibe todo; con varias, {@code repartos} dice cuánto de cada renglón va a
     * cada una, en la unidad de la factura.
     */
    @Transactional
    public CompraPedidoMobile crearCompraDesdeFactura(Long documentoId, Long formaPagoId, Integer plazoCredito,
                                                       Long sucursalId, List<Long> sucursalIds,
                                                       List<RepartoRenglonMobileInput> repartos, Long monedaId) {
        seguridad.exigirCompras();
        DocumentoRecibido doc = documentoObligatorio(documentoId);
        exigirCabeceraConfirmada(doc);
        exigirSinNota(doc);
        exigirQueNoEspereOtroPedido(doc, null);
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
        List<Long> sucursales = sucursalesElegidas(sucursalId, sucursalIds);
        List<DocumentoRecibidoItem> renglones = documentoRecibidoService.renglones(doc.getId());
        Map<Long, RenglonFacturaMobile> porId = factura.getRenglones().stream()
                .collect(Collectors.toMap(RenglonFacturaMobile::getId, r -> r));
        Map<Long, double[]> repartoPorRenglon = repartoPorRenglon(renglones, porId, sucursales, repartos);
        // La moneda del pedido: la de la factura o la que eligió el comprador, con los precios convertidos
        ConversionMonedaMobile conversion = conversion(doc, monedaId, factura);
        Moneda moneda = monedaService.findById(conversion.getMonedaId()).orElseThrow(() -> new GraphQLException("Moneda inexistente."));
        double factor = conversion.getFactor();
        Usuario usuario = seguridad.usuarioActual();
        Long usuarioId = usuario != null ? usuario.getId() : null;

        // 1. El pedido, con sus sucursales y sus etapas: como «Guardar» de la creación en el desktop
        PedidoInput input = new PedidoInput();
        input.setProveedorId(doc.getProveedor().getId());
        input.setFormaPagoId(formaPago);
        input.setMonedaId(moneda.getId());
        input.setPlazoCredito(plazoCredito != null ? plazoCredito : factura.getPlazoCredito());
        input.setUsuarioId(usuarioId);
        List<Integer> sucursalesPedido = sucursales.stream().map(Long::intValue).collect(Collectors.toList());
        Pedido pedido = pedidoGraphQL.savePedidoFull(input, null, sucursalesPedido, sucursalesPedido,
                usuarioId != null ? usuarioId.intValue() : null);
        pedido.setFormaPago(formaPagoService.findById(formaPago).orElse(null));
        pedido.setCotizacion(conversion.getCotizacionPedido());
        pedido = pedidoService.save(pedido);

        // 2. Un ítem por renglón, con su reparto por sucursal: como «Agregar ítem» del desktop
        // ⚠️ Al revés del orden de la factura, a propósito: el desktop lista los ítems del pedido por id descendente
        // (pedidoItemPorPedidoPage), así que creados del último al primero se ven en el orden de la factura y el
        // comprador los revisa con el papel en la mano. La nota se crea en el orden de la factura: esa se lista
        // ascendente.
        List<DocumentoRecibidoItem> enOrdenInverso = new ArrayList<>(renglones);
        Collections.reverse(enOrdenInverso);
        for (DocumentoRecibidoItem renglon : enOrdenInverso) {
            RenglonFacturaMobile cruce = porId.get(renglon.getId());
            PedidoItemInput item = new PedidoItemInput();
            item.setPedidoId(pedido.getId());
            item.setProductoId(renglon.getProducto().getId());
            item.setPresentacionCreacionId(renglon.getPresentacion() != null ? renglon.getPresentacion().getId() : null);
            item.setCantidadSolicitada(cruce.getCantidadBase());
            item.setPrecioUnitarioSolicitado(factor == 1.0 || cruce.getPrecioUnitarioBase() == null
                    ? cruce.getPrecioUnitarioBase() : CruceFactura.redondear(cruce.getPrecioUnitarioBase() * factor, 4));
            item.setEsBonificacion(Boolean.TRUE.equals(cruce.getEsBonificacion()));
            item.setEstado(PedidoItemEstado.ACTIVO);
            item.setVencimientoEsperado(renglon.getVencimiento() != null ? renglon.getVencimiento().atStartOfDay().format(FECHA_HORA_SEG) : null);
            item.setCreadoEn(LocalDateTime.now().format(FECHA_HORA_SEG));
            item.setUsuarioCreacionId(usuarioId);
            double[] partes = repartoPorRenglon.get(renglon.getId());
            ArrayList<PedidoItemDistribucionInput> reparto = new ArrayList<>();
            for (int i = 0; i < sucursales.size(); i++) {
                if (partes[i] <= 0) continue;
                PedidoItemDistribucionInput d = new PedidoItemDistribucionInput();
                d.setSucursalInfluenciaId(sucursales.get(i));
                d.setSucursalEntregaId(sucursales.get(i));
                d.setCantidadAsignada(partes[i]);
                reparto.add(d);
            }
            guardadoPedidoItemGraphQL.guardarPedidoItemConDistribuciones(item, reparto);
        }
        pedidoItemService.getRepository().flush();

        // 3. La factura queda esperando la planificación: se carga como nota al finalizarla
        doc.setPedido(pedido);
        doc.setCargarAlFinalizar(true);
        documentoRepository.save(doc);
        return armarPedido(pedidoObligatorio(pedido.getId()));
    }

    /** Las sucursales de la compra: las de la lista o, si no viene, la única del parámetro viejo. Sin repetir. */
    private List<Long> sucursalesElegidas(Long sucursalId, List<Long> sucursalIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (sucursalIds != null) sucursalIds.stream().filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty() && sucursalId != null) ids.add(sucursalId);
        if (ids.isEmpty()) {
            throw new GraphQLException("Elegí la sucursal que recibe la mercadería.");
        }
        Set<Long> validas = sucursalService.findAllSucursalesEntrega().stream().map(Sucursal::getId).collect(Collectors.toSet());
        for (Long id : ids) {
            if (!validas.contains(id)) {
                throw new GraphQLException("La sucursal " + id + " no recibe mercadería.");
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * Cuánto de cada renglón va a cada sucursal, en unidades base y en el orden de {@code sucursales}. Con una sola
     * sucursal va todo a ella. Con varias, el reparto lo carga el comprador en la unidad de la factura y tiene que
     * sumar el renglón entero; se pasa a unidades base en proporción, así con cantidades enteras queda entero.
     */
    static Map<Long, double[]> repartoPorRenglon(List<DocumentoRecibidoItem> renglones,
                                                         Map<Long, RenglonFacturaMobile> porId, List<Long> sucursales,
                                                         List<RepartoRenglonMobileInput> repartos) {
        Map<Long, double[]> resultado = new HashMap<>();
        Map<Long, double[]> cargado = new HashMap<>();
        if (sucursales.size() > 1) {
            for (RepartoRenglonMobileInput r : repartos != null ? repartos : Collections.<RepartoRenglonMobileInput>emptyList()) {
                if (r == null || r.getRenglonId() == null || r.getSucursalId() == null) continue;
                int i = sucursales.indexOf(r.getSucursalId());
                if (i < 0) {
                    throw new GraphQLException("El reparto usa una sucursal que no está elegida para la compra.");
                }
                double cantidad = r.getCantidad() != null ? r.getCantidad() : 0;
                if (cantidad < 0) {
                    throw new GraphQLException("El reparto no puede tener cantidades negativas.");
                }
                cargado.computeIfAbsent(r.getRenglonId(), k -> new double[sucursales.size()])[i] += cantidad;
            }
        }
        for (DocumentoRecibidoItem renglon : renglones) {
            double base = nz(porId.get(renglon.getId()).getCantidadBase());
            double[] partes = new double[sucursales.size()];
            if (sucursales.size() == 1) {
                partes[0] = base;
            } else {
                double enFactura = nz(renglon.getCantidad());
                double[] pesos = cargado.get(renglon.getId());
                double suma = 0;
                if (pesos != null) for (double p : pesos) suma += p;
                if (pesos == null || Math.abs(suma - enFactura) > CruceFactura.TOLERANCIA_CANTIDAD) {
                    throw new GraphQLException("El renglón #" + renglon.getOrden() + " (" + texto(renglon.getDescripcion())
                            + ") tiene " + CruceFactura.cantidad(enFactura) + " en la factura y el reparto suma "
                            + CruceFactura.cantidad(suma) + ".");
                }
                partes = Reparto.proporcional(base, pesos);
            }
            resultado.put(renglon.getId(), partes);
        }
        return resultado;
    }

    /**
     * Al finalizar la planificación, carga como nota la factura que creó el pedido desde el celular. Corre después de
     * confirmar la finalización y en su propia transacción: si la factura no se puede cargar sola (por ejemplo, en el
     * desktop se cambió un ítem y ahora hay una diferencia que pide observación), la planificación queda finalizada
     * igual y el comprador la carga desde el pedido en el celular, escaneando el KuDE.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alFinalizarPlanificacion(PlanificacionFinalizadaEvent evento) {
        Long pedidoId = evento.getPedidoId();
        List<Long> pendientes = jdbc.queryForList("SELECT id FROM operaciones.documento_recibido WHERE pedido_id = ? "
                + "AND nota_recepcion_id IS NULL AND cargar_al_finalizar = true ORDER BY id", Long.class, pedidoId);
        boolean cargoAlguna = false;
        for (Long documentoId : pendientes) {
            try {
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                Boolean cargo = tx.execute(estado -> cargarFacturaPendiente(documentoId, pedidoId));
                cargoAlguna |= Boolean.TRUE.equals(cargo);
            } catch (RuntimeException e) {
                log.warn("Pedido {}: la factura {} no se pudo cargar sola al finalizar la planificación: {}",
                        pedidoId, documentoId, e.getMessage());
            }
        }
        if (!cargoAlguna) return;
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(estado -> finalizarSiNoQuedaNadaAMano(pedidoId));
        } catch (RuntimeException e) {
            log.warn("Pedido {}: la conciliación no se pudo finalizar sola: {}", pedidoId, e.getMessage());
        }
    }

    /**
     * Si la factura cargada sola dejó todo conciliado, la recepción documental se finaliza en el acto: no hay nada
     * que revisar y pedir «Finalizar conciliación» era un paso de más. Si falta algo, queda en proceso como siempre.
     * Hace lo mismo que el botón del celular ({@link #finalizar}), sin pedir el rol: ya lo pidió quien finalizó la
     * planificación.
     */
    private void finalizarSiNoQuedaNadaAMano(Long pedidoId) {
        Pedido pedido = pedidoObligatorio(pedidoId);
        ResumenFinalizarNotasMobile r = armarResumenFinalizar(pedido);
        if (!conciliacionSinPendientes(r)) {
            log.info("Pedido {}: la conciliación queda para finalizar a mano ({} nota(s) pendientes, {} ítem(s) sin "
                    + "facturar, {} sin distribuir)", pedidoId, r.getNotasPendientes(), r.getItemsSinFacturar().size(),
                    r.getItemsSinDistribuir());
            return;
        }
        pedidoService.finalizarRecepcionNotas(pedidoId);
        log.info("Pedido {}: la factura dejó todo conciliado, la recepción documental se finalizó sola", pedidoId);
    }

    /**
     * Nada para hacer a mano: se puede finalizar, todas las notas quedaron conciliadas, ningún ítem del pedido quedó
     * sin facturar y todo tiene su distribución por sucursal.
     */
    static boolean conciliacionSinPendientes(ResumenFinalizarNotasMobile r) {
        return Boolean.TRUE.equals(r.getPuedeFinalizar())
                && r.getCantidadNotas() != null && r.getCantidadNotas() > 0
                && (r.getNotasPendientes() == null || r.getNotasPendientes() == 0)
                && (r.getItemsSinFacturar() == null || r.getItemsSinFacturar().isEmpty())
                && (r.getItemsSinDistribuir() == null || r.getItemsSinDistribuir() == 0);
    }

    /** Devuelve {@code true} si cargó la nota. */
    private boolean cargarFacturaPendiente(Long documentoId, Long pedidoId) {
        DocumentoRecibido doc = documentoObligatorio(documentoId);
        Pedido pedido = pedidoObligatorio(pedidoId);
        if (doc.getNotaRecepcion() != null || !etapaAbierta(pedido)) return false;
        FacturaProveedorMobile factura = armarFactura(doc, pedido, true);
        if (!Boolean.TRUE.equals(factura.getPuedeConfirmar())) {
            log.info("Pedido {}: la factura {} queda para cargar desde el celular: {}", pedidoId, documentoId,
                    factura.getMotivoNoConfirma());
            return false;
        }
        cargarNota(doc, pedido);
        return true;
    }

    /**
     * Una factura que creó una compra (todavía en planificación) no se usa para otra: se carga sola en esa al
     * finalizarla. Si esa compra se canceló, la factura queda libre.
     */
    void exigirQueNoEspereOtroPedido(DocumentoRecibido doc, Long pedidoPermitido) {
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

    private FacturaProveedorMobile armarSegunModo(DocumentoRecibido doc) {
        return doc.getPedido() == null ? armarBorrador(doc, true) : armarFactura(doc, doc.getPedido(), true);
    }

    /** La factura que va a crear la compra: productos reconocidos, avisos de IVA y lo que trae de forma de pago. */
    private FacturaProveedorMobile armarBorrador(DocumentoRecibido doc, boolean recalcular) {
        if (recalcular) documentoRecibidoService.completarCodigos(doc);
        List<DocumentoRecibidoItem> renglones = documentoRecibidoService.renglones(doc.getId());
        Proveedor proveedor = doc.getProveedor();
        if (recalcular) {
            for (DocumentoRecibidoItem r : renglones) {
                if (hayQueReconocer(r)) {
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
                if (aviso != null && aviso.startsWith("IVA: ")) {
                    res.avisosIva.add(aviso.substring(5));
                    ivaUltima++;
                } else if (aviso != null) {
                    // Un cambio grande de precio no es IVA: suele ser la presentación equivocada
                    res.diferencias.add(aviso);
                }
            }
            RenglonFacturaMobile rm = armarRenglon(r, res);
            if (res.estado == CruceFactura.Estado.OK) {
                rm.setEstadoEtiqueta(res.bonificacion ? "Bonificación" : "Producto reconocido");
            }
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
        alertarProductoRepetido(f.getRenglones());
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
        if (esFoto(doc)) {
            a.addAll(avisosLecturaFoto(doc, renglones));
        } else if (doc.getTotalGeneral() != null && Math.abs(totalRenglones - doc.getTotalGeneral()) > TOLERANCIA_TOTAL) {
            a.add(new AvisoFacturaMobile("TOTAL", "ADVERTENCIA", "La suma de los renglones (" + CruceFactura.importe(totalRenglones)
                    + ") no da el total de la factura (" + CruceFactura.importe(doc.getTotalGeneral()) + "): puede haber un descuento global."));
        }
        a.addAll(avisosFacturaRepetida(doc, null));
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
        c.total = r.getTotal();
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

    Proveedor proveedorPorRuc(String ruc) {
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
        boolean enPlanificacion = !planificacionTerminada(pedido.getId());
        p.setEstadoEtapa(enPlanificacion ? "EN_PLANIFICACION" : etapa != null ? etapa.getEstadoEtapa().name() : null);
        boolean abierta = !enPlanificacion && etapaAbierta(etapa);
        p.setPuedeCargarFacturas(abierta);
        if (!abierta) {
            List<String> esperando = enPlanificacion || etapa == null ? jdbc.queryForList("SELECT establecimiento || '-' || punto_expedicion "
                    + "|| '-' || numero FROM operaciones.documento_recibido WHERE pedido_id = ? AND nota_recepcion_id IS NULL "
                    + "AND cargar_al_finalizar = true ORDER BY id", String.class, pedido.getId()) : Collections.emptyList();
            p.setMotivoNoOpera(!esperando.isEmpty()
                    ? "La compra está en «Ítems del pedido». Revisala en el desktop y finalizá la planificación: la factura "
                    + String.join(", ", esperando) + " se carga sola."
                    : enPlanificacion || etapa == null
                    ? "El pedido todavía está en planificación: finalizala en el desktop para cargarle facturas."
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
        // El mismo orden que el desktop (id descendente): en una compra creada desde la factura es el orden de la
        // factura, así el comprador la sigue con el papel en la mano
        p.getItems().sort(Comparator.comparing(CompraPedidoItemMobile::getPedidoItemId).reversed());
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
        nm.setElectronica(doc != null && !esFoto(doc));
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
        if (recalcular) documentoRecibidoService.completarCodigos(doc);
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
                if (hayQueReconocer(r)) {
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
            c.total = r.getTotal();
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
        alertarProductoRepetido(f.getRenglones());
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

        avisos(f, doc, pedido, moneda, mismaMoneda, sinProd, ivaPedido, ivaProducto, sinDistribucion, totalRenglones, renglones);
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
        f.setOrigen(doc.getOrigen());
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
        f.setMonedaId(moneda != null ? moneda.getId() : null);
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
        if (r.getNotaReconocimiento() != null) rm.getAvisos().add(r.getNotaReconocimiento());
        if (r.getProducto() != null && reconocidoPorCodigo(r.getTipoMatch())
                && !Similitud.compartenNombre(r.getDescripcion(), r.getProducto().getDescripcion())) {
            rm.getAlertas().add("El código es de «" + r.getProducto().getDescripcion().trim() + "», pero la factura dice «"
                    + r.getDescripcion() + "»: revisá que sea el mismo producto.");
        }
        rm.setRequiereObservacion(res.requiereObservacion);
        rm.setObservacionFaltante(res.observacionFaltante);
        rm.setObservacion(r.getObservacion());
        return rm;
    }

    private void avisos(FacturaProveedorMobile f, DocumentoRecibido doc, Pedido pedido, Moneda moneda, boolean mismaMoneda,
                        int sinProd, int ivaPedido, int ivaProducto, int sinDistribucion, double totalRenglones,
                        List<DocumentoRecibidoItem> renglones) {
        List<AvisoFacturaMobile> a = f.getAvisos();
        if (documentoRecibidoService.cancelada(doc)) {
            a.add(new AvisoFacturaMobile("CANCELADA", "BLOQUEO",
                    "SIFEN informa que esta factura fue cancelada por el proveedor."));
        }
        if (doc.getNotaRecepcion() == null) {
            if (!planificacionTerminada(pedido.getId())) {
                a.add(new AvisoFacturaMobile("ETAPA", "BLOQUEO", "El pedido todavía está en planificación."));
            } else if (!etapaAbierta(pedido)) {
                a.add(new AvisoFacturaMobile("ETAPA", "BLOQUEO", "El pedido ya no está esperando facturas."));
            }
        }
        String rucPedido = rucDe(pedido.getProveedor());
        if (rucPedido != null && !mismoRuc(rucPedido, doc.getRucEmisor())) {
            // En papel el RUC lo leyó la IA: si era de otro proveedor, la confirmación de la cabecera ya lo rechazó
            a.add(esFoto(doc)
                    ? new AvisoFacturaMobile("PROVEEDOR", "ADVERTENCIA", "En la foto se leyó el RUC "
                        + texto(conDv(doc.getRucEmisor(), doc.getDvEmisor())) + " y el pedido es de "
                        + nombreDe(pedido.getProveedor()) + " (" + rucPedido + "): revisá con el papel que sea el mismo proveedor.")
                    : new AvisoFacturaMobile("PROVEEDOR", "BLOQUEO", "La factura es de " + texto(doc.getRazonSocialEmisor())
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
        if (esFoto(doc)) {
            a.addAll(avisosLecturaFoto(doc, renglones));
        } else if (doc.getTotalGeneral() != null && Math.abs(totalRenglones - doc.getTotalGeneral()) > TOLERANCIA_TOTAL) {
            a.add(new AvisoFacturaMobile("TOTAL", "ADVERTENCIA", "La suma de los renglones ("
                    + CruceFactura.importe(totalRenglones) + ") no da el total de la factura ("
                    + CruceFactura.importe(doc.getTotalGeneral()) + "): puede haber un descuento global."));
        }
        if (sinDistribucion > 0) {
            a.add(new AvisoFacturaMobile("DISTRIBUCION", "ADVERTENCIA", sinDistribucion
                    + " renglón(es) fuera del pedido van a quedar sin distribuir entre sucursales: terminalos en el desktop."));
        }
        a.addAll(avisosFacturaRepetida(doc, pedido.getId()));
    }

    /**
     * La factura en papel la leyó la IA: los campos de los que no estuvo segura y la suma de los renglones contra el
     * total, con la tolerancia de {@link FacturaPapelReglas#totalesCuadran}.
     */
    static List<AvisoFacturaMobile> avisosLecturaFoto(DocumentoRecibido doc, List<DocumentoRecibidoItem> renglones) {
        List<AvisoFacturaMobile> avisos = new ArrayList<>();
        List<String> dudosos = new ArrayList<>();
        if (!vacio(doc.getIaDudosos())) dudosos.add(doc.getIaDudosos().trim());
        for (DocumentoRecibidoItem r : renglones) {
            if (!vacio(r.getIaDudosos())) dudosos.add("renglón " + r.getOrden() + ": " + r.getIaDudosos().trim());
        }
        if (!dudosos.isEmpty()) {
            avisos.add(new AvisoFacturaMobile("LECTURA", "ADVERTENCIA", "La IA no leyó con seguridad "
                    + String.join("; ", dudosos) + ". Revisalo con el papel."));
        }
        List<Double> totales = totalesRenglones(renglones);
        if (doc.getTotalGeneral() != null && !FacturaPapelReglas.totalesCuadran(doc.getTotalGeneral(), totales)) {
            double suma = totales.stream().mapToDouble(Double::doubleValue).sum();
            avisos.add(new AvisoFacturaMobile("TOTAL", "ADVERTENCIA", "La suma de los renglones (" + CruceFactura.importe(suma)
                    + ") no da el total de la factura (" + CruceFactura.importe(doc.getTotalGeneral())
                    + "): revisá cantidades y precios con el papel"));
        }
        return avisos;
    }

    /** El total de cada renglón; si la IA no lo leyó, cantidad × precio (nunca cuenta como cero). */
    static List<Double> totalesRenglones(List<DocumentoRecibidoItem> renglones) {
        List<Double> totales = new ArrayList<>();
        for (DocumentoRecibidoItem r : renglones) {
            totales.add(r.getTotal() != null ? r.getTotal()
                    : nz(r.getCantidad()) * (nz(r.getPrecioUnitario()) - nz(r.getDescuentoUnitario())));
        }
        return totales;
    }

    /**
     * ¿Esta factura ya está cargada? Solo para las cargas del celular (el desktop sigue igual). El CDC ya impide
     * cargar dos veces la misma factura electrónica; esto cubre la que se cargó a mano en el desktop, comparando
     * proveedor, número y timbrado (y fecha o monto cuando la nota se cargó sin timbrado). Ver {@link FacturaRepetida}.
     *
     * @param pedidoId el pedido al que se carga, o null si la factura crea la compra
     */
    List<AvisoFacturaMobile> avisosFacturaRepetida(DocumentoRecibido doc, Long pedidoId) {
        List<AvisoFacturaMobile> avisos = new ArrayList<>();
        Integer numero = numeroNotaOrNull(doc);
        Long proveedorId = doc.getProveedor() != null ? doc.getProveedor().getId() : null;
        if (doc.getNotaRecepcion() != null || numero == null || proveedorId == null) return avisos;
        List<Map<String, Object>> notas = jdbc.queryForList(
                "SELECT nr.id, nr.pedido_id, nr.timbrado, nr.fecha, nr.valor FROM operaciones.nota_recepcion nr "
                        + "JOIN operaciones.pedido p ON p.id = nr.pedido_id "
                        + "WHERE nr.numero = ? AND p.proveedor_id = ? AND COALESCE(nr.es_nota_rechazo, false) = false",
                numero, proveedorId);
        LocalDate emision = doc.getFechaEmision() != null ? doc.getFechaEmision().toLocalDate() : null;
        for (Map<String, Object> n : notas) {
            Integer timbrado = n.get("timbrado") != null ? ((Number) n.get("timbrado")).intValue() : null;
            LocalDate fecha = n.get("fecha") != null ? ((java.sql.Timestamp) n.get("fecha")).toLocalDateTime().toLocalDate() : null;
            Double valor = n.get("valor") != null ? ((Number) n.get("valor")).doubleValue() : null;
            long pedidoNota = ((Number) n.get("pedido_id")).longValue();
            FacturaRepetida.Veredicto v = FacturaRepetida.comparar(doc.getTimbrado(), emision, doc.getTotalGeneral(),
                    timbrado, fecha, valor);
            String donde = "la nota " + numero + " del pedido " + pedidoNota;
            if (v == FacturaRepetida.Veredicto.MISMA) {
                avisos.add(new AvisoFacturaMobile("DUPLICADA", "BLOQUEO", "Esta factura ya está cargada: " + donde
                        + (timbrado != null ? " (mismo timbrado " + timbrado + ")" : " (cargada sin timbrado, con la misma fecha o monto)")
                        + ". No se puede cargar dos veces."));
            } else if (pedidoId != null && pedidoNota == pedidoId) {
                avisos.add(new AvisoFacturaMobile("DUPLICADA", "BLOQUEO", "Este pedido ya tiene una nota con el número "
                        + numero + ". Si es esta misma factura cargada a mano, eliminala primero."));
            } else if (v == FacturaRepetida.Veredicto.POSIBLE) {
                avisos.add(new AvisoFacturaMobile("DUPLICADA", "ADVERTENCIA", "El proveedor ya tiene " + donde
                        + ", cargada sin timbrado y con otra fecha y monto. Revisá que no sea esta misma factura."));
            }
        }
        return avisos;
    }

    // =================================================================================== Reconocer productos

    enum TipoMatch {
        MANUAL, APRENDIDO, APRENDIDO_DESCRIPCION, CODIGO_BARRAS, CODIGO_PAQUETE, CODIGO_INTERNO, BUSCADOR_PROVEEDOR, BUSCADOR;

        static String etiqueta(String tipo) {
            if (tipo == null) return null;
            switch (tipo) {
                case "MANUAL": return "Vinculado a mano";
                case "APRENDIDO": return "Reconocido por el código del proveedor";
                case "APRENDIDO_DESCRIPCION": return "Aprendido por descripción";
                case "CODIGO_BARRAS": return "Reconocido por el código de barras";
                case "CODIGO_PAQUETE": return "Reconocido por el código del paquete";
                case "CODIGO_INTERNO": return "Reconocido por el código interno";
                case "BUSCADOR_PROVEEDOR": return "Reconocido por el código, entre los productos del proveedor";
                case "BUSCADOR": return "Reconocido por el código (coincide en parte o sin ceros)";
                default: return tipo;
            }
        }
    }

    /**
     * Reconoce el producto del renglón como lo haría una persona con el buscador:
     * <ol>
     *   <li>lo aprendido para ese proveedor y su código;</li>
     *   <li>solo si el renglón no trae código, lo aprendido para esa descripción normalizada (igual a igual,
     *   nunca por parecido);</li>
     *   <li>el código de barras exacto, con y sin ceros a la izquierda;</li>
     *   <li>el código escrito en el buscador de productos (que encuentra un tramo del código o el código sin
     *   ceros), primero entre los productos de ese proveedor y después en todo el catálogo. Solo se toma si hay
     *   un único producto: si hay varios, van como sugerencias.</li>
     * </ol>
     * Después ajusta el bulto: «SALADIX 6*100GR» facturado por caja se lleva a la presentación de 6.
     */
    void resolverProducto(DocumentoRecibidoItem r, Proveedor proveedor, Set<Long> productosDelPedido) {
        r.setProducto(null);
        r.setPresentacion(null);
        r.setFactor(null);
        r.setTipoMatch(null);
        r.setNotaReconocimiento(null);
        // Lo aprendido que apunta a un producto o presentación inactivos no se usa: la compra cargaría stock a algo
        // que no se vende. Se sigue con los códigos, y si nada lo reconoce el renglón queda sin producto, con aviso.
        Presentacion aprendidaInactiva = null;
        if (proveedor != null && !vacio(r.getCodigoProveedor())) {
            ProveedorCodigoProducto aprendido = aprendizajeRepository
                    .findByProveedorIdAndCodigoProveedor(proveedor.getId(), r.getCodigoProveedor().trim()).orElse(null);
            if (aprendido != null && aprendido.getPresentacion() != null && aprendido.getPresentacion().getProducto() != null) {
                if (presentacionUsable(aprendido.getPresentacion())) {
                    asignar(r, aprendido.getPresentacion(), aprendido.getFactor(), TipoMatch.APRENDIDO);
                    return;
                }
                aprendidaInactiva = aprendido.getPresentacion();
            }
        }
        // Sin código (la factura de papel suele no traerlo): lo aprendido por la descripción exacta. Con código se
        // sigue como siempre, por los códigos
        boolean inactivaPorDescripcion = false;
        String descripcion = vacio(r.getCodigoProveedor()) ? DescripcionProveedor.normalizar(r.getDescripcion()) : null;
        if (proveedor != null && descripcion != null) {
            ProveedorCodigoProducto aprendido = aprendizajeRepository
                    .findByProveedorIdAndCodigoProveedorIsNullAndDescripcionNormalizada(proveedor.getId(), descripcion)
                    .orElse(null);
            if (aprendido != null && aprendido.getPresentacion() != null && aprendido.getPresentacion().getProducto() != null) {
                if (presentacionUsable(aprendido.getPresentacion())) {
                    asignar(r, aprendido.getPresentacion(), aprendido.getFactor(), TipoMatch.APRENDIDO_DESCRIPCION);
                    return;
                }
                aprendidaInactiva = aprendido.getPresentacion();
                inactivaPorDescripcion = true;
            }
        }
        resolverPorCodigos(r, proveedor, productosDelPedido);
        if (r.getProducto() == null && aprendidaInactiva != null) {
            r.setNotaReconocimiento((inactivaPorDescripcion ? "Esta descripción estaba vinculada a «" : "Este código estaba vinculado a «")
                    + aprendidaInactiva.getProducto().getDescripcion() + "», que está inactivo. Elegí el producto.");
        }
    }

    /** Lo reconoció el sistema por un código, sin que nadie lo confirmara (lo aprendido y lo manual sí). */
    static boolean reconocidoPorCodigo(String tipoMatch) {
        return tipoMatch != null && !TipoMatch.APRENDIDO.name().equals(tipoMatch)
                && !TipoMatch.APRENDIDO_DESCRIPCION.name().equals(tipoMatch) && !TipoMatch.MANUAL.name().equals(tipoMatch);
    }

    /**
     * Dos códigos distintos del proveedor cargados al mismo producto (MARVI: esponja INOX a 2.688 y ECONÓMICA a
     * 2.000, las dos a «ESPONJA DE ACERO 9 G»). Puede estar bien, pero mezcla stock y costo si son productos
     * distintos: se alerta en los dos renglones. Las bonificaciones del mismo código no cuentan.
     */
    static void alertarProductoRepetido(List<RenglonFacturaMobile> renglones) {
        Map<Long, List<RenglonFacturaMobile>> porProducto = new LinkedHashMap<>();
        for (RenglonFacturaMobile r : renglones) {
            if (r.getProductoId() == null || Boolean.TRUE.equals(r.getEsBonificacion())) continue;
            porProducto.computeIfAbsent(r.getProductoId(), k -> new ArrayList<>()).add(r);
        }
        for (List<RenglonFacturaMobile> grupo : porProducto.values()) {
            for (RenglonFacturaMobile r : grupo) {
                for (RenglonFacturaMobile otro : grupo) {
                    if (otro == r || Objects.equals(claveProveedor(otro), claveProveedor(r))) continue;
                    r.getAlertas().add("El renglón #" + otro.getOrden() + " («" + otro.getDescripcion()
                            + "») también se cargó a «" + texto(r.getProductoDescripcion()).trim()
                            + "»: revisá que sean el mismo producto.");
                }
            }
        }
    }

    private static String claveProveedor(RenglonFacturaMobile r) {
        return r.getCodigoProveedor() != null && !r.getCodigoProveedor().trim().isEmpty()
                ? r.getCodigoProveedor().trim() : texto(r.getDescripcion()).trim();
    }

    /**
     * Lo vinculado a mano se respeta, salvo que el producto se haya inactivado después: entonces se vuelve a
     * reconocer para que no entre a la compra.
     */
    private static boolean hayQueReconocer(DocumentoRecibidoItem r) {
        return !TipoMatch.MANUAL.name().equals(r.getTipoMatch()) || !presentacionUsable(r.getPresentacion());
    }

    /**
     * Si la presentación sirve para cargar una compra: el producto y la presentación están activos ({@code null}
     * cuenta como activo, como en el resto del sistema).
     */
    static boolean presentacionUsable(Presentacion p) {
        return p != null && p.getProducto() != null
                && !Boolean.FALSE.equals(p.getActivo())
                && !Boolean.FALSE.equals(p.getProducto().getActivo());
    }

    /** El código de barras exacto y después el buscador, como en {@link #resolverProducto}. */
    private void resolverPorCodigos(DocumentoRecibidoItem r, Proveedor proveedor, Set<Long> productosDelPedido) {
        List<String[]> codigos = codigosDelRenglon(r);
        for (String[] c : codigos) {
            if (porCodigo(r, c[0], TipoMatch.valueOf(c[1]), productosDelPedido)) {
                ajustarBulto(r);
                return;
            }
        }
        for (String[] c : codigos) {
            Long proveedorId = proveedor != null ? proveedor.getId() : null;
            Presentacion p = proveedorId != null ? porBuscador(c[0], proveedorId) : null;
            TipoMatch tipo = TipoMatch.BUSCADOR_PROVEEDOR;
            if (p == null) {
                p = porBuscador(c[0], null);
                tipo = TipoMatch.BUSCADOR;
            }
            if (p != null) {
                asignar(r, p, p.getCantidad(), tipo);
                ajustarBulto(r);
                return;
            }
        }
    }

    /** La presentación más grande que entra justa en el bulto (804 → la de 67, 12 veces); si no, la unidad. */
    private static Presentacion presentacionQueDivide(List<Presentacion> presentaciones, double factor) {
        Presentacion mejor = null;
        for (Presentacion p : presentaciones) {
            Double c = p.getCantidad();
            if (c == null || c <= 0 || c > factor + 0.001) continue;
            double veces = factor / c;
            if (Math.abs(veces - Math.rint(veces)) > 0.001) continue;
            if (mejor == null || c > mejor.getCantidad()) mejor = p;
        }
        return mejor;
    }

    /** Los códigos con los que se puede buscar el renglón, del más confiable al menos. */
    private static List<String[]> codigosDelRenglon(DocumentoRecibidoItem r) {
        List<String[]> codigos = new ArrayList<>();
        if (!vacio(r.getGtin())) codigos.add(new String[]{r.getGtin().trim(), TipoMatch.CODIGO_BARRAS.name()});
        if (!vacio(r.getGtinPaquete())) codigos.add(new String[]{r.getGtinPaquete().trim(), TipoMatch.CODIGO_PAQUETE.name()});
        // El código interno del proveedor solo sirve si parece un código de barras: «1007167» no es nuestro
        if (r.getCodigoProveedor() != null && r.getCodigoProveedor().trim().matches("\\d{8,14}")) {
            codigos.add(new String[]{r.getCodigoProveedor().trim(), TipoMatch.CODIGO_INTERNO.name()});
        }
        return codigos;
    }

    /**
     * El código escrito en el buscador de productos, como lo haría el comprador. Solo coincidencias de código (nunca
     * por id ni por texto), y solo si el resultado es un único producto.
     */
    private Presentacion porBuscador(String codigo, Long proveedorId) {
        List<Producto> productos = productosPorCodigo(codigo, proveedorId);
        if (productos.size() != 1) return null;
        Presentacion p = presentacionDelCodigo(productos.get(0), codigo);
        return presentacionUsable(p) ? p : null;
    }

    private List<Producto> productosPorCodigo(String codigo, Long proveedorId) {
        if (codigo == null || codigo.length() < 8) return Collections.emptyList();
        try {
            BuscadorProductoFiltros filtros = new BuscadorProductoFiltros();
            filtros.setTexto(codigo);
            filtros.setProveedorId(proveedorId);
            filtros.setActivo(true);
            filtros.setPage(0);
            filtros.setSize(5);
            List<Producto> productos = new ArrayList<>();
            for (BuscadorProductoResultado res : buscadorProductoService.buscar(filtros).getContent()) {
                TipoCoincidenciaBuscador tipo = res.getTipoCoincidencia();
                if (res.getProducto() == null || tipo == null) continue;
                if (tipo != TipoCoincidenciaBuscador.CODIGO_EXACTO && tipo != TipoCoincidenciaBuscador.CODIGO_PREFIJO
                        && tipo != TipoCoincidenciaBuscador.CODIGO_PARCIAL) continue;
                if (productos.stream().noneMatch(p -> p.getId().equals(res.getProducto().getId()))) productos.add(res.getProducto());
            }
            return productos;
        } catch (RuntimeException e) {
            log.warn("El buscador de productos falló con el código {}: {}", codigo, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** La presentación del producto que tiene ese código (comparando sin ceros, entero o un tramo). */
    private Presentacion presentacionDelCodigo(Producto producto, String codigo) {
        String buscado = sinCeros(codigo);
        for (Presentacion p : presentacionService.findByProductoIdOrderByCantidadAsc(producto.getId())) {
            for (Codigo c : codigoRepository.findByPresentacionId(p.getId())) {
                String propio = sinCeros(c.getCodigo());
                if (propio == null || propio.length() < 4) continue;
                if (propio.equals(buscado) || buscado.contains(propio) || propio.contains(buscado)) return p;
            }
        }
        return presentacionParaSugerir(producto);
    }

    private static String sinCeros(String codigo) {
        return codigo == null ? null : codigo.trim().replaceFirst("^0+(?=.)", "");
    }

    /**
     * El proveedor puede facturar por caja, pack o display aunque el código sea el de la unidad («SALADIX JAMON
     * 6*100GR» a 141.865, y la unidad se compró a 4.729: la caja trae 30). Decide el <b>precio</b>: entre las
     * presentaciones del producto, el número de la descripción y la proporción con la última compra, se toma el
     * bulto cuyo precio por unidad cierra con la última compra (±10 %). Si ninguno cierra, o no hay compra anterior,
     * no se adivina: se avisa para que el comprador elija la presentación.
     */
    private void ajustarBulto(DocumentoRecibidoItem r) {
        if (r.getProducto() == null || r.getPresentacion() == null) return;
        double precio = nz(r.getPrecioUnitario()) - nz(r.getDescuentoUnitario());
        if (precio <= 0) return; // bonificación: el precio no dice nada
        List<Presentacion> presentaciones = presentacionService.findByProductoIdOrderByCantidadAsc(r.getProducto().getId()).stream()
                .filter(p -> !Boolean.FALSE.equals(p.getActivo()) && p.getCantidad() != null && p.getCantidad() > 0)
                .collect(Collectors.toList());
        List<Double> candidatos = presentaciones.stream().map(Presentacion::getCantidad).collect(Collectors.toList());
        List<Integer> numeros = Similitud.numerosDeBulto(r.getDescripcion());
        Integer bulto = CruceFactura.productosDe(numeros).stream().max(Integer::compare).orElse(null);
        Double ultimo = ultimoPrecioCompra(r.getProducto().getId());
        double actual = r.getFactor() != null && r.getFactor() > 0 ? r.getFactor() : 1.0;
        Double factor = CruceFactura.factorPorPrecio(precio, ultimo, candidatos, numeros);
        if (factor == null) {
            if (ultimo != null && ultimo > 0) {
                r.setNotaReconocimiento("Revisá la presentación: a " + CruceFactura.importe(precio) + " por "
                        + texto(r.getUnidadMedida()) + ", ninguna da un precio por unidad parecido a la última compra ("
                        + CruceFactura.importe(ultimo) + ").");
            } else if (bulto != null && bulto > 1 && actual < 2) {
                r.setNotaReconocimiento("La descripción dice " + bulto + " por bulto y no hay compra anterior para"
                        + " comparar el precio: revisá la presentación.");
            }
            return;
        }
        if (Math.abs(factor - actual) < 0.001) return;
        double f = factor;
        Presentacion deF = presentacionQueDivide(presentaciones, f);
        if (deF != null) r.setPresentacion(deF);
        r.setFactor(f);
        double porPresentacion = deF != null && deF.getCantidad() != null && deF.getCantidad() > 0 ? f / deF.getCantidad() : f;
        r.setNotaReconocimiento("Cada " + texto(r.getUnidadMedida()) + " de la factura es un bulto de "
                + CruceFactura.cantidad(f) + " u."
                + (deF != null && deF.getCantidad() != null && deF.getCantidad() > 1
                    ? " (" + CruceFactura.cantidad(porPresentacion) + " × " + deF.getDescripcion() + ")" : "")
                + ": " + CruceFactura.importe(precio) + " ÷ " + CruceFactura.cantidad(f) + " = "
                + CruceFactura.importe(precio / f) + " por unidad, como la última compra (" + CruceFactura.importe(ultimo) + ").");
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
                if (presentacionUsable(p) && !encontradas.contains(p)) encontradas.add(p);
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

    /** Hay con qué aprender: el código del proveedor o, si no trae, la descripción. */
    private static boolean sePuedeAprender(DocumentoRecibidoItem r) {
        return !vacio(r.getCodigoProveedor()) || DescripcionProveedor.normalizar(r.getDescripcion()) != null;
    }

    /** Con código se aprende por código (como siempre); sin código, por la descripción normalizada. */
    private void aprender(Proveedor proveedor, DocumentoRecibidoItem r, Presentacion presentacion, double factor) {
        String codigo = vacio(r.getCodigoProveedor()) ? null : r.getCodigoProveedor().trim();
        String descripcion = codigo == null ? DescripcionProveedor.normalizar(r.getDescripcion()) : null;
        if (codigo == null && descripcion == null) return;
        ProveedorCodigoProducto a = (codigo != null
                ? aprendizajeRepository.findByProveedorIdAndCodigoProveedor(proveedor.getId(), codigo)
                : aprendizajeRepository.findByProveedorIdAndCodigoProveedorIsNullAndDescripcionNormalizada(
                        proveedor.getId(), descripcion))
                .orElse(null);
        if (a == null) {
            a = new ProveedorCodigoProducto();
            a.setProveedor(proveedor);
            a.setCodigoProveedor(codigo);
            a.setDescripcionNormalizada(descripcion);
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
            // El código en el buscador, cuando dio más de un producto y no se pudo elegir solo
            for (String[] c : codigosDelRenglon(r)) {
                for (Long proveedorId : proveedor != null ? Arrays.asList(proveedor.getId(), null) : Collections.<Long>singletonList(null)) {
                    for (Producto prod : productosPorCodigo(c[0], proveedorId)) {
                        if (porProducto.size() >= 5 || porProducto.containsKey(prod.getId())) continue;
                        Presentacion p = presentacionDelCodigo(prod, c[0]);
                        if (p == null) continue;
                        porProducto.put(prod.getId(), sugerencia(p, c[0], "CODIGO_PARECIDO",
                                "El código " + c[0] + " coincide en parte o sin ceros"
                                        + (proveedorId != null ? " · producto del proveedor" : "")));
                    }
                }
            }
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

    void exigirEtapaAbierta(Pedido pedido) {
        if (!planificacionTerminada(pedido.getId())) {
            throw new GraphQLException("El pedido " + pedido.getId() + " todavía está en planificación: finalizala en el "
                    + "desktop para cargarle facturas.");
        }
        ProcesoEtapa etapa = procesoEtapaService.getEtapaByPedidoAndTipo(pedido.getId(), ProcesoEtapaTipo.RECEPCION_NOTA).orElse(null);
        if (!etapaAbierta(etapa)) {
            throw new GraphQLException(etapa == null
                    ? "El pedido " + pedido.getId() + " todavía no está esperando facturas."
                    : "El pedido " + pedido.getId() + " ya no está esperando facturas (etapa de notas "
                    + etapa.getEstadoEtapa().name().toLowerCase().replace('_', ' ') + ").");
        }
    }

    private boolean etapaAbierta(Pedido pedido) {
        return planificacionTerminada(pedido.getId())
                && etapaAbierta(procesoEtapaService.getEtapaByPedidoAndTipo(pedido.getId(), ProcesoEtapaTipo.RECEPCION_NOTA).orElse(null));
    }

    /**
     * ⚠️ La etapa de notas se crea PENDIENTE junto con el pedido, así que «PENDIENTE» no alcanza para decir que espera
     * facturas: hace falta además la planificación (CREACION) terminada.
     */
    private boolean planificacionTerminada(Long pedidoId) {
        return procesoEtapaService.getEtapaByPedidoAndTipo(pedidoId, ProcesoEtapaTipo.CREACION)
                .map(e -> e.getEstadoEtapa() == ProcesoEtapaEstado.COMPLETADA || e.getEstadoEtapa() == ProcesoEtapaEstado.OMITIDA)
                .orElse(true);
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

    Set<String> rucsPropios() {
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

    static String rucDe(Proveedor p) {
        return p != null && p.getPersona() != null && !vacio(p.getPersona().getDocumento()) ? p.getPersona().getDocumento().trim() : null;
    }

    static String nombreDe(Proveedor p) {
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

    static String conDv(String ruc, String dv) {
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

    /** La etapa en la que está la compra, en palabras del comprador. */
    static String etiquetaEtapa(String etapaActual, String estadoNotas) {
        if (etapaActual == null) return "Sin etapas";
        switch (etapaActual) {
            case "CREACION": return "En planificación";
            case "RECEPCION_NOTA": return "EN_PROCESO".equals(estadoNotas) ? "Con facturas cargadas" : "Esperando factura";
            case "RECEPCION_MERCADERIA": return "Recepción de mercadería";
            case "SOLICITUD_PAGO": return "Solicitud de pago";
            default: return "Concluida";
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
