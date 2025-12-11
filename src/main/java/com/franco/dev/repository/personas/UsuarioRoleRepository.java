package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UsuarioRoleRepository extends HelperRepository<UsuarioRole, Long> {

    default Class<UsuarioRole> getEntityClass() {
        return UsuarioRole.class;
    }

    List<UsuarioRole> findByUserId(Long id);

    @Query("SELECT ur FROM UsuarioRole ur WHERE ur.role.id = :roleId")
    List<UsuarioRole> findByRoleId(@Param("roleId") Long id);

}
