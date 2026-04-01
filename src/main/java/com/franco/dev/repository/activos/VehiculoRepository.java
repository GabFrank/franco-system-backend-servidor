package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.Vehiculo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VehiculoRepository extends HelperRepository<Vehiculo, Long> {

    default Class<Vehiculo> getEntityClass() {
        return Vehiculo.class;
    }

    public Vehiculo findByChapa(String chapa);

    @Query("select v from Vehiculo v left join v.modelo m left join m.marca ma left join v.propietario p where " +
            "CAST(v.id as text) like concat('%', ?1, '%') or " +
            "UPPER(v.chapa) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(v.color) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(v.identificadorInterno) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(m.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(ma.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(p.nombre) like UPPER(concat('%', ?1, '%'))")
    public List<Vehiculo> findByAll(String texto);

    @Query("select v from Vehiculo v left join v.modelo m left join m.marca ma left join v.propietario p where " +
            "(CAST(v.id as text) like concat('%', ?1, '%') or " +
            "UPPER(v.chapa) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(v.color) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(v.identificadorInterno) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(m.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(ma.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(p.nombre) like UPPER(concat('%', ?1, '%'))) " +
            "order by v.id asc")
    public Page<Vehiculo> findByAllWithPage(String texto, Pageable pageable);

    @Query("select v from Vehiculo v where v.modelo.marca.id = ?1")
    public List<Vehiculo> findByMarcaId(Long marcaId);

    @Query("select v from Vehiculo v where v.modelo.id = ?1")
    public List<Vehiculo> findByModeloId(Long modeloId);

    @Query("select v from Vehiculo v where v.tipoVehiculo.id = ?1")
    public List<Vehiculo> findByTipoVehiculoId(Long tipoVehiculoId);

    public List<Vehiculo> findByPropietarioId(Long propietarioId);

    @Query("select v from Vehiculo v where v.tipoCombustible.id = ?1")
    public List<Vehiculo> findByTipoCombustibleId(Long tipoCombustibleId);

}
