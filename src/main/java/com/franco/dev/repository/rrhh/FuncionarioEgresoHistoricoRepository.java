package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.FuncionarioEgresoHistorico;
import com.franco.dev.repository.HelperRepository;

import java.util.List;
import java.util.Optional;

public interface FuncionarioEgresoHistoricoRepository extends HelperRepository<FuncionarioEgresoHistorico, Long> {

    default Class<FuncionarioEgresoHistorico> getEntityClass() {
        return FuncionarioEgresoHistorico.class;
    }

    /**
     * El egreso vigente del funcionario: el ultimo que todavia no se revirtio.
     *
     * <p>Se ordena por id y no por fecha_egreso porque la fecha la elige el usuario en el
     * dialogo y puede ser retroactiva; el id es el orden real en que se cargaron.</p>
     */
    Optional<FuncionarioEgresoHistorico> findFirstByFuncionarioIdAndRevertidoEnIsNullOrderByIdDesc(Long funcionarioId);

    List<FuncionarioEgresoHistorico> findByFuncionarioIdOrderByIdDesc(Long funcionarioId);
}
