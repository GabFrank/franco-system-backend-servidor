package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.repository.HelperRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    boolean existsByUsuarioIdAndIdDispositivo(Long usuarioId, String idDispositivo);
    boolean existsByUsuarioIdAndIdDispositivoAndHoraFinIsNull(Long usuarioId, String idDispositivo);
    Optional<InicioSesion> findFirstByIdDispositivoAndUsuarioIsNotNullOrderByIdAsc(String idDispositivo);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE InicioSesion s SET s.token = null WHERE s.token = :token")
    void clearTokenByToken(@org.springframework.data.repository.query.Param("token") String token);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM InicioSesion s WHERE s.usuario.id IN :usuarioIds " +
            "AND s.token IS NOT NULL AND s.token != '' " +
            "ORDER BY s.id DESC")
    List<InicioSesion> findByUsuarioIdInWithValidTokens(
            @org.springframework.data.repository.query.Param("usuarioIds") Collection<Long> usuarioIds);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM InicioSesion s WHERE " +
            "s.token IS NOT NULL AND s.token != '' AND s.usuario IS NOT NULL " +
            "ORDER BY s.id DESC")
    List<InicioSesion> findAllWithValidTokens();
}