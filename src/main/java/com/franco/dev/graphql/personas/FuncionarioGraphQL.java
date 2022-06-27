package com.franco.dev.graphql.personas;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.graphql.personas.input.FuncionarioInput;
import com.franco.dev.graphql.personas.input.VendedorInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.CargoService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.*;
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

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Component
public class FuncionarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FuncionarioService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private CargoService cargoService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<Funcionario> funcionario(Long id) {return service.findById(id);}

    public List<Funcionario> funcionarios(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Funcionario> funcionariosSearch(String texto){
        return service.findByPersonaNombre(texto);
    }

    public Funcionario saveFuncionario(FuncionarioInput input){
        ModelMapper m = new ModelMapper();
        Funcionario e = m.map(input, Funcionario.class);
        if(input.getFechaIngreso()!=null) e.setFechaIngreso(toDate(input.getFechaIngreso()));
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getPersonaId()!=null)e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        if(input.getCargoId()!=null)e.setCargo(cargoService.findById(input.getCargoId()).orElse(null));
        if(input.getSucursalId()!=null)e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        e = service.save(e);
        propagacionService.propagarEntidad(e, TipoEntidad.FUNCIONARIO);
        return e;    }

    public Boolean deleteFuncionario(Long id){
        Boolean ok = service.deleteById(id);
        if(ok) propagacionService.eliminarEntidad(id, TipoEntidad.FUNCIONARIO);
        return ok;
    }

    public Long countFuncionario(){
        return service.count();
    }

    public Funcionario funcionarioPorPersona(Long id){
        return service.findByPersonaId(id);
    }

}
