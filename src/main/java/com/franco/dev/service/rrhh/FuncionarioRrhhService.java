package com.franco.dev.service.rrhh;

import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.FuncionarioCargoHistorico;
import com.franco.dev.domain.rrhh.FuncionarioSalarioHistorico;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado;
import com.franco.dev.domain.rrhh.FuncionarioEgresoHistorico;
import com.franco.dev.repository.rrhh.FuncionarioEgresoHistoricoRepository;
import com.franco.dev.repository.rrhh.LiquidacionFinalRepository;
import com.franco.dev.service.empresarial.CargoService;
import com.franco.dev.service.personas.ClienteService;
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
@lombok.extern.slf4j.Slf4j
public class FuncionarioRrhhService {

    private final FuncionarioService funcionarioService;
    private final CargoService cargoService;
    private final MonedaService monedaService;
    private final UsuarioService usuarioService;
    private final FuncionarioCargoHistoricoService cargoHistoricoService;
    private final FuncionarioSalarioHistoricoService salarioHistoricoService;
    private final ClienteService clienteService;
    private final LiquidacionFinalRepository liquidacionFinalRepository;
    private final FuncionarioEgresoHistoricoRepository egresoHistoricoRepository;

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
     *
     * <p>Antes de guardar toma un <b>snapshot</b> de lo que el egreso va a destruir. No es
     * solo trazabilidad: es lo unico que hace reversible un egreso. {@code save()} pone el
     * credito en cero y la cascada apaga usuario y cliente, lo devuelve a NORMAL y le borra
     * el credito -- nada de eso queda en otro lado. El orden importa: leer despues de
     * guardar seria fotografiar el dano, no el estado previo.</p>
     */
    @Transactional
    public Funcionario egresar(Long funcionarioId, LocalDate fecha, String motivo) {
        Funcionario f = funcionarioService.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado"));

        FuncionarioEgresoHistorico snap = snapshotPrevioAlEgreso(f);

        f.setFechaEgreso(fecha != null ? fecha.atStartOfDay() : LocalDateTime.now());
        f.setMotivoEgreso(motivo != null ? motivo.toUpperCase() : null);
        f.setActivo(false);
        Funcionario guardado = funcionarioService.save(f);

        snap.setFechaEgreso(guardado.getFechaEgreso());
        snap.setMotivoEgreso(guardado.getMotivoEgreso());
        snap.setEgresadoPor(usuarioAutenticado());
        egresoHistoricoRepository.save(snap);
        return guardado;
    }

    /** Foto del estado que el egreso va a destruir. Se arma ANTES de guardar. */
    private FuncionarioEgresoHistorico snapshotPrevioAlEgreso(Funcionario f) {
        FuncionarioEgresoHistorico snap = new FuncionarioEgresoHistorico();
        snap.setFuncionario(f);
        snap.setCreditoAnterior(f.getCredito() != null ? BigDecimal.valueOf(f.getCredito()) : null);
        if (f.getPersona() != null && f.getPersona().getId() != null) {
            Usuario u = usuarioService.findByPersonaId(f.getPersona().getId());
            if (u == null) u = f.getUsuario();
            if (u != null) {
                snap.setUsuarioId(u.getId());
                snap.setUsuarioActivoAnterior(u.getActivo());
            }
            Cliente c = clienteService.findByPersonaId(f.getPersona().getId());
            if (c != null) {
                snap.setClienteId(c.getId());
                snap.setClienteTipoAnterior(c.getTipo() != null ? c.getTipo().name() : null);
                snap.setClienteCreditoAnterior(c.getCredito() != null ? BigDecimal.valueOf(c.getCredito()) : null);
                snap.setClienteActivoAnterior(c.getActivo());
            }
        }
        return snap;
    }

