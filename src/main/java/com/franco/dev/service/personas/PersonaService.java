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
        if (entity.getDocumento().contains("-")) {
            int index = entity.getDocumento().indexOf("-");
            entity.setDocumento(entity.getDocumento().substring(0, index));
        }
        Persona p = super.save(entity);
        // personaPublisher.publish(p);
        return p;
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
