package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.repository.personas.PersonaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PersonaService extends CrudService<Persona, PersonaRepository, Long> {

    private final PersonaRepository repository;

    @Override
    public PersonaRepository getRepository() {
        return repository;
    }

    public List<Persona> findByAll(String texto) {
        if (texto == null) texto = "";
        texto = texto.replace(' ', '%');
        return repository.findbyAll(texto.toUpperCase());
    }

    public org.springframework.data.domain.Page<Persona> findByAll(String texto, org.springframework.data.domain.Pageable pageable) {
        if (texto == null) texto = "";
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase(), pageable);
    }

    public org.springframework.data.domain.Page<Persona> findByAllWithPage(String texto, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        if (texto == null) texto = "";
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase(), pageable);
    }

    public Persona findByDocumento(String texto) {
        return repository.findByDocumento(texto);
    }

    /**
     * {@code imagenes} guarda nombres de archivo de la carpeta de fotos (separados por coma), nunca una ruta:
     * quien lo lee arma la ruta con ese nombre.
     */
    public static boolean imagenesValidas(String imagenes) {
        if (imagenes == null) return true;
        for (String parte : imagenes.split(",")) {
            if (parte.contains("/") || parte.contains("\\") || parte.contains("..")) return false;
        }
        return true;
    }

    @Override
    public Persona save(Persona entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        if (entity.getNombre() != null)
            entity.setNombre(entity.getNombre().toUpperCase());
        if (entity.getApodo() != null)
            entity.setApodo(entity.getApodo().toUpperCase());
        if (entity.getDireccion() != null)
            entity.setDireccion(entity.getDireccion().toUpperCase());
        if (entity.getEmail() != null)
            entity.setEmail(entity.getEmail().toLowerCase().trim());
        entity.setDocumento(sinDv(entity.getDocumento()));
        Persona p = super.save(entity);
        // personaPublisher.publish(p);
        return p;
    }

    /**
     * Corta el DV ("4043581-2" -> "4043581"). Un documento con letras es un pasaporte o documento extranjero
     * (#666): el guion es parte del numero ("AB-12345") y no se corta, y va en mayusculas para que "fx1" y "FX1"
     * no sean dos personas (las busquedas por documento son exactas).
     */
    public static String sinDv(String documento) {
        if (documento == null) return null;
        if (documento.matches(".*[A-Za-z].*")) return documento.toUpperCase();
        int guion = documento.indexOf('-');
        return guion >= 0 ? documento.substring(0, guion) : documento;
    }

    /** Pais del receptor solo si la persona no tiene (#666). true si lo grabo. */
    public boolean asignarPaisSiNulo(Long personaId, Long paisId) {
        return repository.asignarPaisSiNulo(personaId, paisId) == 1;
    }

    /**
     * Guarda la persona localmente aplicando solo transformaciones básicas.
     * Usado para actualizar metadata (como nombres de archivo de imagen) sin lógica
     * adicional.
     * 
     * @param entity Persona a guardar
     * @return Persona guardada
     */
    public Persona saveLocal(Persona entity) {
        if (entity.getId() == null) {
            throw new IllegalStateException(
                    "No se puede guardar persona localmente sin ID. La persona debe tener un ID válido.");
        }

        if (entity.getNombre() != null)
            entity.setNombre(entity.getNombre().toUpperCase());
        if (entity.getApodo() != null)
            entity.setApodo(entity.getApodo().toUpperCase());
        if (entity.getDireccion() != null)
            entity.setDireccion(entity.getDireccion().toUpperCase());
        if (entity.getEmail() != null)
            entity.setEmail(entity.getEmail().toLowerCase().trim());

        Persona p = super.save(entity);
        return p;
    }
}
