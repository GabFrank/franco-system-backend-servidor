package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.repository.operaciones.InventarioProductoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.configuraciones.ModificacionService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class InventarioProductoService extends CrudService<InventarioProducto, InventarioProductoRepository, Long> {
    private final InventarioProductoRepository repository;

    @Autowired
    private ModificacionService modificacionService;

    @Override
    public InventarioProductoRepository getRepository() {
        return repository;
    }

    public List<InventarioProducto> findByInventarioId(Long id) {
        return repository.findByInventarioId(id);
    }

    public Boolean verificarUsuarioZonaAbierta(Long invId, Long usuId) {
        return repository.verificarUsuarioZona(invId, usuId).size() != 0;
    }

    @Override
    public InventarioProducto save(InventarioProducto entity) {
        InventarioProducto entidadAnterior = null;
        boolean esNuevo = (entity.getId() == null);
        if (!esNuevo) {
            entidadAnterior = repository.findById(entity.getId()).orElse(null);
        }

        InventarioProducto e = super.save(entity);
        repository.flush();

        try {
            if (esNuevo) {
                modificacionService.registrarInsercion(e, "INVENTARIO_ZONA", "operaciones", "inventario_producto");
            } else if (entidadAnterior != null) {
                modificacionService.registrarActualizacion(entidadAnterior, e, "INVENTARIO_ZONA", "operaciones",
                        "inventario_producto");
            }
        } catch (Exception ex) {
            System.err.println("Error registrando auditoría de inventario zona: " + ex.getMessage());
        }

        return e;
    }

    @Override
    public Boolean deleteById(Long id) {
        try {
            InventarioProducto entidad = repository.findById(id).orElse(null);
            if (entidad != null) {
                Boolean resultado = super.deleteById(id);
                try {
                    modificacionService.registrarEliminacion(entidad, "INVENTARIO_ZONA", "operaciones",
                            "inventario_producto");
                } catch (Exception ex) {
                    System.err.println("Error registrando eliminación de inventario zona: " + ex.getMessage());
                }
                return resultado;
            }
            return super.deleteById(id);
        } catch (Exception e) {
            return false;
        }
    }
}