package com.franco.dev.service.operaciones.compras;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.graphql.operaciones.compras.DatoDePersonaMobile;
import com.franco.dev.graphql.operaciones.compras.ProveedorDeFacturaMobile;
import com.franco.dev.graphql.operaciones.compras.RegistrarProveedorFacturaInput;
import com.franco.dev.repository.general.CiudadRepository;
import com.franco.dev.repository.operaciones.DocumentoRecibidoRepository;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.ProveedorService;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dar de alta al proveedor con los datos de su factura, cuando el RUC no es de ningún proveedor cargado.
 *
 * <ul>
 *   <li><b>Factura electrónica (QR / CDC):</b> los datos salen del XML de SIFEN guardado en el documento.</li>
 *   <li><b>Factura en papel:</b> de lo que leyó la IA (RUC, nombre y, si estaban impresos, dirección, teléfono y
 *   email). Si el DV del RUC no cierra, no se da de alta: primero hay que corregir el RUC.</li>
 * </ul>
 *
 * El alta es <b>idempotente por RUC</b>: si ya es proveedor se devuelve ese; si ya existe una persona con ese
 * documento (un cliente, un funcionario) se la reutiliza, porque {@code persona.documento} es único. La persona se
 * replica sola a las filiales (MAIN_TO_ALL); el proveedor es solo del central.
 */
@Service
public class ProveedorDesdeFacturaService {

