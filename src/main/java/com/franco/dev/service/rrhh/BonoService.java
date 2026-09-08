package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
public class BonoService extends CrudService<Bono, BonoRepository, Long> {

    private final BonoRepository repository;
    private final BonoRecurrenteRepository plantillaRepository;

    public BonoService(BonoRepository repository, BonoRecurrenteRepository plantillaRepository) {
        this.repository = repository;
        this.plantillaRepository = plantillaRepository;
    }

    @Override
    public BonoRepository getRepository() {
        return repository;
    }

    public List<Bono> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByFechaDesc(funcionarioId);
    }

    public Page<Bono> findPage(Long funcionarioId, BonoTipo tipo, LocalDate desde, LocalDate hasta,
                               Pageable pageable) {
        return repository.findPage(funcionarioId, tipo, desde, hasta, pageable);
    }

    @Transactional
    public Bono anular(Long id) {
        Optional<Bono> opt = repository.findById(id);
        if (opt.isEmpty()) throw new GraphQLException("Bono no encontrado");
        Bono b = opt.get();
        b.setAnulado(true);
        return repository.save(b);
    }

    @Override
    public Bono save(Bono entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getAnulado() == null) entity.setAnulado(false);
        if (entity.getEsRecurrente() == null) entity.setEsRecurrente(false);
        if (entity.getMonto() == null) entity.setMonto(BigDecimal.ZERO);
        if (entity.getMotivo() != null) entity.setMotivo(entity.getMotivo().toUpperCase());
        return super.save(entity);
    }

    /**
     * Guarda el bono y sincroniza su plantilla recurrente segun el toggle del dialogo.
     *
     * Un formulario, una transaccion: el bono y la regla que lo repite se escriben
     * juntos o no se escribe ninguno. Editar el monto cambia ambos, por lo que vale
     * para este mes y los siguientes; los meses anteriores son filas propias que el
     * job nunca revisita.
     */
    @Transactional
    public Bono saveConRecurrencia(Bono entity, Boolean esRecurrente, BonoFrecuencia frecuencia) {
        if (entity.getId() != null) {
            Bono previo = repository.findById(entity.getId()).orElse(null);
            if (previo != null && previo.getLiquidacionId() != null) {
                throw new GraphQLException("No se puede editar un bono ya liquidado");
            }
        }

        if (entity.getMotivo() != null) entity.setMotivo(entity.getMotivo().toUpperCase());

        boolean recurrente = Boolean.TRUE.equals(esRecurrente);
        BonoFrecuencia freq = frecuencia != null ? frecuencia : BonoFrecuencia.MENSUAL;
        entity.setEsRecurrente(recurrente);
        entity.setFrecuencia(recurrente ? freq : null);

        if (recurrente) {
            BonoRecurrente p = entity.getBonoRecurrenteId() != null
                    ? plantillaRepository.findById(entity.getBonoRecurrenteId()).orElse(null)
                    : null;
            if (p == null) p = new BonoRecurrente();
            p.setFuncionario(entity.getFuncionario());
            p.setTipo(entity.getTipo());
            p.setMonto(entity.getMonto());
            p.setMotivo(entity.getMotivo());
            p.setFrecuencia(freq);
            p.setActivo(true);
            p.setUsuario(entity.getUsuario());
            p.setAutorizadoPor(entity.getAutorizadoPor());
            p = plantillaRepository.save(p);

            entity.setBonoRecurrenteId(p.getId());
            // El periodo ancla la idempotencia del job: con el seteado, la corrida
            // del dia 1 no vuelve a generar el mes que este bono ya cubre.
            if (entity.getPeriodo() == null && entity.getFecha() != null) {
                entity.setPeriodo(YearMonth.from(entity.getFecha()).toString());
            }
        } else if (entity.getBonoRecurrenteId() != null) {
            // Se apaga la regla, pero el bono del mes se queda: es plata ya devengada.
            plantillaRepository.findById(entity.getBonoRecurrenteId()).ifPresent(p -> {
                p.setActivo(false);
                plantillaRepository.save(p);
            });
        }

        return save(entity);
    }
}
