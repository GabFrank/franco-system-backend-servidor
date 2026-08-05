package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.ProveedorServicio;
import com.franco.dev.graphql.personas.input.ProveedorServicioInput;
import com.franco.dev.service.financiero.CuentaBancariaService;
import com.franco.dev.service.financiero.TerminalPosService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.ProveedorServicioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ProveedorServicioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ProveedorServicioService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private CuentaBancariaService cuentaBancariaService;

    @Autowired
    private TerminalPosService terminalPosService;

    public Optional<ProveedorServicio> proveedorServicio(Long id) {
        return service.findById(id);
    }

    public ProveedorServicio proveedorServicioPorPersona(Long personaId) {
        return service.findByPersonaId(personaId);
    }

    public Page<ProveedorServicio> proveedorServicioSearchPage(String texto, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("persona.nombre"));
        if (texto == null || texto.trim().isEmpty()) {
            return service.getRepository().findAll(pageable);
        }
        String pattern = "%" + texto.trim().replace(" ", "%") + "%";
        return service.getRepository()
                .findByPersonaNombreOrApodoOrDocumentoIgnoreCase(pattern, pattern, pattern, pageable);
    }

    public ProveedorServicio saveProveedorServicio(ProveedorServicioInput input) throws GraphQLException {
        ModelMapper m = new ModelMapper();
        ProveedorServicio e = m.map(input, ProveedorServicio.class);
        e.setUsuario(input.getUsuarioId() != null
                ? usuarioService.findById(input.getUsuarioId()).orElse(null) : null);
        e.setPersona(input.getPersonaId() != null
                ? personaService.findById(input.getPersonaId()).orElse(null) : null);
        e.setCuentaBancaria(input.getCuentaBancariaId() != null
                ? cuentaBancariaService.findById(input.getCuentaBancariaId()).orElse(null) : null);
        try {
            return service.save(e);
        } catch (Exception ex) {
            // El nombre del constraint suele quedar en la causa raiz (PSQLException),
            // no en el mensaje del wrapper de Spring.
            for (Throwable t = ex; t != null; t = t.getCause()) {
                if (t.getMessage() != null && t.getMessage().contains("proveedor_servicio_un")) {
                    throw new GraphQLException("Esta persona ya es un proveedor de servicio");
                }
            }
            throw ex;
        }
    }

    /**
     * CrudService.deleteById se traga la excepcion y devuelve false, asi que la violacion
     * de FK llegaria al desktop como un "eliminado con exito" mentiroso. Chequeamos antes.
     */
    public Boolean deleteProveedorServicio(Long id) throws GraphQLException {
        Long terminales = terminalPosService.countByProveedorServicioId(id);
        if (terminales != null && terminales > 0) {
            throw new GraphQLException(
                    "No se puede eliminar: hay " + terminales + " terminal(es) POS vinculada(s) a este proveedor de servicio");
        }
        return service.deleteById(id);
    }

    public Long countProveedorServicio() {
        return service.count();
    }
}
