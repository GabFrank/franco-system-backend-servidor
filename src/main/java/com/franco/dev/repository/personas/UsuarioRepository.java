package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends HelperRepository<Usuario, Long> {

    default Class<Usuario> getEntityClass() {
        return Usuario.class;
    }

    Usuario findByPersonaId(Long id);

    @Query("select u from Usuario u " +
            "join u.persona p " +
            "where CAST(u.id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(u.nickname) like %?1% or p.documento like %?1% or CAST(p.id as text) like %?1%")
    public List<Usuario> findbyIdOrPersona(String texto);

    @Query("select u from Usuario u " +
            "join u.persona p " +
            "where CAST(u.id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(u.nickname) like %?1% or p.documento like %?1% or CAST(p.id as text) like %?1%")
    Page<Usuario> findbyIdOrPersona(String texto, Pageable pageable);

    public boolean existsByEmail(String email);

    public boolean existsByNicknameIgnoreCase(String nickname);

    public Optional<Usuario> findByNicknameIgnoreCase(String nickname);

    public Optional<Usuario> findByEmail(String email);

    @Query("select u from Usuario u " +
            "join u.persona p " +
            "where u.activo = true " +
            "order by p.nombre asc")
    List<Usuario> findAllActivos();

}
