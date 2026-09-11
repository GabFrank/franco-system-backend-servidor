package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import com.franco.dev.service.CrudService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Genera los bonos de las plantillas recurrentes.
 *
 * El bono generado es una fila normal de rrhh.bono: entra a la liquidacion por
 * el mismo camino que un bono cargado a mano (LiquidacionSueldoService filtra
 * por fecha dentro del periodo y liquidacionId nulo), asi que ese service no se
 * toca.
 */
@Service
public class BonoRecurrenteService extends CrudService<BonoRecurrente, BonoRecurrenteRepository, Long> {

    private final BonoRecurrenteRepository repository;
    private final BonoRepository bonoRepository;

    public BonoRecurrenteService(BonoRecurrenteRepository repository, BonoRepository bonoRepository) {
        this.repository = repository;
        this.bonoRepository = bonoRepository;
    }

    @Override
    public BonoRecurrenteRepository getRepository() {
        return repository;
    }

    public Page<BonoRecurrente> findPage(Long funcionarioId, Boolean activo, Pageable pageable) {
        return repository.findPage(funcionarioId, activo, pageable);
    }

    /**
     * Ids de las plantillas candidatas. Devuelve ids y no entidades a proposito:
     * el scheduler itera esta lista y llama a generarUno() una vez por plantilla,
     * de modo que cada generacion tenga su propia transaccion.
     */
    public List<Long> plantillasActivas() {
        return repository.idsActivos();
    }

    /**
     * Genera (o no) el bono de UNA plantilla para UN periodo.
     *
     * Es idempotente: si el bono de ese periodo ya existe devuelve vacio sin
     * escribir. Ademas del chequeo, el indice unico parcial
     * uq_bono_recurrente_periodo impide el duplicado si dos procesos corren a la vez.
     */
    @Transactional
    public Optional<Bono> generarUno(Long plantillaId, YearMonth periodo) {
        if (plantillaId == null || periodo == null) return Optional.empty();

        Optional<BonoRecurrente> opt = repository.findById(plantillaId);
        if (opt.isEmpty()) return Optional.empty();
        BonoRecurrente p = opt.get();

        if (!Boolean.TRUE.equals(p.getActivo())) return Optional.empty();
        if (p.getFrecuencia() != BonoFrecuencia.MENSUAL) return Optional.empty();

        Funcionario f = p.getFuncionario();
        if (f == null) return Optional.empty();
        LocalDate primerDia = periodo.atDay(1);
        if (!Boolean.TRUE.equals(f.getActivo())) return Optional.empty();
        // Defensivo: cubre la fila que llega con egreso cargado y activo sin actualizar.
        if (f.getFechaEgreso() != null && !f.getFechaEgreso().toLocalDate().isAfter(primerDia)) {
            return Optional.empty();
        }

        String clavePeriodo = periodo.toString(); // "2026-03"
        if (bonoRepository.existsByBonoRecurrenteIdAndPeriodo(p.getId(), clavePeriodo)) {
            return Optional.empty();
        }

        Bono b = new Bono();
        b.setFuncionario(f);
        b.setTipo(p.getTipo());
        b.setMonto(p.getMonto() != null ? p.getMonto() : BigDecimal.ZERO);
        b.setFecha(primerDia);
        b.setMotivo(p.getMotivo());
        b.setEsRecurrente(true);
        b.setFrecuencia(BonoFrecuencia.MENSUAL);
        b.setAnulado(false);
        b.setLiquidacionId(null);
        b.setBonoRecurrenteId(p.getId());
        b.setPeriodo(clavePeriodo);
        b.setUsuario(p.getUsuario());
        b.setAutorizadoPor(p.getAutorizadoPor());

        return Optional.of(bonoRepository.save(b));
    }

    @Override
    public BonoRecurrente save(BonoRecurrente entity) {
        if (entity.getActivo() == null) entity.setActivo(true);
        if (entity.getFrecuencia() == null) entity.setFrecuencia(BonoFrecuencia.MENSUAL);
        if (entity.getMonto() == null) entity.setMonto(BigDecimal.ZERO);
        if (entity.getMotivo() != null) entity.setMotivo(entity.getMotivo().toUpperCase());
        return super.save(entity);
    }
}
