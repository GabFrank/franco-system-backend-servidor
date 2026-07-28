package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionFacturaConVenta;
import com.franco.dev.repository.financiero.ConfiguracionFacturaConVentaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class ConfiguracionFacturaConVentaService extends CrudService<ConfiguracionFacturaConVenta, ConfiguracionFacturaConVentaRepository, Long> {

    private final ConfiguracionFacturaConVentaRepository repository;

    @Override
    public ConfiguracionFacturaConVentaRepository getRepository() {
        return repository;
    }

    /**
     * Devuelve el registro único de configuración. Si no existe, lo crea con valores por defecto.
     */
    public ConfiguracionFacturaConVenta findOrCreate() {
        List<ConfiguracionFacturaConVenta> list = repository.findAllByOrderByIdAsc();
        if (!list.isEmpty()) {
            return list.get(0);
        }
        ConfiguracionFacturaConVenta config = new ConfiguracionFacturaConVenta();
        config.setHabilitado(false);
        config.setCreadoEn(LocalDateTime.now());
        config.setModificadoEn(LocalDateTime.now());
        return repository.save(config);
    }
}
