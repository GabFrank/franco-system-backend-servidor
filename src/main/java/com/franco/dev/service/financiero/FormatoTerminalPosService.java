package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.repository.financiero.FormatoTerminalPosRepository;
import com.franco.dev.repository.financiero.TerminalPosRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Administracion de los formatos de terminal POS.
 * <p>
 * Toda la seguridad de esta feature esta en {@link #save}: el patron lo escribe una persona desde
 * una pantalla y despues corre en cada PDV de la flota, asi que un patron malo no puede llegar a
 * guardarse. Un regex invalido reventaria el escaneo en la caja, y uno que no matchea su propio
 * ejemplo es un formato que nunca va a reconocer un cupon real.
 * <p>
 * <b>Diferencia con {@code FormatoQrPosService}, que es de donde sale este:</b> alla la unicidad
 * era por proveedor, con el mensaje "El proveedor ya tiene el formato X". Eso prohibe exactamente
 * lo que esta tabla existe para permitir --Bancard v5.2 y v5.5 conviviendo-- asi que aca la
 * unicidad es por (proveedor, nombre). Es un cambio de comportamiento deliberado.
 */
@Service
@AllArgsConstructor
public class FormatoTerminalPosService extends CrudService<FormatoTerminalPos, FormatoTerminalPosRepository, Long> {

    /**
     * Tope de la cadena escaneada. Es la misma cota que aplica el cliente antes de intentar el
     * match: acota el costo de un patron con backtracking patologico (Java no tiene timeout de
     * regex) y ningun cupon legitimo se acerca — el FRCP1 real mide 66 caracteres.
     */
    public static final int MAX_LONGITUD_ENTRADA = 512;

    private static final List<String> TIPOS = Arrays.asList(
            FormatoTerminalPos.TIPO_MAQUINA, FormatoTerminalPos.TIPO_WEB, FormatoTerminalPos.TIPO_API);

    private final FormatoTerminalPosRepository repository;

    /** Para no dejar desactivar un formato que tenga terminales asignadas. */
    private final TerminalPosRepository terminalPosRepository;

    @Override
    public FormatoTerminalPosRepository getRepository() {
        return repository;
    }

    public List<FormatoTerminalPos> findActivos() {
        return repository.findByActivoTrueOrderByIdAsc();
    }

    public List<FormatoTerminalPos> findTodos() {
        return repository.findAllByOrderByIdAsc();
    }

    public List<FormatoTerminalPos> findPorProveedor(Long proveedorServicioId) {
        return repository.findByProveedorServicioIdOrderByIdAsc(proveedorServicioId);
    }

    public long cuantasTerminalesUsan(Long formatoId) {
        return terminalPosRepository.countByFormatoTerminalPosId(formatoId);
    }

    @Override
    public FormatoTerminalPos save(FormatoTerminalPos entity) {
        validar(entity);
        return super.save(entity);
    }

    /**
     * Desactivar un formato NO apaga las terminales que ya lo tienen.
     * <p>
     * Con la FK directa, {@code activo = false} podria significar dos cosas y hay que elegir una:
     * o el formato deja de resolver --y esas terminales dejan de poder vender con tarjeta-- o solo
     * deja de ofrecerse para asignaciones nuevas. Se eligio lo segundo, y por eso desactivar uno en
     * uso se rechaza: si el operador de verdad quiere sacarlo de circulacion, primero tiene que
     * reasignar las terminales, que es una decision consciente y no un clic.
     */
    public boolean desactivar(Long id) {
        return repository.findById(id).map(e -> {
            long enUso = cuantasTerminalesUsan(id);
            if (enUso > 0) {
                throw new GraphQLException("No se puede desactivar \"" + e.getNombre() + "\": lo usan "
                        + enUso + " terminal(es). Reasignalas primero.");
            }
            e.setActivo(false);
            // save() y no super.save(): un formato viejo puede no pasar las validaciones de hoy
            // --por ejemplo si se endurecieron despues de cargarlo-- y eso no puede impedir
            // desactivarlo. Desactivar es justamente la salida para un formato que ya no sirve.
            return repository.save(e) != null;
        }).orElse(false);
    }

    /**
     * Las condiciones para poder guardar. Todas existen porque el error se manifestaria lejos: en
     * la caja, con el cliente esperando y el cupon ya impreso.
     */
    private void validar(FormatoTerminalPos entity) {
        if (entity.getNombre() == null || entity.getNombre().trim().isEmpty()) {
            throw new GraphQLException("El nombre es obligatorio: es lo que distingue un modelo de"
                    + " aparato de otro del mismo proveedor.");
        }
        if (entity.getTipo() == null || !TIPOS.contains(entity.getTipo())) {
            throw new GraphQLException("El tipo tiene que ser uno de: " + String.join(", ", TIPOS) + ".");
        }
        validarNombreUnicoEnElProveedor(entity);

        // API no parsea texto: los campos llegan estructurados del proveedor. Los otros dos SI,
        // porque el OCR devuelve texto igual que el QR y se matchea con el mismo patron.
        if (!entity.necesitaPatron()) {
            // API no tiene patron contra el cual verificar los grupos, pero el mapeo igual tiene
            // que ser un JSON: lo va a leer el filial para armar los campos que trae el proveedor.
            // Sin este chequeo se podia guardar cualquier cadena --verificado-- y el error
            // aparecia recien al parsearlo, lejos y sin contexto.
            validarFormaDeJson(entity.getMapeo());
            return;
        }

        if (entity.getPatron() == null || entity.getPatron().trim().isEmpty()) {
            throw new GraphQLException("El patron es obligatorio para un formato " + entity.getTipo()
                    + ": el cupon se lee matcheando texto, sea del lector o del OCR.");
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
    }

    /**
     * Unicidad por (proveedor, nombre), NO por proveedor solo.
     * <p>
     * Un proveedor puede tener tantos formatos como modelos de aparato tenga; lo que no puede es
     * tener dos con el mismo nombre, porque el nombre es lo unico que los distingue en la pantalla
     * donde se elige el formato de una terminal.
     * <p>
     * Los comodines (proveedor NULL) se comparan aparte: en SQL dos NULL no son iguales, asi que
     * una consulta por igualdad nunca los encontraria y se podrian crear dos con el mismo nombre.
     */
    /** Lo minimo que se le exige a cualquier mapeo, tenga patron o no. Devuelve el valor limpio. */
    private static String validarFormaDeJson(String mapeo) {
        if (mapeo == null || mapeo.trim().isEmpty()) {
            throw new GraphQLException("El mapeo es obligatorio.");
        }
        String m = mapeo.trim();
        if (!m.startsWith("{") || !m.endsWith("}")) {
            throw new GraphQLException("El mapeo debe ser un objeto JSON.");
        }
        return m;
    }

    private void validarNombreUnicoEnElProveedor(FormatoTerminalPos entity) {
        // Se normaliza el valor QUE SE GUARDA, no solo el que se compara. Antes solo se trimeaba
        // para buscar, asi que "X " y "X" convivian en la base --verificado, dos filas visualmente
        // identicas en el selector-- y cada una se podia asignar a terminales distintas sin que
        // nadie notara la diferencia.
        String nombre = entity.getNombre().trim();
        entity.setNombre(nombre);
        Long proveedorId = entity.getProveedorServicio() != null
                ? entity.getProveedorServicio().getId() : null;

        FormatoTerminalPos existente = proveedorId != null
                ? repository.findByProveedorServicioIdAndNombre(proveedorId, nombre)
                : repository.findByProveedorServicioIsNullAndNombre(nombre);

        if (existente != null && !existente.getId().equals(entity.getId())) {
            throw new GraphQLException(proveedorId != null
                    ? "Ese proveedor ya tiene un formato llamado \"" + nombre + "\"."
                    : "Ya existe un formato comodin llamado \"" + nombre + "\".");
        }
    }

    /**
     * El mapeo es un JSON chico y de forma fija; se valida a mano para no arrastrar una
     * dependencia de parseo al filial, que tambien lo lee.
     */
    private void validarMapeo(String mapeo, Matcher ejemploMatcheado) {
        String m = validarFormaDeJson(mapeo);
        // Cada "de":"grupo" del mapeo tiene que existir en el patron. Un grupo mal escrito dejaria
        // el campo vacio en silencio, que es peor que no guardar.
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
