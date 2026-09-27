package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.PrecioEspecialSucursal;
import com.franco.dev.graphql.productos.input.PrecioEspecialSucursalInput;
import com.franco.dev.service.productos.PrecioEspecialSucursalService;
import com.franco.dev.service.productos.PrecioSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Precios especiales por sucursal. Las escrituras exigen CREAR PRECIOS o EDITAR PRECIOS; las
 * queries quedan abiertas, como el resto de productos.
 */
@Component
@AllArgsConstructor
public class PrecioEspecialSucursalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final PrecioEspecialSucursalService service;
    private final PrecioSecurityService seg;

    public List<PrecioEspecialSucursal> preciosEspecialesPorPrecio(Long precioId) {
        return service.porPrecio(precioId);
    }

    public Page<PrecioEspecialSucursal> filterPreciosEspeciales(Long sucursalId, String texto, Boolean soloVigentes,
                                                               Integer page, Integer size) {
        // Query abierta a cualquier sesion: se acota page/size antes de llegar al service para que
        // no se pueda bajar toda la tabla de una vez ni romper PageRequest.of con size<=0.
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null ? Math.min(Math.max(size, 1), 100) : 15;
        return service.filtrar(sucursalId, texto, soloVigentes, p, s);
    }

    public List<PrecioEspecialSucursal> savePreciosEspeciales(PrecioEspecialSucursalInput input) {
        seg.requireGestionar();
        return service.crear(input, seg.currentUsuario());
    }

    public PrecioEspecialSucursal editarPrecioEspecial(Long id, Double precio, String fechaDesde, String fechaHasta) {
        seg.requireGestionar();
        return service.editar(id, precio, fechaDesde, fechaHasta, seg.currentUsuario());
    }

    public PrecioEspecialSucursal cortarPrecioEspecial(Long id) {
        seg.requireGestionar();
        return service.cortar(id, seg.currentUsuario());
    }
}
