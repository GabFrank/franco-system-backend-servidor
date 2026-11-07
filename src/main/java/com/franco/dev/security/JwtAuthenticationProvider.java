package com.franco.dev.security;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.security.model.JwtAuthenticationToken;
import com.franco.dev.security.model.JwtUserDetails;
import com.franco.dev.service.personas.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {

    @Autowired
    private JwtValidator validator;

    @Autowired
    private TokenServicioValidator tokenServicioValidator;

    @Autowired
    private final UsuarioService usuarioService;

    public JwtAuthenticationProvider(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails,
            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken) throws AuthenticationException {

    }

    @Override
    protected UserDetails retrieveUser(String username,
            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken) throws AuthenticationException {

        JwtAuthenticationToken jwtAuthenticationToken = (JwtAuthenticationToken) usernamePasswordAuthenticationToken;
        String token = jwtAuthenticationToken.getToken();

        // Token de servicio de un bot (#579). Un JWT empieza con "eyJ", asi que nunca entra aca, y un
        // "frcsvc_" invalido nunca cae al validador de JWT. El token en claro no se guarda en el
        // principal, para que no termine en un log o un volcado del SecurityContext.
        if (TokenServicioFormato.esTokenDeServicio(token)) {
            TokenServicioValidator.Valido servicio = tokenServicioValidator.validar(token)
                    .orElseThrow(() -> new BadCredentialsException("Token de servicio invalido"));
            return new JwtUserDetails(servicio.getNickname(), null, null, new ArrayList<>());
        }

        JwtUser jwtUser = validator.validate(token);

        // AuthenticationException: el filtro responde 401. El username llega como NONE_PROVIDED, no se compara.
        if (jwtUser == null) {
            throw new BadCredentialsException("Token de sesion invalido");
        }
        List<GrantedAuthority> grantedAuthorities = AuthorityUtils
                .commaSeparatedStringToAuthorityList(String.valueOf(jwtUser.getRoles()));
        return new JwtUserDetails(jwtUser.getNickname(), jwtUser.getPassword(),
                token,
                grantedAuthorities);
    }

    @Override
    public boolean supports(Class<?> aClass) {
        return (JwtAuthenticationToken.class.isAssignableFrom(aClass));
    }
}
