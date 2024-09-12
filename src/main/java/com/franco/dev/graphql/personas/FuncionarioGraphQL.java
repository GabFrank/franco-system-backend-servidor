package com.franco.dev.graphql.personas;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.personas.input.FuncionarioInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.CargoService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.*;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

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

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Funcionario> funcionario(Long id) {return service.findById(id);}

    public List<Funcionario> funcionarios(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Page<Funcionario> funcionariosWithPage(int page, int size, Long id, String nombre, List<Long> sucursalList){
        Pageable pageable = PageRequest.of(page,size);
        if(nombre!=null){
            nombre = nombre.replace(" ", "%");
        }
        Page<Funcionario> result = service.findAllWithPage(id, nombre, sucursalList, pageable);
        return result;
    }

    public List<Funcionario> funcionariosSearch(String texto){
        return service.findByPersonaNombre(texto);
    }

    public Funcionario saveFuncionario(FuncionarioInput input){
        ModelMapper m = new ModelMapper();
        Funcionario e = m.map(input, Funcionario.class);
        if(input.getFechaIngreso()!=null) e.setFechaIngreso(stringToDate(input.getFechaIngreso()));
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getPersonaId()!=null)e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        if(input.getCargoId()!=null)e.setCargo(cargoService.findById(input.getCargoId()).orElse(null));
        if(input.getSucursalId()!=null)e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        e = service.save(e);
        Cliente cliente = clienteService.findByPersonaId(e.getPersona().getId());
        if(cliente!=null){
            if(!cliente.getCredito().equals(e.getCredito())){
                cliente.setCredito(e.getCredito());
                cliente = clienteService.save(cliente);
            }
        } else {
            cliente = new Cliente();
            cliente.setPersona(e.getPersona());
            cliente.setTipo(TipoCliente.FUNCIONARIO);
            cliente.setUsuario(e.getUsuario());
            cliente.setCredito(e.getCredito());
            cliente.setSucursal(e.getSucursal());
            cliente.setUsuario(e.getUsuario());
            cliente = clienteService.save(cliente);
        }
        Usuario usuario = usuarioService.findByPersonaId(e.getPersona().getId());
        if(usuario==null){
            usuario = new Usuario();
            usuario.setPassword("123");
            usuario.setPersona(e.getPersona());
            List<String> palabras = Arrays.asList(e.getPersona().getNombre().split("\\s+"));
            switch (palabras.size()){
                case 1:
                case 2:
                    usuario.setNickname(e.getPersona().getNombre());
                    break;
                default:
                    usuario.setNickname(palabras.get(0) + " " + palabras.get(2));
                    break;
            }
            usuario.setActivo(true);
            usuario = usuarioService.save(usuario);

        }
        return e;
    }

    public Boolean deleteFuncionario(Long id){
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countFuncionario(){
        return service.count();
    }

    public Funcionario funcionarioPorPersona(Long id){
        return service.findByPersonaId(id);
    }

}
