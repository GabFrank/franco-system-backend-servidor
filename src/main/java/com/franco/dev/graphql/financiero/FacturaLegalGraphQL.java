package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.security.Unsecured;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class FacturaLegalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FacturaLegalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private TimbradoDetalleService timbradoDetalleService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private FacturaLegalItemGraphQL facturaLegalItemGraphQL;

    public Optional<FacturaLegal> facturaLegal(Long id) {
        return service.findById(id);
    }

    public List<FacturaLegal> facturaLegales(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    @Unsecured
    @Transactional
    public FacturaLegal saveFacturaLegal(FacturaLegalInput input, List<FacturaLegalItemInput> facturaLegalItemInputList) {
        ModelMapper m = new ModelMapper();
        FacturaLegal e = m.map(input, FacturaLegal.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getClienteId() != null) {
            e.setCliente(clienteService.findById(input.getClienteId()).orElse(null));
        } else {
            if(input.getNombre()!=null && input.getRuc()!=null){
                Persona nuevaPersona = personaService.findByDocumento(input.getRuc());
                if(nuevaPersona==null){
                    nuevaPersona = new Persona();
                    nuevaPersona.setNombre(input.getNombre());
                    nuevaPersona.setDocumento(input.getRuc());
                    nuevaPersona.setUsuario(e.getUsuario());
                    nuevaPersona.setDireccion(input.getDireccion());
                    nuevaPersona = personaService.save(nuevaPersona);
                }
                if(nuevaPersona!=null){
                    propagacionService.propagarEntidad(nuevaPersona, TipoEntidad.PERSONA);
                    Cliente cli = clienteService.findByPersonaId(nuevaPersona.getId());
                    if(cli==null){
                        cli = new Cliente();
                        cli.setPersona(nuevaPersona);
                        cli.setUsuario(e.getUsuario());
                        cli.setCredito((float) 0);
                        cli = clienteService.save(cli);
                    }
                    if(cli!=null){
                        propagacionService.propagarEntidad(cli, TipoEntidad.CLIENTE);
                        e.setCliente(cli);
                    }
                }


            }
        }
        if (input.getVentaId() != null) e.setVenta(ventaService.findById(input.getVentaId()).orElse(null));
        if (input.getTimbradoDetalleId() != null)
            e.setTimbradoDetalle(timbradoDetalleService.findById(input.getTimbradoDetalleId()).orElse(null));
        if(e.getTimbradoDetalle()!=null){
            timbradoDetalleService.save(e.getTimbradoDetalle());
            e = service.save(e);
            Long sucId = e.getTimbradoDetalle().getPuntoDeVenta().getSucursal().getId();
            propagacionService.propagarEntidad(e, TipoEntidad.FACTURA, sucId);
            for (FacturaLegalItemInput fi : facturaLegalItemInputList) {
                fi.setFacturaLegalId(e.getId());
                if (input.getUsuarioId() != null) fi.setUsuarioId(e.getUsuario().getId());
                facturaLegalItemGraphQL.saveFacturaLegalItem(fi, sucId);
            }
        }
        return e;
    }

    public Boolean deleteFacturaLegal(Long id) {
        return service.deleteById(id);
    }

    public Long countFacturaLegal() {
        return service.count();
    }

}
