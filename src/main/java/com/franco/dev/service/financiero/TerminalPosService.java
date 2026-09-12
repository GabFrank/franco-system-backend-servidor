package com.franco.dev.service.financiero;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.repository.financiero.TerminalPosRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class TerminalPosService extends CrudService<TerminalPos, TerminalPosRepository, Long> {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TerminalPosRepository repository;

    @Override
    public TerminalPosRepository getRepository() {
        return repository;
    }

    public Long countByProveedorServicioId(Long proveedorServicioId) {
        return repository.countByProveedorServicioId(proveedorServicioId);
    }

    public TerminalPos findByCodigo(String codigo) {
        return repository.findByCodigoIgnoreCase(codigo);
    }

    public List<TerminalPos> searchByAll(String texto) {
        texto = texto != null ? texto.toUpperCase() : "";
        return repository.findByAll(texto);
    }

    public Page<TerminalPos> filter(String descripcion, String codigo, String serie, Long sucursalId,
                                    Boolean activo, int page, int size) {
        descripcion = normalizarBusqueda(descripcion);
        codigo = normalizarBusqueda(codigo);
        serie = normalizarBusqueda(serie);
        return repository.filterTerminalPos(descripcion, codigo, serie, sucursalId, activo,
                PageRequest.of(page, size));
    }

    private static String normalizarBusqueda(String v) {
        return (v != null && !v.trim().isEmpty()) ? v.trim().toUpperCase() : null;
    }

    @Override
    public TerminalPos save(TerminalPos entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        normalizarSerie(entity);
        validarSerieUnica(entity);
        validarCodigoUnico(entity);
        return super.save(entity);
    }

    /**
     * La serie se guarda en mayusculas y sin espacios al borde.
     * <p>
     * Los dos indices unicos de {@code V224.5} comparan la columna cruda: sin normalizar,
     * {@code jf798sjj} y {@code JF798SJJ} son dos maquinas distintas para Postgres y una sola para
     * cualquier persona. Y la serie se tipea mirando una etiqueta pegada al aparato, que es
     * exactamente donde aparecen esas diferencias.
     * <p>
     * Vacio se guarda como NULL, no como cadena vacia: los indices son parciales sobre
     * {@code serie IS NOT NULL}, asi que dos terminales con la serie en {@code ''} chocarian entre
     * si sin motivo.
     */
    private static void normalizarSerie(TerminalPos entity) {
        String s = entity.getSerie();
        if (s == null) return;
        s = s.trim().toUpperCase();
        entity.setSerie(s.isEmpty() ? null : s);
    }

    /**
     * Rechaza una serie repetida con una frase, en vez de con el error del indice unico.
     * <p>
     * Son dos consultas por el mismo motivo por el que son dos indices: en SQL dos NULL no son
     * iguales, asi que una comparacion por {@code proveedor_servicio_id} nunca encontraria los
     * comodines --que hoy son las dos terminales que existen--.
     */
    private void validarSerieUnica(TerminalPos entity) {
        if (entity.getSerie() == null) return;
        Long proveedorId = entity.getProveedorServicio() != null
                ? entity.getProveedorServicio().getId() : null;

        TerminalPos existente = proveedorId != null
                ? repository.findByProveedorServicioIdAndSerie(proveedorId, entity.getSerie())
                : repository.findByProveedorServicioIsNullAndSerie(entity.getSerie());

        if (existente != null && !existente.getId().equals(entity.getId())) {
            throw new GraphQLException("La serie \"" + entity.getSerie() + "\" ya esta registrada en"
                    + " la terminal \"" + descripcionDe(existente) + "\". Dos aparatos no pueden"
                    + " compartir identificador: el cupon no diria de cual salio.");
        }
    }

    /**
     * El codigo es lo que el cajero escanea para elegir la terminal, y el dialogo de escaneo se
     * queda con el primer resultado. Dos terminales con el mismo codigo y el cobro va contra la
     * maquina equivocada, sin ningun aviso.
     * <p>
     * {@code V224.5} lo cierra con un indice unico parcial; esto es para que el operador vea una
     * frase y no el error de Postgres.
     */
    private void validarCodigoUnico(TerminalPos entity) {
        String codigo = entity.getCodigo();
        if (codigo == null || codigo.trim().isEmpty()) return;

        TerminalPos existente = repository.findByCodigoIgnoreCase(codigo.trim());
        if (existente != null && !existente.getId().equals(entity.getId())) {
            throw new GraphQLException("El codigo \"" + codigo.trim() + "\" ya lo usa la terminal \""
                    + descripcionDe(existente) + "\". Es lo que el cajero escanea para elegir la"
                    + " maquina: repetido, cobraria contra la equivocada.");
        }
    }

    /**
     * Configuracion por aparato: si se puede tipear el cupon a mano, y que campos no se pueden
     * dejar vacios en esta terminal.
     * <p>
     * Va por una mutation propia y no por {@code saveTerminalPos} a proposito. Las dos son
     * tri-estado --{@code null} significa "hereda"-- y {@code saveTerminalPos} arma la entidad de
     * cero con ModelMapper: si estos campos vivieran en ese input, un cliente que no los mandara
     * los pondria en null, o sea que apagar la configuracion de una terminal seria el efecto de
     * omitir un campo. Aca, en cambio, mandar la configuracion completa ES el contrato.
     */
    public TerminalPos configurar(TerminalPos terminal, Boolean cargaManualPermitida,
                                  List<String> camposObligatorios) {
        if (Boolean.FALSE.equals(cargaManualPermitida)) {
            exigirOtroCaminoAbierto(terminal);
        }
        terminal.setCargaManualPermitida(cargaManualPermitida);
        terminal.setCamposObligatorios(serializarCampos(terminal, camposObligatorios));
        return save(terminal);
    }

    /**
     * La lista cruda de campos obligatorios de esta terminal. {@code null} = hereda del formato.
     * <p>
     * Es la que edita el dialogo de configuracion, que necesita distinguir "no configurado" de
     * "configurado vacio". Para saber que se le va a exigir de verdad al cajero esta
     * {@link #camposObligatoriosEfectivos}.
     */
    public List<String> camposObligatoriosDe(TerminalPos t) {
        return deserializarCampos(t.getCamposObligatorios());
    }

    /**
     * Lo que se le exige de verdad al cajero en esta terminal: la lista por aparato si la hay, y si
     * no, los que el mapeo del formato declara obligatorios.
     * <p>
     * Es lo que arma el formulario de carga a mano. Que salga de un solo lugar es justamente el
     * punto: tres listas en tres pantallas se desincronizan.
     */
    public List<String> camposObligatoriosEfectivos(TerminalPos t) {
        List<String> propios = camposObligatoriosDe(t);
        if (propios != null) return propios;
        FormatoTerminalPos f = t.getFormatoTerminalPos();
        return f != null
                ? new ArrayList<String>(MapeoFormato.obligatorios(f.getMapeo()))
                : new ArrayList<String>();
    }

    @SuppressWarnings("unchecked")
    private static List<String> deserializarCampos(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return JSON.readValue(json, List.class);
        } catch (Exception e) {
            // Una lista ilegible no puede tumbar el listado de terminales. Se comporta como si no
            // estuviera configurada, que es el estado seguro: se cae al mapeo del formato.
            return null;
        }
    }

    /**
     * El interruptor de carga manual no puede apagar el ULTIMO camino.
     * <p>
     * El tipo del formato ya cierra el camino que no corresponde --una terminal WEB solo lee QR,
     * una MAQUINA solo camara-- y eso unicamente es seguro porque la carga a mano es la salida
     * universal. Si se apaga en una terminal cuyo otro camino tampoco esta abierto, esa caja queda
     * sin ninguna forma de cobrar con tarjeta y nadie avisa: la venta queda PENDIENTE y la caja no
     * cierra.
     * <p>
     * No es un CHECK en la base porque depende de otra columna y de otra tabla --si hay formato y
     * de que tipo es--, y porque un trigger daria un error de Postgres en vez de una frase.
     */
    private static void exigirOtroCaminoAbierto(TerminalPos t) {
        FormatoTerminalPos f = t.getFormatoTerminalPos();
        if (f == null) {
            throw new GraphQLException("\"" + descripcionDe(t) + "\" no tiene formato asignado: sin"
                    + " formato la venta con tarjeta ya esta bloqueada y la carga a mano es el unico"
                    + " camino que le queda. Asignale un formato antes de apagarla.");
        }
        if (!f.esMaquina() && !f.esWeb()) {
            // API: los campos llegarian estructurados del proveedor, pero ese driver todavia no
            // existe. Apagar la carga manual seria apoyarse en una integracion que no esta.
            throw new GraphQLException("\"" + descripcionDe(t) + "\" usa un formato " + f.getTipo()
                    + ", cuyo camino todavia no esta implementado. Si ademas se apaga la carga a"
                    + " mano, esa caja no tiene con que cobrar.");
        }
    }

    /**
     * La lista por POS solo puede APRETAR.
     * <p>
     * Tiene que contener todos los campos que el mapeo del formato ya declara obligatorios. Si
     * pudiera aflojarlos, esta pantalla --que parece menor-- seria una forma de saltear la
     * validacion del formato, y el formato lo comparten todas las terminales del mismo modelo.
     * <p>
     * Y cada campo tiene que existir en el mapeo: exigir un campo que el formato nunca produce
     * bloquearia TODAS las ventas de esa terminal, sin que el mensaje diga por que.
     */
    private String serializarCampos(TerminalPos t, List<String> campos) {
        if (campos == null) return null;

        Set<String> limpios = new LinkedHashSet<String>();
        for (String c : campos) {
            if (c != null && !c.trim().isEmpty()) limpios.add(c.trim());
        }
        if (limpios.isEmpty()) return null;

        FormatoTerminalPos f = t.getFormatoTerminalPos();
        if (f == null) {
            throw new GraphQLException("\"" + descripcionDe(t) + "\" no tiene formato asignado, asi"
                    + " que todavia no se sabe que campos puede producir su cupon.");
        }

        Set<String> delMapeo = MapeoFormato.campos(f.getMapeo());
        if (!delMapeo.isEmpty()) {
            for (String c : limpios) {
                if (!delMapeo.contains(c)) {
                    throw new GraphQLException("El formato \"" + f.getNombre() + "\" no produce el"
                            + " campo \"" + c + "\". Exigirlo bloquearia todas las ventas de esta"
                            + " terminal. Los que produce son: " + String.join(", ", delMapeo) + ".");
                }
            }
        }

        List<String> faltantes = new ArrayList<String>();
        for (String o : MapeoFormato.obligatorios(f.getMapeo())) {
            if (!limpios.contains(o)) faltantes.add(o);
        }
        if (!faltantes.isEmpty()) {
            throw new GraphQLException("La configuracion por terminal solo puede exigir MAS, no"
                    + " menos: \"" + f.getNombre() + "\" ya declara obligatorio "
                    + String.join(", ", faltantes) + ". Si sobran, corregi el formato.");
        }

        try {
            return JSON.writeValueAsString(new ArrayList<String>(limpios));
        } catch (Exception e) {
            throw new GraphQLException("No se pudo guardar la lista de campos obligatorios.");
        }
    }

    private static String descripcionDe(TerminalPos t) {
        return t.getDescripcion() != null && !t.getDescripcion().trim().isEmpty()
                ? t.getDescripcion() : ("#" + t.getId());
    }
}
