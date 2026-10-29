package com.franco.dev.repository.administrativo;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.repository.HelperRepository;
import java.util.List;

public interface HorarioRepository extends HelperRepository<Horario, Long> {

    List<Horario> findByUsuarioIdOrderByIdDesc(Long usuarioId);
}
