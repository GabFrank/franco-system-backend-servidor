package com.franco.dev.controller.personas;

import com.franco.dev.controller.personas.dto.ClienteSyncRequest;
import com.franco.dev.controller.personas.dto.PersonaSyncRequest;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.general.CiudadService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.utils.ImageService;
import graphql.GraphQLException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import com.franco.dev.utilitarios.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/personas")
@CrossOrigin
@RequiredArgsConstructor
public class PersonaSyncController {

    private final PersonaService personaService;
    private final UsuarioService usuarioService;
    private final CiudadService ciudadService;
    private final ClienteService clienteService;
    private final SucursalService sucursalService;
    private final FuncionarioService funcionarioService;
    private final TesoreriaSecurityService seg;
    private final ImageService imageService;

    @PostMapping
    public ResponseEntity<Persona> savePersona(@RequestBody PersonaSyncRequest request) {
        exigirImagenesValidas(request.getImagenes());
        boolean isCreate = request.getId() == null;
        String documento = isCreate ? normalizarDocumento(request.getDocumento()) : null;
        if (documento != null) {
            // Alta idempotente: el filial busca solo en su base y puede no tener la persona todavía.
            Persona existente = personaService.findByDocumento(documento);
            if (existente != null) return ResponseEntity.ok(respuesta(existente));
            request.setDocumento(documento);
        }
        Persona persona = request.getId() != null
                ? personaService.findById(request.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la persona " + request.getId()))
                : new Persona();

        if (request.getNombre() != null)
            persona.setNombre(request.getNombre());
        if (request.getApodo() != null)
            persona.setApodo(request.getApodo());
        if (request.getDocumento() != null)
            persona.setDocumento(request.getDocumento());
        if (request.getEmail() != null)
            persona.setEmail(request.getEmail());
        if (request.getDireccion() != null)
            persona.setDireccion(request.getDireccion());
        if (request.getTelefono() != null)
            persona.setTelefono(request.getTelefono());
        if (request.getSexo() != null)
            persona.setSexo(request.getSexo());
        if (request.getSocialMedia() != null)
            persona.setSocialMedia(request.getSocialMedia());
        if (request.getImagenes() != null)
            persona.setImagenes(request.getImagenes());

        if (request.getUsuarioId() != null) {
            Usuario usuario = usuarioService.findById(request.getUsuarioId()).orElse(null);
            persona.setUsuario(usuario);
        } else if (isCreate) {
            persona.setUsuario(null);
        }

        if (request.getCiudadId() != null) {
            Ciudad ciudad = ciudadService.findById(request.getCiudadId()).orElse(null);
            persona.setCiudad(ciudad);
        } else if (isCreate) {
            persona.setCiudad(null);
        }

        if (request.getNacimiento() != null || isCreate) {
            persona.setNacimiento(DateUtils.stringToDate(request.getNacimiento()));
        }

        Persona saved;
        try {
            saved = personaService.save(persona);
        } catch (DataIntegrityViolationException carrera) {
            // Otra alta del mismo documento ganó entre la búsqueda y el insert.
            Persona existente = documento != null ? personaService.findByDocumento(documento) : null;
            if (existente == null) throw carrera;
            return ResponseEntity.ok(respuesta(existente));
        }
        return ResponseEntity.status(isCreate ? HttpStatus.CREATED : HttpStatus.OK).body(respuesta(saved));
    }

    @PostMapping("/clientes")
    public ResponseEntity<Cliente> saveCliente(@RequestBody ClienteSyncRequest request) {
        boolean isCreate = request.getId() == null;
        if (isCreate && request.getPersonaId() != null) {
            // Alta idempotente: si la persona ya es cliente se devuelve ese, sin tocarlo.
            Cliente existente = clienteService.findByPersonaId(request.getPersonaId());
            if (existente != null) return ResponseEntity.ok(respuesta(existente));
        }
        Cliente cliente = request.getId() != null
                ? clienteService.findById(request.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el cliente " + request.getId()))
                : new Cliente();
        protegerCredito(request, isCreate ? null : cliente);

        if (request.getTipo() != null)
            cliente.setTipo(request.getTipo());
        if (request.getCredito() != null)
            cliente.setCredito(request.getCredito());
        if (request.getCodigo() != null)
            cliente.setCodigo(request.getCodigo());
        if (request.getTributa() != null)
            cliente.setTributa(request.getTributa());
        if (request.getVerificadoSet() != null)
            cliente.setVerificadoSet(request.getVerificadoSet());

        if (request.getPersonaId() != null) {
            Persona persona = personaService.findById(request.getPersonaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Persona no encontrada"));
            cliente.setPersona(persona);
        } else if (isCreate) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personaId es obligatorio para crear un cliente");
        }

        if (request.getSucursalId() != null) {
            Sucursal sucursal = sucursalService.findById(request.getSucursalId()).orElse(null);
            cliente.setSucursal(sucursal);
        } else if (isCreate) {
            cliente.setSucursal(null);
        }

        if (request.getUsuarioId() != null) {
            Usuario usuario = usuarioService.findById(request.getUsuarioId()).orElse(null);
            cliente.setUsuario(usuario);
        } else if (isCreate) {
            cliente.setUsuario(null);
        }

        Cliente saved;
        try {
            saved = clienteService.save(cliente);
        } catch (DataIntegrityViolationException carrera) {
            Cliente existente = isCreate ? clienteService.findByPersonaId(request.getPersonaId()) : null;
            if (existente == null) throw carrera;
            return ResponseEntity.ok(respuesta(existente));
        }
        return ResponseEntity.status(isCreate ? HttpStatus.CREATED : HttpStatus.OK).body(respuesta(saved));
    }

    /** Igual que {@code PersonaService.save}: sin espacios y cortado en el guion del DV. Null si no hay documento útil. */
    static String normalizarDocumento(String documento) {
        if (documento == null) return null;
        String d = documento.trim();
        int guion = d.indexOf('-');
        if (guion >= 0) d = d.substring(0, guion).trim();
        return d.isEmpty() || "X".equalsIgnoreCase(d) ? null : d;
    }

    /** Solo los campos que el filial copia de la respuesta: sin relaciones (nada lazy, ningún usuario). */
    static Persona respuesta(Persona p) {
        Persona r = new Persona();
        r.setId(p.getId());
        r.setNombre(p.getNombre());
        r.setApodo(p.getApodo());
        r.setDocumento(p.getDocumento());
        r.setEmail(p.getEmail());
        r.setDireccion(p.getDireccion());
        r.setTelefono(p.getTelefono());
        r.setSexo(p.getSexo());
        r.setSocialMedia(p.getSocialMedia());
        r.setImagenes(p.getImagenes());
        r.setNacimiento(p.getNacimiento());
        r.setCreadoEn(p.getCreadoEn());
        return r;
    }

    static Cliente respuesta(Cliente c) {
        Cliente r = new Cliente();
        r.setId(c.getId());
        r.setTipo(c.getTipo());
        r.setCredito(c.getCredito());
        r.setCodigo(c.getCodigo());
        r.setTributa(c.getTributa());
        r.setVerificadoSet(c.getVerificadoSet());
        r.setCreadoEn(c.getCreadoEn());
        return r;
    }

    /**
     * Mismo criterio que {@code ClienteGraphQL.saveCliente}, con la semántica de este endpoint: un crédito null
     * conserva el guardado. Cambiar el crédito exige ADMIN, o RRHH GESTIONAR / CONFIG si la persona es
     * funcionario; pasar un cliente existente a otra persona exige ADMIN. Va antes de tocar nada.
     */
    private void protegerCredito(ClienteSyncRequest request, Cliente previo) {
        if (previo != null) {
            Long personaGuardada = previo.getPersona() != null ? previo.getPersona().getId() : null;
            if (request.getPersonaId() != null && !request.getPersonaId().equals(personaGuardada)) seg.requireSuperusuario();
        }
        boolean cambia = previo != null
                ? request.getCredito() != null && !request.getCredito().equals(credito(previo.getCredito()))
                : credito(request.getCredito()) != 0f;
        if (!cambia) return;
        Long personaId = request.getPersonaId() != null ? request.getPersonaId()
                : previo != null && previo.getPersona() != null ? previo.getPersona().getId() : null;
        Funcionario funcionario = personaId != null ? funcionarioService.findByPersonaId(personaId) : null;
        if (funcionario != null) seg.requireAnyRole("RRHH GESTIONAR", "RRHH CONFIG");
        else seg.requireSuperusuario();
    }

    private static Float credito(Float c) { return c == null ? 0f : c; }

    private static void exigirImagenesValidas(String imagenes) {
        if (!PersonaService.imagenesValidas(imagenes))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen tiene que ser un nombre de archivo, no una ruta");
    }

    /** Los rechazos de rol: 403 con el motivo, en vez del 500 sin texto del error page por defecto. */
    @ExceptionHandler(GraphQLException.class)
    public ResponseEntity<Map<String, String>> handleRechazo(GraphQLException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Collections.singletonMap("message", ex.getMessage()));
    }

    /** Solo la foto de perfil, desde su carpeta: {@code imagenes} es un nombre de archivo, nunca una ruta. */
    @GetMapping("/{id}/imagen")
    public ResponseEntity<?> getPersonaImage(@PathVariable Long id) {
        Persona persona = personaService.findById(id).orElse(null);
        if (persona == null || persona.getImagenes() == null) {
            return ResponseEntity.notFound().build();
        }
        String primera = java.util.Arrays.stream(persona.getImagenes().split(","))
                .map(String::trim).filter(n -> !n.isEmpty()).findFirst().orElse("");
        Path file = resolverFotoPerfil(primera);
        if (file == null || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        String nombre = file.getFileName().toString().toLowerCase();
        MediaType tipo = nombre.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(tipo)
                .body(new FileSystemResource(file.toFile()));
    }

    /** null si el nombre no es un archivo de la carpeta de fotos de perfil (con symlinks resueltos). */
    Path resolverFotoPerfil(String nombre) {
        if (nombre.isEmpty() || !PersonaService.imagenesValidas(nombre)) return null;
        try {
            Path base = Paths.get(imageService.getImagePath(), "personas", "perfil").toRealPath();
            Path file = base.resolve(nombre).toRealPath();
            return file.startsWith(base) ? file : null;
        } catch (java.io.IOException noExiste) {
            return null;
        }
    }

    /**
     * Endpoint para sincronizar solo la metadata (nombre de archivo) de imagen.
     * NO recibe el archivo físico, solo actualiza la referencia en la BD.
     * Usado por servidores filiales para sincronizar URLs de imágenes.
     */
    @PostMapping("/{id}/imagen-metadata")
    public ResponseEntity<Void> updateImagenMetadata(
            @PathVariable Long id,
            @RequestBody com.franco.dev.service.personas.dto.ImagenMetadataRequest request) {

        if (request.getNombreImagen() == null || request.getNombreImagen().trim().isEmpty()
                || !PersonaService.imagenesValidas(request.getNombreImagen())) {
            return ResponseEntity.badRequest().build();
        }
        Persona persona = personaService.findById(id).orElse(null);
        if (persona == null) {
            return ResponseEntity.notFound().build();
        }

        // Solo actualizar el campo imagenes sin sincronizar
        persona.setImagenes(request.getNombreImagen());
        personaService.saveLocal(persona);

        return ResponseEntity.ok().build();
    }
}
