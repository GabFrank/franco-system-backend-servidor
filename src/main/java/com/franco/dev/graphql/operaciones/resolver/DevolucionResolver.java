package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.service.operaciones.DevolucionItemService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DevolucionResolver implements GraphQLResolver<Devolucion> {

    @Autowired
    private DevolucionItemService devolucionItemService;

    // return devolucion items
    public List<DevolucionItem> items(Devolucion devolucion) {
        return devolucionItemService.findByDevolucionId(devolucion.getId());
    }

}