    private static final Logger log = LoggerFactory.getLogger(ProveedorDesdeFacturaService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Lo mismo que propone el alta del desktop. */
    static final int CHEQUE_DIAS_POR_DEFECTO = 8;

    @Autowired private ComprasSecurityService seguridad;
    @Autowired private ComprasMobileService compras;
    @Autowired private DocumentoRecibidoService documentoRecibidoService;
    @Autowired private DocumentoRecibidoRepository documentos;
    @Autowired private PersonaService personaService;
    @Autowired private ProveedorService proveedorService;
    @Autowired private CiudadRepository ciudadRepository;
    @Autowired private JdbcTemplate jdbc;

    /**
     * Los datos del emisor de la factura, para precargar el alta. Con {@code qr} trae la factura de SIFEN (o de la
     * caché); con {@code documentoId} usa la ya guardada (por ejemplo, la factura en papel).
     */
    @Transactional
    public ProveedorDeFacturaMobile datos(String qr, Long documentoId) {
        seguridad.exigirCompras();
        return armar(documento(qr, documentoId));
    }

    /** Crea la persona (o reutiliza la existente) y el proveedor, en una sola transacción. */
    @Transactional
    public ProveedorDeFacturaMobile registrar(RegistrarProveedorFacturaInput input) {
        seguridad.exigirCompras();
        if (input == null || input.getDocumentoId() == null) {
            throw new GraphQLException("Falta la factura de la que sale el proveedor.");
        }
        DocumentoRecibido doc = documento(null, input.getDocumentoId());
        ProveedorDeFacturaMobile datos = armar(doc);
        if (datos.getProveedorId() != null) {
            vincular(doc, datos.getProveedorId());
            return datos;
        }
        if (!Boolean.TRUE.equals(datos.getPuedeRegistrar())) {
            throw new GraphQLException(datos.getMotivo());
        }
        String nombre = primero(input.getNombre(), datos.getNombre());
        if (vacio(nombre)) {
            throw new GraphQLException("Escribí la razón social del proveedor.");
        }

        int chequeDias = input.getChequeDias() != null ? input.getChequeDias() : CHEQUE_DIAS_POR_DEFECTO;
        if (chequeDias < 1 || chequeDias > 365) {
            throw new GraphQLException("Los días de cheque van de 1 a 365.");
        }

        // Dos altas del mismo RUC a la vez (dos toques, dos teléfonos): la segunda espera y encuentra la primera.
        jdbc.queryForList("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(?))) l", "proveedor-ruc:" + datos.getRuc());
        Proveedor existente = compras.proveedorPorRuc(datos.getRuc());
        if (existente != null) {
            vincular(doc, existente.getId());
            return armar(doc);
        }

        Persona persona = datos.getPersonaId() != null ? personaService.findById(datos.getPersonaId()).orElse(null) : null;
        if (persona == null) {
            persona = new Persona();
            persona.setDocumento(datos.getRuc());
            persona.setNombre(nombre.trim());
            persona.setUsuario(seguridad.usuarioActual());
        }
        // A una persona existente solo se le completa lo que no tiene: no se pisan los datos que ya cargaron.
        if (vacio(persona.getApodo())) persona.setApodo(limpio(primero(input.getNombreFantasia(), datos.getNombreFantasia())));
        if (vacio(persona.getDireccion())) persona.setDireccion(limpio(primero(input.getDireccion(), datos.getDireccion())));
        if (vacio(persona.getTelefono())) persona.setTelefono(limpio(primero(input.getTelefono(), datos.getTelefono())));
        if (vacio(persona.getEmail())) persona.setEmail(limpio(primero(input.getEmail(), datos.getEmail())));
        if (persona.getCiudad() == null) persona.setCiudad(ciudad(datos.getCiudad()));
        if (datos.getPersonaId() != null) actualizarPersona(persona, input);
        persona = personaService.save(persona);

        Proveedor proveedor = new Proveedor();
        proveedor.setPersona(persona);
        proveedor.setCredito(input.getCredito() != null ? input.getCredito() : Boolean.TRUE.equals(datos.getCredito()));
        proveedor.setChequeDias(chequeDias);
        proveedor.setUsuario(seguridad.usuarioActual());
        proveedor.setCreadoEn(LocalDateTime.now());
        proveedor = proveedorService.save(proveedor);
        log.info("Proveedor {} ({}) dado de alta desde la factura {}", proveedor.getId(), datos.getRuc(), doc.getId());

        vincular(doc, proveedor.getId());
        ProveedorDeFacturaMobile r = armar(doc);
        r.setProveedorId(proveedor.getId());
        r.setProveedorNombre(persona.getNombre());
        r.setPuedeRegistrar(false);
        return r;
    }

    // =================================================================================== Interno

    /** Lo que el comprador pidió actualizar de la persona existente, con lo que mandó (vacío no borra). */
    private static void actualizarPersona(Persona persona, RegistrarProveedorFacturaInput input) {
        List<String> campos = input.getActualizarPersona();
        if (campos == null) return;
        for (String campo : campos) {
            switch (campo == null ? "" : campo) {
                case "nombre":
                    if (!vacio(input.getNombre())) persona.setNombre(input.getNombre().trim());
                    break;
                case "nombreFantasia":
                    if (!vacio(input.getNombreFantasia())) persona.setApodo(input.getNombreFantasia().trim());
                    break;
                case "direccion":
                    if (!vacio(input.getDireccion())) persona.setDireccion(input.getDireccion().trim());
                    break;
                case "telefono":
                    if (!vacio(input.getTelefono())) persona.setTelefono(input.getTelefono().trim());
                    break;
                case "email":
                    if (!vacio(input.getEmail())) persona.setEmail(input.getEmail().trim());
                    break;
                default:
                    throw new GraphQLException("No se puede actualizar el dato «" + campo + "» de la persona.");
            }
        }
    }

    private DocumentoRecibido documento(String qr, Long documentoId) {
        if (documentoId == null && (qr == null || qr.trim().isEmpty())) {
            throw new GraphQLException("Falta la factura de la que sale el proveedor.");
        }
        if (documentoId != null) {
            return documentos.findById(documentoId)
                    .orElseThrow(() -> new GraphQLException("No se encontró la factura " + documentoId + "."));
        }
        KudeQr kude = KudeQr.leer(qr);
        if (!kude.isOk()) {
            throw new GraphQLException(kude.getMensaje());
        }
        return documentoRecibidoService.obtener(kude.getCdc());
    }

    ProveedorDeFacturaMobile armar(DocumentoRecibido doc) {
        ProveedorDeFacturaMobile d = new ProveedorDeFacturaMobile();
        d.setDocumentoId(doc.getId());
        d.setOrigen(doc.getOrigen());
        d.setRuc(ComprasMobileService.rucBase(doc.getRucEmisor()));
        d.setDv(texto(doc.getDvEmisor()));
        d.setNombre(texto(doc.getRazonSocialEmisor()));
        d.setCredito(doc.getCondicionOperacion() != null && doc.getCondicionOperacion() == 2);

        if (doc.getXml() != null) {
            completarDeSifen(d, doc.getXml());
            // El CDC trae el DV del emisor y SIFEN ya validó la factura.
            d.setRucValido(d.getRuc() != null);
        } else {
            completarDeIa(d, doc.getIaRespuesta());
            d.setRucValido(d.getRuc() != null && d.getDv() != null
                    && FacturaPapelReglas.rucValido(d.getRuc() + "-" + d.getDv()));
        }

        if (d.getRuc() != null) {
            Proveedor proveedor = compras.proveedorPorRuc(d.getRuc());
            if (proveedor != null) {
                d.setProveedorId(proveedor.getId());
                d.setProveedorNombre(ComprasMobileService.nombreDe(proveedor));
            } else {
                Map<String, Object> persona = personaPorRuc(d.getRuc());
                if (persona != null) {
                    d.setPersonaId(((Number) persona.get("id")).longValue());
                    d.setPersonaNombre((String) persona.get("nombre"));
                    usarDatosDePersona(d, persona);
                }
            }
        }

        if (d.getProveedorId() != null) {
            d.setPuedeRegistrar(false);
            d.setMotivo("El RUC " + rucConDv(d) + " ya es del proveedor " + d.getProveedorNombre() + ".");
        } else if (d.getRuc() == null) {
            d.setPuedeRegistrar(false);
            d.setMotivo("No se leyó el RUC del proveedor en la factura.");
        } else if (!Boolean.TRUE.equals(d.getRucValido())) {
            d.setPuedeRegistrar(false);
            d.setMotivo("El RUC leído (" + rucConDv(d) + ") no cierra con su dígito verificador: revisá el papel antes de darlo de alta.");
        } else {
            d.setPuedeRegistrar(true);
        }
        return d;
    }

    private static void completarDeSifen(ProveedorDeFacturaMobile d, String xml) {
        FacturaSifenXml.Cabecera c;
        try {
            c = FacturaSifenXml.leer(xml);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (d.getNombre() == null) d.setNombre(texto(c.razonSocialEmisor));
        if (d.getDv() == null) d.setDv(texto(c.dvEmisor));
        d.setNombreFantasia(texto(c.fantasiaEmisor));
        d.setDireccion(texto(c.direccionEmisor));
        d.setCiudad(texto(c.ciudadEmisor));
        d.setTelefono(texto(c.telefonoEmisor));
        d.setEmail(texto(c.emailEmisor));
    }

    /**
     * Lo que la IA leyó del membrete. {@code ia_respuesta} es el JSON de una página o, si eran varias, un arreglo
     * con uno por página: se toma el primer valor que aparezca.
     */
    static void completarDeIa(ProveedorDeFacturaMobile d, String respuesta) {
        if (vacio(respuesta)) return;
        JsonNode raiz;
        try {
            raiz = MAPPER.readTree(respuesta);
        } catch (Exception e) {
            return;
        }
        Iterable<JsonNode> paginas = raiz.isArray() ? raiz : List.of(raiz);
        for (JsonNode p : paginas) {
            if (d.getDireccion() == null) d.setDireccion(texto(p.path("emisorDireccion").asText(null)));
            if (d.getTelefono() == null) d.setTelefono(texto(p.path("emisorTelefono").asText(null)));
            if (d.getEmail() == null) d.setEmail(texto(p.path("emisorEmail").asText(null)));
        }
    }

    /**
     * Con una persona que ya existe, el alta no pisa lo que ya tiene cargado: se muestra eso (y no lo de la
     * factura) y se avisa qué campos son suyos para que no se editen. Lo que le falta queda con lo de la factura.
     */
    static void usarDatosDePersona(ProveedorDeFacturaMobile d, Map<String, Object> persona) {
        List<DatoDePersonaMobile> suyos = new ArrayList<>();
        String nombre = suyo(suyos, "nombre", persona.get("nombre"), d.getNombre());
        if (nombre != null) d.setNombre(nombre);
        String apodo = suyo(suyos, "nombreFantasia", persona.get("apodo"), d.getNombreFantasia());
        if (apodo != null) d.setNombreFantasia(apodo);
        String direccion = suyo(suyos, "direccion", persona.get("direccion"), d.getDireccion());
        if (direccion != null) d.setDireccion(direccion);
        String telefono = suyo(suyos, "telefono", persona.get("telefono"), d.getTelefono());
        if (telefono != null) d.setTelefono(telefono);
        String email = suyo(suyos, "email", persona.get("email"), d.getEmail());
        if (email != null) d.setEmail(email);
        d.setDatosDePersona(suyos);
    }

    /** Anota el dato que la persona ya tiene (con el de la factura si es otro) y lo devuelve; null si no tiene. */
    private static String suyo(List<DatoDePersonaMobile> suyos, String campo, Object valor, String deFactura) {
        String actual = texto((String) valor);
        if (actual == null) return null;
        suyos.add(new DatoDePersonaMobile(campo, actual, mismoDato(campo, actual, deFactura) ? null : deFactura));
        return actual;
    }

    /** Mismo dato escrito distinto: sin importar mayúsculas ni espacios; el teléfono, solo por sus dígitos. */
    static boolean mismoDato(String campo, String a, String b) {
        if (b == null) return true;
        if ("telefono".equals(campo)) {
            String da = a.replaceAll("\\D", ""), db = b.replaceAll("\\D", "");
            return da.equals(db) || da.endsWith(db.replaceFirst("^0", "")) || db.endsWith(da.replaceFirst("^0", ""));
        }
        return a.trim().replaceAll("\\s+", " ").equalsIgnoreCase(b.trim().replaceAll("\\s+", " "));
    }

    /** La persona con ese documento, comparando sin DV, puntos, espacios ni ceros a la izquierda. */
    private Map<String, Object> personaPorRuc(String ruc) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT id, nombre, apodo, direccion, telefono, email FROM personas.persona WHERE documento IS NOT NULL AND "
                        + "regexp_replace(split_part(upper(replace(replace(documento, '.', ''), ' ', '')), '-', 1), '^0+(?=.)', '') = ? "
                        + "ORDER BY id LIMIT 1", ruc);
        return filas.isEmpty() ? null : filas.get(0);
    }

