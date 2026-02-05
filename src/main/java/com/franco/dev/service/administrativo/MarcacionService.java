package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.repository.administrativo.MarcacionRepository;
import com.franco.dev.service.CrudService; // Assuming CrudService exists based on file list
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MarcacionService {

    private final MarcacionRepository repository;

    public Marcacion save(Marcacion entity) {
        return repository.save(entity);
    }

    public Marcacion findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public List<Marcacion> findByUsuarioId(Long usuarioId, LocalDateTime inicio, LocalDateTime fin) {
        return repository.findByUsuarioIdAndFechaEntradaBetween(usuarioId, inicio, fin);
    }

    public List<Marcacion> findAll(int page, int size) {
        return repository.findAll(org.springframework.data.domain.PageRequest.of(page, size)).getContent();
    }
}
