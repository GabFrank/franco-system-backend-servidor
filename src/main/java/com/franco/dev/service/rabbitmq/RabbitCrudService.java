package com.franco.dev.service.rabbitmq;

import com.franco.dev.rabbit.enums.TipoEntidad;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RabbitCrudService {

    @Autowired
    private PropagacionService propagacionService;

    public <T> void guardar(T entidad, TipoEntidad tipoEntidad){
        propagacionService.propagarEntidad(entidad, tipoEntidad);
    }
}
