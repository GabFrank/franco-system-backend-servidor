package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.domain.operaciones.DocumentoRecibidoFoto;
import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.graphql.operaciones.compras.AvisoFacturaMobile;
import com.franco.dev.graphql.operaciones.compras.CabeceraFacturaFotoInput;
import com.franco.dev.graphql.operaciones.compras.CabeceraFacturaFotoMobile;
import com.franco.dev.graphql.operaciones.compras.FacturaProveedorMobile;
import com.franco.dev.repository.operaciones.DocumentoRecibidoFotoRepository;
import com.franco.dev.repository.operaciones.DocumentoRecibidoItemRepository;
import com.franco.dev.repository.operaciones.DocumentoRecibidoRepository;
import com.franco.dev.service.ia.FacturaIaLeida;
import com.franco.dev.service.ia.OpenAiVisionService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.ProveedorService;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La factura en <b>papel</b> en compras móvil: el comprador le saca fotos, la IA la lee y queda como un
 * {@link DocumentoRecibido} de origen FOTO, igual que la electrónica. Hasta que el comprador confirma la cabecera
 * leída ({@code cabeceraConfirmada=false}) no se puede crear la compra ni cargar la nota.
 *
 * Confirmada la cabecera, delega en {@link ComprasMobileService} el mismo armado que el QR: de ahí en adelante el
 * flujo no se entera del origen.
 */
@Service
public class FacturaFotoService {

    private static final Logger log = LoggerFactory.getLogger(FacturaFotoService.class);

    static final int MAX_FOTOS = 5;
    static final int MAX_BYTES = 4 * 1024 * 1024;
    static final String NO_SE_PUDO_LEER = "No se pudo leer una factura en la foto. Sacala de frente, con buena luz y que entre entera.";
    private static final Pattern DATA_URI = Pattern.compile("^data:(image/(?:jpeg|jpg|png));base64,(.*)$", Pattern.DOTALL);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired private ComprasSecurityService seguridad;
    @Autowired private ComprasMobileService compras;
    @Autowired private DocumentoRecibidoService documentoRecibidoService;
    @Autowired private OpenAiVisionService lector;
    @Autowired private DocumentoRecibidoRepository documentos;
    @Autowired private DocumentoRecibidoItemRepository renglones;
    @Autowired private DocumentoRecibidoFotoRepository fotos;
    @Autowired private PedidoService pedidoService;
    @Autowired private ProveedorService proveedorService;
    @Autowired private PlatformTransactionManager transactionManager;

    // =================================================================================== Leer

