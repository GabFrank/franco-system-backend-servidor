package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.dto.ProductoRechazadoDto;
import com.franco.dev.service.operaciones.ProductoRechazadoService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Pantalla "Productos rechazados" del desktop: rechazos de transferencias y de compras. */
@Component
public class ProductoRechazadoGraphQL implements GraphQLQueryResolver {

    private final ProductoRechazadoService service;

    public ProductoRechazadoGraphQL(ProductoRechazadoService service) {
        this.service = service;
    }

    /** Las fechas llegan como yyyy-MM-dd; las dos son inclusivas. */
    public Page<ProductoRechazadoDto> productosRechazados(String fuente, String fechaInicio, String fechaFin,
                                                          Long sucursalId, Long referenciaId, String texto,
                                                          Integer page, Integer size) {
        return service.buscar(fuente, aFecha(fechaInicio), aFecha(fechaFin), sucursalId, referenciaId, texto,
                page, size);
    }

    /** Acepta "yyyy-MM-dd" y también "yyyy-MM-dd HH:mm..." (se queda con el día). */
    public static LocalDate aFecha(String valor) {
        if (valor == null || valor.trim().length() < 10) return null;
        return LocalDate.parse(valor.trim().substring(0, 10));
    }
}
