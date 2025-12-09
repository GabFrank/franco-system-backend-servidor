package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.repository.productos.PrecioPorSucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PrecioPorSucursalService extends CrudService<PrecioPorSucursal, PrecioPorSucursalRepository, Long> {

    @Autowired
    private final PrecioPorSucursalRepository repository;
    @Autowired
    private final com.franco.dev.service.configuraciones.ModificacionService modificacionService;

    @Override
    public PrecioPorSucursalRepository getRepository() {
        return repository;
    }

    public List<PrecioPorSucursal> findByPresentacionId(Long id) {
        return repository.findByPresentacionId(id);
    }

    public List<PrecioPorSucursal> findBySucursalId(Long id) {
        return repository.findBySucursalId(id);
    }

    @Override
    public PrecioPorSucursal save(PrecioPorSucursal entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        
        // Obtener entidad anterior para comparar cambios (si es actualización)
        // IMPORTANTE: Obtener ANTES de guardar para tener los valores anteriores
        PrecioPorSucursal entidadAnterior = null;
        boolean esNuevo = (entity.getId() == null);
        if (!esNuevo) {
            java.util.Optional<PrecioPorSucursal> precioOpt = repository.findById(entity.getId());
            if (precioOpt != null && precioOpt.isPresent()) {
                entidadAnterior = precioOpt.get();
            }
        }
        
        PrecioPorSucursal p = super.save(entity);
        repository.flush(); // Asegurar que se guarde antes de registrar la modificación
        
        // Registrar modificación sin afectar la lógica existente
        try {
            if (esNuevo) {
                // Es una inserción
                modificacionService.registrarInsercion(p, "PRECIO_POR_SUCURSAL", "productos", "precio_por_sucursal");
            } else if (entidadAnterior != null) {
                // Es una actualización
                modificacionService.registrarActualizacion(entidadAnterior, p, "PRECIO_POR_SUCURSAL", "productos", "precio_por_sucursal");
            }
        } catch (Exception ex) {
            // No interrumpir el flujo si falla el registro de modificación
            System.err.println("Error registrando modificación de precio por sucursal: " + ex.getMessage());
            ex.printStackTrace();
        }
        
        return p;
    }

    @Override
    @javax.transaction.Transactional
    public Boolean deleteById(Long id) {
        try {
            // Obtener entidad antes de eliminar para registrar la modificación
            PrecioPorSucursal entidad = repository.findById(id).orElse(null);
            if (entidad != null) {
                Boolean resultado = super.deleteById(id);
                // Registrar eliminación sin afectar la lógica existente
                try {
                    modificacionService.registrarEliminacion(entidad, "PRECIO_POR_SUCURSAL", "productos", "precio_por_sucursal");
                } catch (Exception ex) {
                    // No interrumpir el flujo si falla el registro de modificación
                    System.err.println("Error registrando eliminación de precio por sucursal: " + ex.getMessage());
                }
                return resultado;
            }
            return super.deleteById(id);
        } catch (Exception e) {
            return false;
        }
    }

    public PrecioPorSucursal findPrincipalByPrecionacionId(Long id) {
        return repository.findPrincipalByPresentacionId(id);
    }

    public PrecioPorSucursal findBySucursalIdAndPresentacionId(Long sucId, Long preId) {
        return repository.findBySucursalIdAndPresentacionId(sucId, preId);
    }
}
