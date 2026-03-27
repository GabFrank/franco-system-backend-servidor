package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.FamiliaMueble;
import com.franco.dev.repository.activos.FamiliaMuebleRepository;
import com.franco.dev.repository.activos.TipoMuebleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class FamiliaMuebleService extends CrudService<FamiliaMueble, FamiliaMuebleRepository, Long> {

    private final FamiliaMuebleRepository repository;
    private final TipoMuebleRepository tipoMuebleRepository;

    @Override
    public FamiliaMuebleRepository getRepository() {
        return repository;
    }

    public List<FamiliaMueble> findByAll(String texto) {
        if (texto == null) texto = "";
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    @Override
    public FamiliaMueble save(FamiliaMueble entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        if (entity.getDescripcion() != null) {
            entity.setDescripcion(entity.getDescripcion().toUpperCase());
        }
        return super.save(entity);
    }

    @Override
    public Boolean deleteById(Long id) {
        List<com.franco.dev.domain.activos.TipoMueble> tipos = tipoMuebleRepository.findByFamiliaMuebleId(id);
        if (tipos != null && !tipos.isEmpty()) {
            throw new RuntimeException("No se puede eliminar la familia porque tiene tipos de mueble asociados");
        }
        return super.deleteById(id);
    }
}