    /** El usuario logueado, o null si la mutation corre sin contexto de seguridad. */
    private Usuario usuarioAutenticado() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) return null;
        return usuarioService.findByNickname(auth.getName()).orElse(null);
    }

    /** El egreso vigente del funcionario, si fue hecho desde que existe el historico. */
    public java.util.Optional<FuncionarioEgresoHistorico> egresoVigente(Long funcionarioId) {
        if (funcionarioId == null) return java.util.Optional.empty();
        return egresoHistoricoRepository.findFirstByFuncionarioIdAndRevertidoEnIsNullOrderByIdDesc(funcionarioId);
    }

    /**
     * Revierte un egreso: deshace lo que {@link #egresar} dejo, incluido el dano
     * colateral que egresar provoca y que no se ve en la pantalla de egreso.
     *
     * <p>Existe porque no habia ninguna forma de revertir un egreso desde la aplicacion.
     * El 2026-08-21 se egreso por error a una funcionaria en farmacia y hubo que resolverlo
     * el 22 con un UPDATE directo en produccion.</p>
     *
     * <p><b>Por que hace falta el parametro credito.</b> Egresar no solo apaga tres campos:
     * {@code FuncionarioService.save} pone {@code credito = 0} cuando activo llega en false,
     * y la cascada de estado hace lo mismo con el cliente ademas de pasarlo a NORMAL. Ese
     * crédito no queda guardado en ninguna tabla -- {@code funcionario_salario_historico}
     * guarda salario, no crédito -- asi que reactivar no puede recuperarlo solo: hay que
     * decirle cual era. En el caso real hubo que sacarlo de un backup de nueve dias antes.</p>
     *
     * <p><b>El orden importa.</b> {@code save()} pisa el credito a cero cuando activo viene
     * en false, asi que activo se setea en true ANTES de guardar. Y la cascada deja
     * {@code cliente.credito = 0}, por lo que el crédito del cliente se re-sincroniza
     * DESPUES del save -- esa sincronizacion vive en {@code FuncionarioGraphQL.saveFuncionario},
     * no en el servicio, asi que por este camino hay que hacerla a mano o el funcionario
     * queda con su crédito restaurado y el cliente en cero.</p>
     *
     * <p><b>De donde sale el credito.</b> Del snapshot que {@link #egresar} dejo en
     * {@code rrhh.funcionario_egreso_historico}. El parametro {@code credito} sigue
     * existiendo como override, y es el unico camino para los egresos hechos ANTES de que
     * existiera esa tabla, que no tienen snapshot del cual restaurar.</p>
     *
     * <p><b>El tipo del cliente tambien se restaura desde el snapshot.</b> La cascada lo
     * reactiva siempre como FUNCIONARIO, asi que sin esto revertir el egreso de un cliente
     * VIP lo degradaba en silencio.</p>
     */
    @Transactional
    public Funcionario revertirEgreso(Long funcionarioId, Float credito, String motivo) {
        Funcionario f = funcionarioService.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado"));

        if (Boolean.TRUE.equals(f.getActivo())) {
            throw new GraphQLException("El funcionario ya esta activo: no hay egreso que revertir.");
        }

        // Un finiquito vigente es la contabilidad del egreso. Revertir el egreso dejandolo
        // en pie deja al funcionario activo y con una liquidacion final que dice que se fue;
        // si ademas esta PAGADA, ya salio plata de la caja por su salida.
        for (LiquidacionFinal lf : liquidacionFinalRepository.findByFuncionarioIdOrderByCreadoEnDesc(funcionarioId)) {
            if (lf.getEstado() != null && lf.getEstado() != LiquidacionFinalEstado.ANULADA) {
                throw new GraphQLException("El funcionario tiene una liquidacion final "
                        + lf.getEstado() + " (#" + lf.getId() + "). Anulala antes de revertir el egreso.");
            }
        }

        LocalDateTime egresoPrevio = f.getFechaEgreso();
        String motivoPrevio = f.getMotivoEgreso();

        FuncionarioEgresoHistorico snap = egresoVigente(funcionarioId).orElse(null);
        // El override del cliente gana sobre el snapshot: entre el egreso y la reversa el
        // negocio pudo cambiar. Sin override, manda la foto; sin foto ni override, cero.
        Float creditoRestaurado = credito != null
                ? credito
                : (snap != null && snap.getCreditoAnterior() != null
                        ? snap.getCreditoAnterior().floatValue() : 0f);

        f.setFechaEgreso(null);
        f.setMotivoEgreso(null);
        f.setActivo(true);
        f.setCredito(creditoRestaurado);
        Funcionario guardado = funcionarioService.save(f);

        // La cascada reactivo al cliente y lo devolvio a FUNCIONARIO, pero le dejo el
        // credito en cero y le pudo haber cambiado el tipo.
        if (guardado.getPersona() != null && guardado.getPersona().getId() != null) {
            Cliente cliente = clienteService.findByPersonaId(guardado.getPersona().getId());
            if (cliente != null) {
                boolean cambio = false;
                BigDecimal cliCred = snap != null && snap.getClienteCreditoAnterior() != null
                        ? snap.getClienteCreditoAnterior() : null;
                Float clienteCredito = credito != null
                        ? credito
                        : (cliCred != null ? cliCred.floatValue() : creditoRestaurado);
                if (!java.util.Objects.equals(cliente.getCredito(), clienteCredito)) {
                    cliente.setCredito(clienteCredito);
                    cambio = true;
                }
                // Tipo real desde el snapshot. La cascada ya lo puso en FUNCIONARIO; si
                // antes era VIP, dejarlo asi le saca la categoria sin que nadie lo pida.
                if (snap != null && snap.getClienteTipoAnterior() != null) {
                    try {
                        com.franco.dev.domain.personas.enums.TipoCliente tipoPrevio =
                                com.franco.dev.domain.personas.enums.TipoCliente.valueOf(snap.getClienteTipoAnterior());
                        if (cliente.getTipo() != tipoPrevio) {
                            cliente.setTipo(tipoPrevio);
                            cambio = true;
                        }
                    } catch (IllegalArgumentException e) {
                        // Tipo que ya no existe en el enum: se deja lo que puso la cascada.
                        log.warn("Tipo de cliente desconocido en el snapshot de egreso: {}", snap.getClienteTipoAnterior());
                    }
                }
                if (cambio) clienteService.save(cliente);
            }
        }

        if (snap != null) {
            snap.setRevertidoEn(LocalDateTime.now());
            snap.setRevertidoPor(usuarioAutenticado());
            snap.setMotivoReversion(motivo);
            egresoHistoricoRepository.save(snap);
        }

        log.info("Egreso revertido: funcionario={} egresoPrevio={} motivoPrevio={} creditoRestaurado={} origen={} motivoReversa={}",
                funcionarioId, egresoPrevio, motivoPrevio, creditoRestaurado,
                credito != null ? "OVERRIDE" : (snap != null ? "SNAPSHOT" : "SIN_SNAPSHOT"), motivo);
        return guardado;
    }
}
