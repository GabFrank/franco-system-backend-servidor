package com.franco.dev.service.rrhh;

import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.FuncionarioCargoHistorico;
import com.franco.dev.domain.rrhh.FuncionarioSalarioHistorico;
import com.franco.dev.service.empresarial.CargoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Orquesta cambios con trazabilidad sobre el funcionario (cargo, salario, egreso).
 * NO toca FuncionarioService.saveFuncionario (usado por desktop) — expone
 * mutaciones nuevas dedicadas para no romper el flujo existente (regla Mobile §18.1).
 */
@Service
@AllArgsConstructor
public class FuncionarioRrhhService {

    private final FuncionarioService funcionarioService;
    private final CargoService cargoService;
    private final MonedaService monedaService;
    private final UsuarioService usuarioService;
    private final FuncionarioCargoHistoricoService cargoHistoricoService;
    private final FuncionarioSalarioHistoricoService salarioHistoricoService;

    /**
     * Cambia el cargo del funcionario dejando rastro: cierra el histórico abierto
     * con fecha_hasta y crea uno nuevo. Actualiza funcionario.cargo.
     */
    @Transactional
    public Funcionario cambiarCargo(Long funcionarioId, Long cargoId, LocalDate fecha, String motivo, Long autorizadoPorId) {
        Funcionario f = funcionarioService.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado"));
        Cargo cargo = cargoId != null ? cargoService.findById(cargoId).orElse(null) : null;
        LocalDate desde = fecha != null ? fecha : LocalDate.now();

        // cerrar el/los histórico(s) abierto(s)
        List<FuncionarioCargoHistorico> abiertos = cargoHistoricoService.findVigentes(funcionarioId);
        for (FuncionarioCargoHistorico h : abiertos) {
            h.setFechaHasta(desde);
            cargoHistoricoService.save(h);
        }

        FuncionarioCargoHistorico nuevo = new FuncionarioCargoHistorico();
        nuevo.setFuncionario(f);
        nuevo.setCargo(cargo);
        nuevo.setFechaDesde(desde);
        nuevo.setMotivo(motivo);
        if (autorizadoPorId != null)
            nuevo.setAutorizadoPor(usuarioService.findById(autorizadoPorId).orElse(null));
        cargoHistoricoService.save(nuevo);

        f.setCargo(cargo);
        return funcionarioService.save(f);
    }

    /**
     * Cambia el salario del funcionario dejando rastro: snapshottea anterior/nuevo
     * en numeric(18,2). Actualiza funcionario.sueldo (Float).
     */
    @Transactional
    public Funcionario cambiarSalario(Long funcionarioId, BigDecimal nuevoSalario, Long monedaId, LocalDate fecha, String motivo, Long autorizadoPorId) {
        if (nuevoSalario == null) throw new GraphQLException("El nuevo salario es requerido");
        Funcionario f = funcionarioService.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado"));

        BigDecimal anterior = f.getSueldo() != null ? BigDecimal.valueOf(f.getSueldo()) : null;
        FuncionarioSalarioHistorico h = new FuncionarioSalarioHistorico();
        h.setFuncionario(f);
        h.setSalarioAnterior(anterior);
        h.setSalarioNuevo(nuevoSalario);
        if (monedaId != null) h.setMoneda(monedaService.findById(monedaId).orElse(null));
        else h.setMoneda(f.getMoneda());
        h.setFechaVigencia(fecha != null ? fecha : LocalDate.now());
        h.setMotivo(motivo);
        if (autorizadoPorId != null)
            h.setAutorizadoPor(usuarioService.findById(autorizadoPorId).orElse(null));
        salarioHistoricoService.save(h);

        f.setSueldo(nuevoSalario.floatValue());
        return funcionarioService.save(f);
    }

    /**
     * Egresa al funcionario: marca fecha/motivo de egreso y activo=false.
     * El cálculo de la liquidación final se dispara aparte (Fase 6).
     */
    @Transactional
    public Funcionario egresar(Long funcionarioId, LocalDate fecha, String motivo) {
        Funcionario f = funcionarioService.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado"));
        f.setFechaEgreso(fecha != null ? fecha.atStartOfDay() : LocalDateTime.now());
        f.setMotivoEgreso(motivo != null ? motivo.toUpperCase() : null);
        f.setActivo(false);
        return funcionarioService.save(f);
    }
}
