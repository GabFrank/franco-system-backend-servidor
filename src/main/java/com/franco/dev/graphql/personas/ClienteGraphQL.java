package com.franco.dev.graphql.personas;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.general.Contacto;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.personas.input.ClienteInput;
import com.franco.dev.graphql.personas.input.ClienteUpdateInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.general.ContactoService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.StringUtils.isValidLong;

@Component
public class ClienteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ClienteService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private ContactoService contactoService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Cliente> cliente(Long id) {return service.findById(id);}

    public List<Cliente> clientePorTelefono(String texto){
        List<Contacto> contactoList = contactoService.findByTelefonoOrNombre(texto);
        List<Cliente> clienteList = new ArrayList<>();
        for(Contacto c : contactoList){
            clienteList.add(service.findByPersonaId(c.getPersona().getId()));
        }
        return clienteList;
    }

    public List<Cliente> clientes(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Cliente> clientePorPersona(String texto){
        if(texto==null){
            texto="";
        } else {
            texto = texto.toUpperCase();
        }

        return service.findByAll(texto);
    }

    public Page<Cliente> onSearchWithFilters(String texto, TipoCliente tipoCliente, Integer page, Integer size){
        List<Cliente> lista = new ArrayList<>();
        if(isValidLong(texto)){
            lista.add(service.findById(Long.parseLong(texto)).orElse(null));
            return (Page<Cliente>) lista;
        } else if(texto == null && tipoCliente == null){
            return null;
        } else {
            return service.findByAll2(texto, tipoCliente, page, size);
        }
    }

    public Cliente saveCliente(ClienteInput input){
        ModelMapper m = new ModelMapper();
        Cliente e = m.map(input, Cliente.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.CLIENTE);
        multiTenantService.compartir(null, (Cliente s) -> service.save(s), e);
        Funcionario funcionario = funcionarioService.findByPersonaId(e.getPersona().getId());
        if(funcionario != null && e.getCredito() != funcionario.getCredito()){
            funcionario.setCredito(e.getCredito());
            funcionario = funcionarioService.save(funcionario);
//            propagacionService.propagarEntidad(funcionario, TipoEntidad.FUNCIONARIO);
            multiTenantService.compartir(null, (Funcionario s) -> funcionarioService.save(s), funcionario);
        }
        return e;
    }

    public Boolean deleteCliente(Long id){
        Boolean ok = service.deleteById(id);
        if(ok) multiTenantService.compartir(null, (Long s) -> service.deleteById(s), id);
        return ok;
    }

    public Long countCliente(){
        return service.count();
    }

    public Cliente clientePorPersonaId(Long id){
        return service.findByPersonaId(id);
    }

}
