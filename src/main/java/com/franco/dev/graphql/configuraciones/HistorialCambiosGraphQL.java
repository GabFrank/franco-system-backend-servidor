package com.franco.dev.graphql.configuraciones;

import com.franco.dev.service.configuraciones.HistorialCambiosService;
import com.franco.dev.service.configuraciones.HistorialCambiosService.HistorialCambio;
import com.franco.dev.service.configuraciones.HistorialCambiosService.HistorialCambioCategoria;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/** Historial de cambios del sistema contado para administrativos (pantalla «Modificaciones» del desktop). */
@Component
public class HistorialCambiosGraphQL implements GraphQLQueryResolver {

    private final HistorialCambiosService service;

    public HistorialCambiosGraphQL(HistorialCambiosService service) {
        this.service = service;
    }

    public Page<HistorialCambio> historialCambios(String fechaInicio, String fechaFin, Long usuarioId, Long productoId,
                                                  String categoria, Integer page, Integer size) {
        return service.buscar(fechaInicio, fechaFin, usuarioId, productoId, categoria, page, size);
    }

    public List<HistorialCambioCategoria> historialCambiosCategorias() {
        return service.categorias();
    }
}
