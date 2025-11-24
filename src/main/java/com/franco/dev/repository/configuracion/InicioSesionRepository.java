package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.repository.HelperRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InicioSesionRepository extends HelperRepository<InicioSesion, Long> {

    default Class<InicioSesion> getEntityClass() {
        return InicioSesion.class;
    }

    Page<InicioSesion> findByUsuarioIdAndHoraFinIsNullOrderByIdDesc(Long id, Pageable page);

    Page<InicioSesion> findByUsuarioIdAndSucursalIdAndHoraFinIsNullOrderByIdDesc(Long id, Long sucId, Pageable page);

    List<InicioSesion> findByUsuarioIdInAndHoraFinIsNullOrderByIdDesc(Collection<Long> usuarioIds);

    List<InicioSesion> findByToken(String token);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE InicioSesion s SET s.token = null WHERE s.token = :token")
    void clearTokenByToken(@org.springframework.data.repository.query.Param("token") String token);
}