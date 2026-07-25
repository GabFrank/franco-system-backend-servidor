package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.HojaRuta;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HojaRutaRepository extends HelperRepository<HojaRuta, Long> {

    default Class<HojaRuta> getEntityClass() {
        return HojaRuta.class;
    }

    Page<HojaRuta> findByVehiculoId(Long vehiculoId, Pageable pageable);

    Page<HojaRuta> findByChoferId(Long choferId, Pageable pageable);

    // Devuelve lista y no Optional: un vehiculo puede acumular varias hojas en estado
    // EN_RUTA y un Optional lanzaria NonUniqueResultException. El service toma la mas reciente.
    @Query("SELECT h FROM HojaRuta h WHERE h.vehiculo.id = :vehiculoId AND h.estado = 'EN_RUTA' ORDER BY h.id DESC")
    List<HojaRuta> findActivasByVehiculoId(Long vehiculoId);

    @Query("SELECT h FROM HojaRuta h WHERE h.chofer IS NOT NULL ORDER BY h.id DESC")
    Page<HojaRuta> findHojasRutaConEntregas(Pageable pageable);

    List<HojaRuta> findByFechaSalidaBetweenOrderByIdDesc(LocalDateTime inicio, LocalDateTime fin);
}
