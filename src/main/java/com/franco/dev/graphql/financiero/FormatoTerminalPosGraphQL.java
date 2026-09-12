package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.graphql.financiero.input.FormatoTerminalPosInput;
import com.franco.dev.service.financiero.FormatoTerminalPosService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.ProveedorServicioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * ABM de los formatos de terminal POS. Vive solo en central: la tabla es MAIN_TO_ALL y un formato
 * editable desde una sucursal se desincronizaria del resto de la flota.
 * <p>
 * No hay delete fisico. Un formato borrado dejaria sin explicacion los {@code venta_tarjeta} que ya
 * completo, y ademas la FK desde {@code terminal_pos} lo impediria. Se desactiva — y ni siquiera
 * eso si tiene terminales asignadas.
 * <p>
 * <b>Seguridad a mano, como todo este repo.</b> No existe {@code @PreAuthorize} en todo
 * {@code src/main/java} y {@code @AdminSecured} esta roto de punta a punta (issue #177), asi que
 * cada metodo inyecta {@link TesoreriaSecurityService} y llama {@code requireVer()} o
 * {@code requireGestionar()} como primera linea. {@code FormatoQrPosGraphQL}, de donde sale este
 * ABM, quedo sin ese chequeo — es un hueco previo, no una convencion a imitar.
 */
@Component
public class FormatoTerminalPosGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FormatoTerminalPosService service;

    @Autowired
    private ProveedorServicioService proveedorServicioService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private TesoreriaSecurityService seg;

    public Optional<FormatoTerminalPos> formatoTerminalPos(Long id) {
        seg.requireVer();
        return service.findById(id);
    }

    public List<FormatoTerminalPos> formatosTerminalPos() {
        seg.requireVer();
        return service.findTodos();
    }

    public List<FormatoTerminalPos> formatosTerminalPosActivos() {
        seg.requireVer();
        return service.findActivos();
    }

    public List<FormatoTerminalPos> formatosTerminalPosPorProveedor(Long proveedorServicioId) {
        seg.requireVer();
        return service.findPorProveedor(proveedorServicioId);
    }

    /**
     * Cuantas terminales usan este formato. La pantalla lo necesita para poder avisar ANTES de que
     * el usuario intente desactivarlo y se coma un rechazo.
     */
    public Long terminalesQueUsanFormato(Long id) {
        seg.requireVer();
        return service.cuantasTerminalesUsan(id);
    }

    public FormatoTerminalPos saveFormatoTerminalPos(FormatoTerminalPosInput input) {
        seg.requireGestionar();
        FormatoTerminalPos e = input.getId() != null
                ? service.findById(input.getId()).orElse(new FormatoTerminalPos())
                : new FormatoTerminalPos();
        e.setId(input.getId());
        e.setNombre(input.getNombre());
        e.setTipo(input.getTipo());
        e.setPatron(input.getPatron());
        e.setMapeo(input.getMapeo());
        e.setEjemplo(input.getEjemplo());
        e.setActivo(input.getActivo() != null ? input.getActivo() : Boolean.TRUE);
        e.setProveedorServicio(input.getProveedorServicioId() != null
                ? proveedorServicioService.findById(input.getProveedorServicioId()).orElse(null)
                : null);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.save(e);
    }

    public Boolean desactivarFormatoTerminalPos(Long id) {
        seg.requireGestionar();
        return service.desactivar(id);
    }
}
