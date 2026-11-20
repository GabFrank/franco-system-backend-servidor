package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PersonaRepository extends HelperRepository<Persona, Long> {

    default Class<Persona> getEntityClass() {
        return Persona.class;
    }

    public Persona findByNombre(String nombre);

    public Persona findByDocumento(String documento);

    @Query("select p from Persona p " +
            "where UPPER(CAST(p.id as text)) like UPPER(concat('%', ?1, '%')) " +
            "or UPPER(p.nombre) like UPPER(concat('%', ?1, '%')) " +
            "or UPPER(p.apodo) like UPPER(concat('%', ?1, '%')) " +
            "or UPPER(p.documento) like UPPER(concat('%', ?1, '%')) " +
            // Sin orden fijo Postgres puede repetir o saltear filas entre paginas.
            "order by p.id")
    public org.springframework.data.domain.Page<Persona> findByAll(String texto, org.springframework.data.domain.Pageable pageable);

    @Query("select p from Persona p where CAST(p.id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(p.apodo) like %?1% or UPPER(p.documento) like %?1%")
    public List<Persona> findbyAll(String texto);

    /**
     * Solo si la persona no tiene pais (#666): la caja y el backfill no cambian uno cargado; corregirlo es del
     * admin (savePersona). Atomico, sin leer y guardar la fila entera.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = "update personas.persona set pais_id = :paisId where id = :id and pais_id is null", nativeQuery = true)
    int asignarPaisSiNulo(@org.springframework.data.repository.query.Param("id") Long id,
                          @org.springframework.data.repository.query.Param("paisId") Long paisId);

}
