package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.ProveedorServicio;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface ProveedorServicioRepository extends HelperRepository<ProveedorServicio, Long> {

    default Class<ProveedorServicio> getEntityClass() {
        return ProveedorServicio.class;
    }

    ProveedorServicio findByPersonaId(Long id);

    /**
     * Busqueda paginada ignorando mayusculas/minusculas en nombre, apodo y documento.
     * Los patrones deben incluir % (ej. "%texto%" o "%palabra1%palabra2%").
     */
    @Query("SELECT p FROM ProveedorServicio p LEFT JOIN p.persona per " +
            "WHERE UPPER(per.nombre) LIKE UPPER(?1) OR UPPER(per.apodo) LIKE UPPER(?2) OR UPPER(per.documento) LIKE UPPER(?3)")
    Page<ProveedorServicio> findByPersonaNombreOrApodoOrDocumentoIgnoreCase(String patternNombre, String patternApodo, String patternDocumento, Pageable pageable);
}
