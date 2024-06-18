package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.PreRegistroFuncionario;
import com.franco.dev.graphql.personas.input.PreRegistroFuncionarioInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.general.CiudadService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.PreRegistroFuncionarioService;
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
public class PreRegistroFuncionarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PreRegistroFuncionarioService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CiudadService ciudadService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private FuncionarioService funcionarioService;

    public Optional<PreRegistroFuncionario> preRegistroFuncionario(Long id) {
        return service.findById(id);
    }

    public List<PreRegistroFuncionario> preRegistroFuncionarios(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<PreRegistroFuncionario> preRegistroFuncionariosSearch(String texto) {
        return service.findByNombre(texto);
    }

    public PreRegistroFuncionario savePreRegistroFuncionario(PreRegistroFuncionarioInput input) {
        ModelMapper m = new ModelMapper();
        PreRegistroFuncionario e = m.map(input, PreRegistroFuncionario.class);
        if(input.getFechaIngreso()!=null) e.setFechaIngreso(stringToDate(input.getFechaIngreso()));
        if(input.getFechaNacimiento()!=null) e.setFechaNacimiento(stringToDate(input.getFechaNacimiento()));
        if(input.getFuncionarioId()!=null) {
            e.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
            e.setVerificado(true);
        }
        e = service.save(e);
        return e;
    }

    public Boolean deletePreRegistroFuncionario(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countPreRegistroFuncionario() {
        return service.count();
    }

}
