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
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.general.ContactoService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
    private FuncionarioService funcionarioService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<Cliente> cliente(Long id) {
        return service.findById(id);
    }

    public List<Cliente> clientePorTelefono(String texto) {
        List<Contacto> contactoList = contactoService.findByTelefonoOrNombre(texto);
        List<Cliente> clienteList = new ArrayList<>();
        for (Contacto c : contactoList) {
            clienteList.add(service.findByPersonaId(c.getPersona().getId()));
        }
        return clienteList;
    }

    public List<Cliente> clientes(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Cliente> clientePorPersona(String texto) {
        if (texto == null) {
            texto = "";
        } else {
            texto = texto.toUpperCase();
        }

        return service.findByAll(texto);
    }

    public Page<Cliente> onSearchWithFilters(String texto, TipoCliente tipoCliente, Integer page, Integer size) {
        if (texto == null && tipoCliente == null) {
            return null;
        } else {
            return service.findByAll2(texto, tipoCliente, page, size);
        }
    }

    public Cliente saveCliente(ClienteInput input) {
        Boolean modifPersona = false;
        ModelMapper m = new ModelMapper();
        Cliente e = m.map(input, Cliente.class);
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getSucursalId() != null)
            e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        if (input.getPersonaId() != null)
            e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        if (e.getPersona() == null) {
            Persona newPersona = new Persona();
            newPersona.setNombre(input.getNombre());
            newPersona.setDireccion(input.getDireccion());
            newPersona.setDocumento(input.getDocumento());
            if (e.getUsuario() != null)
                newPersona.setUsuario(e.getUsuario());
            newPersona.setCreadoEn(LocalDateTime.now());
            newPersona = personaService.save(newPersona);
            e.setPersona(newPersona);
        }

        if (input.getDireccion() != null && !input.getDireccion().equals(e.getPersona().getDireccion())) {
            e.getPersona().setDireccion(input.getDireccion());
            modifPersona = true;
        }
        if (input.getNombre() != null && !input.getNombre().equals(e.getPersona().getNombre())) {
            e.getPersona().setNombre(input.getNombre());
            modifPersona = true;
        }
        if (modifPersona) {
            personaService.save(e.getPersona());
        }
        e = service.save(e);
        Funcionario funcionario = funcionarioService.findByPersonaId(e.getPersona().getId());
        if (funcionario != null && e.getCredito() != funcionario.getCredito()) {
            funcionario.setCredito(e.getCredito());
            funcionario = funcionarioService.save(funcionario);
        }
        return e;
    }

    public Boolean deleteCliente(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countCliente() {
        return service.count();
    }

    public Cliente clientePorPersonaId(Long id) {
        return service.findByPersonaId(id);
    }

    public ConsultaRucResponse consultaRuc(String ruc) {
        // Delegar al servicio SIFEN si está disponible
        // Este método se mantiene para compatibilidad con queries GraphQL existentes
        return null; // Por ahora retornar null, usar clientePorPersonaDocumentoDetallado para nueva funcionalidad
    }

    /**
     * Método legacy: Busca cliente por documento de persona.
     * Retorna Cliente si fue guardado exitosamente, null si no se pudo guardar.
     * Mantiene compatibilidad con frontend legacy.
     */
    public Cliente clientePorPersonaDocumento(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }

        // Limpiar documento (remover guiones y caracteres especiales)
        String documentoLimpio = texto.trim().replaceAll("[^0-9]", "");

        try {
            // Usar el método del servicio que consulta SIFEN si es necesario
            Cliente cliente = service.buscarOCrearClientePorDocumento(documentoLimpio);
            return cliente;
        } catch (Exception e) {
            // Si hay error (ej: no es contribuyente), retornar null para mantener compatibilidad legacy
            return null;
        }
    }

    /**
     * Método nuevo: Busca cliente por documento de persona con información detallada.
     * Retorna ClienteResponse con información completa, incluyendo datos básicos cuando no se puede guardar.
     */
    public ClienteResponse clientePorPersonaDocumentoDetallado(String texto) {
        ClienteResponse response = new ClienteResponse();
        response.setWarnings(new ArrayList<>());
        response.setErrores(new ArrayList<>());
        response.setExito(false);

        if (texto == null || texto.trim().isEmpty()) {
            response.getErrores().add("El documento no puede estar vacío");
            return response;
        }

        // Limpiar documento (remover guiones y caracteres especiales)
        String documentoLimpio = texto.trim().replaceAll("[^0-9]", "");

        try {
            // Intentar buscar o crear cliente usando SIFEN
            Cliente cliente = service.buscarOCrearClientePorDocumento(documentoLimpio);
            
            if (cliente != null && cliente.getId() != null) {
                response.setCliente(cliente);
                response.setExito(true);
                return response;
            }

            // Si no se pudo crear cliente, intentar obtener datos básicos de SIFEN
            // (esto puede pasar si SIFEN está disponible pero hay algún problema al guardar)
            ClienteDatosBasicos datosBasicos = new ClienteDatosBasicos();
            datosBasicos.setRuc(documentoLimpio);
            response.setDatosBasicos(datosBasicos);
            response.getWarnings().add("No se pudo guardar el cliente, pero se puede generar la factura con los datos básicos.");
            response.setExito(false);

        } catch (RuntimeException e) {
            // Error conocido (ej: no es contribuyente)
            String mensajeError = e.getMessage();
            if (mensajeError != null && mensajeError.contains("no es contribuyente")) {
                response.getErrores().add(mensajeError);
            } else {
                response.getErrores().add("Error al buscar cliente: " + mensajeError);
            }
            response.setExito(false);
        } catch (Exception e) {
            response.getErrores().add("Error al buscar cliente: " + e.getMessage());
            response.setExito(false);
        }

        return response;
    }

}
