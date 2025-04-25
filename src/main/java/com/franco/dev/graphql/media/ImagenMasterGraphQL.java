package com.franco.dev.graphql.media;

import com.franco.dev.domain.media.ImagenMaster;
import com.franco.dev.domain.media.enums.TipoReferencia;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.media.ImagenMasterService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ImagenMasterGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ImagenMasterService imagenMasterService;
    
    @Autowired
    private UsuarioService usuarioService;
    
    // Queries
    
    public Optional<ImagenMaster> imagenMaster(Long id) {
        return imagenMasterService.findById(id);
    }
    
    public List<ImagenMaster> imagenesMaster() {
        return imagenMasterService.findAll();
    }
    
    public List<ImagenMaster> imagenesByEntityTypeAndId(String tipoReferencia, Long referenciaId) {
        try {
            TipoReferencia tipo = TipoReferencia.valueOf(tipoReferencia.toUpperCase());
            return imagenMasterService.findByTipoReferenciaAndReferenciaId(tipo, referenciaId);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid entity type: " + tipoReferencia);
        }
    }
    
    public ImagenMaster imagenPrincipal(String tipoReferencia, Long referenciaId) {
        try {
            TipoReferencia tipo = TipoReferencia.valueOf(tipoReferencia.toUpperCase());
            return imagenMasterService.findPrincipal(tipo, referenciaId);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid entity type: " + tipoReferencia);
        }
    }
    
    public String imagenAsBase64(Long id) {
        return imagenMasterService.getImageAsBase64(id);
    }
    
    public List<String> imagenesAsBase64(String tipoReferencia, Long referenciaId) {
        try {
            TipoReferencia tipo = TipoReferencia.valueOf(tipoReferencia.toUpperCase());
            return imagenMasterService.getImagesAsBase64(tipo, referenciaId);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid entity type: " + tipoReferencia);
        }
    }
    
    public String getImageWithBackwardCompatibility(String tipoReferencia, Long referenciaId) {
        try {
            TipoReferencia tipo = TipoReferencia.valueOf(tipoReferencia.toUpperCase());
            return imagenMasterService.getOrMigrateImageAsBase64(tipo, referenciaId);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid entity type: " + tipoReferencia);
        }
    }
    
    // Mutations
    
    public ImagenMaster saveImagenMaster(String base64Image, String tipoReferencia, 
                                        Long referenciaId, String nombre, Boolean principal, Long usuarioId) {
        try {
            TipoReferencia tipo = TipoReferencia.valueOf(tipoReferencia.toUpperCase());
            return imagenMasterService.saveImage(base64Image, tipo, referenciaId, nombre, principal, usuarioId);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid entity type: " + tipoReferencia);
        }
    }
    
    public Boolean deleteImagenMaster(Long id) {
        return imagenMasterService.deleteById(id);
    }
    
    public Boolean setPrincipalImagen(Long id) {
        return imagenMasterService.setPrincipal(id);
    }
    
    public ImagenMaster getOrSaveDefaultImage(String tipoReferencia, Long referenciaId, Long usuarioId) {
        try {
            TipoReferencia tipo = TipoReferencia.valueOf(tipoReferencia.toUpperCase());
            return imagenMasterService.getOrSaveDefaultImage(tipo, referenciaId, usuarioId);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid entity type: " + tipoReferencia);
        }
    }
    
    public String migrateSingleImage(String tipoReferencia, Long referenciaId) {
        try {
            TipoReferencia tipo = TipoReferencia.valueOf(tipoReferencia.toUpperCase());
            return imagenMasterService.getOrMigrateImageAsBase64(tipo, referenciaId);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid entity type: " + tipoReferencia);
        }
    }
    
    public Map<String, Object> migrateAllImagesForType(String tipoReferencia) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            TipoReferencia tipo = TipoReferencia.valueOf(tipoReferencia.toUpperCase());
            int migratedCount = imagenMasterService.migrateAllImagesForType(tipo);
            
            result.put("success", true);
            result.put("message", "Successfully migrated " + migratedCount + " images for " + tipoReferencia);
            result.put("count", migratedCount);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", "Invalid entity type: " + tipoReferencia);
            result.put("count", 0);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error during migration: " + e.getMessage());
            result.put("count", 0);
        }
        
        return result;
    }
    
} 