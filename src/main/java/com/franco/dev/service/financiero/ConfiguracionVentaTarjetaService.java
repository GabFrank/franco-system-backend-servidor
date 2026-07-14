package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionVentaTarjeta;
import com.franco.dev.repository.financiero.ConfiguracionVentaTarjetaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class ConfiguracionVentaTarjetaService extends CrudService<ConfiguracionVentaTarjeta, ConfiguracionVentaTarjetaRepository, Long> {

    private final ConfiguracionVentaTarjetaRepository repository;

    @Override
    public ConfiguracionVentaTarjetaRepository getRepository() {
        return repository;
    }

    /**
     * Devuelve el registro único de configuración. Si no existe, lo crea con valores por defecto.
     */
    public ConfiguracionVentaTarjeta findOrCreate() {
        List<ConfiguracionVentaTarjeta> list = repository.findAllByOrderByIdAsc();
        if (!list.isEmpty()) {
            return list.get(0);
        }
        ConfiguracionVentaTarjeta config = new ConfiguracionVentaTarjeta();
        config.setHabilitado(false);
        config.setCreadoEn(LocalDateTime.now());
        config.setModificadoEn(LocalDateTime.now());
        return repository.save(config);
    }
}
