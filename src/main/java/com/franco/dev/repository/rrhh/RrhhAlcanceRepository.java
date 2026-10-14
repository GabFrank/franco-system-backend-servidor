package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Consultas del rol acotado RRHH SUCURSAL: a qué sucursal pertenece el funcionario de un documento y qué
 * funcionarios hay en un conjunto de sucursales.
 *
 * <p>Las de sucursal son proyecciones a propósito: no meten la entidad en el contexto de persistencia antes
 * del lock que toman los servicios. Un funcionario sin sucursal no devuelve fila: queda fuera de alcance.</p>
 */
@org.springframework.stereotype.Repository
public interface RrhhAlcanceRepository extends Repository<Funcionario, Long> {

    @Query("select f.sucursal.id from Funcionario f where f.id = :id")
    List<Long> sucursalDeFuncionario(@Param("id") Long id);

    @Query("select l.funcionario.sucursal.id from LiquidacionSueldo l where l.id = :id")
    List<Long> sucursalDeLiquidacion(@Param("id") Long id);

    @Query("select i.liquidacion.funcionario.sucursal.id from LiquidacionItem i where i.id = :id")
    List<Long> sucursalDeItemDeLiquidacion(@Param("id") Long id);

    @Query("select f.funcionario.sucursal.id from LiquidacionFinal f where f.id = :id")
    List<Long> sucursalDeFiniquito(@Param("id") Long id);

    @Query("select i.liquidacionFinal.funcionario.sucursal.id from LiquidacionFinalItem i where i.id = :id")
    List<Long> sucursalDeItemDeFiniquito(@Param("id") Long id);

    @Query("select f.id from Funcionario f where f.activo = true and f.sucursal.id in :sucursalIds")
    List<Long> funcionariosActivosIds(@Param("sucursalIds") List<Long> sucursalIds);

    @Query("select f from Funcionario f where f.sucursal.id in :sucursalIds "
            + "and (:texto is null or upper(f.persona.nombre) like upper(concat('%', :texto, '%'))) "
            + "and (:soloActivos = false or f.activo = true) "
            + "order by f.persona.nombre")
    List<Funcionario> buscarFuncionarios(@Param("sucursalIds") List<Long> sucursalIds,
                                         @Param("texto") String texto,
                                         @Param("soloActivos") boolean soloActivos,
                                         Pageable pageable);
}
