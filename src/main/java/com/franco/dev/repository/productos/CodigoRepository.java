package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CodigoRepository extends HelperRepository<Codigo, Long> {

    default Class<Codigo> getEntityClass() {
        return Codigo.class;
    }

    public List<Codigo> findByCodigo(String texto);


    public List<Codigo> findByCodigoAndPrincipal(String codigo, Boolean principal);

    @Query(value = "SELECT pr.producto_id FROM productos.codigo c " +
            "INNER JOIN productos.presentacion pr ON pr.id = c.presentacion_id " +
            "WHERE UPPER(c.codigo) LIKE CONCAT(UPPER(?1), '%') " +
            "AND (c.activo IS NULL OR c.activo = true) " +
            "GROUP BY pr.producto_id " +
            "ORDER BY MIN(LENGTH(c.codigo)) ASC " +
            "LIMIT ?2", nativeQuery = true)
    List<Long> findProductoIdsByCodigoPrefijo(String prefijo, int limite);

    public List<Codigo> findByPresentacionId(Long id);

    @Query(value = "select * from productos.presentacion p " +
            "left outer join productos.codigo c on c.presentacion_id = p.id " +
            "where c.principal = true and p.id = ?1 limit 1", nativeQuery = true)
    public Codigo findPrincipalByPresentacionId(Long id);

    /**
     * Máxima secuencia de 8 dígitos para códigos internos {@code 2199########X}.
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(codigo FROM 5 FOR 8) AS BIGINT)), 0) " +
            "FROM productos.codigo WHERE codigo ~ '^2199[0-9]{9}$'", nativeQuery = true)
    Long findMaxInternalEan21Sequence();

    boolean existsByCodigo(String codigo);

}
