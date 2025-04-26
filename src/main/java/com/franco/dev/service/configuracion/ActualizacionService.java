package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.Actualizacion;
import com.franco.dev.domain.configuracion.enums.TipoActualizacion;
import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.graphql.configuracion.input.ActualizacionInput;
import com.franco.dev.repository.configuracion.ActualizacionRepository;
import com.franco.dev.repository.empresarial.CargoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ActualizacionService extends CrudService<Actualizacion, ActualizacionRepository, Long> {

    private final ActualizacionRepository repository;

    @Override
    public ActualizacionRepository getRepository() {
        return repository;
    }

    public Actualizacion findLast(TipoActualizacion tipo){
        return repository.findFirstByTipoOrderByIdDesc(tipo);
    }

    @Override
    public Actualizacion save(Actualizacion entity) {
        Actualizacion e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}