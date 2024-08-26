package com.franco.dev.graphql.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.graphql.operaciones.input.DeliveryInput;
import com.franco.dev.service.general.BarrioService;
import com.franco.dev.service.operaciones.PrecioDeliveryService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.operaciones.VueltoService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class DeliveryGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private DeliveryService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private PrecioDeliveryService deliveryPrecioService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private BarrioService barrioService;

    @Autowired
    private VueltoService vueltoService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Delivery delivery(Long id, Long sucId) {
        return multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> service.findByIdAndSucursalId(id, sucId), id, sucId);
    }

    public List<Delivery> deliverys(int page, int size, Long sucId){
        Pageable pageable = PageRequest.of(page,size);
        return multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> service.findAll(pageable), pageable);
    }

    public List<Delivery> deliverysByEstado(DeliveryEstado estado, Long sucId){
        return multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> service.findByEstado(estado), estado);
    }

    public List<Delivery> deliverysByEstadoNotIn(DeliveryEstado estado, Long sucId){
        return multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> service.findByEstadoNotIn(estado), estado);
    }

    public List<Delivery> deliverysUltimos10(Long sucId){
        return multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> service.findTop10());
    }


    public Delivery saveDelivery(DeliveryInput input){
        ModelMapper m = new ModelMapper();
        Delivery e = m.map(input, Delivery.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getFuncionarioId()!=null){
            e.setEntregador(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        }
        if(input.getVentaId()!=null){
            e.setVenta(ventaService.findById(new EmbebedPrimaryKey(input.getVentaId(), input.getSucursalId())).orElse(null));
        }
        if(input.getPrecioId()!=null){
            e.setPrecio(deliveryPrecioService.findById(input.getPrecioId()).orElse(null));
        }
        if(input.getBarrioId()!=null){
            e.setBarrio(barrioService.findById(input.getBarrioId()).orElse(null));
        }

        if(input.getVueltoId()!=null){
            e.setVuelto(vueltoService.findById(new EmbebedPrimaryKey(input.getVueltoId(), input.getSucursalId())).orElse(null));
        }
        return service.save(e);
    }

    public Boolean deleteDelivery(Long id, Long sucId){
        return service.deleteById(new EmbebedPrimaryKey(id, sucId));
    }

    public Long countDelivery(){
        return service.count();
    }

    public List<Delivery> deliveryPorCajaIdAndEstados(Long id, List<DeliveryEstado> estadoList, Long sucId){
        return multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> service.findByVentaCajaIdAndEstadoIn(id, estadoList, sucId), id, estadoList, sucId);
    }

}
