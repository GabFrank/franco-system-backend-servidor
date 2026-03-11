package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProveedorRepository extends HelperRepository<Proveedor, Long> {

    default Class<Proveedor> getEntityClass() {
        return Proveedor.class;
    }

    Proveedor findByPersonaId(Long id);

    @Query("select distinct p from Proveedor p " +
            "left outer join p.persona as per " +
            "where UPPER(per.nombre) like %?1% or cast(p.id as text) like %?1% or UPPER(per.apodo) like %?1% or UPPER(per.documento) like %?1%")
    public List<Proveedor> findByPersona(String texto);

    @Query("select distinct pro FROM VendedorProveedor v " +
            "left outer JOIN v.vendedor as ven " +
            "left outer JOIN v.proveedor as pro " +
            "where ven.id = ?1")
    public List<Proveedor> findByVendedorId(Long id);

    Page<Proveedor> findByPersonaNombreLikeOrPersonaApodoLikeOrPersonaDocumentoLike(String nombre, String apodo, String documento, Pageable page);

    /**
     * Búsqueda paginada ignorando mayúsculas/minúsculas en nombre, apodo y documento.
     * Los patrones deben incluir % (ej. "%texto%" o "%palabra1%palabra2%").
     */
    @Query("SELECT p FROM Proveedor p LEFT JOIN p.persona per " +
            "WHERE UPPER(per.nombre) LIKE UPPER(?1) OR UPPER(per.apodo) LIKE UPPER(?2) OR UPPER(per.documento) LIKE UPPER(?3)")
    Page<Proveedor> findByPersonaNombreOrApodoOrDocumentoIgnoreCase(String patternNombre, String patternApodo, String patternDocumento, Pageable pageable);
}
