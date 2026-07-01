package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.AliasProductoImport;
import com.franco.dev.domain.operaciones.AliasProveedorImport;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.personas.PersonaRepository;
import com.franco.dev.repository.personas.ProveedorRepository;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.productos.search.ProductoSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Matcher que cruza datos extraidos de una factura (FacturaIaResponse) contra el catalogo
 * interno, devolviendo proveedor y productos sugeridos con un nivel de confianza.
 *
 * Confianza:
 *   HIGH   = match exacto via RUC, barcode o alias previo.
 *   MEDIUM = match via alias por texto OCR, o primer resultado de busqueda fuzzy (Lucene).
 *   NONE   = sin sugerencia, el usuario debe seleccionar manualmente.
 */
@Service
public class FacturaImportMatcherService {

    private static final Logger log = LoggerFactory.getLogger(FacturaImportMatcherService.class);
    private static final int FUZZY_MAX_CANDIDATOS = 5;

    public enum Confianza { HIGH, MEDIUM, NONE }

    public static class MatchProveedor {
        public final Proveedor proveedor;
        public final Confianza confianza;
        public final String razon; // descripcion humana de por que se sugirio

        public MatchProveedor(Proveedor p, Confianza c, String r) {
            this.proveedor = p;
            this.confianza = c;
            this.razon = r;
        }
    }

    public static class MatchProducto {
        public final Producto producto;          // sugerido (HIGH/MEDIUM) o null (NONE)
        public final Confianza confianza;
        public final String razon;
        public final List<Producto> candidatos;  // candidatos adicionales (fuzzy)

        public MatchProducto(Producto producto, Confianza confianza, String razon, List<Producto> candidatos) {
            this.producto = producto;
            this.confianza = confianza;
            this.razon = razon;
            this.candidatos = candidatos != null ? candidatos : Collections.emptyList();
        }
    }

    @Autowired
    private ProveedorRepository proveedorRepository;

    @Autowired
    private PersonaRepository personaRepository;

    @Autowired
    private AliasImportService aliasImportService;

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private ProductoSearchService productoSearchService;

    @Autowired
    private ProductoService productoService;

    /**
     * Prioridad proveedor:
     *  1. RUC exacto en personas.persona.documento -> HIGH
     *  2. RUC en alias previos -> HIGH
     *  3. nombreOcr exacto en alias previos -> MEDIUM
     *  4. NONE
     */
    public MatchProveedor matchProveedor(String ruc, String nombreOcr) {
        // 1. RUC exacto en personas.persona
        if (ruc != null && !ruc.isEmpty()) {
            // El RUC en SIFEN viene "NNNNNNNN-D" (con digito verificador) pero en persona.documento
            // se guarda sin el DV. Probamos ambas formas (con y sin DV).
            for (String cand : rucCandidatos(ruc.trim())) {
                Persona persona = personaRepository.findByDocumento(cand);
                if (persona != null) {
                    Proveedor p = proveedorRepository.findByPersonaId(persona.getId());
                    if (p != null) {
                        log.debug("matchProveedor HIGH via RUC exacto {}", cand);
                        return new MatchProveedor(p, Confianza.HIGH, "RUC exacto en catalogo");
                    }
                }
            }

            // 2. Alias por RUC
            for (String cand : rucCandidatos(ruc.trim())) {
                List<AliasProveedorImport> aliasesRuc = aliasImportService.findProveedorByRuc(cand);
                if (aliasesRuc != null && !aliasesRuc.isEmpty()) {
                    log.debug("matchProveedor HIGH via alias RUC {}", cand);
                    return new MatchProveedor(aliasesRuc.get(0).getProveedor(), Confianza.HIGH, "Alias previo por RUC");
                }
            }
        }

        // 3. Alias por texto OCR
        if (nombreOcr != null && !nombreOcr.isEmpty()) {
            Optional<AliasProveedorImport> aliasTexto = aliasImportService.findProveedorByTextoOcr(nombreOcr.trim());
            if (aliasTexto.isPresent()) {
                log.debug("matchProveedor MEDIUM via alias texto '{}'", nombreOcr);
                return new MatchProveedor(aliasTexto.get().getProveedor(), Confianza.MEDIUM, "Alias previo por nombre");
            }
        }

        return new MatchProveedor(null, Confianza.NONE, "Sin sugerencia automatica");
    }

