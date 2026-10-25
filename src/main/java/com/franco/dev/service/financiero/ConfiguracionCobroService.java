package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionCobro;
import com.franco.dev.repository.financiero.ConfiguracionCobroRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class ConfiguracionCobroService extends CrudService<ConfiguracionCobro, ConfiguracionCobroRepository, Long> {

    private final ConfiguracionCobroRepository repository;

    @Override
    public ConfiguracionCobroRepository getRepository() {
        return repository;
    }

    /**
     * Para leer: la fila, o un default (con tope del 20%) SIN persistir. Abrir el dialogo no puede
     * escribir: la primera escritura sobre una tabla MAIN_TO_ALL recien publicada tiene que esperar a
     * que todas las suscripciones esten listas.
     */
    public ConfiguracionCobro findOrDefault() {
        List<ConfiguracionCobro> list = repository.findAllByOrderByIdAsc();
        return list.isEmpty() ? new ConfiguracionCobro() : list.get(0);
    }

    /** Solo para el save: la fila, o una nueva con los defaults (todavia sin guardar). */
    public ConfiguracionCobro findOrNew() {
        List<ConfiguracionCobro> list = repository.findAllByOrderByIdAsc();
        if (!list.isEmpty()) {
            return list.get(0);
        }
        ConfiguracionCobro config = new ConfiguracionCobro();
        config.setCreadoEn(LocalDateTime.now());
        return config;
    }
}
