package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipo;
import com.franco.dev.graphql.financiero.input.CajaVirtualInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
public class CajaVirtualGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final CajaVirtualService service;
    private final SucursalService sucursalService;
    private final FuncionarioService funcionarioService;
    private final UsuarioService usuarioService;
    private final TesoreriaSecurityService seg;
    private final RrhhSecurityService rrhhSeg;

    public CajaVirtual cajaVirtual(Long id) {
        seg.requireVer();
        return service.findById(id).orElse(null);
    }

    public Page<CajaVirtual> cajaVirtuales(int page, int size) {
        seg.requireVer();
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<CajaVirtual> cajaVirtualesPorTipo(CajaVirtualTipo tipo) {
        seg.requireVer();
        return service.findByTipo(tipo);
    }

    public List<CajaVirtual> cajaVirtualesPorSucursal(Long sucursalId) {
        seg.requireVer();
        return service.findBySucursalId(sucursalId);
    }

    /**
     * Lectura compartida: la usa Tesorería y también RRHH (para elegir la caja
     * mayor destino en liquidación/aguinaldo). Habilitada para cualquier rol de
     * tesorería o de RRHH (o superusuario).
     */
    public List<CajaVirtual> cajaVirtualesActivas() {
        if (!seg.hasAnyRole(TesoreriaSecurityService.TODOS)
                && !rrhhSeg.hasAnyRole(RrhhSecurityService.TODOS)) {
            throw new GraphQLException("No autorizado: se requiere un rol de Tesorería o de RRHH.");
        }
        return service.findActivas();
    }

    public CajaVirtual saveCajaVirtual(CajaVirtualInput input) {
        seg.requireGestionar();
        CajaVirtual entity = new CajaVirtual();
        if (input.getId() != null) {
            entity = service.findById(input.getId())
                    .orElseThrow(() -> new GraphQLException("Caja virtual no encontrada: " + input.getId()));
        }
        entity.setNombre(input.getNombre());
        entity.setTipo(input.getTipo());
        entity.setDescripcion(input.getDescripcion());
        entity.setLimiteGs(input.getLimiteGs());
        entity.setActivo(input.getActivo());
        if (input.getSucursalId() != null) {
            entity.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        }
        if (input.getResponsableId() != null) {
            entity.setResponsable(funcionarioService.findById(input.getResponsableId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.save(entity);
    }

    public Boolean deleteCajaVirtual(Long id) {
        seg.requireGestionar();
        return service.deleteById(id);
    }
}
