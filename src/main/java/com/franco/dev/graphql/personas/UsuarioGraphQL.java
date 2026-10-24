package com.franco.dev.graphql.personas;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.personas.input.UsuarioInput;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioRoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class UsuarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    // Los mismos roles con los que el desktop habilita cada pantalla.
    static final String EDITAR_USUARIOS = "EDITAR USUARIOS";
    static final String VER_FUNCIONARIOS = "VER FUNCIONARIOS";
    static final String RRHH_GESTIONAR = "RRHH GESTIONAR";
    /** El wizard de funcionario borra el usuario que acaba de crear si falla un paso posterior. */
    static final int MINUTOS_ROLLBACK_WIZARD = 15;

    @Autowired
    private TesoreriaSecurityService seg;

    @Autowired
    private UsuarioService service;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UsuarioRoleService usuarioRoleService;


    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Usuario> usuario(Long id) {
        return service.findById(id);
    }

    public Usuario usuarioPorPersonaId(Long id) {
        return service.findByPersonaId(id);
    }

    public List<Usuario> usuarioSearch(String texto) {
        return service.findbyIdOrPersona(texto);
    }

    public List<Usuario> usuarios(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public com.franco.dev.config.multitenant.CustomPage<Usuario> usuarioSearchPaginated(String texto, Integer page, Integer size) {
        org.springframework.data.domain.Page<Usuario> pageResult = service.findbyIdOrPersonaPaginated(texto, page, size);
        return new com.franco.dev.config.multitenant.CustomPageImpl<>(pageResult.getContent(), pageResult.getPageable(), pageResult.getTotalElements(), null);
    }

    public com.franco.dev.config.multitenant.CustomPage<Usuario> usuarioSearchPaginatedPorRoles(String texto, Integer page, Integer size, List<Long> roleIds) {
        org.springframework.data.domain.Page<Usuario> pageResult = service.findbyIdOrPersonaPaginated(texto, page, size, roleIds);
        return new com.franco.dev.config.multitenant.CustomPageImpl<>(pageResult.getContent(), pageResult.getPageable(), pageResult.getTotalElements(), null);
    }

    public Usuario saveUsuario(UsuarioInput input) {
        Usuario guardado = input.getId() != null ? service.findById(input.getId()).orElse(null) : null;
        boolean soloPropio = exigirPermisoParaGuardar(input, guardado);
        ModelMapper m = new ModelMapper();
        Usuario e = m.map(input, Usuario.class);
        if (input.getUsuarioId() != null)
            e.setUsuario(service.findById(input.getUsuarioId()).orElse(null));
        if (input.getPersonaId() != null)
            e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        if (input.getActivo() == null) {
            // caller no envió 'activo': preservar el valor actual (o true si es nuevo)
            e.setActivo(input.getId() != null
                    ? service.findById(input.getId()).map(Usuario::getActivo).orElse(true)
                    : true);
        }
        if (soloPropio) {
            // Sin EDITAR USUARIOS, cada uno solo cambia su clave: el resto queda como estaba.
            e.setNickname(guardado.getNickname());
            e.setActivo(guardado.getActivo());
            e.setPersona(guardado.getPersona());
            e.setUsuario(guardado.getUsuario());
            e.setCreadoEn(guardado.getCreadoEn());
        }
        e = service.save(e, Boolean.TRUE.equals(input.getRespetarClave()));
        return e;
    }

    /** Devuelve true si el que guarda edita su propio usuario sin EDITAR USUARIOS. */
    private boolean exigirPermisoParaGuardar(UsuarioInput input, Usuario guardado) {
        // El nickname ADMIN es superusuario por si solo: crearlo o tomarlo es solo de un administrador.
        boolean pasaASerAdmin = input.getNickname() != null && "ADMIN".equalsIgnoreCase(input.getNickname().trim())
                && (guardado == null || !"ADMIN".equalsIgnoreCase(guardado.getNickname()));
        if (pasaASerAdmin) seg.requireSuperusuario();
        if (input.getId() == null) {
            seg.requireAnyRole(EDITAR_USUARIOS, VER_FUNCIONARIOS);
            return false;
        }
        if (guardado == null) {
            seg.requireAnyRole(EDITAR_USUARIOS);
            return false;
        }
        Usuario yo = seg.requireUsuarioDeSesion();
        if (esSuperusuario(guardado)) seg.requireSuperusuario();
        if (seg.hasAnyRole(EDITAR_USUARIOS)) return false;
        if (yo.getId().equals(guardado.getId())) return true;
        seg.requireAnyRole(EDITAR_USUARIOS);
        return false;
    }

    private boolean esSuperusuario(Usuario u) {
        return "ADMIN".equalsIgnoreCase(u.getNickname()) || service.tieneRol(u.getId(), "ADMIN");
    }

    public Boolean deleteUsuario(Long id) {
        Usuario u = service.findById(id).orElse(null);
        if (u != null && esSuperusuario(u)) seg.requireSuperusuario();
        if (u != null && esRecienCreadoSinRoles(u)) seg.requireAnyRole(EDITAR_USUARIOS, VER_FUNCIONARIOS);
        else seg.requireAnyRole(EDITAR_USUARIOS);
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countUsuario() {
        return service.count();
    }

    public Boolean verificarUsuario(String nickname) {
        Boolean res = service.existsByNickname(nickname);
        return res;
    }

    private boolean esRecienCreadoSinRoles(Usuario u) {
        return u.getCreadoEn() != null
                && u.getCreadoEn().isAfter(LocalDateTime.now().minusMinutes(MINUTOS_ROLLBACK_WIZARD))
                && service.getRoles(u.getId()).isEmpty();
    }

    public Boolean saveUsuarioImage(Long id, String type, String image, List<Double> embedding, String embeddingGaleriaJson)
            throws IOException {
        if (!"perfil".equals(type) && !"auth".equals(type)) {
            throw new GraphQLException("Tipo de imagen no válido.");
        }
        // Marcar horario registra la cara de otro solo si todavía no tiene; reemplazarla es de RRHH.
        if (service.tieneGaleriaFacial(id)) {
            Usuario yo = seg.currentUsuario();
            if (yo == null || !yo.getId().equals(id)) seg.requireAnyRole(RRHH_GESTIONAR);
        }
        return service.saveUserImage(id, type, image, embedding, embeddingGaleriaJson);
    }

    public List<String> getUsuarioImages(Long id, String type) {
        return service.getUserImages(id, type);
    }

    public Integer isUserFaceAuth(Long id) {
        return service.isUserFaceAuth(id);
    }

    public Boolean deleteUserImage() {
        return false;
    }

    /**
     * Obtiene todos los usuarios activos del sistema
     * 
     * @return Lista de usuarios activos ordenados por nombre
     */
    public List<Usuario> usuariosActivos() {
        return service.findAllActivos();
    }

    public UsuarioSimilitudResult usuarioPorEmbedding(List<Double> embedding, List<Integer> excludeIds) {
        return service.findUsuarioByEmbedding(embedding, excludeIds);
    }

    public IncorporarEmbeddingMarcacionResult incorporarEmbeddingMarcacion(Long usuarioId, List<Double> embedding, Double score) {
        return service.incorporarEmbeddingMarcacion(usuarioId, embedding, score);
    }
}
