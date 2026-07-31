package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.graphql.financiero.input.EntradaVariaCategoriaInput;
import com.franco.dev.graphql.financiero.input.EntradaVariaInput;
import com.franco.dev.repository.financiero.EntradaVariaCategoriaRepository;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.EntradaVariaService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@AllArgsConstructor
public class EntradaVariaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final EntradaVariaService service;
    private final EntradaVariaCategoriaRepository categoriaRepository;
    private final CajaVirtualService cajaVirtualService;
    private final MonedaService monedaService;
    private final TesoreriaSecurityService seg;

    public List<EntradaVariaCategoria> entradaVariaCategorias() {
        seg.requireVer();
        return categoriaRepository.findByActivoTrue();
    }

    public Page<EntradaVaria> entradasVarias(Long cajaVirtualId, int page, int size) {
        seg.requireVer();
        return service.findByCaja(cajaVirtualId, PageRequest.of(page, size));
    }

    public EntradaVariaCategoria saveEntradaVariaCategoria(EntradaVariaCategoriaInput input) {
        seg.requireGestionar();
        EntradaVariaCategoria c = input.getId() != null
                ? categoriaRepository.findById(input.getId()).orElse(new EntradaVariaCategoria())
                : new EntradaVariaCategoria();
        c.setNombre(input.getNombre() != null ? input.getNombre().toUpperCase() : null);
        c.setIcono(input.getIcono());
        c.setActivo(input.getActivo() != null ? input.getActivo() : true);
        if (input.getPadreId() != null) {
            c.setPadre(categoriaRepository.findById(input.getPadreId()).orElse(null));
        }
        return categoriaRepository.save(c);
    }

    public EntradaVaria registrarEntradaVaria(EntradaVariaInput input) {
        seg.requireGestionar();
        EntradaVaria e = new EntradaVaria();
        e.setDescripcion(input.getDescripcion());
        e.setMonto(input.getMonto() != null ? BigDecimal.valueOf(input.getMonto()) : null);
        e.setEsIngreso(input.getEsIngreso());
        e.setNumeroComprobante(input.getNumeroComprobante());
        e.setCajaVirtual(cajaVirtualService.findById(input.getCajaVirtualId())
                .orElseThrow(() -> new GraphQLException("Caja mayor no encontrada")));
        if (input.getMonedaId() != null) e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if (input.getCategoriaId() != null) e.setCategoria(categoriaRepository.findById(input.getCategoriaId()).orElse(null));
        return service.registrar(e, seg.currentUsuario());
    }

    public EntradaVaria anularEntradaVaria(Long id, String motivo) {
        seg.requireGestionar();
        return service.anular(id, motivo, seg.currentUsuario());
    }
}
