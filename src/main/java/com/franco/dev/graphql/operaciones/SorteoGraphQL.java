package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Sorteo;
import com.franco.dev.graphql.operaciones.input.SorteoInput;
import com.franco.dev.service.operaciones.SorteoParticipacionConsulta;
import com.franco.dev.service.operaciones.SorteoService;
import com.franco.dev.service.productos.PrecioSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/** Sorteos para clientes. Las escrituras y la lectura de participaciones (datos personales de clientes) exigen el mismo rol que los precios; el listado de sorteos queda abierto. */
@Component
@AllArgsConstructor
public class SorteoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final SorteoService service;
    private final SorteoParticipacionConsulta consulta;
    private final PrecioSecurityService seg;

    public Page<Sorteo> filterSorteos(String texto, Boolean soloVigentes, Integer page, Integer size) {
        return service.filtrar(texto, soloVigentes, pagina(page), tamano(size, 15));
    }

    public Sorteo sorteo(Long id) {
        return id == null ? null : service.buscar(id);
    }

    public Page<SorteoParticipacionConsulta.Fila> sorteoParticipaciones(Long sorteoId, Long sucursalId, String codigo,
                                                                        Integer page, Integer size) {
        seg.requireGestionar();
        return consulta.listar(sorteoId, sucursalId, codigo, pagina(page), tamano(size, 20));
    }

    public List<SorteoParticipacionConsulta.Resumen> sorteoResumen(Long sorteoId) {
        seg.requireGestionar();
        return consulta.resumen(sorteoId);
    }

    public Sorteo saveSorteo(SorteoInput input) {
        seg.requireGestionar();
        return service.guardar(input, seg.currentUsuario());
    }

    public Sorteo cortarSorteo(Long id) {
        seg.requireGestionar();
        return service.cortar(id, seg.currentUsuario());
    }

    private static int pagina(Integer page) {
        return page != null && page >= 0 ? page : 0;
    }

    private static int tamano(Integer size, int porDefecto) {
        return size != null ? Math.min(Math.max(size, 1), 100) : porDefecto;
    }
}
