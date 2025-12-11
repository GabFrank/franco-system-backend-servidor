package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuraciones.ModificacionDetalle;
import com.franco.dev.domain.configuraciones.ModificacionRegistro;
import com.franco.dev.repository.configuraciones.ModificacionDetalleRepository;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ModificacionResolver implements GraphQLResolver<ModificacionRegistro> {

    @Autowired
    private ModificacionDetalleRepository detalleRepository;

    public List<ModificacionDetalle> detalles(ModificacionRegistro modificacionRegistro) {
        return detalleRepository.findByModificacionRegistroId(modificacionRegistro.getId());
    }
}

