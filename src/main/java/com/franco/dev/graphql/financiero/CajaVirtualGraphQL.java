package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipo;
import com.franco.dev.graphql.financiero.input.CajaVirtualInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
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

    public CajaVirtual cajaVirtual(Long id) {
        return service.findById(id).orElse(null);
    }

    public Page<CajaVirtual> cajaVirtuales(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<CajaVirtual> cajaVirtualesPorTipo(CajaVirtualTipo tipo) {
        return service.findByTipo(tipo);
    }

    public List<CajaVirtual> cajaVirtualesPorSucursal(Long sucursalId) {
        return service.findBySucursalId(sucursalId);
    }

    public List<CajaVirtual> cajaVirtualesActivas() {
        return service.findActivas();
    }

    public CajaVirtual saveCajaVirtual(CajaVirtualInput input) {
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
        return service.deleteById(id);
    }
}
