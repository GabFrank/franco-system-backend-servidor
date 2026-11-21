package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.TokenServicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TokenServicioRepository extends JpaRepository<TokenServicio, Long> {

    List<TokenServicio> findAllByOrderByIdDesc();

    List<TokenServicio> findByUsuarioIdOrderByIdDesc(Long usuarioId);
}
