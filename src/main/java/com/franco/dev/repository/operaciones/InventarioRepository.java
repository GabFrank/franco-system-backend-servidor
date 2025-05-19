package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.enums.InventarioEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventarioRepository extends HelperRepository<Inventario, Long> {

    default Class<Inventario> getEntityClass() {
        return Inventario.class;
    }

    @Query(value = "select * from operaciones.inventario ms \n" +
            "where ms.creado_en between cast(?1 as timestamp) and cast(?2 as timestamp) or ms.estado = 'ABIERTO'", nativeQuery = true)
    public List<Inventario> findByDate(String inicio, String fin);

    @Query(value = "select distinct on (i.id) i.id, i.id_origen, i.sucursal_id, i.fecha_inicio, i.fecha_fin, i.abierto, i.tipo, i.estado, i.usuario_id, i.observacion  from operaciones.inventario i " +
            "left join operaciones.inventario_producto ip on i.id = ip.inventario_id " +
            "where i.usuario_id = :usuarioId or ip.usuario_id = :usuarioId order by i.id", nativeQuery = true)
    public List<Inventario> findByUsuarioId(@Param("usuarioId") Long id);

    public Page<Inventario> findByUsuario_IdOrderByIdDesc(Long usuarioId, Pageable pageable);

    @Query("SELECT i FROM Inventario i WHERE i.usuario.id = :usuarioId " +
           "ORDER BY CASE WHEN i.estado = com.franco.dev.domain.operaciones.enums.InventarioEstado.ABIERTO THEN 0 ELSE 1 END, i.fechaInicio DESC")
    public Page<Inventario> findByUsuarioIdOrderByAbiertoPrimero(@Param("usuarioId") Long usuarioId, Pageable pageable);

    @Query("SELECT i FROM Inventario i WHERE i.usuario.id = :usuarioId " +
           "ORDER BY CASE WHEN i.estado = com.franco.dev.domain.operaciones.enums.InventarioEstado.CONCLUIDO THEN 0 ELSE 1 END, i.fechaInicio DESC")
    public Page<Inventario> findByUsuarioIdOrderByConcluidoPrimero(@Param("usuarioId") Long usuarioId, Pageable pageable);

    @Query("SELECT i FROM Inventario i WHERE i.usuario.id = :usuarioId " +
           "ORDER BY CASE WHEN i.estado = com.franco.dev.domain.operaciones.enums.InventarioEstado.CANCELADO THEN 0 ELSE 1 END, i.fechaInicio DESC")
    public Page<Inventario> findByUsuarioIdOrderByCanceladoPrimero(@Param("usuarioId") Long usuarioId, Pageable pageable);

    public List<Inventario> findBySucursalIdAndEstado(Long id, InventarioEstado estado);

    
}