package com.franco.dev.graphql.personas;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.graphql.personas.input.FuncionarioInput;
import com.franco.dev.service.empresarial.CargoService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.*;
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
    private ClienteService clienteService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private com.franco.dev.service.administrativo.HorarioService horarioService;

    public Optional<Funcionario> funcionario(Long id) {
        return service.findById(id);
    }

    public List<Funcionario> funcionarios(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Page<Funcionario> funcionariosWithPage(int page, int size, Long id, String nombre, List<Long> sucursalList,
            Boolean activo, Long cargoId, Boolean diarista, Boolean fasePrueba) {
        Pageable pageable = PageRequest.of(page, size);
        if (nombre != null) {
            nombre = nombre.replace(" ", "%");
        }
        return service.findAllWithPage(id, nombre, sucursalList, activo, cargoId, diarista, fasePrueba, pageable);
    }

    public List<Funcionario> funcionariosSearch(String texto) {
        return service.findByPersonaNombre(texto);
    }

    public Funcionario saveFuncionario(FuncionarioInput input) {
        ModelMapper m = new ModelMapper();
        Funcionario e;
        // Se guardan las relaciones actuales para restaurarlas si el input no las trae:
        // en un update parcial (sin personaId) no hay que perder la persona ya vinculada,
        // sino el save NPEa al buscar el cliente por persona.
        com.franco.dev.domain.personas.Persona personaActual = null;
        com.franco.dev.domain.empresarial.Cargo cargoActual = null;
        com.franco.dev.domain.empresarial.Sucursal sucursalActual = null;
        if (input.getId() != null) {
            e = service.findById(input.getId()).orElse(new Funcionario());
            personaActual = e.getPersona();
            cargoActual = e.getCargo();
            sucursalActual = e.getSucursal();
            // Evitamos que ModelMapper intente mapear relaciones automáticamente y cause
            // errores de Hibernate
            e.setHorario(null);
            e.setPersona(null);
            e.setCargo(null);
            e.setSucursal(null);
            e.setUsuario(null);
            e.setSupervisadoPor(null);
            m.map(input, e);
            // restaurar lo que el input no reemplaza explicitamente
            if (input.getPersonaId() == null) e.setPersona(personaActual);
            if (input.getCargoId() == null) e.setCargo(cargoActual);
            if (input.getSucursalId() == null) e.setSucursal(sucursalActual);
        } else {
            e = m.map(input, Funcionario.class);
        }
        if (input.getFechaIngreso() != null)
            e.setFechaIngreso(stringToDate(input.getFechaIngreso()));
        // ModelMapper no convierte String->LocalDate; el resto (ips, cuenta, contacto)
        // son String/Boolean y los mapea por nombre automaticamente.
        if (input.getFechaIngresoIps() != null) {
            java.time.LocalDateTime d = stringToDate(input.getFechaIngresoIps());
            e.setFechaIngresoIps(d != null ? d.toLocalDate() : null);
        } else if (input.getId() != null) {
            e.setFechaIngresoIps(null);
        }
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getPersonaId() != null)
            e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        if (input.getCargoId() != null)
            e.setCargo(cargoService.findById(input.getCargoId()).orElse(null));
        if (input.getCargoId() != null)
            e.setCargo(cargoService.findById(input.getCargoId()).orElse(null));
        if (input.getSucursalId() != null)
            e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        if (input.getHorarioId() != null)
            e.setHorario(horarioService.findById(input.getHorarioId()).orElse(null));
        if (input.getSupervisadoPorId() != null)
            e.setSupervisadoPor(service.findById(input.getSupervisadoPorId()).orElse(null));
        e = service.save(e);
        Cliente cliente = clienteService.findByPersonaId(e.getPersona().getId());
        if (cliente != null) {
            if (!java.util.Objects.equals(cliente.getCredito(), e.getCredito())) {
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
        if (usuario == null) {
            usuario = new Usuario();
            usuario.setPassword("123");
            usuario.setPersona(e.getPersona());
            List<String> palabras = Arrays.asList(e.getPersona().getNombre().split("\\s+"));
            switch (palabras.size()) {
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

    public Boolean deleteFuncionario(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countFuncionario() {
        return service.count();
    }

    public Funcionario funcionarioPorPersona(Long id) {
        return service.findByPersonaId(id);
    }

}
