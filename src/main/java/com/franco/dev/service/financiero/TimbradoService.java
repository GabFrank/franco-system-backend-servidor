package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.repository.financiero.TimbradoRepository;
import com.franco.dev.service.CrudService;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class TimbradoService extends CrudService<Timbrado, TimbradoRepository, Long> {

    private final TimbradoRepository repository;
    private final Validator validator;

    @Override
    public TimbradoRepository getRepository() {
        return repository;
    }

    public List<Timbrado> findByAll(String texto) {
        texto = texto != null ? texto.replace(' ', '%') : "%";
        return repository.findByAll(texto);
    }

    public Page<Timbrado> findByNumeroLike(String numero, Pageable page) {
        return repository.findByNumeroLike(numero, page);
    }

    @Override
    public Timbrado save(Timbrado entity) {
        // Validar manualmente las reglas de negocio antes de guardar
        Set<ConstraintViolation<Timbrado>> violations = validator.validate(entity);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        
        Timbrado e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public Boolean existeTimbradoActivo(Long excludeId) {
        return repository.existeTimbradoActivo(excludeId);
    }
}