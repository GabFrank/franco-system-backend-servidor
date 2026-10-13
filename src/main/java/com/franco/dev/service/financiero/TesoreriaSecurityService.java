package com.franco.dev.service.financiero;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Control de acceso por rol **específico de Tesorería** (caja virtual / caja
 * mayor), self-contained: resuelve el usuario autenticado desde el principal del
 * SecurityContext (el nickname, que sí se setea) y lee sus roles desde la DB
 * (`personas.usuario_role`). NO depende del marshaling de roles JWT→Authentication
 * (roto a nivel sistema, ver issue #177) ni toca `com.franco.dev.security`.
 *
 * Mismo patrón que {@code service.rrhh.RrhhSecurityService}. Bypass de
 * superusuario: rol "ADMIN" o el nickname "ADMIN" (cuenta superusuario pelada, sin
 * filas en usuario_role). Los demás usuarios necesitan el rol de tesorería
 * correspondiente para cada acción sensible.
 */
@Service
public class TesoreriaSecurityService {

    @Autowired private UsuarioService usuarioService;
    @Autowired private RoleService roleService;
    @Autowired private com.franco.dev.repository.financiero.CajaVirtualAccesoRepository accesoRepository;
    @Autowired private com.franco.dev.repository.financiero.CajaVirtualRepository cajaVirtualRepository;

    public static final String ADMIN = "ADMIN";
    public static final String VER = "TESORERIA VER";
    public static final String GESTIONAR = "TESORERIA GESTIONAR";
    /** Permiso dedicado para cobrar cuentas por cobrar (CPC). */
    public static final String CPC_COBRAR = "TESORERIA CPC COBRAR";
    /** Permiso dedicado para pagar cuentas por pagar / solicitudes (CPP). */
    public static final String CPP_PAGAR = "TESORERIA CPP PAGAR";

    /**
     * Cargar la cotización de una moneda. No es un rol de tesorería (no habilita ver cajas): es el
     * mismo que ya gatea la pantalla en el desktop (ROLES.CAMBIAR_COTIZACION). Lo garantiza V237.3.
     */
    public static final String CAMBIAR_COTIZACION = "CAMBIAR COTIZACION";

    /**
     * Cancelar o reactivar una venta, y cancelar su factura. Tampoco es un rol de tesorería: es el que
     * ya exige el PDV en el desktop (ROLES.CANCELACION_DE_VENTA). Es un dato de personas.role, no lo
     * crea ninguna migración.
     */
    public static final String CANCELACION_DE_VENTA = "CANCELACION DE VENTA";

    /** Cualquier rol de tesorería habilita la lectura. */
    public static final String[] TODOS = {VER, GESTIONAR, CPC_COBRAR, CPP_PAGAR};

    /**
     * Operador de caja acotado: ve y mueve el efectivo de las cajas a las que tiene acceso, y nada más.
     * No está en {@link #TODOS} a propósito: {@code requireVer}/{@code requireGestionar} lo rechazan, y cada
     * resolver delimitado por caja lo habilita con {@code requireVerCaja}/{@code requireOperarCaja}.
     */
    public static final String CAJA = "TESORERIA CAJA";

    /** Cobrar CPC: permiso dedicado, o GESTIONAR, o superusuario. */
    public void requireCobrarCpc() { requireAnyRole(CPC_COBRAR, GESTIONAR); }

    /** Pagar CPP: permiso dedicado, o GESTIONAR, o superusuario. */
    public void requirePagarCpp() { requireAnyRole(CPP_PAGAR, GESTIONAR); }

    /** Cargar una cotización: permiso dedicado, o superusuario (issue #326). */
    public void requireCambiarCotizacion() { requireAnyRole(CAMBIAR_COTIZACION); }

    /** Cancelar o reactivar una venta, o cancelar una factura: permiso dedicado, o superusuario (issue #340). */
    public void requireCancelarVenta() { requireAnyRole(CANCELACION_DE_VENTA); }

    /** Solo superusuario: ningún rol de negocio alcanza. */
    public void requireSuperusuario() {
        if (!esSuperusuario()) {
            throw new GraphQLException("No autorizado: esta acción es solo para un administrador.");
        }
    }

    private String currentNickname() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    /** El usuario autenticado (por nickname del SecurityContext), o null. */
    public Usuario currentUsuario() {
        String nick = currentNickname();
        if (nick == null) return null;
        return usuarioService.findByNickname(nick).orElse(null);
    }

    /** Nombres de rol del usuario autenticado (upper/trim), o vacío. */
    public Set<String> currentRoles() {
        Usuario u = currentUsuario();
        if (u == null) return Collections.emptySet();
        Set<String> names = new HashSet<>();
        for (Role r : roleService.findByUsuarioId(u.getId())) {
            if (r.getNombre() != null) names.add(r.getNombre().trim().toUpperCase());
        }
        return names;
    }

    /** true si es superusuario (rol ADMIN o nickname ADMIN) o tiene alguno de los roles. */
    public boolean hasAnyRole(String... roles) {
        String nick = currentNickname();
        if (nick != null && ADMIN.equalsIgnoreCase(nick)) return true;   // superusuario pelado
        Set<String> mine = currentRoles();
        if (mine.contains(ADMIN)) return true;                            // rol ADMIN = super-rol
        if (roles != null) {
            for (String r : roles) {
                if (r != null && mine.contains(r.trim().toUpperCase())) return true;
            }
        }
        return false;
    }

    /** Lanza excepción si el usuario no tiene ninguno de los roles (ni es superusuario). */
    public void requireAnyRole(String... roles) {
        if (!hasAnyRole(roles)) {
            throw new GraphQLException("No autorizado: se requiere el rol "
                    + String.join(" o ", roles) + " para esta acción.");
        }
    }

    /** Gestión de tesorería (crear/mover/transferir/borrar caja): exige GESTIONAR. */
    public void requireGestionar() {
        requireAnyRole(GESTIONAR);
    }

    /** Lectura de datos de tesorería: exige cualquier rol de tesorería (o superusuario). */
    public void requireVer() {
        if (!hasAnyRole(TODOS)) {
            throw new GraphQLException("No autorizado: se requiere un rol de Tesorería para ver estos datos.");
        }
    }

    // ─────────────────────── Rol acotado (TESORERIA CAJA) ───────────────────────

    /** Lectura delimitada por caja: cualquier rol de tesorería, o el acotado. El ACL decide qué cajas. */
    public void requireVerCaja() {
        if (!hasAnyRole(TODOS) && !hasAnyRole(CAJA)) {
            throw new GraphQLException("No autorizado: se requiere un rol de Tesorería para ver estos datos.");
        }
    }

    /** Mover el efectivo de una caja: GESTIONAR o el acotado. La escritura sobre la caja la exige el ACL. */
    public void requireOperarCaja() { requireAnyRole(GESTIONAR, CAJA); }

    /**
     * Pagar desde la caja: lo mismo que {@link #requirePagarCpp()} más el rol acotado. Solo para los pagos
     * que validan el modo acotado; el resto sigue con {@code requirePagarCpp}.
     */
    public void requirePagarCppCaja() { requireAnyRole(CPP_PAGAR, GESTIONAR, CAJA); }

    /**
     * true si el usuario <b>ve</b> en modo acotado: tiene TESORERIA CAJA y ningún rol amplio de tesorería.
     * Sin usuario autenticado es false: un proceso de sistema no es un operador acotado.
     */
    public boolean esSoloCaja() {
        return tieneCajaSin(TODOS);
    }

    /**
     * true si el usuario <b>mueve plata</b> en modo acotado: tiene TESORERIA CAJA y no TESORERIA GESTIONAR.
     *
     * <p>No alcanza con {@link #esSoloCaja()}: {@code requireOperarCaja} deja pasar por TESORERIA CAJA, y un
     * usuario con CAJA + VER no es «solo caja» pero tampoco gestiona. Si las restricciones dependieran de
     * «ningún rol amplio», sumar un rol de solo lectura le daría las operaciones de GESTIONAR.</p>
     */
    public boolean operaAcotado() {
        return tieneCajaSin(GESTIONAR);
    }

    /** Idem para pagar: tiene TESORERIA CAJA y ninguno de los roles que pagan (CPP PAGAR, GESTIONAR). */
    public boolean pagaAcotado() {
        return tieneCajaSin(CPP_PAGAR, GESTIONAR);
    }

    private boolean tieneCajaSin(String... amplios) {
        String nick = currentNickname();
        if (nick == null || ADMIN.equalsIgnoreCase(nick)) return false;
        Set<String> mine = currentRoles();   // una sola lectura de roles por consulta
        if (mine.contains(ADMIN) || !mine.contains(CAJA)) return false;
        for (String r : amplios) {
            if (mine.contains(r)) return false;
        }
        return true;
    }

    // Un listado pide el saldo de cada fila: el rol y las cajas visibles se resuelven una vez por request.
    private static final String ATRIBUTO_SOLO_CAJA = TesoreriaSecurityService.class.getName() + ".soloCaja";
    private static final String ATRIBUTO_VISIBLES = TesoreriaSecurityService.class.getName() + ".cajasVisibles";
    private static final Object TODAS = new Object();

    /** {@link #esSoloCaja()} resuelto una vez por request. Sin request (un test, otro hilo) se calcula cada vez. */
    public boolean esSoloCajaEnRequest() {
        org.springframework.web.context.request.RequestAttributes request =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (request == null) return esSoloCaja();
        Object guardado = request.getAttribute(ATRIBUTO_SOLO_CAJA,
                org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        if (guardado instanceof Boolean) return (Boolean) guardado;
        boolean solo = esSoloCaja();
        request.setAttribute(ATRIBUTO_SOLO_CAJA, solo,
                org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        return solo;
    }

    /** true si el usuario puede ver la caja, con {@link #cajasVisiblesIds()} resuelto una vez por request. */
    @SuppressWarnings("unchecked")
    public boolean puedeVerCajaEnRequest(Long cajaId) {
        if (cajaId == null) return false;
        org.springframework.web.context.request.RequestAttributes request =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        Object guardado = request != null ? request.getAttribute(ATRIBUTO_VISIBLES,
                org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST) : null;
        if (guardado == null) {
            java.util.List<Long> visibles = cajasVisiblesIds();   // null = ve todas
            guardado = visibles == null ? TODAS : visibles;
            if (request != null) {
                request.setAttribute(ATRIBUTO_VISIBLES, guardado,
                        org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            }
        }
        return guardado == TODAS || ((java.util.List<Long>) guardado).contains(cajaId);
    }

    /**
     * Sucursales de las cajas sobre las que el usuario tiene escritura (propias u otorgadas). Nunca null:
     * la lista vacía significa «ninguna», no «todas». Las cajas sin sucursal no aportan.
     */
    public java.util.List<Long> sucursalesOperablesIds() {
        Long id = idUsuarioActual();
        if (id == null) return java.util.Collections.emptyList();
        return accesoRepository.sucursalesOperablesIds(id);
    }

    /**
     * Cajas que el usuario puede mover (propias u otorgadas con escritura). Nunca null: es para el rol
     * acotado, donde la lista vacía significa «ninguna».
     */
    public java.util.List<Long> cajasEscribiblesIds() {
        Long id = idUsuarioActual();
        if (id == null) return java.util.Collections.emptyList();
        return accesoRepository.cajasEscribiblesIds(id);
    }

    // ─────────────────────── Acceso por caja (ACL) ───────────────────────
    //
    // El rol habilita la capacidad; el ACL delimita sobre que cajas (modelo AND). El
    // propietario de la caja tiene lectura y escritura implicitas y no lleva fila en la tabla;
    // un superusuario pasa por encima de todo.

    /** true si es superusuario: rol ADMIN o nickname ADMIN (cuenta pelada, sin usuario_role). */
    public boolean esSuperusuario() {
        String nick = currentNickname();
        if (nick != null && ADMIN.equalsIgnoreCase(nick)) return true;
        return currentRoles().contains(ADMIN);
    }

    /** true si el usuario autenticado creo la caja (y por lo tanto administra su lista). */
    public boolean esPropietario(Long cajaId) {
        if (cajaId == null) return false;
        Usuario u = currentUsuario();
        if (u == null) return false;
        com.franco.dev.domain.financiero.CajaVirtual caja = cajaVirtualRepository.findById(cajaId).orElse(null);
        return caja != null && caja.getUsuario() != null && u.getId().equals(caja.getUsuario().getId());
    }

    public boolean puedeLeerCaja(Long cajaId) {
        if (esSuperusuario()) return true;
        if (esPropietario(cajaId)) return true;
        return accesoRepository.findByCajaVirtualIdAndUsuarioId(cajaId, idUsuarioActual())
                .map(a -> Boolean.TRUE.equals(a.getPuedeLeer()) || Boolean.TRUE.equals(a.getPuedeEscribir()))
                .orElse(false);
    }

    public boolean puedeEscribirCaja(Long cajaId) {
        if (esSuperusuario()) return true;
        if (esPropietario(cajaId)) return true;
        return accesoRepository.findByCajaVirtualIdAndUsuarioId(cajaId, idUsuarioActual())
                .map(a -> Boolean.TRUE.equals(a.getPuedeEscribir()))
                .orElse(false);
    }

    /**
     * Proceso de sistema: no hay usuario autenticado en el contexto.
     *
     * <p>Los schedulers y la replicacion de retiros postean en caja sin sesion. Si el ACL los
     * rechazara se cortaria la replicacion, asi que se los deja pasar. Es deliberado y acotado:
     * solo aplica cuando <b>no hay principal</b>, no cuando hay uno sin permisos.</p>
     */
    public boolean esProcesoDeSistema() {
        return currentNickname() == null;
    }

    public void requireLecturaCaja(Long cajaId) {
        if (esProcesoDeSistema()) return;
        if (!puedeLeerCaja(cajaId)) {
            throw new GraphQLException("No tenes acceso a esta caja. Pediselo a su responsable.");
        }
    }

    public void requireEscrituraCaja(Long cajaId) {
        if (esProcesoDeSistema()) return;
        if (!puedeEscribirCaja(cajaId)) {
            throw new GraphQLException("No tenes permiso para mover plata en esta caja. "
                    + "Pediselo a su responsable.");
        }
    }

    /**
     * Enviar plata A una caja (la pata de entrada de una transferencia): alcanza con poder moverla, o con
     * que la caja esté activa y marcada «recibe envíos». Lo segundo no da nada más: ni verla ni sacarle.
     */
    public void requireEnvioACaja(Long cajaId) {
        if (esProcesoDeSistema()) return;
        if (puedeEscribirCaja(cajaId)) return;
        com.franco.dev.domain.financiero.CajaVirtual caja =
                cajaId != null ? cajaVirtualRepository.findById(cajaId).orElse(null) : null;
        if (caja != null && Boolean.TRUE.equals(caja.getActivo()) && Boolean.TRUE.equals(caja.getRecibeEnvios())) return;
        throw new GraphQLException("No tenes permiso para enviar plata a esta caja. "
                + "Pedile a tesorería que la habilite para recibir envíos.");
    }

    /** Administrar la lista de accesos: solo el propietario de la caja, o un superusuario. */
    public void requirePropietarioCaja(Long cajaId) {
        if (esSuperusuario() || esPropietario(cajaId)) return;
        throw new GraphQLException("Solo el responsable de la caja puede administrar sus accesos.");
    }

    /**
     * Ids de las cajas visibles para el usuario autenticado, o {@code null} si ve todas
     * (superusuario). El null es la senal de "no filtres": es distinto de la lista vacia,
     * que significa "no ve ninguna".
     */
    public java.util.List<Long> cajasVisiblesIds() {
        if (esProcesoDeSistema() || esSuperusuario()) return null;
        Long id = idUsuarioActual();
        if (id == null) return java.util.Collections.emptyList();
        return accesoRepository.cajasVisiblesIds(id);
    }

    private Long idUsuarioActual() {
        Usuario u = currentUsuario();
        return u != null ? u.getId() : null;
    }

}