    /**
     * Lee las fotos (data URI JPEG o PNG, 1 a 5, hasta 4 MB cada una) y crea el documento sin confirmar. No es
     * transaccional a propósito: la IA tarda y no se retiene una conexión a la base mientras tanto.
     *
     * @param pedidoId el pedido al que se carga, o null si la factura crea la compra
     */
    public CabeceraFacturaFotoMobile leer(List<String> fotosDataUri, Long pedidoId) {
        seguridad.exigirCompras();
        List<Foto> hojas = decodificar(fotosDataUri);
        if (pedidoId != null) {
            compras.exigirEtapaAbierta(pedidoObligatorio(pedidoId));
        }
        FacturaIaLeida leida;
        try {
            List<byte[]> bytes = new ArrayList<>();
            for (Foto f : hojas) bytes.add(f.bytes);
            leida = lector.leer(bytes);
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("no está configurada")) throw new GraphQLException(msg);
            log.warn("Factura en papel: la IA no pudo leer las fotos: {}", msg);
            throw new GraphQLException(msg.startsWith("La IA") ? msg
                    : "No se pudo leer la foto con la IA. Probá de nuevo en unos minutos.");
        }
        if (leida == null || leida.getError() != null || leida.getItems() == null || leida.getItems().isEmpty()) {
            throw new GraphQLException(NO_SE_PUDO_LEER);
        }
        return new TransactionTemplate(transactionManager).execute(estado -> crear(leida, hojas, pedidoId));
    }

    private CabeceraFacturaFotoMobile crear(FacturaIaLeida leida, List<Foto> hojas, Long pedidoId) {
        Pedido pedido = pedidoId != null ? pedidoObligatorio(pedidoId) : null;
        DocumentoRecibido doc = new DocumentoRecibido();
        doc.setOrigen(DocumentoRecibido.ORIGEN_FOTO);
        doc.setCabeceraConfirmada(false);
        doc.setTipoDocumento(1);
        doc.setCreadoEn(LocalDateTime.now());
        doc.setUsuario(seguridad.usuarioActual());
        String emisor = normalizarRuc(leida.getEmisorRuc());
        doc.setRucEmisor(rucSinDv(emisor));
        doc.setDvEmisor(dv(emisor));
        doc.setRazonSocialEmisor(cortar(leida.getEmisorNombre(), 255));
        String receptor = normalizarRuc(leida.getReceptorRuc());
        doc.setRucReceptor(rucSinDv(receptor));
        doc.setDvReceptor(dv(receptor));
        doc.setRazonSocialReceptor(cortar(leida.getReceptorNombre(), 255));
        doc.setTimbrado(entero(leida.getTimbrado()));
        doc.setEstablecimiento(FacturaPapelReglas.rellenar(leida.getEstablecimiento(), 3));
        doc.setPuntoExpedicion(FacturaPapelReglas.rellenar(leida.getPuntoExpedicion(), 3));
        doc.setNumero(FacturaPapelReglas.rellenar(leida.getNumero(), 7));
        doc.setFechaEmision(fecha(leida.getFechaEmision()));
        doc.setCondicionOperacion(condicion(leida.getCondicion()));
        doc.setMoneda(moneda(leida.getMoneda()));
        doc.setTotalGeneral(numero(leida.getTotalGeneral()));
        doc.setIaModelo(leida.getModelo());
        doc.setIaTokensEntrada(leida.getTokensEntrada());
        doc.setIaTokensSalida(leida.getTokensSalida());
        doc.setIaRespuesta(leida.getRespuestaCruda());
        doc.setIaDudosos(unir(leida.getDudosos()));

        boolean rucValido = FacturaPapelReglas.rucValido(emisor);
        Proveedor proveedor = pedido != null ? pedido.getProveedor()
                : rucValido ? compras.proveedorPorRuc(doc.getRucEmisor()) : null;
        doc.setProveedor(proveedor);
        doc.setPedido(pedido);
        documentoRecibidoService.exigirFacturaLibre(doc.getRucEmisor(), doc.getTipoDocumento(), doc.getTimbrado(),
                doc.getEstablecimiento(), doc.getPuntoExpedicion(), doc.getNumero(), null, true);
        doc = documentos.save(doc);

        List<DocumentoRecibidoItem> items = new ArrayList<>();
        int orden = 1;
        for (FacturaIaLeida.Item it : leida.getItems()) {
            if (it == null) continue;
            items.add(renglones.save(renglon(doc, it, orden++)));
        }
        orden = 1;
        for (Foto hoja : hojas) {
            DocumentoRecibidoFoto f = new DocumentoRecibidoFoto();
            f.setDocumentoRecibido(doc);
            f.setOrden(orden++);
            f.setMime(hoja.mime);
            f.setImagen(hoja.bytes);
            f.setCreadoEn(LocalDateTime.now());
            fotos.save(f);
        }
        return cabecera(doc, items, hojas.size(), emisor, rucValido, pedido,
                leida.getDudosos() != null ? leida.getDudosos() : new ArrayList<>());
    }

    private static DocumentoRecibidoItem renglon(DocumentoRecibido doc, FacturaIaLeida.Item it, int orden) {
        DocumentoRecibidoItem r = new DocumentoRecibidoItem();
        r.setDocumentoRecibido(doc);
        r.setOrden(orden);
        r.setCodigoProveedor(cortar(it.getCodigoProveedor(), 60));
        String barras = it.getCodigoBarras() != null ? it.getCodigoBarras().trim() : null;
        r.setGtin(barras != null && !barras.isEmpty() && barras.length() <= 20 ? barras : null);
        r.setDescripcion(cortar(it.getDescripcion(), 500));
        r.setUnidadMedida(cortar(it.getUnidadMedida(), 20));
        Double cantidad = numero(it.getCantidad());
        Double precio = numero(it.getPrecioUnitario());
        Double total = numero(it.getTotalItem());
        r.setCantidad(cantidad);
        // Con IVA, como lo imprime la factura (igual que la electrónica)
        r.setPrecioUnitario(precio);
        // El descuento se deduce del total: la IA no dice si es por unidad o por renglón
        Double descuento = numero(it.getDescuento());
        if (descuento != null && descuento > 0 && total != null && cantidad != null && cantidad > 0 && precio != null) {
            double porUnidad = precio - total / cantidad;
            if (porUnidad > 0.005) r.setDescuentoUnitario(CruceFactura.redondear(porUnidad, 4));
        }
        r.setTotal(total != null ? total
                : cantidad != null && precio != null ? CruceFactura.redondear(cantidad * precio, 4) : null);
        Integer tasa = it.getTasaIva() != null ? it.getTasaIva() : 0;
        r.setTasaIva(tasa);
        r.setAfectacionIva(tasa > 0 ? 1 : 3);
        r.setIaDudosos(unir(it.getDudosos()));
        return r;
    }

    private CabeceraFacturaFotoMobile cabecera(DocumentoRecibido doc, List<DocumentoRecibidoItem> items, int cantidadFotos,
                                               String emisor, boolean rucValido, Pedido pedido, List<String> dudosos) {
        CabeceraFacturaFotoMobile c = new CabeceraFacturaFotoMobile();
        c.setDocumentoId(doc.getId());
        c.setPedidoId(pedido != null ? pedido.getId() : null);
        c.setCantidadFotos(cantidadFotos);
        c.setEmisorRuc(emisor);
        c.setEmisorNombre(doc.getRazonSocialEmisor());
        c.setRucValido(rucValido);
        if (doc.getProveedor() != null) {
            c.setProveedorId(doc.getProveedor().getId());
            c.setProveedorNombre(ComprasMobileService.nombreDe(doc.getProveedor()));
        }
        String receptor = ComprasMobileService.conDv(doc.getRucReceptor(), doc.getDvReceptor());
        c.setReceptorRuc(receptor);
        Set<String> nuestros = compras.rucsPropios();
        Boolean esEmpresa = receptor == null || nuestros == null || nuestros.isEmpty() ? null
                : nuestros.stream().anyMatch(r -> ComprasMobileService.mismoRuc(r, receptor));
        c.setReceptorEsEmpresa(esEmpresa);
        c.setTimbrado(doc.getTimbrado());
        c.setEstablecimiento(doc.getEstablecimiento());
        c.setPuntoExpedicion(doc.getPuntoExpedicion());
        c.setNumero(doc.getNumero());
        c.setFechaEmision(doc.getFechaEmision() != null ? doc.getFechaEmision().format(FECHA) : null);
        c.setCondicion(doc.getCondicionOperacion() == null ? null : doc.getCondicionOperacion() == 2 ? "CREDITO" : "CONTADO");
        c.setMoneda(doc.getMoneda());
        c.setTotalGeneral(doc.getTotalGeneral());
        List<Double> totales = ComprasMobileService.totalesRenglones(items);
        double suma = totales.stream().mapToDouble(Double::doubleValue).sum();
        c.setSumaRenglones(CruceFactura.redondear(suma, 2));
        boolean cuadran = doc.getTotalGeneral() != null && FacturaPapelReglas.totalesCuadran(doc.getTotalGeneral(), totales);
        c.setTotalesCuadran(cuadran);
        c.setCantidadRenglones(items.size());
        c.setDudosos(new ArrayList<>(dudosos));

        List<String> a = c.getAvisos();
        if (emisor == null) {
            a.add("No se leyó el RUC del proveedor: elegilo.");
        } else if (!rucValido) {
            a.add("El RUC leído (" + emisor + ") no cierra con su dígito verificador: revisá el papel y elegí el proveedor.");
        } else if (pedido == null && doc.getProveedor() == null) {
            a.add("El RUC " + emisor + " no es de ningún proveedor cargado: elegí el proveedor o dalo de alta en el desktop.");
        } else if (pedido != null) {
            String rucPedido = ComprasMobileService.rucDe(pedido.getProveedor());
            if (rucPedido != null && !ComprasMobileService.mismoRuc(rucPedido, doc.getRucEmisor())) {
                Proveedor otro = compras.proveedorPorRuc(doc.getRucEmisor());
                if (otro != null) {
                    a.add("Esta factura es del RUC " + emisor + " (" + ComprasMobileService.nombreDe(otro) + ") y el pedido es de "
                            + ComprasMobileService.nombreDe(pedido.getProveedor()) + ": no se puede cargar a este pedido.");
                }
            }
        }
        if (Boolean.FALSE.equals(esEmpresa)) {
            a.add("La factura está a nombre de " + (doc.getRazonSocialReceptor() != null ? doc.getRazonSocialReceptor() : "otro cliente")
                    + " (RUC " + receptor + "), no de la empresa: revisá el papel.");
        }
        if (!FacturaPapelReglas.numeroValido(doc.getEstablecimiento(), doc.getPuntoExpedicion(), doc.getNumero())) {
            a.add("Revisá el número de la factura: no se leyó completo (001-001-0000001).");
        }
        if (!FacturaPapelReglas.timbradoValido(doc.getTimbrado())) {
            a.add("Revisá el timbrado: no se leyó con sus 8 dígitos.");
        }
        if (doc.getFechaEmision() == null) {
            a.add("Revisá la fecha de la factura: no se leyó.");
        }
        if (doc.getTotalGeneral() == null) {
            a.add("No se leyó el total de la factura: copialo del papel.");
        } else if (!cuadran) {
            a.add("La suma de los renglones (" + CruceFactura.importe(suma) + ") no da el total de la factura ("
                    + CruceFactura.importe(doc.getTotalGeneral()) + "): revisá cantidades y precios con el papel");
        }
        return c;
    }

    // =================================================================================== Confirmar

    /**
     * El comprador revisó la cabecera con el papel: se valida, se guarda y sigue como la factura electrónica (al
     * pedido en que se leyó o a crear la compra).
     */
    @Transactional
    public FacturaProveedorMobile confirmarCabecera(CabeceraFacturaFotoInput in) {
        seguridad.exigirCompras();
        if (in == null || in.getDocumentoId() == null) throw new GraphQLException("Falta la factura.");
        DocumentoRecibido doc = documentos.findById(in.getDocumentoId())
                .orElseThrow(() -> new GraphQLException("La factura ya no está. Sacale las fotos de nuevo."));
        if (!ComprasMobileService.esFoto(doc)) {
            throw new GraphQLException("Esa factura no es en papel: no tiene cabecera para confirmar.");
        }
        if (doc.getNotaRecepcion() != null) {
            throw new GraphQLException("Esta factura ya está cargada en la nota " + doc.getNotaRecepcion().getNumero() + ".");
        }
        String est = FacturaPapelReglas.rellenar(in.getEstablecimiento(), 3);
        String pto = FacturaPapelReglas.rellenar(in.getPuntoExpedicion(), 3);
        String num = FacturaPapelReglas.rellenar(in.getNumero(), 7);
        if (!FacturaPapelReglas.numeroValido(est, pto, num)) {
            throw new GraphQLException("Revisá el número de la factura: son 3, 3 y hasta 7 dígitos (001-001-0001234).");
        }
        if (!FacturaPapelReglas.timbradoValido(in.getTimbrado())) {
            throw new GraphQLException("Revisá el timbrado: son 8 dígitos.");
        }
        LocalDateTime fecha = fecha(in.getFechaEmision());
        if (fecha == null) throw new GraphQLException("Revisá la fecha de la factura.");
        if (fecha.toLocalDate().isAfter(LocalDate.now())) {
            throw new GraphQLException("La fecha de la factura no puede ser posterior a hoy.");
        }
        if (in.getTotalGeneral() == null || !(in.getTotalGeneral() > 0)) {
            throw new GraphQLException("Revisá el total de la factura.");
        }
        Proveedor proveedor = in.getProveedorId() != null ? proveedorService.findById(in.getProveedorId()).orElse(null) : null;
        if (proveedor == null) throw new GraphQLException("Elegí el proveedor de la factura.");

        Pedido pedido = doc.getPedido();
        Proveedor delPedido = pedido != null ? pedido.getProveedor() : null;
        if (pedido != null) {
            compras.exigirEtapaAbierta(pedido);
            if (delPedido != null && !delPedido.getId().equals(proveedor.getId())) {
                throw new GraphQLException("La factura se carga al pedido " + pedido.getId() + ", que es de "
                        + ComprasMobileService.nombreDe(delPedido) + ": el proveedor no se puede cambiar.");
            }
        }
        Proveedor elegido = delPedido != null ? delPedido : proveedor;
        String rucLeido = ComprasMobileService.conDv(doc.getRucEmisor(), doc.getDvEmisor());
        boolean rucValido = FacturaPapelReglas.rucValido(rucLeido);
        // Un RUC confiable (DV ok) de un proveedor cargado manda: el elegido tiene que tener ese documento, como en la
        // electrónica (varios proveedores pueden compartir el documento)
        String rucElegido = ComprasMobileService.rucDe(elegido);
        if (rucValido && !ComprasMobileService.mismoRuc(rucElegido, doc.getRucEmisor())
                && compras.proveedorPorRuc(doc.getRucEmisor()) != null) {
            throw new GraphQLException("Esta factura es del RUC " + rucLeido
                    + (pedido != null ? " y el pedido es de " : " y el proveedor elegido es ")
                    + ComprasMobileService.nombreDe(elegido) + (rucElegido != null ? " (" + rucElegido + ")" : "") + ".");
        }
        // Un RUC mal leído no sirve para reconocer la factura repetida: queda el del proveedor elegido
        String ruc = doc.getRucEmisor();
        String dvRuc = doc.getDvEmisor();
        String deElegido = normalizarRuc(rucElegido);
        if (!rucValido && deElegido != null) {
            ruc = rucSinDv(deElegido);
            dvRuc = dv(deElegido);
        }
        // La clave nueva se valida ANTES de tocar el documento: la consulta hace flush y el UPDATE chocaría con el índice
        documentoRecibidoService.exigirFacturaLibre(ruc, doc.getTipoDocumento(), in.getTimbrado(), est, pto, num,
                doc.getId(), true);

        doc.setRucEmisor(ruc);
        doc.setDvEmisor(dvRuc);
        doc.setTimbrado(in.getTimbrado());
        doc.setEstablecimiento(est);
        doc.setPuntoExpedicion(pto);
        doc.setNumero(num);
        doc.setFechaEmision(fecha);
        Integer condicion = condicion(in.getCondicion());
        doc.setCondicionOperacion(condicion != null ? condicion : 1);
        doc.setTotalGeneral(in.getTotalGeneral());
        doc.setProveedor(elegido);
        // Contra las notas ya cargadas del proveedor (FacturaRepetida), como la electrónica. Va por JDBC: no hace flush
        for (AvisoFacturaMobile a : compras.avisosFacturaRepetida(doc, pedido != null ? pedido.getId() : null)) {
            if ("BLOQUEO".equals(a.getNivel())) throw new GraphQLException(a.getMensaje());
        }
        doc.setCabeceraConfirmada(true);
        documentos.save(doc);
        return pedido != null ? compras.abrirEnPedido(doc, pedido) : compras.abrirNuevaCompra(doc, proveedor);
    }

    // =================================================================================== Foto

    /** Una hoja de la factura como data URI, para mostrarla junto a la cabecera. Null si no existe. */
    @Transactional(readOnly = true)
    public String foto(Long documentoId, int orden) {
        seguridad.exigirCompras();
        return fotos.findFirstByDocumentoRecibidoIdAndOrden(documentoId, orden)
                .filter(f -> f.getImagen() != null)
                .map(f -> "data:" + f.getMime() + ";base64," + Base64.getEncoder().encodeToString(f.getImagen()))
                .orElse(null);
    }

    // =================================================================================== Ayudas

    private static final class Foto {
        final String mime;
        final byte[] bytes;

        Foto(String mime, byte[] bytes) {
            this.mime = mime;
            this.bytes = bytes;
        }
    }

    static List<Foto> decodificar(List<String> dataUris) {
        if (dataUris == null || dataUris.isEmpty()) throw new GraphQLException("Sacale al menos una foto a la factura.");
        if (dataUris.size() > MAX_FOTOS) throw new GraphQLException("Son hasta " + MAX_FOTOS + " fotos por factura.");
        List<Foto> hojas = new ArrayList<>();
        for (int i = 0; i < dataUris.size(); i++) {
            String uri = dataUris.get(i);
            Matcher m = uri != null ? DATA_URI.matcher(uri.trim()) : null;
            if (m == null || !m.matches()) {
                throw new GraphQLException("La foto " + (i + 1) + " no es una imagen JPEG o PNG.");
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(m.group(2).replaceAll("\\s", ""));
            } catch (IllegalArgumentException e) {
                throw new GraphQLException("La foto " + (i + 1) + " llegó dañada: sacala de nuevo.");
            }
            if (bytes.length == 0) throw new GraphQLException("La foto " + (i + 1) + " llegó vacía: sacala de nuevo.");
            if (bytes.length > MAX_BYTES) {
                throw new GraphQLException("La foto " + (i + 1) + " pesa más de 4 MB: sacala de nuevo.");
            }
            String mime = "image/jpg".equals(m.group(1)) ? "image/jpeg" : m.group(1);
            hojas.add(new Foto(mime, bytes));
        }
        return hojas;
    }

    private Pedido pedidoObligatorio(Long pedidoId) {
        return pedidoService.findById(pedidoId)
                .orElseThrow(() -> new GraphQLException("El pedido " + pedidoId + " no existe."));
    }

    /** Sin espacios ni puntos: «80.012.345 - 6» → «80012345-6». */
    static String normalizarRuc(String ruc) {
        if (ruc == null) return null;
        String s = ruc.trim().toUpperCase().replace(".", "").replace(" ", "");
        return s.isEmpty() ? null : s;
    }

    /** Sin DV ni ceros a la izquierda, como guarda SIFEN el dRucEm. */
    private static String rucSinDv(String ruc) {
        return cortar(FacturaSifenXml.sinCeros(FacturaPapelReglas.sinDv(ruc)), 20);
    }

    private static String dv(String ruc) {
        if (ruc == null) return null;
        int i = ruc.indexOf('-');
        if (i < 0) return null;
        String d = ruc.substring(i + 1);
        return d.isEmpty() || d.length() > 2 ? null : d;
    }

    private static Integer entero(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.matches("\\d{1,9}") ? Integer.valueOf(t) : null;
    }

    static LocalDateTime fecha(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String t = s.trim();
        try {
            return t.length() > 10 ? LocalDateTime.parse(t.substring(0, Math.min(16, t.length())), FECHA_HORA)
                    : LocalDate.parse(t, FECHA).atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 1 contado, 2 crédito (SIFEN iCondOpe). */
    private static Integer condicion(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase().replace("É", "E");
        return t.startsWith("CRED") ? Integer.valueOf(2) : t.startsWith("CONT") ? Integer.valueOf(1) : null;
    }

    /** El código ISO de la moneda; sin moneda, guaraníes. Una que el sistema no tiene la bloquea el armado. */
    private static String moneda(String s) {
        if (s == null || s.trim().isEmpty()) return "PYG";
        String t = s.trim().toUpperCase();
        if (t.equals("GS") || t.startsWith("GUARAN")) return "PYG";
        if (t.matches("[A-Z]{3}")) return t;
        return "PYG";
    }

    private static Double numero(BigDecimal b) {
        return b != null ? b.doubleValue() : null;
    }

    private static String unir(List<String> campos) {
        if (campos == null) return null;
        List<String> limpios = new ArrayList<>();
        for (String c : campos) if (c != null && !c.trim().isEmpty() && !limpios.contains(c.trim())) limpios.add(c.trim());
        return limpios.isEmpty() ? null : String.join(", ", limpios);
    }

    private static String cortar(String s, int n) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > n ? t.substring(0, n) : t;
    }
}
