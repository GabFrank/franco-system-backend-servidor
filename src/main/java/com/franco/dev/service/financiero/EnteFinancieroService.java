package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EnteFinanciero;
import com.franco.dev.repository.financiero.EnteFinancieroRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@AllArgsConstructor
public class EnteFinancieroService extends CrudService<EnteFinanciero, EnteFinancieroRepository, Long> {

    private final EnteFinancieroRepository repository;

    @Override
    public EnteFinancieroRepository getRepository() {
        return repository;
    }

    public Optional<EnteFinanciero> findByEnteId(Long enteId) {
        return repository.findByEnteId(enteId);
    }

    public Page<EnteFinanciero> findByAllWithPage(String texto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return repository.findByAllWithPage(texto, pageable);
    }

    @Override
    public EnteFinanciero save(EnteFinanciero entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }
}
