package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.VentaCreditoCuota;
import com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.financiero.VentaCreditoCuotaService;
import com.franco.dev.service.financiero.VentaCreditoService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class VentaCreditoCuotasGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VentaCreditoCuotaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private VentaCreditoService ventaCreditoService;

    @Autowired
    private CobroService cobroService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<VentaCreditoCuota> ventaCreditoCuota(Long id, Long sucId) {
        return service.findById(new EmbebedPrimaryKey(id, sucId));
    }

    public List<VentaCreditoCuota> ventaCreditoCuotas(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public VentaCreditoCuota saveVentaCreditoCuota(VentaCreditoCuotaInput input) {
        ModelMapper m = new ModelMapper();
        VentaCreditoCuota e = m.map(input, VentaCreditoCuota.class);
        if(input.getVencimiento()!=null) e.setVencimiento(stringToDate(input.getVencimiento()));
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getVentaCreditoId() != null)
            e.setVentaCredito(ventaCreditoService.findById(new EmbebedPrimaryKey(input.getVentaCreditoId(), input.getSucursalId())).orElse(null));
        if (input.getCobroId() != null) e.setCobro(cobroService.findById(new EmbebedPrimaryKey(input.getCobroId(), input.getSucursalId())).orElse(null));
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.BANCO);
        return e;
    }

    public List<VentaCreditoCuota> ventaCreditoCuotaPorVentaCredito(Long id, Long sucId) {
        return service.findByVentaCreditoId(id, sucId);
    }

    public Boolean deleteVentaCreditoCuota(Long id, Long sucId) {
        Boolean ok = service.deleteById(new EmbebedPrimaryKey(id, sucId));
        if (ok) propagacionService.eliminarEntidad(id, TipoEntidad.BANCO);
        return ok;
    }

    public Long countVentaCreditoCuota() {
        return service.count();
    }


}
