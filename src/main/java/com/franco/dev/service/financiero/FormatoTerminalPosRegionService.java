package com.franco.dev.service.financiero;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.domain.financiero.FormatoTerminalPosRegion;
import com.franco.dev.repository.financiero.FormatoTerminalPosRegionRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ABM del mapa de un formato.
 *
 * <p>Toda la seguridad de esta feature esta en las validaciones de {@link #save}, por el mismo
 * motivo que en {@link FormatoTerminalPosService}: lo que se guarda desde una pantalla de central
 * baja a las 24 filiales y despues corre en cada PDV. Y aca el modo de falla es peor que un patron
 * malo: <b>una region mal puesta acota el reconocimiento a un rectangulo equivocado y hace
 * DESAPARECER un campo que hoy se lee bien</b>, en silencio y sin error.
 */
@Service
@AllArgsConstructor
public class FormatoTerminalPosRegionService
        extends CrudService<FormatoTerminalPosRegion, FormatoTerminalPosRegionRepository, Long> {

    private static final List<String> POSICIONES = Arrays.asList(
            FormatoTerminalPosRegion.POSICION_DERECHA,
            FormatoTerminalPosRegion.POSICION_ABAJO,
            FormatoTerminalPosRegion.POSICION_DENTRO);

    private static final List<String> TIPOS = Arrays.asList(
            FormatoTerminalPosRegion.TIPO_TEXTO,
            FormatoTerminalPosRegion.TIPO_NUMERO,
            FormatoTerminalPosRegion.TIPO_FECHA);

    private static final List<String> ORIGENES = Arrays.asList(
            FormatoTerminalPosRegion.ORIGEN_DERIVADA,
            FormatoTerminalPosRegion.ORIGEN_MANUAL);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final FormatoTerminalPosRegionRepository repository;

    @Override
    public FormatoTerminalPosRegionRepository getRepository() {
        return repository;
    }

    public List<FormatoTerminalPosRegion> findPorFormato(Long formatoId) {
        return repository.findByFormatoTerminalPosIdOrderByOrdenAscIdAsc(formatoId);
    }

    public long cuantasTiene(Long formatoId) {
        return repository.countByFormatoTerminalPosId(formatoId);
    }

    @Override
    public FormatoTerminalPosRegion save(FormatoTerminalPosRegion entity) {
        validar(entity);
        return super.save(entity);
    }

    /**
     * Lo que devuelve una corrida de derivacion. No es una excepcion cuando pide confirmacion: el
     * desktop necesita mostrar el diff, y un error no lleva datos.
     */
    public static final class ResultadoDerivacion {
        /** Si se escribio algo. {@code false} cuando falta la confirmacion. */
        public final boolean aplicado;
        public final int creadas;
        public final int actualizadas;
        public final int eliminadas;
        /** Campos cuya region MANUAL se dejo intacta. La derivacion nunca las toca. */
        public final List<String> conservadasManuales;
        /** El diff, en frases: que cambiaria por campo. Es lo que el operador tiene que leer. */
        public final List<String> cambios;
        public final String mensaje;

        ResultadoDerivacion(boolean aplicado, int creadas, int actualizadas, int eliminadas,
                            List<String> conservadasManuales, List<String> cambios, String mensaje) {
            this.aplicado = aplicado;
            this.creadas = creadas;
            this.actualizadas = actualizadas;
            this.eliminadas = eliminadas;
            this.conservadasManuales = conservadasManuales;
            this.cambios = cambios;
            this.mensaje = mensaje;
        }

        // Getters ademas de los campos publicos: graphql-java-kickstart resuelve por propiedad, y
        // no conviene depender de que el PropertyDataFetcher caiga al campo.
        public boolean getAplicado() { return aplicado; }
        public int getCreadas() { return creadas; }
        public int getActualizadas() { return actualizadas; }
        public int getEliminadas() { return eliminadas; }
        public List<String> getConservadasManuales() { return conservadasManuales; }
        public List<String> getCambios() { return cambios; }
        public String getMensaje() { return mensaje; }
    }

    /**
     * Guarda el mapa que el filial derivo de un cupon de muestra.
     *
     * <p><b>La derivacion es repetible, asi que necesita reglas de sobrescritura.</b> El desktop la
     * dispara como accion del usuario, no una sola vez en el alta, y la tabla se replica
     * MAIN_TO_ALL: una sobrescritura mala no queda local, baja a las 24 filiales. Las reglas:
     *
     * <ol>
     *   <li>Sobre un formato <b>sin regiones</b>: corre y guarda, sin preguntar. No hay nada que
     *       perder.</li>
     *   <li>Sobre un formato <b>que ya tiene regiones</b>: no pisa. Devuelve el diff --que campo
     *       cambiaria de region-- y pide confirmacion explicita.</li>
     *   <li>Una region <b>MANUAL nunca se toca</b>, ni siquiera con la confirmacion. Es una
     *       correccion que alguien hizo mirando un cupon, y el plan mismo anticipa esa correccion
     *       cuando dice que el editor drag-and-drop vuelve para arreglar mapas torcidos. Para
     *       reemplazarla hay que editarla o borrarla a mano, que es una decision y no un clic.</li>
     * </ol>
     *
     * <p>Correrla dos veces sobre el mismo cupon da el mismo resultado: la derivacion es
     * deterministica por construccion y no hay estado acumulado.
     */
    @Transactional
    public ResultadoDerivacion guardarDerivadas(FormatoTerminalPos formato,
                                                List<FormatoTerminalPosRegion> propuestas,
                                                boolean confirmarSobrescritura) {
        exigirFormatoQueAdmiteMapa(formato);
        if (propuestas == null || propuestas.isEmpty()) {
            throw new GraphQLException("La derivacion no propuso ninguna region. Proba el patron"
                    + " contra este cupon antes de derivar el mapa.");
        }

        List<FormatoTerminalPosRegion> existentes = findPorFormato(formato.getId());

        Set<String> camposManuales = new LinkedHashSet<String>();
        for (FormatoTerminalPosRegion r : existentes) {
            if (r.esManual()) camposManuales.add(r.getCampo());
        }

        // Lo que la derivacion puede tocar: todo menos lo que una persona corrigio.
        List<FormatoTerminalPosRegion> aplicables = new ArrayList<FormatoTerminalPosRegion>();
        for (FormatoTerminalPosRegion p : propuestas) {
            p.setFormatoTerminalPos(formato);
            p.setOrigen(FormatoTerminalPosRegion.ORIGEN_DERIVADA);
            validar(p);
            if (!camposManuales.contains(p.getCampo())) aplicables.add(p);
        }

        List<FormatoTerminalPosRegion> derivadasViejas = new ArrayList<FormatoTerminalPosRegion>();
        for (FormatoTerminalPosRegion r : existentes) {
            if (!r.esManual()) derivadasViejas.add(r);
        }

        List<String> conservadas = new ArrayList<String>(camposManuales);
        List<String> cambios = diff(derivadasViejas, aplicables);

        if (!derivadasViejas.isEmpty() && !confirmarSobrescritura) {
            return new ResultadoDerivacion(false, 0, 0, 0, conservadas, cambios,
                    "Este formato ya tiene un mapa derivado. Revisa los cambios y confirma para"
                            + " reemplazarlo: baja a todas las sucursales.");
        }

        Set<String> camposPropuestos = new HashSet<String>();
        for (FormatoTerminalPosRegion p : aplicables) camposPropuestos.add(p.getCampo());

        // Una region derivada que el patron ya no produce no es inofensiva: sigue acotando el
        // reconocimiento a una zona por un campo que no existe. Se borra, y se informa.
        int eliminadas = 0;
        for (FormatoTerminalPosRegion vieja : derivadasViejas) {
            if (!camposPropuestos.contains(vieja.getCampo())) {
                repository.delete(vieja);
                eliminadas++;
            }
        }

        int creadas = 0, actualizadas = 0;
        for (FormatoTerminalPosRegion p : aplicables) {
            FormatoTerminalPosRegion existente =
                    repository.findByFormatoTerminalPosIdAndCampo(formato.getId(), p.getCampo());
            if (existente == null) {
                super.save(p);
                creadas++;
            } else {
                copiarEn(p, existente);
                super.save(existente);
                actualizadas++;
            }
        }

        return new ResultadoDerivacion(true, creadas, actualizadas, eliminadas, conservadas, cambios,
                "Mapa guardado.");
    }

    /** El diff en frases. Es lo que el operador lee antes de confirmar. */
    private static List<String> diff(List<FormatoTerminalPosRegion> viejas,
                                     List<FormatoTerminalPosRegion> nuevas) {
        List<String> out = new ArrayList<String>();
        Set<String> camposNuevos = new LinkedHashSet<String>();
        for (FormatoTerminalPosRegion n : nuevas) {
            camposNuevos.add(n.getCampo());
            FormatoTerminalPosRegion vieja = buscarCampo(viejas, n.getCampo());
            if (vieja == null) {
                out.add(n.getCampo() + ": se agrega (" + describir(n) + ")");
            } else if (!mismaRegion(vieja, n)) {
                out.add(n.getCampo() + ": " + describir(vieja) + " -> " + describir(n));
            }
        }
        for (FormatoTerminalPosRegion v : viejas) {
            if (!camposNuevos.contains(v.getCampo())) {
                out.add(v.getCampo() + ": se elimina, el patron ya no lo produce");
            }
        }
        return out;
    }

    private static FormatoTerminalPosRegion buscarCampo(List<FormatoTerminalPosRegion> lista, String campo) {
        for (FormatoTerminalPosRegion r : lista) if (campo.equals(r.getCampo())) return r;
        return null;
    }

    private static boolean mismaRegion(FormatoTerminalPosRegion a, FormatoTerminalPosRegion b) {
        return iguales(a.getEtiqueta(), b.getEtiqueta())
                && iguales(a.getPosicion(), b.getPosicion())
                && iguales(a.getTipo(), b.getTipo())
                && igualesNum(a.getX1(), b.getX1()) && igualesNum(a.getY1(), b.getY1())
                && igualesNum(a.getX2(), b.getX2()) && igualesNum(a.getY2(), b.getY2());
    }

    private static boolean iguales(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** compareTo y no equals: BigDecimal.equals compara la escala, 0.10 != 0.1. */
    private static boolean igualesNum(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : (b != null && a.compareTo(b) == 0);
    }

    private static String describir(FormatoTerminalPosRegion r) {
        String ancla = r.getEtiqueta() != null && !r.getEtiqueta().trim().isEmpty()
                ? "\"" + r.getEtiqueta() + "\" " + (r.getPosicion() != null ? r.getPosicion().toLowerCase() : "")
                : "sin etiqueta";
        return r.tieneCaja() ? ancla + ", con zona" : ancla + ", sin zona";
    }

    private static void copiarEn(FormatoTerminalPosRegion desde, FormatoTerminalPosRegion hacia) {
        hacia.setEtiqueta(desde.getEtiqueta());
        hacia.setPosicion(desde.getPosicion());
        hacia.setTipo(desde.getTipo());
        hacia.setObligatorio(desde.getObligatorio());
        hacia.setX1(desde.getX1());
        hacia.setY1(desde.getY1());
        hacia.setX2(desde.getX2());
        hacia.setY2(desde.getY2());
        hacia.setOrden(desde.getOrden());
        hacia.setOrigen(FormatoTerminalPosRegion.ORIGEN_DERIVADA);
    }

    /**
     * Las condiciones para poder guardar una region. Todas existen porque el error se manifestaria
     * lejos: en la caja, con el cupon ya impreso y un campo que desaparecio sin decir nada.
     */
    private void validar(FormatoTerminalPosRegion e) {
        FormatoTerminalPos formato = e.getFormatoTerminalPos();
        exigirFormatoQueAdmiteMapa(formato);

        if (e.getCampo() == null || e.getCampo().trim().isEmpty()) {
            throw new GraphQLException("La region tiene que decir a que campo corresponde.");
        }
        e.setCampo(e.getCampo().trim());

        // Una region para un campo que el mapeo no produce no solo es peso muerto: ademas acota el
        // reconocimiento a una zona por un campo que nadie va a leer.
        Set<String> delMapeo = camposDelMapeo(formato.getMapeo());
        if (!delMapeo.isEmpty() && !delMapeo.contains(e.getCampo())) {
            throw new GraphQLException("El mapeo de \"" + formato.getNombre() + "\" no produce el campo \""
                    + e.getCampo() + "\". Los que produce son: " + String.join(", ", delMapeo) + ".");
        }

        if (e.getPosicion() != null && !POSICIONES.contains(e.getPosicion())) {
            throw new GraphQLException("La posicion tiene que ser una de: " + String.join(", ", POSICIONES) + ".");
        }
        if (e.getTipo() != null && !TIPOS.contains(e.getTipo())) {
            throw new GraphQLException("El tipo tiene que ser uno de: " + String.join(", ", TIPOS) + ".");
        }
        if (e.getOrigen() == null || !ORIGENES.contains(e.getOrigen())) {
            throw new GraphQLException("El origen tiene que ser uno de: " + String.join(", ", ORIGENES) + ".");
        }
        if (e.getObligatorio() == null) e.setObligatorio(Boolean.FALSE);
        if (e.getOrden() == null) e.setOrden(0);

        validarCaja(e);

        // Sin etiqueta y sin zona, la region no dice nada: no hay ancla ni pista, y el campo cae al
        // patron igual que si la region no existiera. Guardarla solo agrega una fila que el
        // administrador cree que esta haciendo algo.
        if ((e.getEtiqueta() == null || e.getEtiqueta().trim().isEmpty()) && !e.tieneCaja()) {
            throw new GraphQLException("La region de \"" + e.getCampo() + "\" no tiene etiqueta ni zona:"
                    + " asi no ancla a nada y el campo se resuelve por patron igual.");
        }
        if (e.getEtiqueta() != null) {
            String et = e.getEtiqueta().trim();
            e.setEtiqueta(et.isEmpty() ? null : et);
        }
        // Con etiqueta hay que saber de que lado mirar; sin posicion, "AUT:" no dice si el valor
        // esta a la derecha o en el renglon de abajo.
        if (e.getEtiqueta() != null && e.getPosicion() == null) {
            e.setPosicion(FormatoTerminalPosRegion.POSICION_DERECHA);
        }
    }

    /**
     * Un formato WEB es patron puro: la cadena entra por el lector del PDV y no hay imagen sobre la
     * cual haya regiones que dibujar. Un mapa ahi no se aplica nunca, y guardarlo da la falsa
     * impresion de haber configurado algo.
     */
    private static void exigirFormatoQueAdmiteMapa(FormatoTerminalPos formato) {
        if (formato == null) {
            throw new GraphQLException("La region tiene que pertenecer a un formato.");
        }
        if (!formato.esMaquina()) {
            throw new GraphQLException("\"" + formato.getNombre() + "\" es un formato "
                    + formato.getTipo() + ": se lee matcheando la cadena del lector, no hay imagen"
                    + " donde ubicar regiones. El mapa es solo para los formatos MAQUINA.");
        }
    }

    private static void validarCaja(FormatoTerminalPosRegion e) {
        BigDecimal[] c = {e.getX1(), e.getY1(), e.getX2(), e.getY2()};
        int nulos = 0;
        for (BigDecimal v : c) if (v == null) nulos++;
        if (nulos == 4) return;
        if (nulos > 0) {
            // Media caja no es una pista: es un rectangulo abierto que despues acota el
            // reconocimiento a cualquier cosa.
            throw new GraphQLException("La zona de \"" + e.getCampo() + "\" esta incompleta: van las"
                    + " cuatro coordenadas o ninguna.");
        }
        for (BigDecimal v : c) {
            if (v.compareTo(BigDecimal.ZERO) < 0 || v.compareTo(BigDecimal.ONE) > 0) {
                throw new GraphQLException("Las coordenadas van normalizadas entre 0 y 1;"
                        + " \"" + e.getCampo() + "\" tiene " + v + ".");
            }
        }
        if (e.getX1().compareTo(e.getX2()) >= 0 || e.getY1().compareTo(e.getY2()) >= 0) {
            throw new GraphQLException("La zona de \"" + e.getCampo() + "\" esta al reves o vacia:"
                    + " x1 tiene que ser menor que x2, e y1 menor que y2.");
        }
    }

    /**
     * Las claves de primer nivel del mapeo, que son los campos que el formato produce.
     *
     * <p>Aca si se parsea con Jackson, a diferencia de {@code FormatoTerminalPosService}, que valida
     * el mapeo a mano. El motivo de aquella decision era no arrastrar una dependencia de parseo al
     * filial, que lee el mismo JSON; esto corre solo en el ABM de central, donde Jackson ya esta.
     * Y aca la precision importa: un regex sobre las claves tambien matchearia las anidadas --el
     * {@code mapa} de una regla-- y aceptaria una region para un campo que no existe.
     *
     * <p>Devuelve vacio si el mapeo no se puede leer: la validacion de forma es responsabilidad de
     * {@code FormatoTerminalPosService}, y este chequeo no puede bloquear por un motivo ajeno.
     */
    private static Set<String> camposDelMapeo(String mapeo) {
        Set<String> out = new LinkedHashSet<String>();
        if (mapeo == null || mapeo.trim().isEmpty()) return out;
        try {
            JsonNode root = JSON.readTree(mapeo);
            if (!root.isObject()) return out;
            Iterator<String> it = root.fieldNames();
            while (it.hasNext()) out.add(it.next());
        } catch (Exception ignored) {
            // Mapeo ilegible: no es asunto de esta validacion.
        }
        return out;
    }
}
