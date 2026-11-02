package com.franco.dev.graphql.personas.resolver;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FuncionarioResolver implements GraphQLResolver<Funcionario> {

    @Autowired
    private FuncionarioService personaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private com.franco.dev.service.administrativo.HorarioService horarioService;


    public String nombrePersona(Funcionario e) {
        if(e.getPersona()!=null) return e.getPersona().getNombre();
        return "Sin nombre";
    }

    public String nombreCargo(Funcionario e) {
        if(e.getCargo()!=null) return e.getCargo().getNombre();
        return "Sin nombre";
    }

    public String nombreSupervisor(Funcionario e) {

        if(e.getSupervisadoPor()!=null) return e.getSupervisadoPor().getPersona().getNombre();
        return "Sin supervisor";
    }

    public String nombreSucursal(Funcionario e) {

        if(e.getSucursal()!=null) return e.getSucursal().getNombre();
        return "Sin sucursal";
    }

    public String nickname(Funcionario f){
        Usuario usuario = f.getPersona() != null ? usuarioService.findByPersonaId(f.getPersona().getId()) : null;
        return usuario != null ? usuario.getNickname().toUpperCase() : "";
    }

    /** Horario por dia de la semana, ej. "L-Mi Día 08:00-17:00 · J Madrugada 17:00-01:00" (#523). */
    public String horarioResumen(Funcionario f) {
        return horarioService.resumen(f);
    }

}
