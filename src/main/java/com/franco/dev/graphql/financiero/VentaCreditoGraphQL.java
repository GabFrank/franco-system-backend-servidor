package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.MovimientoPersonas;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.VentaCreditoCuota;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.financiero.enums.TipoMovimientoPersonas;
import com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput;
import com.franco.dev.graphql.financiero.input.VentaCreditoInput;
import com.franco.dev.graphql.financiero.publisher.VentaCreditoQRAuthPublisher;
import com.franco.dev.graphql.financiero.publisher.VentaCreditoQRAuthUpdate;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.security.Unsecured;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.MovimientoPersonasService;
import com.franco.dev.service.financiero.VentaCreditoService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.kickstart.tools.GraphQLSubscriptionResolver;
import org.modelmapper.ModelMapper;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.getLastDayOfMonth;
import static com.franco.dev.utilitarios.DateUtils.toDate;

@Component
public class VentaCreditoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver, GraphQLSubscriptionResolver {

    @Autowired
    private VentaCreditoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private VentaCreditoCuotasGraphQL ventaCreditoCuotasGraphQL;

    @Autowired
    private MovimientoPersonasService movimientoPersonasService;

    @Autowired
    private VentaCreditoQRAuthPublisher qrAuthPublisher;

    @Unsecured
    public Publisher<VentaCreditoQRAuthUpdate> ventaCreditoAuthQrSub(){
        return qrAuthPublisher.getPublisher();
    }

    public Optional<VentaCredito> ventaCredito(Long id) {
        return service.findById(id);
    }

    public List<VentaCredito> ventaCreditos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    @Transactional
    public VentaCredito saveVentaCredito(VentaCreditoInput input, List<VentaCreditoCuotaInput> ventaCreditoCuotaInputList) {
        ModelMapper m = new ModelMapper();
        VentaCredito e = m.map(input, VentaCredito.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getClienteId() != null) e.setCliente(clienteService.findById(input.getClienteId()).orElse(null));
        if (input.getSucursalId() != null) e.setSucursalId(input.getSucursalId());
        e = service.save(e);
        if (e.getId() != null) {
            for (VentaCreditoCuotaInput vc : ventaCreditoCuotaInputList) {
                vc.setVentaCreditoId(e.getId());
                vc.setUsuarioId(input.getUsuarioId());
                VentaCreditoCuota ventaCreditoCuota = ventaCreditoCuotasGraphQL.saveVentaCreditoCuota(vc);
                if (ventaCreditoCuota != null) {
                    MovimientoPersonas movimientoPersonas = new MovimientoPersonas();
                    movimientoPersonas.setVencimiento(ventaCreditoCuota.getVencimiento());
                    movimientoPersonas.setPersona(e.getCliente().getPersona());
                    movimientoPersonas.setActivo(true);
                    movimientoPersonas.setReferenciaId(ventaCreditoCuota.getId());
                    movimientoPersonas.setTipo(TipoMovimientoPersonas.VENTA_CREDITO);
                    movimientoPersonas.setValorTotal(vc.getValor() * -1);
                    movimientoPersonasService.save(movimientoPersonas);
                }
            }
        }
        return e;
    }

    public List<VentaCredito> ventaCreditoPorClienteAndVencimiento(Long id, String inicio, String fin) {
        return service.findByClienteAndVencimiento(id, toDate(inicio), toDate(fin));
    }

    public List<VentaCredito> ventaCreditoPorCliente(Long id, EstadoVentaCredito estado, int page, int size) {
        if (estado == EstadoVentaCredito.ABIERTO) {
            return service.findByClienteId(id, estado);
        } else {
            Pageable pageable = PageRequest.of(page, size);
            return service.findByClienteId(id, estado, pageable);
        }

    }

    public Boolean deleteVentaCredito(Long id) {
        Boolean ok = service.deleteById(id);
        if (ok) propagacionService.eliminarEntidad(id, TipoEntidad.BANCO);
        return ok;
    }

    public Long resumentByClienteIdAndEstado(Long id, EstadoVentaCredito estado) {
        return service.countByClienteIdAndEstado(id, estado);
    }

    public Long countVentaCredito() {
        return service.count();
    }

    @Unsecured
    public Boolean ventaCreditoQrAuth(Long id, String timestamp) {
        try {
            VentaCreditoQRAuthUpdate entity = new VentaCreditoQRAuthUpdate();
            entity.setClienteId(id);
            entity.setTimestamp(timestamp);
            qrAuthPublisher.publish(entity);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
