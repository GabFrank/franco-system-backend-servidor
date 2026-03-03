package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItemVariacion;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemVariacionService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.kickstart.tools.GraphQLMutationResolver;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
public class RecepcionMercaderiaItemVariacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private RecepcionMercaderiaItemVariacionService recepcionMercaderiaItemVariacionService;

    /**
     * Elimina una variación de recepción de mercadería por ID
     * @param id ID de la variación a eliminar
     * @return true si se eliminó correctamente, false en caso contrario
     */
    public Boolean deleteRecepcionMercaderiaItemVariacion(Long id) {
        try {
            if (id == null) {
                return false;
            }
            
            // Eliminar la variación directamente
            return recepcionMercaderiaItemVariacionService.deleteById(id);
        } catch (Exception e) {
            // Log del error para debugging
            System.err.println("Error al eliminar variación con ID " + id + ": " + e.getMessage());
            return false;
        }
    }
}