    /** Formas del RUC a probar: exacto (con DV) y sin el DV (parte antes del guion). Sin duplicados. */
    private List<String> rucCandidatos(String rucNormalizado) {
        String rucSinDv = rucNormalizado.contains("-")
                ? rucNormalizado.substring(0, rucNormalizado.lastIndexOf('-')) : rucNormalizado;
        return rucNormalizado.equals(rucSinDv)
                ? Collections.singletonList(rucNormalizado)
                : Arrays.asList(rucNormalizado, rucSinDv);
    }

    /**
     * Prioridad producto:
     *  1. codigoOcr matchea Codigo (barcode/EAN) -> HIGH
     *  2. codigoOcr matchea alias_producto.codigo_ocr -> HIGH
     *  3. nombreOcr + proveedorId matchea alias_producto -> MEDIUM
     *  4. busqueda fuzzy via ProductoSearchService -> MEDIUM (primer resultado), candidatos restantes
     *  5. NONE
     */
    public MatchProducto matchProducto(String codigoOcr, String nombreOcr, Long proveedorId) {
        return matchProducto(null, codigoOcr, nombreOcr, proveedorId);
    }

    /**
     * @param codigoBarras codigo de barras / GTIN (EAN) extraido de la factura (dGtin en SIFEN)
     * @param codigoOcr    codigo interno del proveedor (dCodInt en SIFEN) / codigo leido por OCR
     */
    public MatchProducto matchProducto(String codigoBarras, String codigoOcr, String nombreOcr, Long proveedorId) {
        // 1. Barcode exacto: primero el GTIN/EAN (matchea nuestro catalogo), luego el codigo interno del proveedor
        Producto porBarcode = buscarProductoPorBarcode(codigoBarras);
        if (porBarcode == null) porBarcode = buscarProductoPorBarcode(codigoOcr);
        if (porBarcode != null) {
            return new MatchProducto(porBarcode, Confianza.HIGH, "Codigo de barras exacto", null);
        }

        // 2. Alias por codigo_ocr (codigo interno del proveedor)
        if (codigoOcr != null && !codigoOcr.trim().isEmpty()) {
            List<AliasProductoImport> aliasCod = aliasImportService.findProductoByCodigoOcr(codigoOcr.trim());
            if (!aliasCod.isEmpty()) {
                log.debug("matchProducto HIGH via alias codigo {}", codigoOcr);
                return new MatchProducto(aliasCod.get(0).getProducto(),
                        Confianza.HIGH, "Alias previo por codigo", null);
            }
        }

        // 3. Alias texto + proveedor
        if (nombreOcr != null && !nombreOcr.trim().isEmpty() && proveedorId != null) {
            Optional<AliasProductoImport> aliasTexto =
                    aliasImportService.findProductoByTextoYProveedor(nombreOcr.trim(), proveedorId);
            if (aliasTexto.isPresent()) {
                log.debug("matchProducto MEDIUM via alias texto '{}' proveedor={}", nombreOcr, proveedorId);
                return new MatchProducto(aliasTexto.get().getProducto(),
                        Confianza.MEDIUM, "Alias previo del proveedor por nombre", null);
            }
        }

        // 4. Fuzzy via ProductoSearchService (Hibernate Search/Lucene)
        if (nombreOcr != null && !nombreOcr.trim().isEmpty()) {
            List<Long> ids = productoSearchService.buscarIdsPorTexto(nombreOcr.trim(), FUZZY_MAX_CANDIDATOS);
            if (ids != null && !ids.isEmpty()) {
                List<Producto> productos = new ArrayList<>();
                for (Long id : ids) {
                    productoService.findById(id).ifPresent(productos::add);
                }
                if (!productos.isEmpty()) {
                    Producto primero = productos.get(0);
                    List<Producto> candidatos = productos.size() > 1
                            ? productos.subList(1, productos.size())
                            : Collections.emptyList();
                    log.debug("matchProducto MEDIUM via fuzzy '{}', {} candidatos extra",
                            nombreOcr, candidatos.size());
                    return new MatchProducto(primero, Confianza.MEDIUM,
                            "Coincidencia aproximada en catalogo", candidatos);
                }
            }
        }

        return new MatchProducto(null, Confianza.NONE, "Sin sugerencia automatica", null);
    }

    /** Busca un producto por codigo de barras exacto. Devuelve null si el codigo es vacio o no matchea. */
    private Producto buscarProductoPorBarcode(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) return null;
        List<Codigo> codigos = codigoService.findByCodigo(codigo.trim());
        if (codigos != null && !codigos.isEmpty()) {
            Codigo c = codigos.get(0);
            if (c.getPresentacion() != null && c.getPresentacion().getProducto() != null) {
                log.debug("matchProducto HIGH via barcode {}", codigo);
                return c.getPresentacion().getProducto();
            }
        }
        return null;
    }
}
