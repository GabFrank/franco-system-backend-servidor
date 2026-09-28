package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(
            value = "select u from Usuario u " +
                    "join u.persona p " +
                    "where CAST(u.id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(u.nickname) like %?1% or p.documento like %?1% or CAST(p.id as text) like %?1% " +
                    "order by u.id asc",
            countQuery = "select count(u) from Usuario u " +
                    "join u.persona p " +
                    "where CAST(u.id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(u.nickname) like %?1% or p.documento like %?1% or CAST(p.id as text) like %?1%"
    )
    org.springframework.data.domain.Page<Usuario> findbyIdOrPersonaPaginated(String texto, org.springframework.data.domain.Pageable pageable);

    /**
     * Igual que {@link #findbyIdOrPersonaPaginated}, pero solo usuarios con al menos uno de los roles.
     * El bloque de OR va entre parentesis: sin ellos el AND solo aplicaria a la ultima condicion y el
     * filtro por rol dejaria pasar casi todo (UsuarioServiceFiltroRolTest lo verifica).
     */
    @Query(
            value = "select u from Usuario u " +
                    "join u.persona p " +
                    "where (CAST(u.id as text) like %:texto% or UPPER(p.nombre) like %:texto% or UPPER(u.nickname) like %:texto% or p.documento like %:texto% or CAST(p.id as text) like %:texto%) " +
                    "and exists (select ur.id from UsuarioRole ur where ur.user = u and ur.role.id in :roleIds) " +
                    "order by u.id asc",
            countQuery = "select count(u) from Usuario u " +
                    "join u.persona p " +
                    "where (CAST(u.id as text) like %:texto% or UPPER(p.nombre) like %:texto% or UPPER(u.nickname) like %:texto% or p.documento like %:texto% or CAST(p.id as text) like %:texto%) " +
                    "and exists (select ur.id from UsuarioRole ur where ur.user = u and ur.role.id in :roleIds)"
    )
    org.springframework.data.domain.Page<Usuario> findbyIdOrPersonaAndRolesPaginated(@Param("texto") String texto,
                                                                                   @Param("roleIds") List<Long> roleIds,
                                                                                   org.springframework.data.domain.Pageable pageable);

    public boolean existsByEmail(String email);

    public boolean existsByNicknameIgnoreCase(String nickname);

    public Optional<Usuario> findByNicknameIgnoreCase(String nickname);

    public Optional<Usuario> findByEmail(String email);

    @Query("select u from Usuario u " +
            "join u.persona p " +
            "where u.activo = true " +
            "order by p.nombre asc")
    List<Usuario> findAllActivos();

    @Query("select u from Usuario u " +
            "join fetch u.persona p " +
            "where u.activo = true " +
            "and p.embedding is not null " +
            "and trim(p.embedding) <> ''")
    List<Usuario> findActivosConEmbedding();

}
