package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RoleRepository extends HelperRepository<Role, Long> {

    default Class<Role> getEntityClass() {
        return Role.class;
    }

    Role findByNombre(String email);

    @Query(value = "select * from personas.\"role\" r \n" +
            "left join personas.usuario_role ur on ur.role_id = r.id \n" +
            "left join personas.usuario u on u.id = ur.user_id \n" +
            "where u.id = ?1", nativeQuery = true)
    public List<Role> findByUsuarioId(Long id);

    @Query(
            value = "select r from Role r " +
                    "where CAST(r.id as text) like %?1% or UPPER(r.nombre) like %?1% " +
                    "order by r.id asc",
            countQuery = "select count(r) from Role r " +
                    "where CAST(r.id as text) like %?1% or UPPER(r.nombre) like %?1%"
    )
    Page<Role> findByNombrePaginated(String texto, Pageable pageable);


}
