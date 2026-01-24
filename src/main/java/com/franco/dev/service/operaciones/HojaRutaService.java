package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.HojaRuta;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.repository.operaciones.HojaRutaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class HojaRutaService extends CrudService<HojaRuta, HojaRutaRepository, Long> {

    private final HojaRutaRepository repository;

    @Override
    public HojaRutaRepository getRepository() {
        return repository;
    }

    public Page<HojaRuta> findByVehiculoId(Long vehiculoId, Pageable pageable) {
        return repository.findByVehiculoId(vehiculoId, pageable);
    }

    public Page<HojaRuta> findByChoferId(Long choferId, Pageable pageable) {
        return repository.findByChoferId(choferId, pageable);
    }

    public Optional<HojaRuta> findActivaByVehiculoId(Long vehiculoId) {
        return repository.findActivaByVehiculoId(vehiculoId);
    }

    public Page<HojaRuta> findHojasRutaConEntregas(Pageable pageable) {
        return repository.findHojasRutaConEntregas(pageable);
    }

    public List<HojaRuta> findByFecha(LocalDateTime inicio, LocalDateTime fin) {
        return repository.findByFechaSalidaBetween(inicio, fin);
    }
}
