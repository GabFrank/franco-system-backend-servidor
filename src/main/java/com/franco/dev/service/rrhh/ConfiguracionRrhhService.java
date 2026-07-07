package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.ConfiguracionRrhh;
import com.franco.dev.domain.rrhh.enums.ConfiguracionRrhhTipo;
import com.franco.dev.repository.rrhh.ConfiguracionRrhhRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class ConfiguracionRrhhService extends CrudService<ConfiguracionRrhh, ConfiguracionRrhhRepository, Long> {

    private final ConfiguracionRrhhRepository repository;

    @Override
    public ConfiguracionRrhhRepository getRepository() {
        return repository;
    }

    public Optional<ConfiguracionRrhh> findByClave(String clave) {
        if (clave == null) return Optional.empty();
        return repository.findByClave(clave.toUpperCase());
    }

    public List<ConfiguracionRrhh> findByAll(String texto) {
        if (texto == null) texto = "";
        texto = texto.replace(' ', '%').toUpperCase();
        return repository.findByAll(texto);
    }

    @Override
    public ConfiguracionRrhh save(ConfiguracionRrhh entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getActivo() == null) entity.setActivo(true);
        if (entity.getTipo() == null) entity.setTipo(ConfiguracionRrhhTipo.STRING);
        if (entity.getClave() != null) entity.setClave(entity.getClave().toUpperCase());
        return super.save(entity);
    }
}
