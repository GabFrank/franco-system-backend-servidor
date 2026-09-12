package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.graphql.financiero.input.TerminalPosInput;
import com.franco.dev.service.financiero.CuentaBancariaService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.FormatoTerminalPosService;
import com.franco.dev.service.financiero.TerminalPosService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.ProveedorServicioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import com.franco.dev.repository.financiero.TerminalPosRepository;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TerminalPosGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TerminalPosService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CuentaBancariaService cuentaBancariaService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private ProveedorServicioService proveedorServicioService;

    @Autowired
    private FormatoTerminalPosService formatoTerminalPosService;

    @Autowired
    private SucursalService sucursalService;

    /** Para leer el formato actual sin traer la terminal entera. Ver saveTerminalPos. */
    @Autowired
    private TerminalPosRepository terminalPosRepository;

    /**
     * Seguridad a mano, como en todo este repo: no hay {@code @PreAuthorize} ni {@code @Secured}
     * aplicados en ningun lado, y {@code @AdminSecured} esta roto (issue #177).
     * <p>
     * <b>Solo en las mutations, a proposito.</b> Las queries de esta clase quedan sin chequeo y no
     * es un olvido: {@code filterTerminalPos} la consume {@code list-venta-tarjeta} del desktop
     * contra el central, y esa pantalla la abre hoy quien tiene VENTA TARJETA COMPLETAR sin ser de
     * tesoreria. Cerrarlas le sacaria la pantalla a quien la usa todos los dias, y eso excede lo
     * que esta entrega vino a hacer. Queda anotado como hueco preexistente en el plan de fase 2.
     * <p>
     * Las escrituras si se cierran: reasignan la cuenta bancaria de una terminal y deciden si una
     * caja puede cobrar con tarjeta. Eso es tesoreria, no caja.
     */
    @Autowired
    private TesoreriaSecurityService seg;

    public Optional<TerminalPos> terminalPos(Long id) {
        return service.findById(id);
    }

    public List<TerminalPos> terminalesPos(int page, int size) {
        return service.findAll2();
    }

    public List<TerminalPos> searchTerminalPos(String texto) {
        return service.searchByAll(texto);
    }

    /**
     * El filtro por sucursal responde el caso de uso que motivo la columna: <i>un gerente quiere
     * saber cuantas maquinas deberia tener en su local</i>.
     * <p>
     * Es un filtro de <b>consulta</b>, no de replicacion: {@code terminal_pos} sigue bajando entera
     * a las 24 filiales. Filtrar la replicacion haria que una terminal sin sucursal asignada deje
     * de bajar, y si alguna caja dependia de ella para cobrar se queda sin cobrar.
     */
    public Page<TerminalPos> filterTerminalPos(String descripcion, String codigo, String serie,
                                               Long sucursalId, Boolean activo, int page, int size) {
        return service.filter(descripcion, codigo, serie, sucursalId, activo, page, size);
    }

    public Long countTerminalPos() {
        return service.count();
    }

    public TerminalPos saveTerminalPos(TerminalPosInput input) {
        seg.requireGestionar();
        ModelMapper m = new ModelMapper();
        TerminalPos e = m.map(input, TerminalPos.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getCuentaBancariaId() != null) {
            e.setCuentaBancaria(cuentaBancariaService.findById(input.getCuentaBancariaId()).orElse(null));
        }
        if (input.getMonedaId() != null) {
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        // Se setea siempre (no solo cuando viene != null) para poder desvincular el
        // proveedor de servicio de una terminal desde el desktop.
        e.setProveedorServicio(input.getProveedorServicioId() != null
                ? proveedorServicioService.findById(input.getProveedorServicioId()).orElse(null)
                : null);
        // ⚠️ MISMA REGLA QUE EL FORMATO MAS ABAJO: si el input no los trae, NO se pisan.
        //
        // El motivo es el mismo y vale la pena no perderlo: el desktop se actualiza con
        // electron-updater, que pide consentimiento y se puede posponer indefinidamente. Despues de
        // que central suba va a haber cajas corriendo un desktop que no manda estos campos, y como
        // `m.map(input, TerminalPos.class)` arma la entidad NUEVA desde el input, todo lo que no
        // venga nace en null y se PERSISTE en null. Cualquier edicion trivial --cambiar la
        // descripcion, activar la terminal-- borraria la sucursal y la serie que alguien acaba de
        // cargar a mano sobre las 24 sucursales.
        //
        // La diferencia con el formato: aca NO hace falta un camino explicito para desvincular. Una
        // maquina se muda a otra sucursal, no a ninguna, y una serie se corrige, no se borra.
        Long sucursalId = input.getSucursalId() != null
                ? input.getSucursalId()
                : (input.getId() != null ? terminalPosRepository.findSucursalIdDe(input.getId()) : null);
        e.setSucursal(sucursalId != null
                ? sucursalService.findById(sucursalId)
                        .orElseThrow(() -> new GraphQLException("No existe la sucursal " + sucursalId + "."))
                : null);

        if (input.getSerie() == null && input.getId() != null) {
            e.setSerie(terminalPosRepository.findSerieDe(input.getId()));
        }

        // La configuracion por aparato ni siquiera esta en el input: tiene su propia mutation,
        // porque las dos son tri-estado y null significa "hereda". Pero justamente por eso hay que
        // devolverla a su lugar despues del ModelMapper, o cualquier edicion la apagaria.
        if (input.getId() != null) {
            e.setCargaManualPermitida(terminalPosRepository.findCargaManualPermitidaDe(input.getId()));
            e.setCamposObligatorios(terminalPosRepository.findCamposObligatoriosDe(input.getId()));
        }

        // ⚠️ AL REVES QUE proveedorServicio: el formato SOLO se toca si el input lo trae.
        //
        // La version anterior lo seteaba siempre, copiando el patron de arriba, y eso resultaba en
        // un borrado silencioso: el desktop de hoy NO manda formatoTerminalPosId --no existe en su
        // modelo-- asi que cualquier edicion trivial de una terminal (cambiar la descripcion,
        // activarla) dejaba formato_terminal_pos_id en NULL. Y NULL bloquea la venta con tarjeta en
        // esa caja, sin ningun aviso. Verificado con una mutation real el 2026-09-10.
        //
        // El comentario viejo justificaba el "setear siempre" citando a `mobile`, que ni siquiera
        // llama esta mutation. El cliente que rompia era el desktop vigente.
        //
        // Desvincular sigue siendo posible, pero por un camino explicito:
        // desasignarFormatoTerminalPos. Un borrado que apaga la venta de una caja tiene que ser una
        // decision, no el efecto de omitir un campo.
        //
        // Y ojo con el mecanismo: `m.map(input, TerminalPos.class)` arma una entidad NUEVA desde el
        // input, asi que no alcanza con "no setear" el campo — nace en null y se persiste en null.
        // Hay que ir a buscar el valor actual y volver a ponerlo. Primer intento de arreglo fallo
        // justamente por corregir la capa equivocada; verificado con una mutation real.
        Long formatoId = input.getFormatoTerminalPosId() != null
                ? input.getFormatoTerminalPosId()
                : (input.getId() != null ? terminalPosRepository.findFormatoTerminalPosIdDe(input.getId()) : null);
        if (formatoId != null) {
            e.setFormatoTerminalPos(formatoTerminalPosService.findById(formatoId)
                    // Si el id no existe se avisa, en vez de dejar la terminal sin formato --que se
                    // ve igual que "no se configuro todavia" y bloquea la venta.
                    .orElseThrow(() -> new GraphQLException("No existe el formato de terminal "
                            + formatoId + ".")));
        }
        return service.save(e);
    }

    /**
     * Saca el formato de una terminal. Es el unico camino para dejarla sin formato.
     * <p>
     * Existe separado de {@code saveTerminalPos} a proposito: esa mutation ya no toca el formato si
     * el input no lo trae, justamente para que omitir un campo no apague la venta con tarjeta de una
     * caja. Desvincular tiene que costar un click deliberado.
     */
    public Boolean desasignarFormatoTerminalPos(Long terminalPosId) {
        seg.requireGestionar();
        return service.findById(terminalPosId).map(t -> {
            t.setFormatoTerminalPos(null);
            service.save(t);
            return true;
        }).orElse(false);
    }

    /**
     * Configuracion por aparato: si se puede tipear el cupon a mano en esta terminal, y que campos
     * no se pueden dejar vacios.
     * <p>
     * Mutation propia y no parte de {@code saveTerminalPos}: las dos son tri-estado --{@code null}
     * = hereda la configuracion general-- y {@code saveTerminalPos} arma la entidad de cero, asi
     * que ahi apagar la configuracion de una terminal seria el efecto de omitir un campo. Aca
     * mandar la configuracion completa ES el contrato.
     */
    public TerminalPos configurarTerminalPos(Long terminalPosId, Boolean cargaManualPermitida,
                                             List<String> camposObligatorios) {
        seg.requireGestionar();
        TerminalPos t = service.findById(terminalPosId)
                .orElseThrow(() -> new GraphQLException("No existe la terminal " + terminalPosId + "."));
        return service.configurar(t, cargaManualPermitida, camposObligatorios);
    }

    public Boolean deleteTerminalPos(Long id) {
        seg.requireGestionar();
        return service.deleteById(id);
    }
}
