package com.franco.dev.graphql.activos.resolver;

import com.franco.dev.domain.activos.Inmueble;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.domain.financiero.EnteVinculacion;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.financiero.EnteVinculacionService;
import graphql.kickstart.tools.GraphQLResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@AllArgsConstructor
public class InmuebleResolver implements GraphQLResolver<Inmueble> {

    private final EnteService enteService;
    private final EnteVinculacionService enteVinculacionService;

    public List<EnteVinculacion> vinculacionesSucursal(Inmueble inmueble) {
        if (inmueble == null || inmueble.getId() == null) {
            return Collections.emptyList();
        }
        return enteService.findByTipoEnteAndReferenciaId(TipoEnte.INMUEBLE, inmueble.getId())
                .map(ente -> enteVinculacionService.findByEnteId(ente.getId()))
                .orElse(Collections.emptyList());
    }
}
