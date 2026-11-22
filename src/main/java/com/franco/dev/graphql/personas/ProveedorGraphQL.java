package com.franco.dev.graphql.personas;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.personas.input.ClienteInput;
import com.franco.dev.graphql.personas.input.ClienteUpdateInput;
import com.franco.dev.graphql.personas.input.ProveedorInput;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.graphql.exceptions.ConstraintViolada;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.franco.dev.utilitarios.Paginacion;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ProveedorGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private com.franco.dev.service.financiero.TesoreriaSecurityService seg;

    @Autowired
    private ProveedorService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;


    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Proveedor> proveedor(Long id) {return service.findById(id);}

    public List<Proveedor> proveedores(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Proveedor> proveedorPorVendedor(Long id){
        return service.findByVendedorId(id);
    }

    public Proveedor saveProveedor(ProveedorInput input) throws GraphQLException {
        seg.requireAnyRole(PersonaGraphQL.ROLES_PERSONAS);
        ModelMapper m = new ModelMapper();
        Proveedor e = m.map(input, Proveedor.class);
        // Los clientes que no conocen el estado (PWA, versiones viejas) no lo mandan: se conserva el guardado.
        if (input.getActivo() == null) {
            e.setActivo(input.getId() == null ? Boolean.TRUE
                    : service.findById(input.getId()).map(Proveedor::getActivo).orElse(Boolean.TRUE));
        }
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        try {
            return service.save(e);
        } catch (RuntimeException ex) {
            if (ConstraintViolada.en(ex, "proveedor_un")) throw new GraphQLException("Esta persona ya es un proveedor");
            throw ex;
        }
    }

    public Boolean deleteProveedor(Long id){
        seg.requireSuperusuario(); // pantalla solo ADMIN o sin pantalla que la llame
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countProveedor(){
        return service.count();
    }

    public Proveedor proveedorPorPersona(Long id){
        return service.findByPersonaId(id);
    }

    /** activo: true (por defecto en el schema) solo activos, false solo inactivos, null todos. */
    public List<Proveedor> proveedorSearchByPersona(String texto, Boolean activo) {
        return service.findByPersonaNombre(texto, activo);
    }

    /** activo: true (por defecto en el schema) solo activos, false solo inactivos, null todos. */
    public Page<Proveedor> proveedorSearchByPersonaPage(String texto, Integer page, Integer size, Boolean activo) {
        Pageable pageable = Paginacion.of(page, size, Sort.by("persona.nombre", "id"));
        boolean sinTexto = texto == null || texto.trim().isEmpty();
        String pattern = sinTexto ? null : "%" + texto.trim().replace(" ", "%") + "%";
        if (activo == null) {
            return sinTexto ? service.getRepository().findAll(pageable)
                    : service.getRepository().findByPersonaNombreOrApodoOrDocumentoIgnoreCase(pattern, pattern, pattern, pageable);
        }
        return sinTexto ? service.getRepository().findByActivo(activo, pageable)
                : service.getRepository().findByTextoAndActivo(pattern, activo, pageable);
    }

}
