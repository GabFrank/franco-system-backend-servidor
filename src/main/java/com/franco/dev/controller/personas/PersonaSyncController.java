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
import java.io.File;
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

    @PostMapping
    public ResponseEntity<Persona> savePersona(@RequestBody PersonaSyncRequest request) {
        boolean isCreate = request.getId() == null;
        Persona persona = request.getId() != null
                ? personaService.findById(request.getId()).orElse(new Persona())
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

        Persona saved = personaService.save(persona);
        return ResponseEntity.status(isCreate ? HttpStatus.CREATED : HttpStatus.OK).body(saved);
    }

    @PostMapping("/clientes")
    public ResponseEntity<Cliente> saveCliente(@RequestBody ClienteSyncRequest request) {
        boolean isCreate = request.getId() == null;
        Cliente cliente = request.getId() != null
                ? clienteService.findById(request.getId()).orElse(new Cliente())
                : new Cliente();

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

        Cliente saved = clienteService.save(cliente);
        return ResponseEntity.status(isCreate ? HttpStatus.CREATED : HttpStatus.OK).body(saved);
    }

    @GetMapping("/{id}/imagen")
    public ResponseEntity<?> getPersonaImage(@PathVariable Long id) {
        Persona persona = personaService.findById(id).orElse(null);
        if (persona == null || persona.getImagenes() == null) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(persona.getImagenes());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(new FileSystemResource(file));
    }
}
