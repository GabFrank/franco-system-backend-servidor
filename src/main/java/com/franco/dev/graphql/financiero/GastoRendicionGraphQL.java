package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.GastoRendicion;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.graphql.financiero.input.GastoRendicionInput;
import com.franco.dev.service.financiero.GastoRendicionService;
import com.franco.dev.service.financiero.PreGastoService;
import com.franco.dev.service.financiero.TipoGastoService;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GastoRendicionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final GastoRendicionService service;
    private final PreGastoService preGastoService;
    private final TipoGastoService tipoGastoService;
    private final EnteService enteService;
    private final PersonaService personaService;
    private final UsuarioService usuarioService;

    public List<GastoRendicion> gastoRendicionesByPreGasto(Long preGastoId, Long sucursalId) {
        return service.findByPreGasto(preGastoId, sucursalId);
    }

    public GastoRendicion saveGastoRendicion(GastoRendicionInput input) {
        GastoRendicion entity = new GastoRendicion();
        if (input.getId() != null) entity.setId(input.getId());
        
        if (input.getPreGastoId() != null && input.getSucursalId() != null) {
            PreGasto pg = preGastoService.findByIdAndSucursalId(input.getPreGastoId(), input.getSucursalId());
            entity.setPreGasto(pg);
            if (input.getTipoGastoId() != null) {
                entity.setTipoGasto(tipoGastoService.findById(input.getTipoGastoId()).orElse(null));
            } else if (pg != null && pg.getTipoGasto() != null) {
                entity.setTipoGasto(pg.getTipoGasto());
            }
        } else if (input.getTipoGastoId() != null) {
            entity.setTipoGasto(tipoGastoService.findById(input.getTipoGastoId()).orElse(null));
        }
        
        entity.setMontoTotal(input.getMontoTotal());
        entity.setFotoFacturaUrl(input.getFotoFacturaUrl());
        entity.setFotoProductoUrl(input.getFotoProductoUrl());
        
        if (input.getEnteId() != null) {
            entity.setEnte(enteService.findById(input.getEnteId()).orElse(null));
        }
        
        entity.setKmActual(input.getKmActual());
        entity.setLitros(input.getLitros());
        entity.setPrecioPorLitro(input.getPrecioPorLitro());
        entity.setUbicacionProvisoria(input.getUbicacionProvisoria());
        entity.setEstablecimientoAlimentacion(input.getEstablecimientoAlimentacion());

        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }

        if (input.getFuncionariosComensalesIds() != null) {
            entity.setFuncionariosComensales(input.getFuncionariosComensalesIds().stream()
                    .map(id -> personaService.findById(id).orElse(null))
                    .collect(Collectors.toList()));
        }

        return service.save(entity);
    }

    public Boolean deleteGastoRendicion(Long id) {
        return service.deleteById(id);
    }
}
