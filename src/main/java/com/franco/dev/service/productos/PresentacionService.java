package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.repository.productos.PresentacionRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.utils.ImageService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class PresentacionService extends CrudService<Presentacion, PresentacionRepository, Long> {

    private final PresentacionRepository repository;

    @Autowired
    private ImageService imageService;
    @Autowired
    private com.franco.dev.service.configuraciones.ModificacionService modificacionService;

    @Override
    public PresentacionRepository getRepository() {
        return repository;
    }
    public List<Presentacion> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto.toUpperCase())
                .stream()
                .sorted(Comparator.comparing(Presentacion::getCantidad))
                .collect(Collectors.toList());
    }
    public List<Presentacion> findByProductoIdOrderByCantidadAsc(Long id){
        return repository.findByProductoIdOrderByCantidadAsc(id);
    }
    public List<Presentacion> findByProductoId(Long id){
        return findByProductoIdOrderByCantidadAsc(id);
    }

    @Override
    public Presentacion save(Presentacion entity){
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        
        // Obtener entidad anterior para comparar cambios (si es actualización)
        // IMPORTANTE: Obtener ANTES de guardar para tener los valores anteriores
        Presentacion entidadAnterior = null;
        boolean esNuevo = (entity.getId() == null);
        if (!esNuevo) {
            java.util.Optional<Presentacion> presentacionOpt = repository.findById(entity.getId());
            if (presentacionOpt != null && presentacionOpt.isPresent()) {
                entidadAnterior = presentacionOpt.get();
            }
        }
        
        Presentacion e = super.save(entity);
//        personaPublisher.publish(p);
        repository.flush(); // Asegurar que se guarde antes de registrar la modificación
        
        // Registrar modificación sin afectar la lógica existente
        try {
            if (esNuevo) {
                // Es una inserción
                modificacionService.registrarInsercion(e, "PRESENTACION", "productos", "presentacion");
            } else if (entidadAnterior != null) {
                // Es una actualización
                modificacionService.registrarActualizacion(entidadAnterior, e, "PRESENTACION", "productos", "presentacion");
            }
        } catch (Exception ex) {
            // No interrumpir el flujo si falla el registro de modificación
            System.err.println("Error registrando modificación de presentación: " + ex.getMessage());
            ex.printStackTrace();
        }
        
        return e;
    }

    @Override
    @javax.transaction.Transactional
    public Boolean deleteById(Long id) {
        try {
            // Obtener entidad antes de eliminar para registrar la modificación
            Presentacion entidad = repository.findById(id).orElse(null);
            if (entidad != null) {
                Boolean resultado = super.deleteById(id);
                // Registrar eliminación sin afectar la lógica existente
                try {
                    modificacionService.registrarEliminacion(entidad, "PRESENTACION", "productos", "presentacion");
                } catch (Exception ex) {
                    // No interrumpir el flujo si falla el registro de modificación
                    System.err.println("Error registrando eliminación de presentación: " + ex.getMessage());
                }
                return resultado;
            }
            return super.deleteById(id);
        } catch (Exception e) {
            return false;
        }
    }

    public Presentacion findByPrincipalAndProductoId(Boolean principal, Long id){
        return repository.findByPrincipalAndProductoId(principal, id);
    }

    public void enviarImagenes(Long sucId){
        List<Presentacion> presentacionList = findAll2();
        for(Presentacion p: presentacionList){
            String image = imageService.getImageWithMediaType(p.getId()+".jpg", imageService.getImagePresentaciones());
            if(image!="") {
                log.info("Imagen encontrada: " + imageService.getImagePresentaciones()+p.getId()+".jpg");
//                propagacionService.propagarImagen(image, p.getId() + ".jpg", TipoEntidad.PRESENTACION, sucId);
            } else {
//                log.info("Imagen no encontrada: " + imageService.getImagePresentaciones()+p.getId()+".jpg");
            }
        }
    }
}