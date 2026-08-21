package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.FuncionarioSalarioHistorico;
import com.franco.dev.repository.personas.FuncionarioRepository;
import com.franco.dev.service.personas.FuncionarioService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Ajuste de salarios ante un cambio de SALARIO_MINIMO_LEGAL_PYG.
 *
 * Deliberadamente NO hay cascade automatico. Los salarios son registros legales
 * con historico: subir el minimo no puede reescribir sueldos en silencio. El flujo
 * es opt-in y en dos pasos — primero se listan los afectados (vista previa), y
 * despues el usuario elige a cuales aplicar.
 *
 * Ojo: esto aplica solo a los parametros MATERIALIZADOS en registros. Los parametros
 * que se leen al calcular (IPS, recargos de HE, dias de vacaciones) no necesitan
 * cascade: cambiarlos ya afecta todo calculo futuro, y reescribir historico seria
 * incorrecto.
 */
@Service
@AllArgsConstructor
public class AjusteSalarioMinimoService {

    private static final String MOTIVO = "AJUSTE POR CAMBIO DE SALARIO MINIMO";

    private final FuncionarioRepository funcionarioRepository;
    private final FuncionarioService funcionarioService;
    private final FuncionarioSalarioHistoricoService salarioHistoricoService;

    /** Vista previa: funcionarios activos que quedaron por debajo del nuevo minimo. */
    public List<Funcionario> findAfectadosPorMinimo(BigDecimal minimo) {
        if (minimo == null) return Collections.emptyList();
        return funcionarioRepository.findConSueldoMenorA(minimo);
    }

    /**
     * Sube al minimo el sueldo de los funcionarios indicados, dejando rastro en
     * funcionario_salario_historico (uno por funcionario, con SU moneda). Devuelve
     * cuantos se ajustaron efectivamente.
     */
    @Transactional
    public int ajustarAlMinimo(List<Long> funcionarioIds, BigDecimal minimo, Usuario autorizadoPor) {
        if (funcionarioIds == null || funcionarioIds.isEmpty() || minimo == null) return 0;

        int ajustados = 0;

        for (Long id : funcionarioIds) {
            Funcionario f = funcionarioService.findById(id).orElse(null);
            if (f == null) continue;
            BigDecimal anterior = f.getSueldo();
            // Se revalidan los mismos invariantes de la vista previa: entre el preview y
            // la confirmacion el sueldo pudo cambiar por otra via, y ademas el mutation
            // es alcanzable sin pasar por la UI.
            if (anterior == null || anterior.signum() <= 0) continue;
            if (anterior.compareTo(minimo) >= 0) continue;
            if (!Boolean.TRUE.equals(f.getActivo())) continue;

            FuncionarioSalarioHistorico h = new FuncionarioSalarioHistorico();
            h.setFuncionario(f);
            h.setSalarioAnterior(anterior);
            h.setSalarioNuevo(minimo);
            // La moneda sale de CADA funcionario: puede variar entre ellos, y el historico
            // salarial es un registro legal — no se puede estampar la moneda de otro.
            h.setMoneda(f.getMoneda());
            h.setFechaVigencia(LocalDate.now());
            h.setMotivo(MOTIVO);
            h.setAutorizadoPor(autorizadoPor);
            salarioHistoricoService.save(h);

            f.setSueldo(minimo);
            funcionarioService.save(f);
            ajustados++;
        }
        return ajustados;
    }
}
