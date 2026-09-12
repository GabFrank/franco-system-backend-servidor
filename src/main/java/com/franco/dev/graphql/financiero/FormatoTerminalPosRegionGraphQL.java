package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.domain.financiero.FormatoTerminalPosRegion;
import com.franco.dev.graphql.financiero.input.FormatoTerminalPosRegionInput;
import com.franco.dev.service.financiero.FormatoTerminalPosRegionService;
import com.franco.dev.service.financiero.FormatoTerminalPosService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ABM del mapa de un formato: que caja del OCR es que campo.
 *
 * <p>Vive solo en central, como el formato mismo: la tabla es MAIN_TO_ALL y un mapa editable desde
 * una sucursal se desincronizaria del resto de la flota. El filial tiene el espejo en modo lectura
 * y lo usa para acotar el reconocimiento.
 *
 * <p><b>Seguridad a mano</b>, como todo este repo: no existe {@code @PreAuthorize} en
 * {@code src/main/java} y {@code @AdminSecured} esta roto (issue #177). Cada metodo llama
 * {@code requireVer()} o {@code requireGestionar()} como primera linea.
 */
@Component
public class FormatoTerminalPosRegionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FormatoTerminalPosRegionService service;

    @Autowired
    private FormatoTerminalPosService formatoService;

    @Autowired
    private TesoreriaSecurityService seg;

    public List<FormatoTerminalPosRegion> regionesDeFormatoTerminalPos(Long formatoTerminalPosId) {
        seg.requireVer();
        return service.findPorFormato(formatoTerminalPosId);
    }

    /**
     * Guarda una region escrita o corregida por una persona.
     *
     * <p><b>Siempre queda MANUAL</b>, venga como venga el input. Este es el camino manual por
     * definicion, y el flag es lo unico que protege la correccion de la proxima corrida de
     * derivacion. Si se pudiera elegir, una region derivada podria quedar marcada MANUAL por error
     * y volverse intocable sin que nadie la haya revisado.
     */
    public FormatoTerminalPosRegion saveRegionTerminalPos(FormatoTerminalPosRegionInput input) {
        seg.requireGestionar();
        FormatoTerminalPosRegion e = input.getId() != null
                ? service.findById(input.getId()).orElse(new FormatoTerminalPosRegion())
                : new FormatoTerminalPosRegion();
        e.setId(input.getId());
        e.setFormatoTerminalPos(formatoDe(input.getFormatoTerminalPosId(), e));
        copiar(input, e);
        e.setOrigen(FormatoTerminalPosRegion.ORIGEN_MANUAL);
        return service.save(e);
    }

    public Boolean deleteRegionTerminalPos(Long id) {
        seg.requireGestionar();
        return service.deleteById(id);
    }

    /**
     * Persiste el mapa que el filial derivo de un cupon de muestra.
     *
     * <p>Devuelve un resultado y no lanza cuando falta la confirmacion: el desktop tiene que mostrar
     * el diff, y una excepcion no lleva datos. Las reglas de sobrescritura estan en
     * {@link FormatoTerminalPosRegionService#guardarDerivadas}.
     */
    public FormatoTerminalPosRegionService.ResultadoDerivacion guardarRegionesDerivadas(
            Long formatoTerminalPosId,
            List<FormatoTerminalPosRegionInput> regiones,
            Boolean confirmarSobrescritura) {
        seg.requireGestionar();
        FormatoTerminalPos formato = formatoService.findById(formatoTerminalPosId)
                .orElseThrow(() -> new GraphQLException("No existe el formato de terminal "
                        + formatoTerminalPosId + "."));

        List<FormatoTerminalPosRegion> propuestas = new ArrayList<FormatoTerminalPosRegion>();
        if (regiones != null) {
            for (FormatoTerminalPosRegionInput i : regiones) {
                FormatoTerminalPosRegion r = new FormatoTerminalPosRegion();
                r.setFormatoTerminalPos(formato);
                copiar(i, r);
                propuestas.add(r);
            }
        }
        return service.guardarDerivadas(formato, propuestas,
                Boolean.TRUE.equals(confirmarSobrescritura));
    }

    /**
     * El formato de una region no se puede cambiar en una edicion: mover una region de formato es
     * borrarla de uno y crearla en el otro, y hacerlo por omision --el input no trae el id, la
     * region se queda huerfana-- no puede pasar calladamente.
     */
    private FormatoTerminalPos formatoDe(Long idDelInput, FormatoTerminalPosRegion actual) {
        if (idDelInput == null) {
            if (actual.getFormatoTerminalPos() != null) return actual.getFormatoTerminalPos();
            throw new GraphQLException("La region tiene que decir a que formato pertenece.");
        }
        return formatoService.findById(idDelInput)
                .orElseThrow(() -> new GraphQLException("No existe el formato de terminal "
                        + idDelInput + "."));
    }

    private static void copiar(FormatoTerminalPosRegionInput i, FormatoTerminalPosRegion e) {
        e.setCampo(i.getCampo());
        e.setEtiqueta(i.getEtiqueta());
        e.setPosicion(i.getPosicion());
        e.setTipo(i.getTipo());
        if (i.getObligatorio() != null) e.setObligatorio(i.getObligatorio());
        e.setX1(i.getX1());
        e.setY1(i.getY1());
        e.setX2(i.getX2());
        e.setY2(i.getY2());
        if (i.getOrden() != null) e.setOrden(i.getOrden());
    }
}
