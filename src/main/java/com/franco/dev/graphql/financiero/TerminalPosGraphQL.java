package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.graphql.financiero.input.TerminalPosInput;
import com.franco.dev.service.financiero.CuentaBancariaService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.FormatoTerminalPosService;
import com.franco.dev.service.financiero.TerminalPosService;
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

    /** Para leer el formato actual sin traer la terminal entera. Ver saveTerminalPos. */
    @Autowired
    private TerminalPosRepository terminalPosRepository;

    public Optional<TerminalPos> terminalPos(Long id) {
        return service.findById(id);
    }

    public List<TerminalPos> terminalesPos(int page, int size) {
        return service.findAll2();
    }

    public List<TerminalPos> searchTerminalPos(String texto) {
        return service.searchByAll(texto);
    }

    public Page<TerminalPos> filterTerminalPos(String descripcion, String codigo, Boolean activo, int page, int size) {
        return service.filter(descripcion, codigo, activo, page, size);
    }

    public Long countTerminalPos() {
        return service.count();
    }

    public TerminalPos saveTerminalPos(TerminalPosInput input) {
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
        return service.findById(terminalPosId).map(t -> {
            t.setFormatoTerminalPos(null);
            service.save(t);
            return true;
        }).orElse(false);
    }

    public Boolean deleteTerminalPos(Long id) {
        return service.deleteById(id);
    }
}
