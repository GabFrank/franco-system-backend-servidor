package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.enums.InventarioEstado;
import com.franco.dev.print.operaciones.MovimientoPrintService;
import com.franco.dev.repository.operaciones.InventarioRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.configuraciones.ModificacionService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class InventarioService extends CrudService<Inventario, InventarioRepository, Long> {

    private final Logger log = LoggerFactory.getLogger(InventarioService.class);

    private final InventarioRepository repository;

    @Autowired
    private MovimientoStockService movimientoStockService;
    @Autowired
    private MovimientoPrintService movimientoPrintService;
    @Autowired
    private ModificacionService modificacionService;

    @Override
    public InventarioRepository getRepository() {
        return repository;
    }

    @Override
    public Inventario save(Inventario entity) {
        if (entity.getFechaInicio() == null)
            entity.setFechaInicio(LocalDateTime.now());

        Inventario entidadAnterior = null;
        boolean esNuevo = (entity.getId() == null);
        if (!esNuevo) {
            entidadAnterior = repository.findById(entity.getId()).orElse(null);
        }

        Inventario e = super.save(entity);
        repository.flush();

        try {
            if (esNuevo) {
                modificacionService.registrarInsercion(e, "INVENTARIO", "operaciones", "inventario");
            } else if (entidadAnterior != null) {
                modificacionService.registrarActualizacion(entidadAnterior, e, "INVENTARIO", "operaciones",
                        "inventario");
            }
        } catch (Exception ex) {
            log.warn("Error registrando auditoría de inventario: " + ex.getMessage());
        }

        return e;
    }

    @Override
    public Boolean deleteById(Long id) {
        try {
            Inventario entidad = repository.findById(id).orElse(null);
            if (entidad != null) {
                Boolean resultado = super.deleteById(id);
                try {
                    modificacionService.registrarEliminacion(entidad, "INVENTARIO", "operaciones", "inventario");
                } catch (Exception ex) {
                    log.warn("Error registrando eliminación de inventario: " + ex.getMessage());
                }
                return resultado;
            }
            return super.deleteById(id);
        } catch (Exception e) {
            return false;
        }
    }

    public List<Inventario> findByDate(String inicio, String fin) {
        return repository.findByDate(inicio, fin);
    }

    public List<Inventario> findByUsuario(Long id) {
        return repository.findByUsuarioId(id);
    }

    public Page<Inventario> findPageByUsuarioId(Long usuarioId, int pageNumber, int pageSize, String sortOrder) {
        Pageable pageable;

        if (sortOrder != null) {
            switch (sortOrder) {
                case "abiertas":
                    pageable = PageRequest.of(pageNumber, pageSize);
                    return repository.findByUsuarioIdOrderByAbiertoPrimero(usuarioId, pageable);
                case "concluidas":
                    pageable = PageRequest.of(pageNumber, pageSize);
                    return repository.findByUsuarioIdOrderByConcluidoPrimero(usuarioId, pageable);
                case "cancelados":
                    pageable = PageRequest.of(pageNumber, pageSize);
                    return repository.findByUsuarioIdOrderByCanceladoPrimero(usuarioId, pageable);
                case "fecha":
                    Sort sortByFecha = Sort.by("fechaInicio").descending();
                    pageable = PageRequest.of(pageNumber, pageSize, sortByFecha);
                    break;
                default:
                    Sort sortByIdDefault = Sort.by("id").descending();
                    pageable = PageRequest.of(pageNumber, pageSize, sortByIdDefault);
                    break;
            }
        } else {
            Sort sortByIdInitial = Sort.by("id").descending();
            pageable = PageRequest.of(pageNumber, pageSize, sortByIdInitial);
        }
        return repository.findByUsuario_IdOrderByIdDesc(usuarioId, pageable);
    }

    public List<Inventario> findInventarioAbiertoPorSucursal(Long id) {
        return repository.findBySucursalIdAndEstado(id, InventarioEstado.ABIERTO);
    }
}