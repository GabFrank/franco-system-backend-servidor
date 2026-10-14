package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Envia el KuDE (PDF) de cada factura electronica al correo que el cliente tiene guardado.
 *
 * <p><b>Por que en el central y por sondeo.</b> La factura y su documento electronico nacen en la
 * filial, que es quien los manda a SIFEN; al central llegan por replicacion logica, sin pasar por
 * ningun codigo de la aplicacion, asi que no hay evento del que colgarse. Y el KuDE solo se sabe
 * generar aca. Por eso se buscan cada tanto los DE de factura ya APROBADOS.
 *
 * <p><b>Sin correo no se envia.</b> Una factura innominada, o de un cliente sin correo, no entra
 * en la busqueda. Si mas tarde se nomina a un cliente con correo, entra sola mientras este dentro
 * de la ventana. Un correo guardado que no es una direccion ("WW", ",") queda OMITIDO.
 *
 * <p>Cada factura se envia sola una unica vez: {@code financiero.factura_legal_correo} (V240.1).
 *
 * <p>El envio manual ({@link #enviarManual}) es aparte: lo pide un usuario para una factura
 * puntual, a la direccion que escriba, las veces que haga falta, y tambien sirve para facturas
 * no electronicas.
 */
@Service
public class FacturaCorreoService {

    private static final Logger log = LoggerFactory.getLogger(FacturaCorreoService.class);

    static final String ENVIADO = "ENVIADO";
    static final String ERROR = "ERROR";
    static final String OMITIDO = "OMITIDO";

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Entra en el indice de creado_en: la tabla de DE tiene cientos de miles de filas. */
    private static final String SQL_PENDIENTES =
            "SELECT f.id, f.sucursal_id "
            + "FROM financiero.documento_electronico d "
            + "JOIN financiero.factura_legal f ON f.id = d.factura_legal_id AND f.sucursal_id = d.sucursal_id "
            + "JOIN personas.cliente c ON c.id = f.cliente_id "
            + "JOIN personas.persona p ON p.id = c.persona_id "
            + "LEFT JOIN financiero.factura_legal_correo e "
            + "  ON e.factura_legal_id = f.id AND e.sucursal_id = f.sucursal_id "
            + "WHERE d.creado_en >= ? AND d.estado = 'APROBADO' AND d.tipo_documento = 'FACTURA' "
            + "  AND trim(coalesce(p.email, '')) <> '' "
            + "  AND (e.id IS NULL OR (e.estado = 'ERROR' AND e.intentos < ? "
            + "       AND e.ultimo_intento_en < now() - make_interval(mins => e.intentos * ?))) "
            + "ORDER BY d.creado_en LIMIT ?";

    private static final String SQL_REGISTRAR =
            "INSERT INTO financiero.factura_legal_correo "
            + "(factura_legal_id, sucursal_id, email, estado, ultimo_error, enviado_en) "
            + "VALUES (?, ?, ?, ?, ?, CASE WHEN ? = 'ENVIADO' THEN now() END) "
            + "ON CONFLICT (factura_legal_id, sucursal_id) DO UPDATE SET "
            + "email = EXCLUDED.email, estado = EXCLUDED.estado, ultimo_error = EXCLUDED.ultimo_error, "
            + "enviado_en = EXCLUDED.enviado_en, ultimo_intento_en = now(), "
            + "intentos = financiero.factura_legal_correo.intentos + 1";

    private final JdbcTemplate jdbc;
    private final FacturaLegalService facturaLegalService;
    private final DocumentoElectronicoService documentoElectronicoService;
    private final SucursalService sucursalService;
    private final FacturaLegalGraphQL facturaLegalGraphQL;
    private final ObjectProvider<JavaMailSender> mailSender;
    /** El hilo del scheduler no tiene sesion abierta: las relaciones lazy de la factura la necesitan. */
    private final TransactionTemplate lectura;

    @Value("${spring.mail.host:}")
    private String mailHost;
    @Value("${spring.mail.username:}")
    private String mailUsername;
    @Value("${factura.correo.remitente:}")
    private String remitente;
    @Value("${factura.correo.ventana-horas:48}")
    private int ventanaHoras;
    @Value("${factura.correo.max-intentos:6}")
    private int maxIntentos;
    @Value("${factura.correo.reintento-minutos:10}")
    private int reintentoMinutos;
    @Value("${factura.correo.lote:20}")
    private int lote;

    public FacturaCorreoService(JdbcTemplate jdbc,
                                FacturaLegalService facturaLegalService,
                                DocumentoElectronicoService documentoElectronicoService,
                                SucursalService sucursalService,
                                FacturaLegalGraphQL facturaLegalGraphQL,
                                ObjectProvider<JavaMailSender> mailSender,
                                PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.facturaLegalService = facturaLegalService;
        this.documentoElectronicoService = documentoElectronicoService;
        this.sucursalService = sucursalService;
        this.facturaLegalGraphQL = facturaLegalGraphQL;
        this.mailSender = mailSender;
        this.lectura = new TransactionTemplate(transactionManager);
        this.lectura.setReadOnly(true);
    }

    /** @return cuantas facturas se enviaron en esta vuelta. */
    public int enviarPendientes() {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || esVacio(mailHost) || esVacio(direccionRemitente())) {
            log.warn("Correo de facturas: falta configurar spring.mail.host y el remitente; no se envia nada");
            return 0;
        }
        Timestamp desde = Timestamp.valueOf(LocalDateTime.now().minusHours(ventanaHoras));
        List<long[]> pendientes = jdbc.query(SQL_PENDIENTES,
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)},
                desde, maxIntentos, reintentoMinutos, lote);
        int enviadas = 0;
        for (long[] pendiente : pendientes) {
            if (enviar(sender, pendiente[0], pendiente[1])) {
                enviadas++;
            }
        }
        if (!pendientes.isEmpty()) {
            log.info("Correo de facturas: {} enviadas de {} pendientes", enviadas, pendientes.size());
        }
        return enviadas;
    }

    private boolean enviar(JavaMailSender sender, long facturaId, long sucursalId) {
        String email = null;
        try {
            Correo correo = lectura.execute(status -> armar(facturaId, sucursalId, null));
            email = correo.email;
            if (!EMAIL.matcher(email).matches()) {
                registrar(facturaId, sucursalId, email, OMITIDO, "El correo guardado no es una direccion valida");
                return false;
            }
            sender.send(mensaje(sender, correo));
            registrar(facturaId, sucursalId, email, ENVIADO, null);
            return true;
        } catch (NoEnviableException e) {
            // Dejo de calificar entre la busqueda y el envio (se quito el correo, se cancelo el DE).
            return false;
        } catch (Exception e) {
            log.error("Correo de facturas: fallo la factura {} de la sucursal {}: {}",
                    facturaId, sucursalId, e.getMessage());
            registrar(facturaId, sucursalId, email, ERROR, e.getMessage());
            return false;
        }
    }

    /**
     * Envia ahora la factura a {@code email}, a pedido de un usuario. No mira si ya se envio.
     *
     * @throws GraphQLException con el motivo, que es lo que ve el usuario en el desktop.
     */
    public boolean enviarManual(long facturaId, long sucursalId, String email) {
        String destino = email == null ? "" : email.trim().toLowerCase();
        if (!EMAIL.matcher(destino).matches()) {
            throw new GraphQLException("El correo no es una dirección válida");
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || esVacio(mailHost) || esVacio(direccionRemitente())) {
            throw new GraphQLException("El servidor de correo no está configurado");
        }
        Correo correo;
        try {
            correo = lectura.execute(status -> armar(facturaId, sucursalId, destino));
        } catch (NoEnviableException e) {
            throw new GraphQLException(e.getMessage());
        }
        try {
            sender.send(mensaje(sender, correo));
        } catch (Exception e) {
            log.error("Correo de facturas: fallo el envio manual de la factura {} de la sucursal {}: {}",
                    facturaId, sucursalId, e.getMessage());
            registrar(facturaId, sucursalId, destino, ERROR, e.getMessage());
            throw new GraphQLException("No se pudo enviar el correo: " + e.getMessage());
        }
        registrar(facturaId, sucursalId, destino, ENVIADO, null);
        return true;
    }

    /**
     * Lee todo lo que lleva el correo.
     *
     * @param destino direccion elegida en el envio manual; null = la que el cliente tiene guardada.
     * @throws NoEnviableException si la factura no se puede enviar, con el motivo.
     */
    private Correo armar(long facturaId, long sucursalId, String destino) {
        FacturaLegal factura = facturaLegalService.findByIdAndSucursalId(facturaId, sucursalId);
        if (factura == null) {
            throw new NoEnviableException("Factura no encontrada");
        }
        String email = destino;
        if (email == null && factura.getCliente() != null && factura.getCliente().getPersona() != null) {
            email = factura.getCliente().getPersona().getEmail();
        }
        if (esVacio(email)) {
            throw new NoEnviableException("El cliente no tiene un correo guardado");
        }
        Timbrado timbrado = factura.getTimbradoDetalle() != null ? factura.getTimbradoDetalle().getTimbrado() : null;
        boolean electronica = timbrado != null && Boolean.TRUE.equals(timbrado.getIsElectronico());
        DocumentoElectronico de = documentoElectronicoService.findByFacturaLegalId(facturaId, sucursalId).orElse(null);
        // El envio automatico es solo de electronicas; a mano tambien sale la factura no electronica.
        if ((electronica || destino == null) && (de == null || de.getEstado() != EstadoDE.APROBADO)) {
            throw new NoEnviableException("La factura electrónica no está aprobada por SIFEN");
        }

        Correo correo = new Correo();
        correo.email = email.trim().toLowerCase();
        correo.electronica = de != null;
        correo.cliente = esVacio(factura.getNombre()) ? "cliente" : factura.getNombre().trim();
        correo.numero = numeroFormateado(factura, sucursalService.findById(sucursalId).orElse(null));
        correo.emisor = timbrado != null && !esVacio(timbrado.getRazonSocial()) ? timbrado.getRazonSocial().trim() : "";
        correo.fecha = factura.getFecha() != null ? factura.getFecha() : factura.getCreadoEn();
        correo.cdc = de != null ? de.getCdc() : null;
        correo.xml = de != null ? de.getXmlOriginal() : null;
        correo.pdf = facturaLegalGraphQL.generarPdfFacturaElectronica(facturaId, sucursalId);
        return correo;
    }

    private MimeMessage mensaje(JavaMailSender sender, Correo correo) throws Exception {
        MimeMessage mensaje = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
        if (esVacio(correo.emisor)) {
            helper.setFrom(direccionRemitente());
        } else {
            helper.setFrom(direccionRemitente(), correo.emisor);
        }
        helper.setTo(correo.email);
        String documento = correo.electronica ? "factura electrónica" : "factura";
        helper.setSubject("Factura " + (correo.electronica ? "electrónica " : "") + correo.numero
                + (esVacio(correo.emisor) ? "" : " - " + correo.emisor));

        StringBuilder texto = new StringBuilder();
        texto.append("Estimado/a ").append(correo.cliente).append(":\n\n");
        texto.append("Adjuntamos su ").append(documento).append(" N° ").append(correo.numero);
        if (!esVacio(correo.emisor)) {
            texto.append(" de ").append(correo.emisor);
        }
        if (correo.fecha != null) {
            texto.append(", emitida el ").append(correo.fecha.format(FECHA));
        }
        texto.append(".\n\n");
        if (!esVacio(correo.cdc)) {
            texto.append("Puede consultar su validez en https://ekuatia.set.gov.py/consultas/ con el CDC:\n")
                    .append(correo.cdc).append("\n\n");
        }
        texto.append("Este es un mensaje automático, por favor no lo responda.\n");
        helper.setText(texto.toString());

        String archivo = "Factura-" + correo.numero;
        helper.addAttachment(archivo + ".pdf", new ByteArrayResource(correo.pdf), "application/pdf");
        if (!esVacio(correo.xml)) {
            helper.addAttachment(archivo + ".xml",
                    new ByteArrayResource(correo.xml.getBytes(StandardCharsets.UTF_8)), "application/xml");
        }
        return mensaje;
    }

    private void registrar(long facturaId, long sucursalId, String email, String estado, String error) {
        try {
            String detalle = error != null && error.length() > 500 ? error.substring(0, 500) : error;
            jdbc.update(SQL_REGISTRAR, facturaId, sucursalId, email, estado, detalle, estado);
        } catch (Exception e) {
            log.error("Correo de facturas: no se pudo registrar el estado {} de la factura {} (sucursal {}): {}",
                    estado, facturaId, sucursalId, e.getMessage());
        }
    }

    /** {establecimiento}-{punto de expedicion}-{numero}, igual que en el KuDE. */
    private static String numeroFormateado(FacturaLegal factura, Sucursal sucursal) {
        String establecimiento = sucursal != null && sucursal.getCodigoEstablecimientoFactura() != null
                ? sucursal.getCodigoEstablecimientoFactura() : "";
        String punto = factura.getTimbradoDetalle() != null && factura.getTimbradoDetalle().getPuntoExpedicion() != null
                ? factura.getTimbradoDetalle().getPuntoExpedicion() : "";
        String numero = factura.getNumeroFactura() != null ? String.format("%07d", factura.getNumeroFactura()) : "";
        return establecimiento + "-" + punto + "-" + numero;
    }

    private String direccionRemitente() {
        return esVacio(remitente) ? mailUsername : remitente.trim();
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }

    /** La factura no se puede enviar; el mensaje dice por que. */
    private static class NoEnviableException extends RuntimeException {
        NoEnviableException(String mensaje) {
            super(mensaje);
        }
    }

    private static class Correo {
        String email;
        boolean electronica;
        String cliente;
        String numero;
        String emisor;
        LocalDateTime fecha;
        String cdc;
        String xml;
        byte[] pdf;
    }
}
