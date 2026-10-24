package com.franco.dev.graphql.personas;

import com.franco.dev.service.financiero.TesoreriaSecurityService;
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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class FuncionarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TesoreriaSecurityService seg;

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

    // sucursalIdList se declara como [Int] en el schema, por lo que graphql-java-tools
    // entrega la lista con Integer sin convertirla al tipo generico del parametro. Se
    // recibe como List<Integer> y se convierte a Long, que es lo que espera la consulta.
    public Page<Funcionario> funcionariosWithPage(int page, int size, Long id, String nombre,
            List<Integer> sucursalList, Boolean activo, Long cargoId, Boolean diarista, Boolean fasePrueba,
            Boolean cobraBanco, Boolean ipsActivo) {
        Pageable pageable = PageRequest.of(page, size);
        if (nombre != null) {
            nombre = nombre.replace(" ", "%");
        }
        List<Long> sucursalIdList = null;
        if (sucursalList != null && !sucursalList.isEmpty()) {
            sucursalIdList = sucursalList.stream()
                    .filter(Objects::nonNull)
                    .map(Number::longValue)
                    .collect(Collectors.toList());
            if (sucursalIdList.isEmpty()) {
                sucursalIdList = null;
            }
        }
        return service.findAllWithPage(id, nombre, sucursalIdList, activo, cargoId, diarista, fasePrueba, cobraBanco,
                ipsActivo, pageable);
    }

    public List<Funcionario> funcionariosSearch(String texto) {
        return service.findByPersonaNombre(texto);
    }

    public Page<Funcionario> funcionariosSearchPage(String texto, Integer page, Integer size, Boolean soloActivos) {
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 15);
        return service.searchPage(texto, soloActivos, pageable);
    }

    /** Alta (sin id): el wizard se abre con VER FUNCIONARIOS, igual que el alta del usuario. */
    static final String[] ROLES_ALTA = {"CREAR FUNCIONARIOS", "VER FUNCIONARIOS", "RRHH GESTIONAR"};
    /** Edicion (con id): legajo y asignacion de horario. */
    static final String[] ROLES_EDICION = {"EDITAR FUNCIONARIOS", "RRHH GESTIONAR"};

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FuncionarioGraphQL.class);

    public Funcionario saveFuncionario(FuncionarioInput input) {
        boolean alta = input == null || input.getId() == null;
        seg.requireAnyRole(alta ? ROLES_ALTA : ROLES_EDICION);
        if (alta) protegerCreditoDeClienteExistente(input);
        else protegerCamposDeRrhh(input);
        ModelMapper m = new ModelMapper();
        Funcionario e;
        // Se guardan las relaciones actuales para restaurarlas si el input no las trae:
        // en un update parcial (sin personaId) no hay que perder la persona ya vinculada,
        // sino el save NPEa al buscar el cliente por persona.
        com.franco.dev.domain.personas.Persona personaActual = null;
        com.franco.dev.domain.empresarial.Cargo cargoActual = null;
        com.franco.dev.domain.empresarial.Sucursal sucursalActual = null;
        com.franco.dev.domain.administrativo.Horario horarioActual = null;
        java.math.BigDecimal sueldoActual = null;
        Float creditoActual = null;
        Boolean diaristaActual = null;
        if (input.getId() != null) {
            e = service.findById(input.getId()).orElse(new Funcionario());
            personaActual = e.getPersona();
            cargoActual = e.getCargo();
            sucursalActual = e.getSucursal();
            horarioActual = e.getHorario();
            sueldoActual = e.getSueldo();
            creditoActual = e.getCredito();
            diaristaActual = e.getDiarista();
            // Evitamos que ModelMapper intente mapear relaciones automáticamente y cause
            // errores de Hibernate
            e.setHorario(null);
            e.setPersona(null);
            e.setCargo(null);
            e.setSucursal(null);
            e.setUsuario(null);
            e.setSupervisadoPor(null);
            Boolean activoPrevio = e.getActivo();
            // ModelMapper copia los null del input: sin esta foto, un guardado que no gestiona
            // los datos del legajo (horario en lote, edicion por persona) los borraba (#452).
            Funcionario previo = new Funcionario();
            previo.setIpsActivo(e.getIpsActivo());
            previo.setNumeroIps(e.getNumeroIps());
            previo.setFechaIngresoIps(e.getFechaIngresoIps());
            previo.setCobraBanco(e.getCobraBanco());
            previo.setCuentaBancaria(e.getCuentaBancaria());
            previo.setCodigoInterno(e.getCodigoInterno());
            previo.setContactoEmergenciaNombre(e.getContactoEmergenciaNombre());
            previo.setContactoEmergenciaTelefono(e.getContactoEmergenciaTelefono());
            previo.setCreadoEn(e.getCreadoEn());
            m.map(input, e);
            // El flag decide el bloque entero: solo el legajo gestiona IPS y banco, y siempre
            // manda el flag; cuando lo apaga, manda numero/cuenta en null para borrarlos.
            if (input.getIpsActivo() == null) {
                e.setIpsActivo(previo.getIpsActivo());
                e.setNumeroIps(previo.getNumeroIps());
            }
            if (input.getCobraBanco() == null) {
                e.setCobraBanco(previo.getCobraBanco());
                e.setCuentaBancaria(previo.getCuentaBancaria());
            }
            if (input.getCodigoInterno() == null) e.setCodigoInterno(previo.getCodigoInterno());
            if (input.getContactoEmergenciaNombre() == null)
                e.setContactoEmergenciaNombre(previo.getContactoEmergenciaNombre());
            if (input.getContactoEmergenciaTelefono() == null)
                e.setContactoEmergenciaTelefono(previo.getContactoEmergenciaTelefono());
            if (input.getCreadoEn() == null) e.setCreadoEn(previo.getCreadoEn());
            // fechaIngresoIps no la mapea ModelMapper (String -> LocalDate): se resuelve abajo.
            e.setFechaIngresoIps(previo.getFechaIngresoIps());
            // restaurar lo que el input no reemplaza explicitamente
            if (input.getPersonaId() == null) e.setPersona(personaActual);
            if (input.getCargoId() == null) e.setCargo(cargoActual);
            if (input.getSucursalId() == null) e.setSucursal(sucursalActual);
            if (input.getActivo() == null) {
                // caller no envió 'activo': preservar el valor actual. 'activo' dispara la
                // cascada de estado, un null accidental inactivaría usuario y cliente.
                e.setActivo(activoPrevio != null ? activoPrevio : true);
            }
            // Sueldo, credito y horario no los gestiona todo formulario que llama a este
            // save (el legajo edita datos personales y deja el salario a su propio flujo
            // con historico). Sin esto, un input que no los trae los borraba, o forzaba al
            // cliente a reenviar una copia posiblemente vieja y pisar el valor real.
            if (input.getSueldo() == null) e.setSueldo(sueldoActual);
            if (input.getDiarista() == null) e.setDiarista(diaristaActual);
            if (input.getCredito() == null) e.setCredito(creditoActual);
            if (input.getHorarioId() == null) e.setHorario(horarioActual);
        } else {
            e = m.map(input, Funcionario.class);
            if (input.getActivo() == null) {
                e.setActivo(true);
            }
            // Sin esto Hibernate inserta NULL y el DEFAULT false no se aplica: el legajo lo
            // mostraba «no inscripto» y la liquidacion le descontaba IPS (#452).
            if (e.getIpsActivo() == null) e.setIpsActivo(false);
            if (e.getCobraBanco() == null) e.setCobraBanco(false);
        }
        if (input.getFechaIngreso() != null)
            e.setFechaIngreso(stringToDate(input.getFechaIngreso()));
        // ModelMapper no convierte String->LocalDate; el resto (ips, cuenta, contacto)
        // son String/Boolean y los mapea por nombre automaticamente.
        // En un update, solo quien gestiona el bloque IPS (manda el flag) toca la fecha.
        if (input.getId() == null || input.getIpsActivo() != null) {
            java.time.LocalDateTime d = input.getFechaIngresoIps() != null
                    ? stringToDate(input.getFechaIngresoIps()) : null;
            e.setFechaIngresoIps(d != null ? d.toLocalDate() : null);
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
            // Un funcionario inactivo no puede estrenar un cliente activo de tipo
            // FUNCIONARIO: sin esto la desactivación terminaba creando justo eso.
            boolean activo = Boolean.TRUE.equals(e.getActivo());
            cliente.setActivo(activo);
            cliente.setTipo(activo ? TipoCliente.FUNCIONARIO : TipoCliente.NORMAL);
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
            // Mismo criterio que el cliente: un funcionario inactivo no puede estrenar
            // un usuario habilitado (y encima con la password por defecto).
            usuario.setActivo(Boolean.TRUE.equals(e.getActivo()));
            usuario = usuarioService.save(usuario);

        }
        return e;
    }

    /**
     * Sueldo, diarista y cargo tienen su propio flujo con historico en RRHH (cambiarSalarioFuncionario,
     * cambiarCargoFuncionario). En una edicion, sin esos roles se descartan del input y quedan los de la base.
     */
    /**
     * Sueldo, diarista y cargo tienen su flujo con historico en RRHH. Sin el rol, un valor igual al
     * guardado se ignora (el legajo y la asignacion de horario los reenvian) y uno distinto se rechaza,
     * para que la pantalla no muestre "Guardado" sin haber cambiado nada.
     */
    /**
     * Sueldo, diarista, credito, banco e IPS los gestiona RRHH (GESTIONAR o CONFIG); cargo y activo, solo
     * GESTIONAR. Sin el rol, un valor igual al guardado se ignora (el legajo y la asignacion de horario
     * reenvian todo) y uno distinto se rechaza, para que la pantalla no muestre "Guardado" sin haber
     * cambiado nada. Ignorar = anular el campo del input: el save conserva lo guardado.
     */
    private void protegerCamposDeRrhh(FuncionarioInput in) {
        // un id que no existe no es una edicion: el save lo crearia como alta
        Funcionario a = service.findById(in.getId())
                .orElseThrow(() -> new graphql.GraphQLException("No existe el funcionario " + in.getId()));
        if (!seg.hasAnyRole("RRHH GESTIONAR", "RRHH CONFIG")) {
            boolean cambia = in.getSueldo() != null && (a.getSueldo() == null
                    || new java.math.BigDecimal(in.getSueldo().toString()).compareTo(a.getSueldo()) != 0);
            cambia |= in.getDiarista() != null && in.getDiarista() != flag(a.getDiarista());
            // el legajo manda 0 si el credito esta vacio
            cambia |= in.getCredito() != null
                    && in.getCredito().floatValue() != (a.getCredito() == null ? 0f : a.getCredito());
            // banco e IPS: el flag decide el bloque; con el flag apagado el resto no cuenta (lo manda en null)
            if (in.getCobraBanco() != null) {
                cambia |= in.getCobraBanco() != flag(a.getCobraBanco())
                        || in.getCobraBanco() && !texto(in.getCuentaBancaria()).equals(texto(a.getCuentaBancaria()));
            }
            if (in.getIpsActivo() != null) {
                cambia |= in.getIpsActivo() != flag(a.getIpsActivo())
                        || in.getIpsActivo() && (!texto(in.getNumeroIps()).equals(texto(a.getNumeroIps()))
                        || !java.util.Objects.equals(fecha(in.getFechaIngresoIps()), a.getFechaIngresoIps()));
            }
            if (cambia) seg.requireAnyRole("RRHH GESTIONAR", "RRHH CONFIG");
            in.setSueldo(null);
            in.setDiarista(null);
            in.setCredito(null);
            in.setCobraBanco(null);
            in.setCuentaBancaria(null);
            in.setIpsActivo(null);
            in.setNumeroIps(null);
            in.setFechaIngresoIps(null);
        }
        if (!seg.hasAnyRole("RRHH GESTIONAR")) {
            Long cargo = a.getCargo() == null ? null : a.getCargo().getId();
            boolean cambia = in.getCargoId() != null && !in.getCargoId().equals(cargo);
            // pasar el legajo a otra persona le pisaria el credito a su cliente
            cambia |= in.getPersonaId() != null
                    && (a.getPersona() == null || !in.getPersonaId().equals(a.getPersona().getId()));
            // el save trata un activo nulo como true
            cambia |= in.getActivo() != null && in.getActivo() != (a.getActivo() == null || a.getActivo());
            if (cambia) seg.requireAnyRole("RRHH GESTIONAR");
            in.setCargoId(null);
            in.setActivo(null);
            in.setPersonaId(null);
        }
    }

    /**
     * El save copia el credito del funcionario al cliente de la persona. En un alta sin RRHH, si la persona
     * ya es cliente, su credito no cambia (el wizard siempre crea una persona nueva, sin cliente).
     */
    private void protegerCreditoDeClienteExistente(FuncionarioInput in) {
        if (in == null || in.getPersonaId() == null || seg.hasAnyRole("RRHH GESTIONAR", "RRHH CONFIG")) return;
        Cliente c = clienteService.findByPersonaId(in.getPersonaId());
        if (c == null) return;
        float pedido = in.getCredito() == null ? 0f : in.getCredito();
        if (pedido != (c.getCredito() == null ? 0f : c.getCredito())) seg.requireAnyRole("RRHH GESTIONAR", "RRHH CONFIG");
    }

    private static boolean flag(Boolean b) { return Boolean.TRUE.equals(b); }

    /** El legajo manda trim + mayusculas; vacio y null son lo mismo. */
    private static String texto(String s) { return s == null ? "" : s.trim().toUpperCase(); }

    private static java.time.LocalDate fecha(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        java.time.LocalDateTime d = stringToDate(s);
        return d != null ? d.toLocalDate() : null;
    }

    public Boolean deleteFuncionario(Long id) {
        seg.requireSuperusuario();
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
