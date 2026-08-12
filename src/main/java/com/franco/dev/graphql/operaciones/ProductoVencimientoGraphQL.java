package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.ProductoVencimiento;
import com.franco.dev.service.operaciones.ProductoVencimientoService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductoVencimientoGraphQL implements GraphQLQueryResolver {

    @Autowired
    private ProductoVencimientoService service;

    public ProductoVencimiento productoVencimiento(Long id) {
        return service.findById(id).orElse(null);
    }

    public List<ProductoVencimiento> productoVencimientosPorProductoYSucursal(Long productoId, Long sucursalId) {
        return service.findByProductoIdAndSucursalId(productoId, sucursalId);
    }
}
