package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FormatoQrPos;
import com.franco.dev.graphql.financiero.input.FormatoQrPosInput;
import com.franco.dev.service.financiero.FormatoQrPosService;
import com.franco.dev.service.personas.ProveedorServicioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * ABM de los formatos de QR de POS. Vive solo en central: la tabla es MAIN_TO_ALL y un formato
 * editable desde una sucursal se desincronizaria del resto de la flota.
 * <p>
 * No hay delete fisico. Un formato borrado dejaria sin explicacion los venta_tarjeta que ya
 * completo, asi que se desactiva.
 */
@Component
public class FormatoQrPosGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FormatoQrPosService service;

    @Autowired
    private ProveedorServicioService proveedorServicioService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<FormatoQrPos> formatoQrPos(Long id) {
        return service.findById(id);
    }

    public List<FormatoQrPos> formatosQrPos() {
        return service.findTodos();
    }

    public List<FormatoQrPos> formatosQrPosActivos() {
        return service.findActivos();
    }

    public FormatoQrPos saveFormatoQrPos(FormatoQrPosInput input) {
        FormatoQrPos e = input.getId() != null
                ? service.findById(input.getId()).orElse(new FormatoQrPos())
                : new FormatoQrPos();
        e.setId(input.getId());
        e.setNombre(input.getNombre());
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

    public Boolean desactivarFormatoQrPos(Long id) {
        return service.findById(id).map(e -> {
            e.setActivo(false);
            service.save(e);
            return true;
        }).orElse(false);
    }
}
