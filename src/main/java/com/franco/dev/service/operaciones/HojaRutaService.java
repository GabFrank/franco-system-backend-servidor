package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.HojaRuta;
import com.franco.dev.repository.operaciones.HojaRutaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        return repository.findActivasByVehiculoId(vehiculoId).stream().findFirst();
    }

    public Page<HojaRuta> findHojasRutaConEntregas(Pageable pageable) {
        return repository.findHojasRutaConEntregas(pageable);
    }

    public List<HojaRuta> findByFecha(LocalDateTime inicio, LocalDateTime fin) {
        return repository.findByFechaSalidaBetweenOrderByIdDesc(inicio, fin);
    }

    /**
     * Pagina las hojas de ruta por rango de salida y texto libre, dejando los
     * acompaniantes ya inicializados. Son dos consultas fijas: la paginada con las
     * relaciones to-one y una segunda para la coleccion, en vez de una por cada fila.
     */
    @Transactional(readOnly = true)
    public Page<HojaRuta> buscarPorFecha(LocalDateTime inicio, LocalDateTime fin, String texto, Pageable pageable) {
        String filtro = (texto == null || texto.trim().isEmpty())
                ? null
                : "%" + texto.trim().toUpperCase() + "%";
        Page<HojaRuta> pagina = repository.buscarPorFecha(inicio, fin, filtro, pageable);
        List<Long> ids = pagina.getContent().stream().map(HojaRuta::getId).collect(Collectors.toList());
        if (!ids.isEmpty()) {
            repository.fetchAcompanantes(ids);
        }
        return pagina;
    }
}
