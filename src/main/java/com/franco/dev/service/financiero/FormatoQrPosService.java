package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FormatoQrPos;
import com.franco.dev.repository.financiero.FormatoQrPosRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Administracion de los formatos de QR de POS.
 * <p>
 * Toda la seguridad de esta feature esta en {@link #save}: el patron lo escribe una persona
 * desde una pantalla y despues corre en cada PDV de la flota, asi que un patron malo no puede
 * llegar a guardarse. Un regex invalido reventaria el escaneo en la caja, y uno que no matchea
 * su propio ejemplo es un formato que nunca va a reconocer un cupon real.
 */
@Service
@AllArgsConstructor
public class FormatoQrPosService extends CrudService<FormatoQrPos, FormatoQrPosRepository, Long> {

    /**
     * Tope de la cadena escaneada. Es la misma cota que aplica el cliente antes de intentar el
     * match: acota el costo de un patron con backtracking patologico (Java no tiene timeout de
     * regex) y ningun cupon legitimo se acerca — el FRCP1 real mide 66 caracteres.
     */
    public static final int MAX_LONGITUD_ENTRADA = 512;

    private final FormatoQrPosRepository repository;

    @Override
    public FormatoQrPosRepository getRepository() {
        return repository;
    }

    public List<FormatoQrPos> findActivos() {
        return repository.findByActivoTrueOrderByIdAsc();
    }

    public List<FormatoQrPos> findTodos() {
        return repository.findAllByOrderByIdAsc();
    }

    @Override
    public FormatoQrPos save(FormatoQrPos entity) {
        validar(entity);
        return super.save(entity);
    }

    /**
     * Cuatro condiciones para poder guardar. Las cuatro existen porque el error se manifestaria
     * lejos: en la caja, con el cliente esperando y el cupon ya impreso.
     */
    private void validar(FormatoQrPos entity) {
        if (entity.getPatron() == null || entity.getPatron().trim().isEmpty()) {
            throw new GraphQLException("El patron es obligatorio.");
        }
        if (entity.getEjemplo() == null || entity.getEjemplo().trim().isEmpty()) {
            throw new GraphQLException(
                    "La cadena de ejemplo es obligatoria: es lo que prueba que el patron sirve.");
        }
        if (entity.getEjemplo().length() > MAX_LONGITUD_ENTRADA) {
            throw new GraphQLException("La cadena de ejemplo supera los " + MAX_LONGITUD_ENTRADA
                    + " caracteres, que es el maximo que el lector puede entregar.");
        }

        // 1) El patron compila.
        Pattern pattern;
        try {
            pattern = Pattern.compile(entity.getPatron());
        } catch (PatternSyntaxException e) {
            throw new GraphQLException("El patron no es una expresion regular valida: " + e.getDescription());
        }

        // 2) Esta anclado. Sin ^ y $ un patron laxo matchea un pedazo de cualquier cosa y el PDV
        //    daria por bueno un cupon de otro proveedor, cargando importes equivocados.
        String p = entity.getPatron().trim();
        if (!p.startsWith("^") || !p.endsWith("$")) {
            throw new GraphQLException("El patron debe estar anclado: empezar con ^ y terminar con $.");
        }

        // 3) El patron matchea su propio ejemplo.
        Matcher matcher = pattern.matcher(entity.getEjemplo());
        if (!matcher.matches()) {
            throw new GraphQLException(
                    "El patron no reconoce la cadena de ejemplo. Corregi uno de los dos antes de guardar.");
        }

        // 4) El mapeo es un JSON con forma y todos los grupos que nombra existen en el patron.
        validarMapeo(entity.getMapeo(), matcher);

        // 5) Un solo formato por proveedor (el indice unico parcial de la V217.5 lo garantiza en
        //    la base; aca se adelanta el error para que la pantalla diga algo entendible).
        if (entity.getProveedorServicio() != null && entity.getProveedorServicio().getId() != null) {
            FormatoQrPos existente = repository.findByProveedorServicioId(entity.getProveedorServicio().getId());
            if (existente != null && !existente.getId().equals(entity.getId())) {
                throw new GraphQLException("El proveedor ya tiene el formato \"" + existente.getNombre()
                        + "\". Editalo en vez de crear otro.");
            }
        }
    }

    /**
     * El mapeo es un JSON chico y de forma fija; se valida a mano para no arrastrar una
     * dependencia de parseo al filial, que tambien lo lee.
     */
    private void validarMapeo(String mapeo, Matcher ejemploMatcheado) {
        if (mapeo == null || mapeo.trim().isEmpty()) {
            throw new GraphQLException("El mapeo es obligatorio.");
        }
        String m = mapeo.trim();
        if (!m.startsWith("{") || !m.endsWith("}")) {
            throw new GraphQLException("El mapeo debe ser un objeto JSON.");
        }
        // Cada "de":"grupo" del mapeo tiene que existir en el patron. Un grupo mal escrito
        // dejaria el campo vacio en silencio, que es peor que no guardar.
        Matcher refs = Pattern.compile("\"de\"\\s*:\\s*\"([A-Za-z][A-Za-z0-9]*)\"").matcher(m);
        boolean alguno = false;
        while (refs.find()) {
            alguno = true;
            String grupo = refs.group(1);
            try {
                ejemploMatcheado.group(grupo);
            } catch (IllegalArgumentException e) {
                throw new GraphQLException("El mapeo usa el grupo \"" + grupo
                        + "\", que el patron no define. Los grupos se declaran como (?<" + grupo + ">...).");
            }
        }
        if (!alguno) {
            throw new GraphQLException("El mapeo no vincula ningun campo con un grupo del patron.");
        }
    }
}
