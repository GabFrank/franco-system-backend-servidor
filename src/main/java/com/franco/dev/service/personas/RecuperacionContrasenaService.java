package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.RecuperacionContrasena;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.enums.EstadoRecuperacionContrasena;
import com.franco.dev.domain.personas.enums.TipoRecuperacionContrasena;
import com.franco.dev.repository.personas.RecuperacionContrasenaRepository;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * "¿Olvido su contrasena?" del login. Corre solo en el central: es el que tiene el correo
 * configurado, y la contrasena nueva baja a las filiales por la replicacion de personas.usuario.
 *
 * <p><b>Paso 1, identidad.</b> Usuario + cedula + fecha de nacimiento, con un captcha
 * ({@link CaptchaService}) y un bloqueo por intentos fallidos (por usuario y por IP). Cedula y
 * fecha no son secretos --cualquier compañero puede saberlas--: son un filtro, no la prueba.
 *
 * <p><b>Paso 2, la prueba.</b>
 * <ul>
 *   <li>Con correo ({@code usuario.email} o, si esta vacio, {@code persona.email}): un codigo de 6
 *       digitos al correo. La prueba es tener acceso a ese correo.</li>
 *   <li>Sin correo (o sin fecha de nacimiento cargada): no hay autoservicio. Se registra una
 *       solicitud que aprueba RRHH, comparando el telefono del WhatsApp que recibe con el
 *       registrado. Al crearla se le muestra al usuario un codigo secreto que no viaja por
 *       WhatsApp: aunque alguien engañe a RRHH, sin ese codigo no puede usar la aprobacion.</li>
 * </ul>
 *
 * <p>Los codigos se guardan con BCrypt y admiten {@link #MAX_INTENTOS_CODIGO} intentos. Los
 * mensajes de error no dicen si el usuario existe.
 */
@Service
public class RecuperacionContrasenaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacionContrasenaService.class);

    static final int MINUTOS_CODIGO_CORREO = 15;
    static final int HORAS_APROBACION_RRHH = 72;
    static final int HORAS_USO_APROBADA = 24;
    static final int MAX_INTENTOS_CODIGO = 5;
    static final int MINUTOS_BLOQUEO = 30;
    static final int MAX_FALLOS_POR_USUARIO = 5;
    static final int MAX_FALLOS_POR_IP = 20;
    static final int MAX_CORREOS_POR_HORA = 3;
    static final int MAX_SOLICITUDES_RRHH_POR_DIA = 3;
    static final int MINUTOS_VERIFICACION_SIN_CORREO = 20;
    static final int LARGO_MINIMO_CONTRASENA = 6;

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$");
    private static final String ALFABETO_SECRETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String MENSAJE_NO_COINCIDE =
            "Los datos ingresados no coinciden con un usuario activo. Verifique e intente nuevamente.";
    private static final String MENSAJE_BLOQUEADO =
            "Demasiados intentos fallidos. Por seguridad, espere " + MINUTOS_BLOQUEO + " minutos e intente nuevamente.";

    /**
     * La identidad, de una: el documento se compara solo por digitos (hay cedulas cargadas con
     * puntos).
     *
     * <p>La fecha de nacimiento se guardo como medianoche local en una columna timestamptz, a veces
     * con UTC-3 y a veces con UTC-4 (03:00Z o 04:00Z). Con la zona {@code America/Asuncion} las
     * fechas viejas de invierno se corren al dia anterior, porque el desktop las guardo con el
     * UTC-3 de hoy; restando 3 horas fijas caen todas en su dia (verificado en bodega el 2026-10-16).
     * {@code 1970-01-01 00:00Z} es un relleno, no una fecha: cuenta como sin fecha.
     */
    private static final String SQL_IDENTIDAD =
            "SELECT u.id, u.email AS usuario_email, p.email AS persona_email, "
            + "  (p.nacimiento IS NOT NULL AND p.nacimiento <> 'epoch'::timestamptz) AS tiene_nacimiento, "
            + "  ((p.nacimiento AT TIME ZONE 'UTC') - interval '3 hours')::date = ? AS nacimiento_ok "
            + "FROM personas.usuario u JOIN personas.persona p ON p.id = u.persona_id "
            + "WHERE upper(u.nickname) = upper(?) AND u.activo IS NOT FALSE "
            + "  AND regexp_replace(coalesce(p.documento, ''), '\\D', '', 'g') = ?";

    private static final String SQL_FALLOS_USUARIO =
            "SELECT count(*) FROM personas.recuperacion_contrasena_intento "
            + "WHERE nickname = ? AND NOT exito AND creado_en > now() - make_interval(mins => ?)";

    private static final String SQL_FALLOS_IP =
            "SELECT count(*) FROM personas.recuperacion_contrasena_intento "
            + "WHERE ip = ? AND NOT exito AND creado_en > now() - make_interval(mins => ?)";

    private static final String SQL_REGISTRAR_INTENTO =
            "INSERT INTO personas.recuperacion_contrasena_intento (nickname, ip, exito) VALUES (?, ?, ?)";

    private final RecuperacionContrasenaRepository repository;
    private final UsuarioService usuarioService;
    private final CaptchaService captchaService;
    private final RrhhSecurityService seguridad;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    /** Identidades ya verificadas sin correo, a la espera del formulario de la solicitud a RRHH. */
    private final Map<String, VerificacionSinCorreo> verificacionesSinCorreo = new ConcurrentHashMap<>();

    @Value("${spring.mail.host:}")
    private String mailHost;
    @Value("${spring.mail.username:}")
    private String mailUsername;
    @Value("${factura.correo.remitente:}")
    private String remitente;

    public RecuperacionContrasenaService(RecuperacionContrasenaRepository repository,
                                         UsuarioService usuarioService,
                                         CaptchaService captchaService,
                                         RrhhSecurityService seguridad,
                                         JdbcTemplate jdbc,
                                         ObjectProvider<JavaMailSender> mailSender) {
        this.repository = repository;
        this.usuarioService = usuarioService;
        this.captchaService = captchaService;
        this.seguridad = seguridad;
        this.jdbc = jdbc;
        this.mailSender = mailSender;
    }

    /** Error previsto, con un mensaje listo para mostrarle al usuario. */
    public static class RecuperacionException extends RuntimeException {
        public RecuperacionException(String mensaje) {
            super(mensaje);
        }
    }

    // ------------------------------------------------------------------ resultados publicos

    public static class Verificacion {
        /** CORREO o SIN_CORREO. */
        public String tipo;
        public Long solicitudId;
        public String emailEnmascarado;
        public Integer minutosVigencia;
        /** Solo SIN_CORREO: liga el formulario de la solicitud a esta verificacion. */
        public String verificacionToken;
    }

    public static class SolicitudRrhhCreada {
        public Long solicitudId;
        /** Se muestra una sola vez. No se guarda en claro ni viaja en el mensaje de WhatsApp. */
        public String codigoSecreto;
        public String mensajeWhatsapp;
        public Integer horasParaAprobar;
    }

    public static class EstadoPublico {
        public Long solicitudId;
        public String tipo;
        public String estado;
        public String motivoRechazo;
        public Boolean puedeRestablecer;
        public String mensaje;
    }

    private static class VerificacionSinCorreo {
        final Long usuarioId;
        final Instant vence;

        VerificacionSinCorreo(Long usuarioId, Instant vence) {
            this.usuarioId = usuarioId;
            this.vence = vence;
        }
    }

    private static class Identidad {
        Long usuarioId;
        String email;
        boolean tieneNacimiento;
        boolean nacimientoOk;
    }

    // ------------------------------------------------------------------ paso 1: identidad

    @Transactional(noRollbackFor = RecuperacionException.class)
    public Verificacion verificarIdentidad(String nickname, String documento, LocalDate nacimiento,
                                           String captchaId, String captchaRespuesta, String ip) {
        if (!captchaService.validar(captchaId, captchaRespuesta)) {
            throw new RecuperacionException("El código de la imagen no es correcto. Escriba el nuevo código.");
        }
        String nick = nickname == null ? "" : nickname.trim().toUpperCase();
        String doc = documento == null ? "" : documento.replaceAll("\\D", "");
        if (nick.isEmpty() || doc.isEmpty() || nacimiento == null) {
            throw new RecuperacionException("Complete usuario, cédula y fecha de nacimiento.");
        }
        if (bloqueado(nick, ip)) {
            throw new RecuperacionException(MENSAJE_BLOQUEADO);
        }

        List<Identidad> encontradas = jdbc.query(SQL_IDENTIDAD, (rs, i) -> {
            Identidad id = new Identidad();
            id.usuarioId = rs.getLong("id");
            id.email = primerCorreoValido(rs.getString("usuario_email"), rs.getString("persona_email"));
            id.tieneNacimiento = rs.getBoolean("tiene_nacimiento");
            id.nacimientoOk = rs.getBoolean("nacimiento_ok");
            return id;
        }, java.sql.Date.valueOf(nacimiento), nick, doc);

        Identidad identidad = encontradas.isEmpty() ? null : encontradas.get(0);
        // Sin fecha de nacimiento cargada no se la puede comparar: esa persona va siempre por RRHH,
        // nunca por correo, asi que la fecha que escribio no le abre nada.
        boolean coincide = identidad != null && (identidad.nacimientoOk || !identidad.tieneNacimiento);
        jdbc.update(SQL_REGISTRAR_INTENTO, nick, ip, coincide);
        if (!coincide) {
            throw new RecuperacionException(MENSAJE_NO_COINCIDE);
        }

        if (identidad.tieneNacimiento && identidad.email != null) {
            return enviarCodigoPorCorreo(identidad, ip);
        }
        limpiarVerificacionesVencidas();
        String token = UUID.randomUUID().toString();
        verificacionesSinCorreo.put(token, new VerificacionSinCorreo(identidad.usuarioId,
                Instant.now().plusSeconds(MINUTOS_VERIFICACION_SIN_CORREO * 60L)));
        Verificacion v = new Verificacion();
        v.tipo = "SIN_CORREO";
        v.verificacionToken = token;
        v.minutosVigencia = MINUTOS_VERIFICACION_SIN_CORREO;
        return v;
    }

    private boolean bloqueado(String nick, String ip) {
        Long porUsuario = jdbc.queryForObject(SQL_FALLOS_USUARIO, Long.class, nick, MINUTOS_BLOQUEO);
        if (porUsuario != null && porUsuario >= MAX_FALLOS_POR_USUARIO) return true;
        if (ip == null) return false;
        Long porIp = jdbc.queryForObject(SQL_FALLOS_IP, Long.class, ip, MINUTOS_BLOQUEO);
        return porIp != null && porIp >= MAX_FALLOS_POR_IP;
    }

    private Verificacion enviarCodigoPorCorreo(Identidad identidad, String ip) {
        LocalDateTime ahora = LocalDateTime.now();
        long enviadas = repository.countByUsuarioIdAndTipoAndCreadoEnAfter(identidad.usuarioId,
                TipoRecuperacionContrasena.CORREO, ahora.minusHours(1));
        if (enviadas >= MAX_CORREOS_POR_HORA) {
            throw new RecuperacionException("Ya se enviaron " + MAX_CORREOS_POR_HORA + " códigos en la última hora. "
                    + "Revise su correo (también la carpeta de spam) o intente más tarde.");
        }
        Usuario usuario = usuarioService.findById(identidad.usuarioId)
                .orElseThrow(() -> new RecuperacionException(MENSAJE_NO_COINCIDE));
        String codigo = String.format("%06d", random.nextInt(1_000_000));

        // Primero el correo y despues la fila: si el envio falla no queda una solicitud huerfana
        // ni se anulan las que estaban abiertas.
        enviarCorreo(identidad.email, "Código para restablecer su contraseña",
                "<p>Hola <b>" + escapar(usuario.getNickname()) + "</b>,</p>"
                + "<p>Recibimos un pedido para restablecer la contraseña de su usuario en FRC Sistemas.</p>"
                + "<p style=\"font-size:28px;letter-spacing:6px;font-weight:bold\">" + codigo + "</p>"
                + "<p>El código vence en " + MINUTOS_CODIGO_CORREO + " minutos y sirve una sola vez.</p>"
                + "<p>Si usted no lo pidió, ignore este correo: su contraseña no cambia. "
                + "Nadie de la empresa le va a pedir este código.</p>");

        RecuperacionContrasena r = new RecuperacionContrasena();
        r.setUsuario(usuario);
        r.setTipo(TipoRecuperacionContrasena.CORREO);
        r.setEstado(EstadoRecuperacionContrasena.PENDIENTE);
        r.setEmail(identidad.email);
        r.setCodigoHash(bcrypt.encode(codigo));
        r.setIntentos(0);
        r.setIp(ip);
        r.setCreadoEn(ahora);
        r.setVenceEn(ahora.plusMinutes(MINUTOS_CODIGO_CORREO));
        r = repository.save(r);
        repository.anularAbiertas(usuario.getId(), r.getId());

        Verificacion v = new Verificacion();
        v.tipo = "CORREO";
        v.solicitudId = r.getId();
        v.emailEnmascarado = enmascarar(identidad.email);
        v.minutosVigencia = MINUTOS_CODIGO_CORREO;
        return v;
    }

    // ------------------------------------------------------------------ sin correo: solicitud a RRHH

    @Transactional(noRollbackFor = RecuperacionException.class)
    public SolicitudRrhhCreada crearSolicitudRrhh(String verificacionToken, String telefono, String email,
                                                  String observacion, String ip) {
        VerificacionSinCorreo v = verificacionToken == null ? null : verificacionesSinCorreo.remove(verificacionToken);
        if (v == null || Instant.now().isAfter(v.vence)) {
            throw new RecuperacionException("La verificación venció. Vuelva a ingresar sus datos.");
        }
        String tel = telefono == null ? "" : telefono.replaceAll("[^0-9+]", "");
        if (tel.replace("+", "").length() < 6 || tel.length() > 20) {
            throw new RecuperacionException("Ingrese un número de teléfono válido.");
        }
        String correo = email == null || email.trim().isEmpty() ? null : email.trim();
        if (correo != null && !EMAIL.matcher(correo).matches()) {
            throw new RecuperacionException("El correo ingresado no es válido.");
        }
        String obs = observacion == null || observacion.trim().isEmpty() ? null : observacion.trim();
        if (obs != null && obs.length() > 500) {
            throw new RecuperacionException("La observación no puede superar los 500 caracteres.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        long hoy = repository.countByUsuarioIdAndTipoAndCreadoEnAfter(v.usuarioId,
                TipoRecuperacionContrasena.RRHH, ahora.minusDays(1));
        if (hoy >= MAX_SOLICITUDES_RRHH_POR_DIA) {
            throw new RecuperacionException("Ya registró " + MAX_SOLICITUDES_RRHH_POR_DIA
                    + " solicitudes en las últimas 24 horas. Comuníquese con RRHH.");
        }
        Usuario usuario = usuarioService.findById(v.usuarioId)
                .orElseThrow(() -> new RecuperacionException(MENSAJE_NO_COINCIDE));

        String secreto = codigoSecreto();
        RecuperacionContrasena r = new RecuperacionContrasena();
        r.setUsuario(usuario);
        r.setTipo(TipoRecuperacionContrasena.RRHH);
        r.setEstado(EstadoRecuperacionContrasena.PENDIENTE);
        r.setEmail(correo);
        r.setTelefono(tel);
        r.setObservacion(obs);
        r.setCodigoHash(bcrypt.encode(normalizarSecreto(secreto)));
        r.setIntentos(0);
        r.setIp(ip);
        r.setCreadoEn(ahora);
        r.setVenceEn(ahora.plusHours(HORAS_APROBACION_RRHH));
        r = repository.save(r);
        repository.anularAbiertas(usuario.getId(), r.getId());

        String nombre = usuario.getPersona() != null ? usuario.getPersona().getNombre() : null;
        String documento = usuario.getPersona() != null ? usuario.getPersona().getDocumento() : null;
        SolicitudRrhhCreada creada = new SolicitudRrhhCreada();
        creada.solicitudId = r.getId();
        creada.codigoSecreto = secreto;
        creada.horasParaAprobar = HORAS_APROBACION_RRHH;
        creada.mensajeWhatsapp = "*Solicitud de restablecimiento de contraseña N° " + r.getId() + "*\n"
                + "Usuario: " + usuario.getNickname() + "\n"
                + (nombre != null ? "Nombre: " + nombre + "\n" : "")
                + (documento != null ? "Cédula: " + documento + "\n" : "")
                + "Teléfono: " + tel + "\n"
                + "Fecha: " + ahora.format(FECHA_HORA) + "\n\n"
                + "Solicito la aprobación desde FRC Sistemas (RRHH > Recuperación de contraseñas).";
        return creada;
    }

    // ------------------------------------------------------------------ paso 2: el codigo

    /**
     * Comprueba el codigo (el del correo o el secreto de la solicitud a RRHH) y dice en que esta la
     * solicitud. Cada codigo equivocado cuenta: al {@link #MAX_INTENTOS_CODIGO} la solicitud se anula.
     */
    @Transactional(noRollbackFor = RecuperacionException.class)
    public EstadoPublico consultar(Long solicitudId, String codigo, String documento) {
        RecuperacionContrasena r = comprobarCodigo(solicitudId, codigo, documento);
        return estadoPublico(r);
    }

    @Transactional(noRollbackFor = RecuperacionException.class)
    public EstadoPublico restablecer(Long solicitudId, String codigo, String documento, String nuevaContrasena) {
        RecuperacionContrasena r = comprobarCodigo(solicitudId, codigo, documento);
        if (!puedeRestablecer(r)) {
            throw new RecuperacionException(estadoPublico(r).mensaje);
        }
        Usuario usuario = r.getUsuario();
        validarContrasena(usuario, nuevaContrasena);

        usuario.setPassword(nuevaContrasena);
        // El correo de la solicitud queda como correo del usuario si no tenia uno: en CORREO acaba
        // de probar que es suyo; en RRHH lo aprobo RRHH.
        boolean sinCorreo = usuario.getEmail() == null || usuario.getEmail().trim().isEmpty();
        if (sinCorreo && r.getEmail() != null && !usuarioService.existsByEmail(r.getEmail())) {
            usuario.setEmail(r.getEmail());
        }
        usuarioService.save(usuario);

        r.setEstado(EstadoRecuperacionContrasena.COMPLETADA);
        r.setCompletadoEn(LocalDateTime.now());
        repository.save(r);
        repository.anularAbiertas(usuario.getId(), r.getId());
        log.info("Recuperacion de contrasena: usuario id={} restablecio su contrasena (solicitud {} por {})",
                usuario.getId(), r.getId(), r.getTipo());

        avisarCambio(usuario, r);
        EstadoPublico e = estadoPublico(r);
        e.mensaje = "Su contraseña fue restablecida. Ya puede ingresar con la nueva contraseña.";
        return e;
    }

    private RecuperacionContrasena comprobarCodigo(Long solicitudId, String codigo, String documento) {
        String noValida = "El código no es válido o la solicitud ya no está vigente.";
        if (solicitudId == null || codigo == null || codigo.trim().isEmpty()) {
            throw new RecuperacionException(noValida);
        }
        RecuperacionContrasena r = repository.findById(solicitudId)
                .orElseThrow(() -> new RecuperacionException(noValida));
        if (r.getEstado() == EstadoRecuperacionContrasena.COMPLETADA
                || r.getEstado() == EstadoRecuperacionContrasena.ANULADA) {
            throw new RecuperacionException(noValida);
        }
        if (r.getIntentos() != null && r.getIntentos() >= MAX_INTENTOS_CODIGO) {
            throw new RecuperacionException(noValida);
        }
        if (r.getTipo() == TipoRecuperacionContrasena.RRHH) {
            String doc = documento == null ? "" : documento.replaceAll("\\D", "");
            String registrado = r.getUsuario().getPersona() != null && r.getUsuario().getPersona().getDocumento() != null
                    ? r.getUsuario().getPersona().getDocumento().replaceAll("\\D", "") : null;
            if (doc.isEmpty() || !doc.equals(registrado)) {
                contarFallo(r);
                throw new RecuperacionException("Los datos no coinciden con la solicitud." + restantes(r));
            }
        }
        String ingresado = r.getTipo() == TipoRecuperacionContrasena.RRHH
                ? normalizarSecreto(codigo) : codigo.replaceAll("\\D", "");
        if (!bcrypt.matches(ingresado, r.getCodigoHash())) {
            contarFallo(r);
            throw new RecuperacionException("El código no es correcto." + restantes(r));
        }
        return r;
    }

    private void contarFallo(RecuperacionContrasena r) {
        r.setIntentos((r.getIntentos() == null ? 0 : r.getIntentos()) + 1);
        if (r.getIntentos() >= MAX_INTENTOS_CODIGO) {
            r.setEstado(EstadoRecuperacionContrasena.ANULADA);
        }
        repository.save(r);
    }

    private static String restantes(RecuperacionContrasena r) {
        int quedan = MAX_INTENTOS_CODIGO - r.getIntentos();
        return quedan <= 0
                ? " Se superó la cantidad de intentos: la solicitud quedó anulada. Inicie una nueva."
                : " Le quedan " + quedan + (quedan == 1 ? " intento." : " intentos.");
    }

    private static boolean puedeRestablecer(RecuperacionContrasena r) {
        if (Boolean.TRUE.equals(r.getVencida())) return false;
        return (r.getTipo() == TipoRecuperacionContrasena.CORREO && r.getEstado() == EstadoRecuperacionContrasena.PENDIENTE)
                || (r.getTipo() == TipoRecuperacionContrasena.RRHH && r.getEstado() == EstadoRecuperacionContrasena.APROBADA);
    }

    private static EstadoPublico estadoPublico(RecuperacionContrasena r) {
        EstadoPublico e = new EstadoPublico();
        e.solicitudId = r.getId();
        e.tipo = r.getTipo().name();
        e.estado = Boolean.TRUE.equals(r.getVencida()) ? "VENCIDA" : r.getEstado().name();
        e.motivoRechazo = r.getMotivoRechazo();
        e.puedeRestablecer = puedeRestablecer(r);
        switch (e.estado) {
            case "VENCIDA":
                e.mensaje = r.getTipo() == TipoRecuperacionContrasena.CORREO
                        ? "El código venció. Pida uno nuevo."
                        : (r.getEstado() == EstadoRecuperacionContrasena.PENDIENTE
                            ? "La solicitud venció sin ser aprobada. Inicie una nueva."
                            : "Pasó el plazo para usar la aprobación. Inicie una nueva solicitud.");
                break;
            case "PENDIENTE":
                e.mensaje = r.getTipo() == TipoRecuperacionContrasena.CORREO
                        ? "Código correcto. Elija su nueva contraseña."
                        : "Su solicitud todavía no fue aprobada por RRHH.";
                break;
            case "APROBADA":
                e.mensaje = "RRHH aprobó su solicitud. Elija su nueva contraseña.";
                break;
            case "RECHAZADA":
                e.mensaje = "RRHH rechazó la solicitud"
                        + (r.getMotivoRechazo() != null ? ": " + r.getMotivoRechazo() : ".");
                break;
            default:
                e.mensaje = "La solicitud ya no está vigente.";
        }
        return e;
    }

    private static void validarContrasena(Usuario usuario, String nueva) {
        if (nueva == null || nueva.trim().length() < LARGO_MINIMO_CONTRASENA) {
            throw new RecuperacionException("La contraseña debe tener al menos " + LARGO_MINIMO_CONTRASENA + " caracteres.");
        }
        if (nueva.length() > 50) {
            throw new RecuperacionException("La contraseña no puede superar los 50 caracteres.");
        }
        if (!nueva.equals(nueva.trim())) {
            throw new RecuperacionException("La contraseña no puede empezar ni terminar con espacios.");
        }
        if (nueva.equalsIgnoreCase(usuario.getNickname())) {
            throw new RecuperacionException("La contraseña no puede ser igual al nombre de usuario.");
        }
        String doc = usuario.getPersona() != null && usuario.getPersona().getDocumento() != null
                ? usuario.getPersona().getDocumento().replaceAll("\\D", "") : null;
        if (doc != null && !doc.isEmpty() && nueva.replaceAll("\\D", "").equals(doc) && nueva.matches("[0-9.\\- ]+")) {
            throw new RecuperacionException("La contraseña no puede ser su número de cédula.");
        }
        if (nueva.chars().distinct().count() == 1) {
            throw new RecuperacionException("La contraseña no puede ser un mismo carácter repetido.");
        }
    }

    // ------------------------------------------------------------------ RRHH

    public Page<RecuperacionContrasena> listarSolicitudesRrhh(String estado, int page, int size) {
        seguridad.requireAnyRole(RrhhSecurityService.APROBAR);
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        if (estado == null || estado.trim().isEmpty() || "TODAS".equalsIgnoreCase(estado)) {
            return repository.findByTipoOrderByIdDesc(TipoRecuperacionContrasena.RRHH, pr);
        }
        EstadoRecuperacionContrasena e;
        try {
            e = EstadoRecuperacionContrasena.valueOf(estado.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new GraphQLException("Estado desconocido: " + estado);
        }
        return repository.findByTipoAndEstadoInOrderByIdDesc(TipoRecuperacionContrasena.RRHH, Arrays.asList(e), pr);
    }

    public long contarPendientesRrhh() {
        if (!seguridad.hasAnyRole(RrhhSecurityService.APROBAR)) return 0;
        return repository.countByTipoAndEstadoAndVenceEnAfter(TipoRecuperacionContrasena.RRHH,
                EstadoRecuperacionContrasena.PENDIENTE, LocalDateTime.now());
    }

    @Transactional
    public RecuperacionContrasena aprobar(Long id) {
        seguridad.requireAnyRole(RrhhSecurityService.APROBAR);
        RecuperacionContrasena r = pendienteParaResolver(id);
        Usuario yo = seguridad.currentUsuario();
        r.setEstado(EstadoRecuperacionContrasena.APROBADA);
        r.setResueltoPor(yo);
        r.setResueltoEn(LocalDateTime.now());
        r.setVenceEn(LocalDateTime.now().plusHours(HORAS_USO_APROBADA));
        log.info("Recuperacion de contrasena: solicitud {} aprobada por usuario id={}", id, yo.getId());
        return repository.save(r);
    }

    @Transactional
    public RecuperacionContrasena rechazar(Long id, String motivo) {
        seguridad.requireAnyRole(RrhhSecurityService.APROBAR);
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new GraphQLException("Indique el motivo del rechazo.");
        }
        RecuperacionContrasena r = pendienteParaResolver(id);
        Usuario yo = seguridad.currentUsuario();
        r.setEstado(EstadoRecuperacionContrasena.RECHAZADA);
        r.setResueltoPor(yo);
        r.setResueltoEn(LocalDateTime.now());
        r.setMotivoRechazo(motivo.trim());
        log.info("Recuperacion de contrasena: solicitud {} rechazada por usuario id={}", id, yo.getId());
        return repository.save(r);
    }

    private RecuperacionContrasena pendienteParaResolver(Long id) {
        RecuperacionContrasena r = repository.findById(id)
                .orElseThrow(() -> new GraphQLException("No se encontró la solicitud."));
        if (r.getTipo() != TipoRecuperacionContrasena.RRHH) {
            throw new GraphQLException("Esta solicitud no se aprueba desde RRHH.");
        }
        if (r.getEstado() != EstadoRecuperacionContrasena.PENDIENTE) {
            throw new GraphQLException("La solicitud ya fue resuelta.");
        }
        if (Boolean.TRUE.equals(r.getVencida())) {
            throw new GraphQLException("La solicitud venció: el usuario tiene que iniciar una nueva.");
        }
        Usuario yo = seguridad.currentUsuario();
        if (yo == null) {
            throw new GraphQLException("No se pudo identificar al usuario que resuelve.");
        }
        if (yo.getId().equals(r.getUsuario().getId())) {
            throw new GraphQLException("No puede resolver su propia solicitud: tiene que hacerlo otra persona de RRHH.");
        }
        return r;
    }

    // ------------------------------------------------------------------ correo

    private void enviarCorreo(String destino, String asunto, String html) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || esVacio(mailHost) || esVacio(direccionRemitente())) {
            throw new RecuperacionException("El envío de correos no está configurado en el servidor. Comuníquese con RRHH.");
        }
        try {
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, "UTF-8");
            helper.setFrom(direccionRemitente(), "FRC Sistemas");
            helper.setTo(destino);
            helper.setSubject(asunto);
            helper.setText("<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#222\">" + html
                    + "<p style=\"color:#888;font-size:12px\">Este es un correo automático, no lo responda.</p></div>", true);
            sender.send(mensaje);
        } catch (Exception e) {
            log.warn("Recuperacion de contrasena: no se pudo enviar el correo a {}: {}", enmascarar(destino), e.getMessage());
            throw new RecuperacionException("No se pudo enviar el correo. Intente nuevamente en unos minutos.");
        }
    }

    /** Aviso de que la contrasena cambio. Si falla no se deshace nada: el cambio ya se hizo. */
    private void avisarCambio(Usuario usuario, RecuperacionContrasena r) {
        String destino = primerCorreoValido(usuario.getEmail(), r.getEmail(),
                usuario.getPersona() != null ? usuario.getPersona().getEmail() : null);
        if (destino == null) return;
        try {
            enviarCorreo(destino, "Su contraseña fue cambiada",
                    "<p>Hola <b>" + escapar(usuario.getNickname()) + "</b>,</p>"
                    + "<p>La contraseña de su usuario en FRC Sistemas se cambió el "
                    + LocalDateTime.now().format(FECHA_HORA) + ".</p>"
                    + "<p>Si no fue usted, comuníquese de inmediato con RRHH o con el administrador del sistema.</p>");
        } catch (RecuperacionException e) {
            log.warn("Recuperacion de contrasena: no se pudo avisar el cambio al usuario id={}", usuario.getId());
        }
    }

    private String direccionRemitente() {
        return esVacio(remitente) ? mailUsername : remitente.trim();
    }

    // ------------------------------------------------------------------ utilitarios

    private static String primerCorreoValido(String... correos) {
        for (String c : correos) {
            if (c != null && EMAIL.matcher(c.trim()).matches()) return c.trim();
        }
        return null;
    }

    /** fr•••@gmail.com: alcanza para que el dueño lo reconozca sin revelarlo a cualquiera. */
    static String enmascarar(String email) {
        if (email == null) return null;
        int arroba = email.indexOf('@');
        if (arroba <= 0) return "•••";
        String local = email.substring(0, arroba);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "•••" + email.substring(arroba);
    }

    /** 8 caracteres en dos grupos (ABCD-2345): se dicta y se copia sin confusiones. */
    private String codigoSecreto() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) sb.append('-');
            sb.append(ALFABETO_SECRETO.charAt(random.nextInt(ALFABETO_SECRETO.length())));
        }
        return sb.toString();
    }

    private static String normalizarSecreto(String secreto) {
        return secreto == null ? "" : secreto.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private void limpiarVerificacionesVencidas() {
        Instant ahora = Instant.now();
        Iterator<Map.Entry<String, VerificacionSinCorreo>> it = verificacionesSinCorreo.entrySet().iterator();
        while (it.hasNext()) {
            if (ahora.isAfter(it.next().getValue().vence)) it.remove();
        }
    }

    private static String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
