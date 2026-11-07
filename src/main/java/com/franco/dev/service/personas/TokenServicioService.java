package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.TokenServicio;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.personas.TokenServicioRepository;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.security.TokenServicioFormato;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Emision, listado y revocacion de tokens de servicio para bots (issue #579). Todo es solo para
 * superusuario. La validacion de cada request vive aparte, en {@code TokenServicioValidator}.
 *
 * Reglas de emision:
 * <ul>
 *   <li>El usuario existe, esta activo y no es superusuario: un bot no lleva llave maestra. Como un
 *       token nunca es de un superusuario, un token de servicio tampoco puede emitir otros.</li>
 *   <li>Vigencia de 1 a 365 dias, 90 por defecto.</li>
 *   <li>La clave del usuario se reemplaza por una aleatoria que no se guarda en otro lado: un
 *       usuario de servicio no conserva login humano (sin esto queda con la clave por defecto y
 *       podria sacar por /login un JWT que no vence).</li>
 * </ul>
 */
@Service
public class TokenServicioService {

    private static final Logger log = LoggerFactory.getLogger(TokenServicioService.class);

    public static final int DIAS_VIGENCIA_DEFAULT = 90;
    public static final int DIAS_VIGENCIA_MAXIMO = 365;
    static final String ADMIN = "ADMIN";

    private final TokenServicioRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final TesoreriaSecurityService seg;

    public TokenServicioService(TokenServicioRepository repository, UsuarioRepository usuarioRepository,
            UsuarioService usuarioService, TesoreriaSecurityService seg) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.seg = seg;
    }

    /** Un token recien emitido: el unico momento en que existe el token en claro. */
    public static class TokenServicioCreado {
        private final TokenServicio tokenServicio;
        private final String token;

        public TokenServicioCreado(TokenServicio tokenServicio, String token) {
            this.tokenServicio = tokenServicio;
            this.token = token;
        }

        public TokenServicio getTokenServicio() { return tokenServicio; }

        public String getToken() { return token; }
    }

    @Transactional
    public TokenServicioCreado crear(Long usuarioId, String nombre, Integer diasVigencia) {
        seg.requireSuperusuario();
        Usuario creador = seg.requireUsuarioDeSesion();

        String nombreLimpio = nombre == null ? "" : nombre.trim();
        if (nombreLimpio.length() < 3 || nombreLimpio.length() > 80) {
            throw new GraphQLException("El nombre del token tiene que tener entre 3 y 80 caracteres.");
        }
        int dias = diasVigencia == null ? DIAS_VIGENCIA_DEFAULT : diasVigencia;
        if (dias < 1 || dias > DIAS_VIGENCIA_MAXIMO) {
            throw new GraphQLException("La vigencia del token tiene que ser de 1 a " + DIAS_VIGENCIA_MAXIMO + " dias.");
        }
        Usuario usuario = usuarioId == null ? null : usuarioService.findById(usuarioId).orElse(null);
        if (usuario == null) {
            throw new GraphQLException("No existe el usuario " + usuarioId + ".");
        }
        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw new GraphQLException("El usuario " + usuario.getNickname() + " esta inactivo.");
        }
        if (esSuperusuario(usuario)) {
            throw new GraphQLException("Un token de servicio no puede ser de un superusuario (ADMIN): "
                    + "usá un usuario propio del bot con los roles que necesita.");
        }

        String token = TokenServicioFormato.generar();
        TokenServicio t = new TokenServicio();
        t.setUsuario(usuario);
        t.setNombre(nombreLimpio);
        t.setPrefijo(TokenServicioFormato.prefijoVisible(token));
        t.setHash(TokenServicioFormato.hash(token));
        t.setCreadoPor(creador);
        t.setCreadoEn(LocalDateTime.now());
        t.setExpiraEn(LocalDateTime.now().plusDays(dias));
        t = repository.save(t);

        usuario.setPassword(TokenServicioFormato.claveAleatoria());
        usuarioRepository.save(usuario);

        log.info("Token de servicio {} ({}) emitido para el usuario {} por {}, vence {}",
                t.getId(), t.getPrefijo(), usuario.getNickname(), creador.getNickname(), t.getExpiraEn());
        return new TokenServicioCreado(t, token);
    }

    /** Idempotente: revocar uno ya revocado devuelve true y no cambia la fecha. */
    @Transactional
    public Boolean revocar(Long id) {
        seg.requireSuperusuario();
        Usuario quien = seg.requireUsuarioDeSesion();
        TokenServicio t = id == null ? null : repository.findById(id).orElse(null);
        if (t == null) {
            throw new GraphQLException("No existe el token de servicio " + id + ".");
        }
        if (t.getRevocadoEn() == null) {
            t.setRevocadoEn(LocalDateTime.now());
            t.setRevocadoPor(quien);
            repository.save(t);
            log.info("Token de servicio {} ({}) revocado por {}", t.getId(), t.getPrefijo(), quien.getNickname());
        }
        return true;
    }

    public List<TokenServicio> listar(Long usuarioId) {
        seg.requireSuperusuario();
        return usuarioId == null
                ? repository.findAllByOrderByIdDesc()
                : repository.findByUsuarioIdOrderByIdDesc(usuarioId);
    }

    private boolean esSuperusuario(Usuario u) {
        String nick = u.getNickname() == null ? "" : u.getNickname().trim();
        return ADMIN.equalsIgnoreCase(nick) || usuarioService.tieneRol(u.getId(), ADMIN);
    }
}
