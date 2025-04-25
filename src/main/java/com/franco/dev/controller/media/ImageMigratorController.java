package com.franco.dev.controller.media;

import com.franco.dev.domain.media.enums.TipoReferencia;
import com.franco.dev.service.media.ImagenMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller to handle administrative operations for migrating images
 * from the old system to the new one.
 */
@RestController
@RequestMapping("/api/imagen-migration")
@CrossOrigin(origins = "*")
public class ImageMigratorController {

    private final Logger logger = LoggerFactory.getLogger(ImageMigratorController.class);

    @Autowired
    private ImagenMasterService imagenMasterService;

    /**
     * Migrate a single image from the old system to the new one.
     * 
     * @param tipo Entity type
     * @param id Entity ID
     * @return Result of the migration operation
     */
    @PostMapping("/migrate-single/{tipo}/{id}")
    public ResponseEntity<?> migrateSingleImage(
            @PathVariable("tipo") String tipo,
            @PathVariable("id") Long id) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            TipoReferencia tipoReferencia = TipoReferencia.valueOf(tipo.toUpperCase());
            String imageBase64 = imagenMasterService.getOrMigrateImageAsBase64(tipoReferencia, id);
            
            if (imageBase64 != null) {
                response.put("success", true);
                response.put("message", "Image migrated successfully");
                response.put("base64Data", imageBase64);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "No image found to migrate");
                return ResponseEntity.ok(response);
            }
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", "Invalid entity type: " + tipo);
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error during image migration", e);
            response.put("success", false);
            response.put("message", "Error during migration: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Migrate all images of a specific entity type from the old system to the new one.
     * 
     * @param tipo Entity type
     * @return Result of the batch migration operation
     */
    @PostMapping("/migrate-all/{tipo}")
    public ResponseEntity<?> migrateAllImagesForType(@PathVariable("tipo") String tipo) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            TipoReferencia tipoReferencia = TipoReferencia.valueOf(tipo.toUpperCase());
            int migratedCount = imagenMasterService.migrateAllImagesForType(tipoReferencia);
            
            response.put("success", true);
            response.put("message", "Migration completed for " + tipo);
            response.put("migratedCount", migratedCount);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", "Invalid entity type: " + tipo);
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error during batch image migration", e);
            response.put("success", false);
            response.put("message", "Error during migration: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get status of migration by checking sample entities
     * 
     * @return Status for each entity type
     */
    @GetMapping("/status")
    public ResponseEntity<?> getMigrationStatus() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> statusByType = new HashMap<>();
        
        try {
            for (TipoReferencia tipo : TipoReferencia.values()) {
                String typeName = tipo.name().toLowerCase();
                boolean hasOldImages = false;
                boolean hasNewImages = false;
                
                // Check for entity-specific status
                switch (tipo) {
                    case PRESENTACION:
                        // Check if old path exists and has files
                        hasOldImages = new java.io.File(imagenMasterService.getOldSystemPath(tipo)).exists();
                        // Check if there are any images in the new system
                        hasNewImages = !imagenMasterService.findByTipoReferenciaAndReferenciaId(tipo, 1L).isEmpty();
                        break;
                    // Other types follow similar pattern
                    default:
                        // Generic check
                        hasOldImages = true; // We can't easily check this generically
                        hasNewImages = !imagenMasterService.findAll().isEmpty();
                }
                
                Map<String, Object> typeStatus = new HashMap<>();
                typeStatus.put("hasOldImages", hasOldImages);
                typeStatus.put("hasNewImages", hasNewImages);
                
                statusByType.put(typeName, typeStatus);
            }
            
            response.put("status", statusByType);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting migration status", e);
            response.put("success", false);
            response.put("message", "Error getting migration status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
} 