package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Sorteo;
import com.franco.dev.domain.operaciones.SorteoSucursal;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.SorteoService;
import graphql.kickstart.tools.GraphQLResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
public class SorteoResolver implements GraphQLResolver<Sorteo> {

    private final SorteoService service;
    private final SucursalService sucursalService;

    public Boolean tieneParticipaciones(Sorteo sorteo) {
        return sorteo.getId() != null && service.tieneParticipaciones(sorteo.getId());
    }

    /** SorteoSucursal solo guarda ids: se resuelven con una sola consulta. */
    public List<Sucursal> sucursales(Sorteo sorteo) {
        List<Long> ids = new ArrayList<>();
        for (SorteoSucursal ss : sorteo.getSucursales()) ids.add(ss.getSucursalId());
        if (ids.isEmpty()) return new ArrayList<>();
        return sucursalService.getRepository().findAllById(ids);
    }
}
