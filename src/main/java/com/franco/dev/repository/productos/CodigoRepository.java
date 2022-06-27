package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CodigoRepository extends HelperRepository<Codigo, Long> {

    default Class<Codigo> getEntityClass() {
        return Codigo.class;
    }

    public List<Codigo> findByCodigo(String texto);

    public List<Codigo> findByPresentacionId(Long id);

    @Query(value = "select * from productos.presentacion p " +
            "left outer join productos.codigo c on c.presentacion_id = p.id " +
            "where c.principal = true and p.id = ?1 limit 1", nativeQuery = true)
    public Codigo findPrincipalByPresentacionId(Long id);

}
