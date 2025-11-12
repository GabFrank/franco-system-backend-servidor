package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.empresarial.Sector;
import com.franco.dev.domain.empresarial.Zona;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class InventarioProductoItemResolver implements GraphQLResolver<InventarioProductoItem> {

    public Sector sector(InventarioProductoItem item) {
        return item.getInventarioProducto().getZona().getSector();
    }

    public Zona zona(InventarioProductoItem item) {
        if (item.getZona() != null) {
            return item.getZona();
        }
        return item.getInventarioProducto().getZona();
    }
}