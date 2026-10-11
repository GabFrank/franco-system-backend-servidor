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

import java.time.LocalDateTime;

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
        return service.buscar(DateUtils.stringToDate(fechaInicio), finInclusivo(DateUtils.stringToDate(fechaFin)),
                sucursalId, tipo, texto, stock, page, size);
    }

    /**
     * El desktop envia el fin con precision de minuto ("yyyy-MM-dd HH:mm"), que se parsea como :00.
     * Se extiende al ultimo instante de ese minuto para no dejar afuera lo registrado en sus segundos.
     */
    static LocalDateTime finInclusivo(LocalDateTime fin) {
        if (fin != null && fin.getSecond() == 0 && fin.getNano() == 0) {
            return fin.withSecond(59).withNano(999_999_999);
        }
        return fin;
    }
}
