package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.service.financiero.CapturaMuestraService;
import com.franco.dev.service.financiero.FormatoTerminalPosService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.financiero.ocr.DerivadorMapa;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * El cupon de muestra con el que se configura un formato, y el mapa que sale de el.
 *
 * <p><b>Todo el ciclo vive en central.</b> El formato, su patron, su mapa y las regiones se
 * administran aca y bajan a las 24 filiales; que la foto de muestra tuviera que venir del filial
 * era un puente innecesario — y ademas imposible, porque la venta con tarjeta se bloquea cuando la
 * terminal no tiene formato, asi que antes de configurarlo no hay capturas de ese ticket.
 *
 * <p>Seguridad a mano, como todo este repo: {@code requireGestionar()} en las escrituras. Derivar
 * un mapa decide como se leen los cupones de todas las terminales de ese modelo: es tesoreria, no
 * caja.
 */
@Component
public class CapturaMuestraGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    /** Lo que el desktop necesita para dibujar el QR. */
    public static final class CapturaMuestraQr {
        private final String token, ruta, url, expiraEn;

        CapturaMuestraQr(String token, String ruta, String url, String expiraEn) {
            this.token = token; this.ruta = ruta; this.url = url; this.expiraEn = expiraEn;
        }

        public String getToken() { return token; }
        public String getRuta() { return ruta; }
        public String getUrl() { return url; }
        public String getExpiraEn() { return expiraEn; }
    }

    /** El estado de una muestra, como lo consulta el ABM. */
    public static final class EstadoMuestra {
        private final String token, estado, textoOcr, error;
        private final Integer msOcr;

        EstadoMuestra(String token, String estado, String textoOcr, String error, Integer msOcr) {
            this.token = token; this.estado = estado; this.textoOcr = textoOcr;
            this.error = error; this.msOcr = msOcr;
        }

        public String getToken() { return token; }
        public String getEstado() { return estado; }
        public String getTextoOcr() { return textoOcr; }
        public String getError() { return error; }
        public Integer getMsOcr() { return msOcr; }
    }

    /** Una region propuesta, o el motivo por el que ese campo no se pudo derivar. */
    public static final class RegionDerivada {
        private final DerivadorMapa.RegionPropuesta r;

        RegionDerivada(DerivadorMapa.RegionPropuesta r) { this.r = r; }

        public String getCampo() { return r.campo; }
        public String getEtiqueta() { return r.etiqueta; }
        public String getPosicion() { return r.posicion; }
        public String getValorLeido() { return r.valorLeido; }
        public Double getX1() { return r.x1 == null ? null : r.x1.doubleValue(); }
        public Double getY1() { return r.y1 == null ? null : r.y1.doubleValue(); }
        public Double getX2() { return r.x2 == null ? null : r.x2.doubleValue(); }
        public Double getY2() { return r.y2 == null ? null : r.y2.doubleValue(); }
        public String getSinRegion() { return r.sinRegion; }
    }

    @Autowired
    private CapturaMuestraService service;

    @Autowired
    private FormatoTerminalPosService formatos;

    @Autowired
    private TesoreriaSecurityService seg;

    /**
     * La direccion publica de central, para armar la URL del QR.
     *
     * <p>Vacio = el desktop la compone con el endpoint de central que ya esta usando, que es el
     * default correcto: el telefono deberia llegar al mismo host. Se setea solo cuando la
     * direccion por la que el desktop habla con central NO es alcanzable desde un telefono.
     */
    @Value("${frc.captura-muestra.base-url:}")
    private String baseUrl;

    public Boolean lectorDeCuponesDisponible() {
        seg.requireVer();
        return service.lectorDisponible();
    }

    public EstadoMuestra capturaMuestra(String token) {
        seg.requireVer();
        return service.porToken(token)
                .map(m -> new EstadoMuestra(m.token, m.vencida() ? "VENCIDA" : m.estado,
                        m.textoOcr, m.error, m.msOcr))
                .orElse(null);
    }

    public CapturaMuestraQr crearCapturaMuestra(Long formatoTerminalPosId) {
        seg.requireGestionar();
        // Se exige que el formato exista antes de abrir nada: derivar un mapa para un formato que
        // no esta guardado no tiene donde terminar.
        FormatoTerminalPos f = formatos.findById(formatoTerminalPosId)
                .orElseThrow(() -> new GraphQLException("No existe el formato de terminal "
                        + formatoTerminalPosId + "."));
        if (!f.esMaquina()) {
            throw new GraphQLException("\"" + f.getNombre() + "\" es un formato " + f.getTipo()
                    + ": se lee matcheando la cadena del lector, no hay imagen donde ubicar"
                    + " regiones. El mapa es solo para los formatos MAQUINA.");
        }

        CapturaMuestraService.Muestra m;
        try {
            m = service.abrir(formatoTerminalPosId);
        } catch (IllegalStateException e) {
            throw new GraphQLException(e.getMessage());
        }

        String ruta = "/public/captura-muestra/" + m.token;
        String url = baseUrl == null || baseUrl.trim().isEmpty()
                ? null
                : baseUrl.replaceAll("/+$", "") + ruta;
        return new CapturaMuestraQr(m.token, ruta, url,
                m.expiraEn.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    /**
     * Propone el mapa a partir de la muestra. <b>No guarda nada</b>: la propuesta la revisa una
     * persona y la persiste {@code guardarRegionesDerivadas}, que es quien aplica las reglas de
     * sobrescritura.
     */
    public List<RegionDerivada> derivarMapaDeMuestra(String token, Long formatoTerminalPosId) {
        seg.requireGestionar();
        FormatoTerminalPos f = formatos.findById(formatoTerminalPosId)
                .orElseThrow(() -> new GraphQLException("No existe el formato de terminal "
                        + formatoTerminalPosId + "."));

        DerivadorMapa.Resultado r = service.derivar(token, f);
        if (!r.ok()) throw new GraphQLException(r.error);

        List<RegionDerivada> out = new ArrayList<RegionDerivada>();
        for (DerivadorMapa.RegionPropuesta p : r.regiones) out.add(new RegionDerivada(p));
        return out;
    }

    /** Cierra la muestra cuando el administrador termino. Libera la memoria antes del vencimiento. */
    public Boolean cerrarCapturaMuestra(String token) {
        seg.requireGestionar();
        service.cerrar(token);
        return true;
    }
}
