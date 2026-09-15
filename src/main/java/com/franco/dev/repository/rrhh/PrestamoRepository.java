package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Prestamo;
import com.franco.dev.domain.rrhh.enums.PrestamoEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface PrestamoRepository extends HelperRepository<Prestamo, Long> {

    default Class<Prestamo> getEntityClass() {
        return Prestamo.class;
    }

    List<Prestamo> findByFuncionarioIdOrderByFechaInicioDesc(Long funcionarioId);

    List<Prestamo> findByEstadoOrderByFechaInicioDesc(PrestamoEstado estado);

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select p from Prestamo p where " +
            "(:funcionarioId is null or p.funcionario.id = :funcionarioId) and " +
            "(:estado is null or p.estado = :estado) " +
            "order by p.fechaInicio desc, p.id desc")
    Page<Prestamo> findPage(@Param("funcionarioId") Long funcionarioId,
                            @Param("estado") PrestamoEstado estado,
                            Pageable pageable);

    /** Lock pesimista para sumar al monto pagado: dos cuotas del mismo prestamo cobradas a la vez no se pisan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prestamo p where p.id = :id")
    Optional<Prestamo> lockById(@Param("id") Long id);
}
