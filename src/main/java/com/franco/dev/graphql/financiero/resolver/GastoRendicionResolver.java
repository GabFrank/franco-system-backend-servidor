package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.GastoRendicion;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.financiero.GastoRendicionService;
import graphql.kickstart.tools.GraphQLResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GastoRendicionResolver implements GraphQLResolver<GastoRendicion> {

    private final GastoRendicionService gastoRendicionService;

    public PreGasto preGasto(GastoRendicion rendicion) {
        return rendicion.getPreGasto();
    }

    public TipoGasto tipoGasto(GastoRendicion rendicion) {
        return rendicion.getTipoGasto();
    }

    public Ente ente(GastoRendicion rendicion) {
        return rendicion.getEnte();
    }

    public List<Persona> funcionariosComensales(GastoRendicion rendicion) {
        return rendicion.getFuncionariosComensales();
    }

    public Usuario usuario(GastoRendicion rendicion) {
        return rendicion.getUsuario();
    }

    public Long gasolineraId(GastoRendicion rendicion) {
        return rendicion.getGasolinera() != null ? rendicion.getGasolinera().getId() : null;
    }

    public String fotoFacturaUrl(GastoRendicion rendicion) {
        return gastoRendicionService.resolveImageAsDataUrl(rendicion.getFotoFacturaUrl(), "factura");
    }

    public String fotoProductoUrl(GastoRendicion rendicion) {
        return gastoRendicionService.resolveImageAsDataUrl(rendicion.getFotoProductoUrl(), "producto");
    }
}
