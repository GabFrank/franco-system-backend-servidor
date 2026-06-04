package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.ConfiguracionTransferencia;
import com.franco.dev.repository.operaciones.ConfiguracionTransferenciaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class ConfiguracionTransferenciaService extends CrudService<ConfiguracionTransferencia, ConfiguracionTransferenciaRepository, Long> {

    private final ConfiguracionTransferenciaRepository repository;

    @Override
    public ConfiguracionTransferenciaRepository getRepository() {
        return repository;
    }

    /**
     * Devuelve el registro único de configuración. Si no existe, lo crea con valores por defecto.
     */
    public ConfiguracionTransferencia findOrCreate() {
        List<ConfiguracionTransferencia> list = repository.findAllByOrderByIdAsc();
        if (!list.isEmpty()) {
            return list.get(0);
        }
        ConfiguracionTransferencia config = new ConfiguracionTransferencia();
        config.setPermitirStockNegativo(false);
        config.setCreadoEn(LocalDateTime.now());
        config.setModificadoEn(LocalDateTime.now());
        return repository.save(config);
    }
}
