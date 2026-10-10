package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.ControlStockNegativo;
import com.franco.dev.domain.operaciones.enums.FiltroStockControl;
import com.franco.dev.domain.operaciones.enums.TipoControlStock;
import com.franco.dev.service.operaciones.ControlStockNegativoService;
import com.franco.dev.service.operaciones.InventarioSecurityService;
import com.franco.dev.utilitarios.DateUtils;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class ControlStockNegativoGraphQL implements GraphQLQueryResolver {

    private final ControlStockNegativoService service;
    private final InventarioSecurityService seg;

    public ControlStockNegativoGraphQL(ControlStockNegativoService service, InventarioSecurityService seg) {
        this.service = service;
        this.seg = seg;
    }

    public Page<ControlStockNegativo> controlStockNegativo(String fechaInicio, String fechaFin, Long sucursalId,
                                                           TipoControlStock tipo, String texto, FiltroStockControl stock,
                                                           int page, int size) {
        seg.requireVerInventario();
        return service.buscar(DateUtils.stringToDate(fechaInicio), DateUtils.stringToDate(fechaFin),
                sucursalId, tipo, texto, stock, page, size);
    }
}
