package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.RetiroDetalleInput;
import com.franco.dev.graphql.financiero.input.RetiroInput;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.RetiroService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.impresion.dto.RetiroDto;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RetiroGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private RetiroService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private RetiroDetalleGraphQL retiroDetalleGraphQL;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Retiro retiro(Long id, Long sucId) {return service.findByIdAndSucursalId(id, sucId);}

    public List<Retiro> retiros(Integer page, Integer size, Long sucId){
        if(page==null) page = 0;
        if(size==null) size = 20;
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Page<Retiro> filterRetiros(Long id, Long cajaId, Long sucId, Long responsableId, Long cajeroId, Integer page, Integer size){
        Pageable pageable = PageRequest.of(page, size);
        return service.filterRetirosPage(id, cajaId, sucId, responsableId, cajeroId, pageable);
    }

    public Retiro saveRetiro(RetiroInput input, List<RetiroDetalleInput> retiroDetalleInputList, String printerName, String local) throws GraphQLException {
        ModelMapper m = new ModelMapper();
        Retiro e = m.map(input, Retiro.class);

//        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
//        if (input.getCajaSalidaId() != null)
//            e.setCajaSalida(pdvCajaService.findById(input.getCajaSalidaId(), input.getSucursalSalidaId()).orElse(null));
//        if (input.getCajaEntradaId() != null)
//            e.setCajaEntrada(pdvCajaService.findById(input.getCajaEntradaId(), input.getSucursalEntradaId()).orElse(null));
//        if (input.getResponsableId() != null)
//            e.setResponsable(funcionarioService.findById(input.getResponsableId()).orElse(null));
//
//        e = service.saveAndSend(e, false);
//
//        if (retiro != null) {
//            for (RetiroDetalleInput r : retiroDetalleInputList) {
//                r.setRetiroId(retiro.getId());
//                r.setUsuarioId(retiro.getUsuario().getId());
//                r.setCreadoEn(retiro.getCreadoEn());
//                retiroDetalleGraphQL.saveRetiroDetalle(r);
//            }
//
//            RetiroDto retiroDto = new RetiroDto();
//            retiroDto.setId(retiro.getId());
//            retiroDto.setCajaId(retiro.getCajaSalida().getId());
//            retiroDto.setFecha(retiro.getCreadoEn());
//            retiroDto.setResponsable(retiro.getResponsable());
//            retiroDto.setRetiroGs(input.getRetiroGs());
//            retiroDto.setRetiroRs(input.getRetiroRs());
//            retiroDto.setRetiroDs(input.getRetiroDs());
//            retiroDto.setUsuario(retiro.getUsuario());
//            impresionService.printRetiro(retiroDto, printerName, local, false);
//        }
        return e;
    }

    public List<Retiro> retiroListPorCajaSalidaId(Long id, Long sucId){
        return service.findByCajaSalidaId(id);
    }

//    public List<Retiro> retirosSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteRetiro(Long id, Long sucId){
        Retiro retiro = retiro(id, sucId);
        if(retiro!=null){
            return service.delete(retiro);
        } else {
            throw new GraphQLException("No se pudo eliminar el retiro");
        }
    }

    public Long countRetiro(){
        return service.count();
    }


}