    /** La factura en papel sin pedido queda con el proveedor recién creado (la cabecera todavía no se confirmó). */
    private void vincular(DocumentoRecibido doc, Long proveedorId) {
        if (doc.getProveedor() == null && doc.getPedido() == null && doc.getNotaRecepcion() == null) {
            proveedorService.findById(proveedorId).ifPresent(p -> {
                doc.setProveedor(p);
                documentos.save(doc);
            });
        }
    }

    private Ciudad ciudad(String descripcion) {
        if (vacio(descripcion)) return null;
        List<Ciudad> ciudades = ciudadRepository.findByDescripcion(descripcion.trim().toUpperCase());
        if (ciudades == null || ciudades.isEmpty()) {
            ciudades = ciudadRepository.findByDescripcion(descripcion.trim());
        }
        return ciudades == null || ciudades.isEmpty() ? null : ciudades.get(0);
    }

    private static String rucConDv(ProveedorDeFacturaMobile d) {
        return d.getRuc() + (d.getDv() != null ? "-" + d.getDv() : "");
    }

    private static String primero(String a, String b) {
        return !vacio(a) ? a : b;
    }

    private static String limpio(String s) {
        return vacio(s) ? null : s.trim();
    }

    private static String texto(String s) {
        return vacio(s) || "null".equalsIgnoreCase(s.trim()) ? null : s.trim();
    }

    private static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }
}
