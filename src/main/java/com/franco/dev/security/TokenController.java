package com.franco.dev.security;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.configuracion.InicioSesionRepository;
import com.franco.dev.security.jwt.JwtGenerator;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/login")
@CrossOrigin
public class TokenController {

    private final UsuarioService service;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private JwtValidator jwtValidator;

    @Autowired
    private InicioSesionRepository inicioSesionRepository;

    private JwtGenerator jwtGenerator;

    public TokenController(UsuarioService service, JwtGenerator jwtGenerator) {
        this.service = service;
        this.jwtGenerator = jwtGenerator;
    }

    @PostMapping(path = "/new")
    @ResponseBody
    public ResponseEntity<LoginResponse> newUser(@RequestBody final Usuario usuario){
        if(!service.existsByNickname(usuario.getNickname())){
            if(!service.existsByEmail(usuario.getEmail())){
                if(usuario.getNickname()!=null && usuario.getPassword()!=null) {
                    Usuario newUser = service.save(usuario);
                    JwtUser jwtUser = new JwtUser();
                    jwtUser.setId(newUser.getId());
                    jwtUser.setNickname(newUser.getNickname().toUpperCase());
                    jwtUser.setPassword(usuario.getPassword().toUpperCase());
                    return generate(jwtUser);
                } else {
                    throw new GraphQLException("Datos incompletos");
                }
            } else {
                throw new GraphQLException("Ya existe un usuario con utilizando este email.");
            }
        } else {
            throw new GraphQLException("Nombre de usuario ya existe, intente con otro");
        }
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<LoginResponse> generate(@RequestBody final JwtUser jwtUser) {
        Usuario usuario = service.findByNickname(jwtUser.getNickname()).orElse(null);
        if(usuario!=null){
            Boolean matches = jwtUser.getPassword().toUpperCase().equals(usuario.getPassword().toUpperCase());
            if(matches){
                if(Boolean.FALSE.equals(usuario.getActivo())){
                    throw new GraphQLException("Usuario inactivo, contacte al administrador");
                }
                jwtUser.setRoles(service.getRoles(usuario.getId()));
            } else {
                throw new GraphQLException("Ups!! Contraseña inválida.");
            }
        } else {
            throw new GraphQLException("Ups!! El usuario no existe");
        }
        LoginResponse response = new LoginResponse(
                usuario.getId(),
                jwtGenerator.generate(jwtUser),
                sanitizeSucursal(sucursalService.sucursalActual()));
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/biometric")
    @ResponseBody
    public ResponseEntity<LoginResponse> biometricLogin(@RequestBody final BiometricLoginRequest request) {
        if (request == null || isBlank(request.getBiometricToken())
                || isBlank(request.getIdDispositivo())) {
            throw new GraphQLException("Datos biométricos incompletos.");
        }

        Long biometricOwnerId = inicioSesionRepository
                .findFirstByIdDispositivoAndUsuarioIsNotNullOrderByIdAsc(request.getIdDispositivo())
                .map(inicioSesion -> inicioSesion.getUsuario() != null ? inicioSesion.getUsuario().getId() : null)
                .orElse(null);
        if (biometricOwnerId == null) {
            throw new GraphQLException("No se encontró usuario biométrico para este dispositivo.");
        }

        JwtUser tokenPayload = jwtValidator.validate(request.getBiometricToken());
        if (tokenPayload == null || isBlank(tokenPayload.getNickname())) {
            throw new GraphQLException("Token biométrico inválido.");
        }

        Usuario usuarioToken = service.findByNickname(tokenPayload.getNickname()).orElse(null);
        if (usuarioToken == null || !usuarioToken.getId().equals(biometricOwnerId)) {
            throw new GraphQLException("El token biométrico no corresponde al usuario.");
        }
        if (Boolean.FALSE.equals(usuarioToken.getActivo())) {
            throw new GraphQLException("Usuario inactivo, contacte al administrador");
        }

        boolean existeSesionDelOwnerEnDispositivo = inicioSesionRepository
                .existsByUsuarioIdAndIdDispositivo(biometricOwnerId, request.getIdDispositivo());
        if (!existeSesionDelOwnerEnDispositivo) {
            throw new GraphQLException("No existe historial de sesión del usuario biométrico en el dispositivo.");
        }

        JwtUser jwtUser = new JwtUser();
        jwtUser.setId(usuarioToken.getId());
        jwtUser.setNickname(usuarioToken.getNickname().toUpperCase());
        jwtUser.setPassword(usuarioToken.getPassword().toUpperCase());
        jwtUser.setRoles(service.getRoles(usuarioToken.getId()));

        LoginResponse response = new LoginResponse(
                usuarioToken.getId(),
                jwtGenerator.generate(jwtUser),
                sanitizeSucursal(sucursalService.sucursalActual()));
        return ResponseEntity.ok(response);
    }

    @GetMapping(path = "/biometric-owner/{idDispositivo}")
    @ResponseBody
    public ResponseEntity<Long> biometricOwner(@PathVariable String idDispositivo) {
        if (isBlank(idDispositivo)) {
            throw new GraphQLException("Dispositivo inválido.");
        }
        Long biometricOwnerId = inicioSesionRepository
                .findFirstByIdDispositivoAndUsuarioIsNotNullOrderByIdAsc(idDispositivo)
                .map(inicioSesion -> inicioSesion.getUsuario() != null ? inicioSesion.getUsuario().getId() : null)
                .orElse(null);
        return ResponseEntity.ok(biometricOwnerId);
    }

    /**
     * Sin este handler el GraphQLException lanzado desde este @RestController termina
     * en el error page por defecto: HTTP 500 y, con server.error.include-message=never
     * (default en Boot 2.7), sin el texto del motivo. El desktop mostraba entonces un
     * error generico de servidor en vez de "Usuario inactivo, contacte al administrador".
     * Se limita a este controller a proposito: no se agrega un @ControllerAdvice global.
     */
    @ExceptionHandler(GraphQLException.class)
    public ResponseEntity<Map<String, String>> handleLoginError(GraphQLException ex) {
        String mensaje = ex.getMessage() != null ? ex.getMessage() : "No se pudo iniciar sesión.";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Collections.singletonMap("message", mensaje));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Sucursal sanitizeSucursal(Sucursal sucursal) {
        if (sucursal == null) {
            return null;
        }
        Sucursal safeSucursal = new Sucursal();
        safeSucursal.setId(sucursal.getId());
        safeSucursal.setNombre(sucursal.getNombre());
        safeSucursal.setLocalizacion(sucursal.getLocalizacion());
        safeSucursal.setDeposito(sucursal.getDeposito());
        safeSucursal.setIsConfigured(sucursal.getIsConfigured());
        safeSucursal.setDepositoPredeterminado(sucursal.getDepositoPredeterminado());
        safeSucursal.setDireccion(sucursal.getDireccion());
        safeSucursal.setNroDelivery(sucursal.getNroDelivery());
        safeSucursal.setCodigoEstablecimientoFactura(sucursal.getCodigoEstablecimientoFactura());
        safeSucursal.setIp(sucursal.getIp());
        safeSucursal.setPuerto(sucursal.getPuerto());
        safeSucursal.setActivo(sucursal.getActivo());
        safeSucursal.setCreadoEn(sucursal.getCreadoEn());
        return safeSucursal;
    }
}