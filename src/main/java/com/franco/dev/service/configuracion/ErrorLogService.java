package com.franco.dev.service.configuracion;

import com.franco.dev.config.ErrorLog;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.configuracion.Actualizacion;
import com.franco.dev.domain.configuracion.enums.TipoActualizacion;
import com.franco.dev.repository.configuracion.ActualizacionRepository;
import com.franco.dev.repository.configuracion.ErrorLogRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class ErrorLogService extends CrudService<ErrorLog, ErrorLogRepository, EmbebedPrimaryKey> {

    private final ErrorLogRepository repository;

    @Override
    public ErrorLogRepository getRepository() {
        return repository;
    }


    @Override
    public ErrorLog save(ErrorLog entity) {
        Long sucId = env.getProperty("sucursal_id", Long.class);
        if(entity.getSucursalId() == null) {
            if(sucId == null) sucId = Long.valueOf(0);
            entity.setSucursalId(sucId);
        }
        if(entity.getFecha_primera_ocurrencia() == null) {
            entity.setFecha_primera_ocurrencia(LocalDateTime.now());
        }
        ErrorLog e = super.save(entity);
        return e;
    }
}