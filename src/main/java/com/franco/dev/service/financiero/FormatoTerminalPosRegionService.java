package com.franco.dev.service.financiero;

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

    private static final BigDecimal CIEN = new BigDecimal("100");

    /**
     * A partir de aca una zona ya no acota: el 25% del cupon con el margen del motor encima deja
     * pasar casi toda caja detectada, que es justo la ganancia que el mapa venia a dar.
     */
    private static final BigDecimal ZONA_GRANDE = new BigDecimal("0.25");

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
        return guardarDerivadas(formato, propuestas, confirmarSobrescritura, false);
    }

    /**
     * Igual, pero pudiendo empezar de cero en vez de acumular.
     *
     * <p><b>Por que la derivacion ACUMULA y no reemplaza.</b> Un mismo modelo de aparato imprime
     * mas de un layout: medido en INFONET el 2026-09-15, el ticket con QR tiene dos renglones menos
     * que el de tarjeta, asi que el monto queda mas arriba. Las dos operaciones salen de la misma
     * terminal, el mismo dia, en la misma caja. Un mapa derivado de un ticket de tarjeta deja de
     * servir para uno de QR, y al reves.
     *
     * <p>Por eso una segunda foto <b>ensancha</b> la caja hasta cubrir las dos posiciones, en vez
     * de pisarla. La union se calcula por campo: {@code x1,y1} al minimo y {@code x2,y2} al maximo.
     *
     * <p><b>Y por que union y no una lista de variantes.</b> El filtro de zonas se aplica DESPUES
     * de detectar, asi que probar N variantes en secuencia serian N pasadas completas del pipeline
     * --deteccion incluida, que es la etapa que no se ahorra--. La union cuesta una sola pasada y
     * reconoce unas cajas mas. Y no hace falta "elegir la variante": el patron ya hace eso sobre el
     * texto.
     *
     * @param desdeCero descarta lo derivado y deja solo esta foto. Es la salida para cuando alguien
     *                  derivo con un ticket de otro modelo y la union quedo inservible.
     */
    public ResultadoDerivacion guardarDerivadas(FormatoTerminalPos formato,
                                                List<FormatoTerminalPosRegion> propuestas,
                                                boolean confirmarSobrescritura,
                                                boolean desdeCero) {
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
        Set<String> vistos = new LinkedHashSet<String>();
        for (FormatoTerminalPosRegion p : propuestas) {
            p.setFormatoTerminalPos(formato);
            p.setOrigen(FormatoTerminalPosRegion.ORIGEN_DERIVADA);
            validar(p);
            // Un campo repetido en la misma tanda se guardaria dos veces sobre la misma fila: el
            // diff mostraria dos lineas para el mismo campo, los contadores quedarian inflados y
            // el resultado dependeria del orden. El indice unico lo atajaria como error de
            // constraint, que no le dice nada al operador.
            if (!vistos.add(p.getCampo())) {
                throw new GraphQLException("El campo \"" + p.getCampo() + "\" viene dos veces en la"
                        + " misma derivacion. Cada campo va una sola vez.");
            }
            if (!camposManuales.contains(p.getCampo())) aplicables.add(p);
        }

        List<FormatoTerminalPosRegion> derivadasViejas = new ArrayList<FormatoTerminalPosRegion>();
        for (FormatoTerminalPosRegion r : existentes) {
            if (!r.esManual()) derivadasViejas.add(r);
        }

        List<String> conservadas = new ArrayList<String>(camposManuales);
        List<String> cambios = diff(derivadasViejas, aplicables, desdeCero);

        // Nada que confirmar cuando nada cambia. Acumulando, volver a derivar con la misma foto
        // --o con otra que caiga dentro de las zonas ya mapeadas-- no modifica una sola region, y
        // el diff sale vacio: pedir confirmacion ahi seria mostrar "esto es lo que cambiaria"
        // seguido de nada, y hacer clic en aceptar para no hacer nada. Aplicar es un no-op.
        //
        // Solo en ese camino. Desde cero se copia la propuesta entera --`orden` y `obligatorio`
        // incluidos, que el diff no describe-- asi que ahi un diff vacio NO prueba que no cambie
        // nada, y la confirmacion se pide igual.
        boolean nadaQueHacer = !desdeCero && cambios.isEmpty();
        if (!derivadasViejas.isEmpty() && !confirmarSobrescritura && !nadaQueHacer) {
            return new ResultadoDerivacion(false, 0, 0, 0, conservadas, cambios,
                    "Este formato ya tiene un mapa derivado. Revisa los cambios y confirma para"
                            + " reemplazarlo: baja a todas las sucursales.");
        }

        Set<String> camposPropuestos = new HashSet<String>();
        for (FormatoTerminalPosRegion p : aplicables) camposPropuestos.add(p.getCampo());

        // Al acumular, un campo ausente de ESTA foto no significa que el patron no lo produzca:
        // significa que esta variante del ticket no lo trae. Solo se borra al empezar de cero.
        int eliminadas = 0;
        if (desdeCero) {
            for (FormatoTerminalPosRegion vieja : derivadasViejas) {
                if (!camposPropuestos.contains(vieja.getCampo())) {
                    repository.delete(vieja);
                    eliminadas++;
                }
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
                if (desdeCero) copiarEn(p, existente); else unirEn(p, existente);
                super.save(existente);
                actualizadas++;
            }
        }

        return new ResultadoDerivacion(true, creadas, actualizadas, eliminadas, conservadas, cambios,
                "Mapa guardado.");
    }

    /**
     * Ensancha la region existente para que cubra tambien la nueva.
     *
     * <p>La geometria se une; <b>la etiqueta y la posicion NO se pisan</b> si la existente ya
     * tenia una. El ancla describe de que texto cuelga el campo, y la primera derivacion ya la
     * eligio; cambiarla en cada foto haria que el mapa dependiera del orden en que se cargaron los
     * tickets. Si la existente no tenia ancla y la nueva si, se toma la nueva: es informacion que
     * antes faltaba.
     *
     * <p>Una region sin las cuatro coordenadas no aporta geometria, asi que no se une: se copia la
     * que si las tenga.
     */
    private static void unirEn(FormatoTerminalPosRegion nueva, FormatoTerminalPosRegion existente) {
        // El ancla y el tipo se completan SIEMPRE, aunque esta foto no haya aportado geometria:
        // una region solo por etiqueta es valida, y es informacion que a la existente le faltaba.
        if (existente.getEtiqueta() == null && nueva.getEtiqueta() != null) {
            existente.setEtiqueta(nueva.getEtiqueta());
            existente.setPosicion(nueva.getPosicion());
        }
        // El tipo NO se acumula: no es evidencia de la foto, es lo que el mapeo del formato
        // declara. Si alguien edito el mapeo para sacarle el tipo a un campo --relajando la
        // validacion a proposito-- una re-derivacion tiene que reflejarlo, no arrastrar el viejo.
        existente.setTipo(nueva.getTipo());

        if (!nueva.tieneCaja()) return;                      // no hay geometria que unir
        if (!existente.tieneCaja()) {
            existente.setX1(nueva.getX1());
            existente.setY1(nueva.getY1());
            existente.setX2(nueva.getX2());
            existente.setY2(nueva.getY2());
            return;
        }

        existente.setX1(existente.getX1().min(nueva.getX1()));
        existente.setY1(existente.getY1().min(nueva.getY1()));
        existente.setX2(existente.getX2().max(nueva.getX2()));
        existente.setY2(existente.getY2().max(nueva.getY2()));
    }

    /**
     * El diff en frases. Es lo que el operador lee antes de confirmar, y es lo unico que lee antes
     * de un cambio que baja a las 24 sucursales: <b>tiene que describir el modo en el que se va a
     * guardar</b>. Acumulando y desde cero hacen cosas distintas con el mismo par de regiones --una
     * ensancha, la otra pisa; una conserva lo ausente, la otra lo borra-- asi que el texto depende
     * de {@code desdeCero}. Anunciar un borrado que no va a ocurrir es peor que no decir nada:
     * ensena al operador a ignorar el aviso, justo para el modo donde si es cierto.
     */
    private static List<String> diff(List<FormatoTerminalPosRegion> viejas,
                                     List<FormatoTerminalPosRegion> nuevas, boolean desdeCero) {
        List<String> out = new ArrayList<String>();
        Set<String> camposNuevos = new LinkedHashSet<String>();
        for (FormatoTerminalPosRegion n : nuevas) {
            camposNuevos.add(n.getCampo());
            FormatoTerminalPosRegion vieja = buscarCampo(viejas, n.getCampo());
            if (vieja == null) {
                out.add(n.getCampo() + ": se agrega (" + describir(n) + ")");
            } else if (desdeCero) {
                if (!mismaRegion(vieja, n)) {
                    out.add(n.getCampo() + ": se reemplaza, " + describir(vieja) + " -> " + describir(n));
                }
            } else {
                String union = describirUnion(vieja, n);
                if (union != null) out.add(n.getCampo() + ": " + union);
            }
        }
        for (FormatoTerminalPosRegion v : viejas) {
            if (!camposNuevos.contains(v.getCampo())) {
                out.add(v.getCampo() + (desdeCero
                        ? ": se elimina, esta foto no lo produce"
                        : ": no aparece en esta foto, se conserva lo que ya estaba mapeado"));
            }
        }
        return out;
    }

    /**
     * Que le pasa a una region cuando se acumula. {@code null} si no le pasa nada.
     *
     * <p>Tiene que espejar exactamente lo que hace {@link #unirEn}: el operador confirma sobre este
     * texto. Y dice el tamano resultante en porcentaje del cupon porque el riesgo de acumular es la
     * caja que crece de mas --una foto de otro modelo la estira hasta cubrir medio ticket y el
     * filtro de zonas deja de acotar nada--, y no hay otra senal de que eso paso.
     */
    private static String describirUnion(FormatoTerminalPosRegion vieja, FormatoTerminalPosRegion nueva) {
        List<String> partes = new ArrayList<String>();

        if (nueva.tieneCaja()) {
            if (!vieja.tieneCaja()) {
                partes.add("se le agrega la zona (" + pct(area(nueva)) + " del cupon)");
            } else {
                BigDecimal x1 = vieja.getX1().min(nueva.getX1());
                BigDecimal y1 = vieja.getY1().min(nueva.getY1());
                BigDecimal x2 = vieja.getX2().max(nueva.getX2());
                BigDecimal y2 = vieja.getY2().max(nueva.getY2());
                boolean crece = !(igualesNum(x1, vieja.getX1()) && igualesNum(y1, vieja.getY1())
                        && igualesNum(x2, vieja.getX2()) && igualesNum(y2, vieja.getY2()));
                if (crece) {
                    BigDecimal unida = area(x1, y1, x2, y2);
                    String frase = "la zona se ensancha, de " + pct(area(vieja)) + " a "
                            + pct(unida) + " del cupon";
                    if (unida.compareTo(ZONA_GRANDE) > 0) {
                        frase += " -- es una zona muy grande, ya casi no acota el reconocimiento;"
                                + " si esta foto es de otro modelo de aparato conviene empezar de cero";
                    }
                    partes.add(frase);
                }
            }
        }
        if (vieja.getEtiqueta() == null && nueva.getEtiqueta() != null) {
            partes.add("se toma el ancla \"" + nueva.getEtiqueta() + "\"");
        }
        if (!iguales(vieja.getTipo(), nueva.getTipo())) {
            partes.add("el tipo pasa a " + (nueva.getTipo() == null ? "sin declarar" : nueva.getTipo()));
        }
        if (partes.isEmpty()) return null;
        return String.join("; ", partes);
    }

    private static BigDecimal area(FormatoTerminalPosRegion r) {
        return r.tieneCaja() ? area(r.getX1(), r.getY1(), r.getX2(), r.getY2()) : BigDecimal.ZERO;
    }

    private static BigDecimal area(BigDecimal x1, BigDecimal y1, BigDecimal x2, BigDecimal y2) {
        return x2.subtract(x1).multiply(y2.subtract(y1));
    }

    private static String pct(BigDecimal fraccion) {
        return fraccion.multiply(CIEN).setScale(1, java.math.RoundingMode.HALF_UP) + "%";
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
        Set<String> delMapeo = MapeoFormato.campos(formato.getMapeo());
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

}
