package com.franco.dev.graphql.personas.resolver;

import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.general.Contacto;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.financiero.MovimientoPersonasService;
import com.franco.dev.service.financiero.VentaCreditoService;
import com.franco.dev.service.general.ContactoService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.getLastDayOfMonth;

@Component
public class ClienteResolver implements GraphQLResolver<Cliente> {

    @Autowired
    private PersonaService personaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ContactoService contactoService;

    @Autowired
    private VentaCreditoService ventaCreditoService;

    @Autowired
    private MovimientoPersonasService movimientoPersonasService;

    public List<Contacto> contactos(Cliente e) {
        return contactoService.findByPersonaId(e.getPersona().getId());
    }

    public String nombre(Cliente e) {
        return e.getPersona().getNombre();
    }

    public Optional<Usuario> usuarioId(Cliente e) {
        return usuarioService.findById(e.getUsuario().getId());
    }

    public Double saldo(Cliente e) {
//        Double movimiento = movimientoPersonasService.getTotalCredito(e.getPersona().getId(), getLastDayOfMonth(0));
        List<VentaCredito> ventaCreditoList = ventaCreditoService.findByClienteId(e.getId(), EstadoVentaCredito.ABIERTO);
        Double movimiento = 0.0;
        for(VentaCredito vc : ventaCreditoList){
                movimiento += vc.getValorTotal();
        }
        Double saldo = (e.getCredito() != null ? e.getCredito() : 0) - (movimiento != null ? movimiento : 0);
        return saldo!=null ? saldo : 0;
    }

    public String password(Cliente e) throws GraphQLException {
        Usuario user = usuarioService.findByPersonaId(e.getPersona().getId());
        if(user!=null){
            return user.getPassword();
        } else {
            return null;
        }
    }
}
