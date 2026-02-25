package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.SesionInventario;
import com.franco.dev.repository.operaciones.SesionInventarioRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.configuraciones.ModificacionService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class SesionInventarioService extends CrudService<SesionInventario, SesionInventarioRepository, Long> {

    private final Logger log = LoggerFactory.getLogger(SesionInventarioService.class);

    private final SesionInventarioRepository repository;

    @Autowired
    private ModificacionService modificacionService;

    @Override
    public SesionInventarioRepository getRepository() {
        return repository;
    }

    @Override
    public SesionInventario save(SesionInventario entity) {
        if (entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());

        SesionInventario entidadAnterior = null;
        boolean esNuevo = (entity.getId() == null);
        if (!esNuevo) {
            entidadAnterior = repository.findById(entity.getId()).orElse(null);
        }

        SesionInventario e = super.save(entity);
        repository.flush();

        try {
            if (esNuevo) {
                modificacionService.registrarInsercion(e, "INVENTARIO_SESION", "operaciones", "sesion_inventario");
            } else if (entidadAnterior != null) {
                modificacionService.registrarActualizacion(entidadAnterior, e, "INVENTARIO_SESION", "operaciones",
                        "sesion_inventario");
            }
        } catch (Exception ex) {
            log.warn("Error registrando auditoría de sesión de inventario: " + ex.getMessage());
        }

        return e;
    }

    @Override
    public Boolean deleteById(Long id) {
        try {
            SesionInventario entidad = repository.findById(id).orElse(null);
            if (entidad != null) {
                Boolean resultado = super.deleteById(id);
                try {
                    modificacionService.registrarEliminacion(entidad, "INVENTARIO_SESION", "operaciones",
                            "sesion_inventario");
                } catch (Exception ex) {
                    log.warn("Error registrando eliminación de sesión de inventario: " + ex.getMessage());
                }
                return resultado;
            }
            return super.deleteById(id);
        } catch (Exception e) {
            return false;
        }
    }
}