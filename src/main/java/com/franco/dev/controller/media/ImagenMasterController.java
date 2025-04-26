package com.franco.dev.controller.media;

import com.franco.dev.domain.media.ImagenMaster;
import com.franco.dev.domain.media.enums.TipoReferencia;
import com.franco.dev.service.media.ImagenMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/imagenes")
@CrossOrigin(origins = "*")
public class ImagenMasterController {

    private final Logger logger = LoggerFactory.getLogger(ImagenMasterController.class);

    @Autowired
    private ImagenMasterService imagenMasterService;

    @GetMapping("/download/{id}")
    public ResponseEntity<?> downloadImage(@PathVariable Long id) {
        try {
            Optional<ImagenMaster> imagenOpt = imagenMasterService.findById(id);
            if (imagenOpt.isPresent()) {
                ImagenMaster imagen = imagenOpt.get();
                File file = new File(imagen.getUrl());
                
                if (file.exists()) {
                    String contentType;
                    switch (imagen.getExtension().toLowerCase()) {
                        case "png":
                            contentType = MediaType.IMAGE_PNG_VALUE;
                            break;
                        case "gif":
                            contentType = MediaType.IMAGE_GIF_VALUE;
                            break;
                        default:
                            contentType = MediaType.IMAGE_JPEG_VALUE;
                    }
                    
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + imagen.getNombre() + "." + imagen.getExtension() + "\"")
                            .body(new FileSystemResource(file));
                } else {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Image file not found");
                }
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Image not found");
            }
        } catch (Exception e) {
            logger.error("Error downloading image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error downloading image");
        }
    }

    @GetMapping("/entity/{tipo}/{id}")
    public ResponseEntity<?> getImagesByEntityTypeAndId(
            @PathVariable("tipo") String tipo,
            @PathVariable("id") Long id) {
        try {
            TipoReferencia tipoReferencia = TipoReferencia.valueOf(tipo.toUpperCase());
            List<ImagenMaster> imagenes = imagenMasterService.findByTipoReferenciaAndReferenciaId(tipoReferencia, id);
            return ResponseEntity.ok(imagenes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid entity type");
        } catch (Exception e) {
            logger.error("Error fetching images", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching images");
        }
    }

    @GetMapping("/principal/{tipo}/{id}")
    public ResponseEntity<?> getPrincipalImage(
            @PathVariable("tipo") String tipo,
            @PathVariable("id") Long id) {
        try {
            TipoReferencia tipoReferencia = TipoReferencia.valueOf(tipo.toUpperCase());
            ImagenMaster imagen = imagenMasterService.findPrincipal(tipoReferencia, id);
            
            if (imagen != null) {
                return ResponseEntity.ok(imagen);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No principal image found");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid entity type");
        } catch (Exception e) {
            logger.error("Error fetching principal image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching principal image");
        }
    }

    @GetMapping("/render/{tipo}/{id}")
    public ResponseEntity<?> renderPrincipalImage(
            @PathVariable("tipo") String tipo,
            @PathVariable("id") Long id) {
        try {
            TipoReferencia tipoReferencia = TipoReferencia.valueOf(tipo.toUpperCase());
            ImagenMaster imagen = imagenMasterService.findPrincipal(tipoReferencia, id);
            
            if (imagen != null) {
                File file = new File(imagen.getUrl());
                
                if (file.exists()) {
                    String contentType;
                    switch (imagen.getExtension().toLowerCase()) {
                        case "png":
                            contentType = MediaType.IMAGE_PNG_VALUE;
                            break;
                        case "gif":
                            contentType = MediaType.IMAGE_GIF_VALUE;
                            break;
                        default:
                            contentType = MediaType.IMAGE_JPEG_VALUE;
                    }
                    
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .body(new FileSystemResource(file));
                } else {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Image file not found");
                }
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No principal image found");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid entity type");
        } catch (Exception e) {
            logger.error("Error rendering principal image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error rendering principal image");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteImage(@PathVariable Long id) {
        try {
            boolean success = imagenMasterService.deleteById(id);
            if (success) {
                return ResponseEntity.ok().body("Image deleted successfully");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Image not found or could not be deleted");
            }
        } catch (Exception e) {
            logger.error("Error deleting image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting image");
        }
    }

    @PutMapping("/principal/{id}")
    public ResponseEntity<?> setPrincipal(@PathVariable Long id) {
        try {
            boolean success = imagenMasterService.setPrincipal(id);
            if (success) {
                return ResponseEntity.ok().body("Principal image set successfully");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Image not found");
            }
        } catch (Exception e) {
            logger.error("Error setting principal image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error setting principal image");
        }
    }
} 