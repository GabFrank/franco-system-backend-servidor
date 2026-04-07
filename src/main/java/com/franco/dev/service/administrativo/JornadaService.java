package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.repository.administrativo.JornadaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class JornadaService extends CrudService<Jornada, JornadaRepository, EmbebedPrimaryKey> {

    private final JornadaRepository repository;

    @Override
    public JornadaRepository getRepository() {
        return repository;
    }

    public List<Jornada> findByUsuarioId(Long usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }

    public List<Jornada> findByUsuarioIdAndFechaRange(Long usuarioId, String fechaInicio, String fechaFin) {
        return repository.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin);
    }

    public List<Jornada> findByFechaRange(String fechaInicio, String fechaFin) {
        return repository.findByFechaRange(fechaInicio, fechaFin);
    }

    public List<Jornada> findByUsuarioIdAndFecha(Long usuarioId, String fecha) {
        return repository.findByUsuarioIdAndFecha(usuarioId, fecha);
    }

    public Optional<Jornada> findByMarcacionEntradaId(Long id) {
        return repository.findByMarcacionEntradaId(id);
    }

    public Jornada ajustarA8Horas(Long id, Long sucursalId, String observacion) {
        Optional<Jornada> optionalJornada = repository.findById(new EmbebedPrimaryKey(id, sucursalId));
        if (optionalJornada.isPresent()) {
            Jornada jornada = optionalJornada.get();
            jornada.setMinutosTrabajados(480L);
            jornada.setMinutosExtras(0L);
            jornada.setEstado(com.franco.dev.domain.administrativo.enums.EstadoJornada.NORMAL);
            if (observacion != null) {
                jornada.setObservacion(observacion);
            }
            return repository.save(jornada);
        }
        return null;
    }

    public Jornada guardarObservacion(Long id, Long sucursalId, String observacion) {
        Optional<Jornada> optionalJornada = repository.findById(new EmbebedPrimaryKey(id, sucursalId));
        if (optionalJornada.isPresent()) {
            Jornada jornada = optionalJornada.get();
            jornada.setObservacion(observacion);
            return repository.save(jornada);
        }
        return null;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public Jornada save(Jornada entity) {
        if (entity.getId() == null) {
            Long lastId = repository.findMaxId(entity.getSucursalId());
            long newId = (lastId == null ? 0L : lastId) + 1L;
            if (newId % 2 == 0) {
                newId++;
            }
            entity.setId(newId);
        }
        Jornada e = super.save(entity);
        return e;
    }
}
