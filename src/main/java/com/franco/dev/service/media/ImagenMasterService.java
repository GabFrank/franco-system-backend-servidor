package com.franco.dev.service.media;

import com.franco.dev.domain.media.ImagenMaster;
import com.franco.dev.domain.media.enums.TipoReferencia;
import com.franco.dev.repository.media.ImagenMasterRepository;
import com.franco.dev.service.utils.ImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ImagenMasterService {

    private final Logger logger = LoggerFactory.getLogger(ImagenMasterService.class);
    
    @Autowired
    private ImagenMasterRepository repository;
    
    @Autowired
    private Environment env;
    
    @Autowired
    private ImageService imageService;
    
    public ImagenMaster save(ImagenMaster imagenMaster) {
        imagenMaster.setCreadoEn(LocalDateTime.now());
        return repository.save(imagenMaster);
    }
    
    public Optional<ImagenMaster> findById(Long id) {
        return repository.findById(id);
    }
    
    public List<ImagenMaster> findAll() {
        return repository.findAll();
    }
    
    public List<ImagenMaster> findByTipoReferenciaAndReferenciaId(TipoReferencia tipoReferencia, Long referenciaId) {
        List<ImagenMaster> imagenes = repository.findByTipoReferenciaAndReferenciaId(tipoReferencia, referenciaId);
        
        // If no images found in new system, try to migrate from old system
        if (imagenes.isEmpty()) {
            ImagenMaster migratedImage = migrateFromOldSystem(tipoReferencia, referenciaId, null);
            if (migratedImage != null) {
                imagenes = new ArrayList<>();
                imagenes.add(migratedImage);
            }
        }
        
        return imagenes;
    }
    
    public ImagenMaster findPrincipal(TipoReferencia tipoReferencia, Long referenciaId) {
        ImagenMaster imagen = repository.findPrincipalByTipoReferenciaAndReferenciaId(tipoReferencia, referenciaId);
        
        // If not found in new system, try to migrate from old system
        if (imagen == null) {
            imagen = migrateFromOldSystem(tipoReferencia, referenciaId, true);
        }
        
        return imagen;
    }
    
    public boolean deleteById(Long id) {
        Optional<ImagenMaster> imagen = findById(id);
        if (imagen.isPresent()) {
            try {
                // Delete physical file
                File file = new File(imagen.get().getUrl());
                if (file.exists()) {
                    file.delete();
                }
                // Delete from database
                repository.deleteById(id);
                return true;
            } catch (Exception e) {
                logger.error("Error deleting image with id " + id, e);
                return false;
            }
        }
        return false;
    }
    
    public List<ImagenMaster> findByUsuarioId(Long id) {
        return repository.findByUsuarioId(id);
    }
    
    public String calculateChecksum(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Error calculating checksum", e);
            return null;
        }
    }
    
    public String getRootImagePath() {
        String configuredPath = env.getProperty("image.root.path");
        if (configuredPath != null && !configuredPath.isEmpty()) {
            return configuredPath;
        }
        return imageService.getHomePath() + "/FRC/";
    }
    
    public String getEntityPath(TipoReferencia tipoReferencia) {
        return tipoReferencia.name().toLowerCase() + "/";
    }
    
    public String generateImageUrl(TipoReferencia tipoReferencia, Long referenciaId, Long imagenId, String extension) {
        return getRootImagePath() + getEntityPath(tipoReferencia) + referenciaId + "/image-" + imagenId + "." + extension;
    }
    
    public ImagenMaster saveImage(String base64Image, TipoReferencia tipoReferencia, Long referenciaId, 
                                 String nombre, Boolean principal, Long usuarioId) {
        try {
            // Convert base64 to MultipartFile
            MultipartFile file = ImageService.converter(base64Image);
            
            // Create image entity
            ImagenMaster imagenMaster = new ImagenMaster();
            imagenMaster.setTipoReferencia(tipoReferencia);
            imagenMaster.setReferenciaId(referenciaId);
            imagenMaster.setNombre(nombre);
            imagenMaster.setPrincipal(principal);
            imagenMaster.setCreadoEn(LocalDateTime.now());
            //set fake url
            imagenMaster.setUrl("https://www.google.com");
            
            // Set extension from file name or content type
            String contentType = file.getContentType();
            String extension = "jpg"; // default
            if (contentType != null) {
                if (contentType.contains("jpeg") || contentType.contains("jpg")) {
                    extension = "jpg";
                } else if (contentType.contains("png")) {
                    extension = "png";
                } else if (contentType.contains("gif")) {
                    extension = "gif";
                }
            }
            imagenMaster.setExtension(extension);
            
            // Calculate checksum
            imagenMaster.setChecksum(calculateChecksum(file.getBytes()));
            
            // Save to database to get ID
            imagenMaster = save(imagenMaster);
            
            // Generate file path and URL
            String url = generateImageUrl(tipoReferencia, referenciaId, imagenMaster.getId(), extension);
            imagenMaster.setUrl(url);
            
            // Ensure directory exists
            String directory = url.substring(0, url.lastIndexOf("/"));
            File dir = new File(directory);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // Save physical file
            Path path = Paths.get(url);
            Files.copy(file.getInputStream(), path);
            
            // Update URL in database
            return save(imagenMaster);
            
        } catch (Exception e) {
            logger.error("Error saving image", e);
            return null;
        }
    }
    
    public String getImageAsBase64(Long id) {
        Optional<ImagenMaster> imagenOpt = findById(id);
        if (imagenOpt.isPresent()) {
            ImagenMaster imagen = imagenOpt.get();
            try {
                return imageService.fileToBase64(new File(imagen.getUrl()));
            } catch (Exception e) {
                logger.error("Error converting image to base64", e);
                return null;
            }
        }
        return null;
    }
    
    public List<String> getImagesAsBase64(TipoReferencia tipoReferencia, Long referenciaId) {
        List<ImagenMaster> imagenes = findByTipoReferenciaAndReferenciaId(tipoReferencia, referenciaId);
        List<String> base64Images = new ArrayList<>();
        
        for (ImagenMaster imagen : imagenes) {
            try {
                String base64 = imageService.fileToBase64(new File(imagen.getUrl()));
                if (base64 != null) {
                    base64Images.add(base64);
                }
            } catch (Exception e) {
                logger.error("Error converting image to base64", e);
            }
        }
        
        return base64Images;
    }
    
    public boolean setPrincipal(Long id) {
        Optional<ImagenMaster> imagenOpt = findById(id);
        if (imagenOpt.isPresent()) {
            ImagenMaster imagen = imagenOpt.get();
            
            // Reset all images of this entity to non-principal
            List<ImagenMaster> imagenes = findByTipoReferenciaAndReferenciaId(
                    imagen.getTipoReferencia(), imagen.getReferenciaId());
            
            for (ImagenMaster img : imagenes) {
                img.setPrincipal(false);
                save(img);
            }
            
            // Set this image as principal
            imagen.setPrincipal(true);
            save(imagen);
            return true;
        }
        return false;
    }
    
    public ImagenMaster getOrSaveDefaultImage(TipoReferencia tipoReferencia, Long referenciaId, Long usuarioId) {
        // Check if there's already a principal image
        ImagenMaster principal = findPrincipal(tipoReferencia, referenciaId);
        if (principal != null) {
            return principal;
        }
        
        // Check if there's any image
        List<ImagenMaster> imagenes = findByTipoReferenciaAndReferenciaId(tipoReferencia, referenciaId);
        if (!imagenes.isEmpty()) {
            // Set the first one as principal
            ImagenMaster imagen = imagenes.get(0);
            imagen.setPrincipal(true);
            return save(imagen);
        }
        
        // Try to migrate from old system first
        ImagenMaster migratedImage = migrateFromOldSystem(tipoReferencia, referenciaId, true);
        if (migratedImage != null) {
            return migratedImage;
        }
        
        // No images - add a default placeholder based on entity type
        try {
            // Create a basic placeholder image path
            String defaultImagePath = getRootImagePath() + "default/" + tipoReferencia.name().toLowerCase() + ".jpg";
            File defaultImage = new File(defaultImagePath);
            
            if (defaultImage.exists()) {
                // Convert to base64
                String base64 = imageService.fileToBase64(defaultImage);
                if (base64 != null) {
                    // Save as new image
                    return saveImage(base64, tipoReferencia, referenciaId, "Default", true, usuarioId);
                }
            }
        } catch (Exception e) {
            logger.error("Error creating default image", e);
        }
        
        return null;
    }
    
    /**
     * Migrates an image from the old system to the new one.
     * @param tipoReferencia The type of entity
     * @param referenciaId The ID of the entity
     * @param setPrincipal Whether to set the image as principal
     * @return The migrated image, or null if not found
     */
    private ImagenMaster migrateFromOldSystem(TipoReferencia tipoReferencia, Long referenciaId, Boolean setPrincipal) {
        try {
            String oldImageBase64 = null;
            String imageName = null;
            
            // Attempt to get image from old system based on entity type
            switch (tipoReferencia) {
                case PRESENTACION:
                    // Try to get presentacion image from old system
                    oldImageBase64 = imageService.getImageWithMediaType(referenciaId + ".jpg", 
                            imageService.getImagePresentaciones());
                    imageName = "Presentacion " + referenciaId;
                    break;
                case PERSONA:
                    // Try to get persona image from old system
                    oldImageBase64 = imageService.getImageWithMediaType("persona_" + referenciaId + ".jpg", 
                            imageService.getImagePath());
                    imageName = "Persona " + referenciaId;
                    break;
                case SUCURSAL:
                    // Try to get sucursal image from old system
                    oldImageBase64 = imageService.getImageWithMediaType("sucursal_" + referenciaId + ".jpg", 
                            imageService.getImagePath());
                    imageName = "Sucursal " + referenciaId;
                    break;
                case FACTURA_LEGAL:
                    // Try to get factura legal image from old system
                    oldImageBase64 = imageService.getImageWithMediaType("factura_" + referenciaId + ".jpg", 
                            imageService.getImagePath());
                    imageName = "Factura " + referenciaId;
                    break;
                case PRODUCTO:
                    // Try to get producto image from old system
                    oldImageBase64 = imageService.getImageWithMediaType("producto_" + referenciaId + ".jpg", 
                            imageService.getImagePath());
                    imageName = "Producto " + referenciaId;
                    break;
                case USUARIO:
                    // Try to get usuario image from old system
                    oldImageBase64 = imageService.getImageWithMediaType("usuario_" + referenciaId + ".jpg", 
                            imageService.getImagePath());
                    imageName = "Usuario " + referenciaId;
                    break;
                case CLIENTE:
                    // Try to get cliente image from old system
                    oldImageBase64 = imageService.getImageWithMediaType("cliente_" + referenciaId + ".jpg", 
                            imageService.getImagePath());
                    imageName = "Cliente " + referenciaId;
                    break;
                case PROVEEDOR:
                    // Try to get proveedor image from old system
                    oldImageBase64 = imageService.getImageWithMediaType("proveedor_" + referenciaId + ".jpg", 
                            imageService.getImagePath());
                    imageName = "Proveedor " + referenciaId;
                    break;
                default:
                    logger.warn("No migration path defined for entity type: " + tipoReferencia);
                    break;
            }
            
            // If image was found in old system, save it to new system
            if (oldImageBase64 != null && !oldImageBase64.contains("iVBORw0KGgoAAAANSUhEUgAAAMgAAADICAYAAACtWK6eAAAXwElEQVR42uxdDZRdVXXeoyQSQPAFiBCJhhFQtAoyUUBEBCYC4tKAvFhqsegqk64WwVbpRAEtFu1EpL8WYYooWJH")) {
                logger.info("Migrating image from old system for " + tipoReferencia + " with ID " + referenciaId);
                return saveImage(oldImageBase64, tipoReferencia, referenciaId, imageName, 
                        setPrincipal != null ? setPrincipal : true, null);
            }
            
            // Try additional legacy locations or naming conventions if needed for specific entity types
            if (tipoReferencia == TipoReferencia.PRESENTACION) {
                // Check thumbnail location for presentacion images
                oldImageBase64 = imageService.getImageWithMediaType(referenciaId + ".jpg", 
                        imageService.getImagePresentacionesThumb());
                if (oldImageBase64 != null && !oldImageBase64.contains("iVBORw0KGgoAAAANSUhEUgAAAMgAAADICAYAAACtWK6eAAAXwElEQVR42uxdDZRdVXXeoyQSQPAFiBCJhhFQtAoyUUBEBCYC4tKAvFhqsegqk64WwVbpRAEtFu1EpL8WYYooWJH")) {
                    logger.info("Migrating thumbnail image from old system for Presentacion with ID " + referenciaId);
                    return saveImage(oldImageBase64, tipoReferencia, referenciaId, "Presentacion Thumbnail " + referenciaId, 
                            setPrincipal != null ? setPrincipal : true, null);
                }
            }
        } catch (Exception e) {
            logger.error("Error migrating image from old system for " + tipoReferencia + " with ID " + referenciaId, e);
        }
        
        return null;
    }
    
    /**
     * Batch migrates all images for a specific entity type from the old system
     * @param tipoReferencia The type of entity
     * @return Number of images migrated
     */
    public int migrateAllImagesForType(TipoReferencia tipoReferencia) {
        int migratedCount = 0;
        String oldPath = "";
        String prefix = "";
        String suffix = ".jpg";
        
        // Determine the old path and prefix based on entity type
        switch (tipoReferencia) {
            case PRESENTACION:
                oldPath = imageService.getImagePresentaciones();
                break;
            case PERSONA:
                oldPath = imageService.getImagePath();
                prefix = "persona_";
                break;
            case SUCURSAL:
                oldPath = imageService.getImagePath();
                prefix = "sucursal_";
                break;
            case FACTURA_LEGAL:
                oldPath = imageService.getImagePath();
                prefix = "factura_";
                break;
            case PRODUCTO:
                oldPath = imageService.getImagePath();
                prefix = "producto_";
                break;
            case USUARIO:
                oldPath = imageService.getImagePath();
                prefix = "usuario_";
                break;
            case CLIENTE:
                oldPath = imageService.getImagePath();
                prefix = "cliente_";
                break;
            case PROVEEDOR:
                oldPath = imageService.getImagePath();
                prefix = "proveedor_";
                break;
            default:
                logger.warn("No migration path defined for entity type: " + tipoReferencia);
                return 0;
        }
        
        File directory = new File(oldPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().startsWith(prefix) && file.getName().endsWith(suffix)) {
                        try {
                            // Extract ID from filename
                            String fileName = file.getName();
                            String idStr = fileName.substring(prefix.length(), fileName.length() - suffix.length());
                            Long referenciaId = Long.parseLong(idStr);
                            
                            // Check if image already exists in new system
                            if (findByTipoReferenciaAndReferenciaId(tipoReferencia, referenciaId).isEmpty()) {
                                // Get image as base64
                                String base64 = imageService.fileToBase64(file);
                                if (base64 != null) {
                                    // Save to new system
                                    String imageName = tipoReferencia.name() + " " + referenciaId;
                                    ImagenMaster savedImage = saveImage(base64, tipoReferencia, referenciaId, imageName, true, null);
                                    if (savedImage != null) {
                                        migratedCount++;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.error("Error migrating image: " + file.getName(), e);
                        }
                    }
                }
            }
        }
        
        return migratedCount;
    }
    
    /**
     * Gets an image in Base64 format, first checking the new system and then falling back to the old system if needed.
     * If an image is found in the old system, it is automatically migrated to the new system.
     * 
     * @param tipoReferencia Type of entity
     * @param referenciaId ID of the entity
     * @return Base64 encoded image or null if not found
     */
    public String getOrMigrateImageAsBase64(TipoReferencia tipoReferencia, Long referenciaId) {
        // First check if we have this image in the new system
        List<ImagenMaster> imagenes = findByTipoReferenciaAndReferenciaId(tipoReferencia, referenciaId);
        if (!imagenes.isEmpty()) {
            ImagenMaster principalImagen = null;
            
            // Look for principal image first
            for (ImagenMaster imagen : imagenes) {
                if (imagen.getPrincipal() != null && imagen.getPrincipal()) {
                    principalImagen = imagen;
                    break;
                }
            }
            
            // If no principal image, use the first one
            if (principalImagen == null && !imagenes.isEmpty()) {
                principalImagen = imagenes.get(0);
            }
            
            if (principalImagen != null) {
                try {
                    return imageService.fileToBase64(new File(principalImagen.getUrl()));
                } catch (Exception e) {
                    logger.error("Error converting image to base64", e);
                }
            }
        }
        
        // If not found in new system, try to migrate from old system
        ImagenMaster migratedImage = migrateFromOldSystem(tipoReferencia, referenciaId, true);
        if (migratedImage != null) {
            try {
                return imageService.fileToBase64(new File(migratedImage.getUrl()));
            } catch (Exception e) {
                logger.error("Error converting migrated image to base64", e);
            }
        }
        
        return null;
    }
    
    /**
     * Gets the path to the images in the old system based on entity type
     * @param tipoReferencia The type of entity
     * @return The path in the old system
     */
    public String getOldSystemPath(TipoReferencia tipoReferencia) {
        switch (tipoReferencia) {
            case PRESENTACION:
                return imageService.getImagePresentaciones();
            case PERSONA:
            case SUCURSAL:
            case FACTURA_LEGAL:
            case PRODUCTO:
            case USUARIO:
            case CLIENTE:
            case PROVEEDOR:
                return imageService.getImagePath();
            default:
                return imageService.getImagePath();
        }
    }
} 