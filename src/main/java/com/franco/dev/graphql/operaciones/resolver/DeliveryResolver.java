package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.Vuelto;
import com.franco.dev.domain.operaciones.VueltoItem;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.operaciones.VueltoItemService;
import com.franco.dev.service.operaciones.VueltoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeliveryResolver implements GraphQLResolver<Delivery> {

    @Autowired
    private VentaService ventaService;

    public Venta venta(Delivery e) {
        return ventaService.getRepository().findByDeliveryIdAndSucursalId(e.getId(), e.getSucursalId());
    }
}
