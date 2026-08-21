package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.HoraExtra;
import com.franco.dev.domain.rrhh.enums.HoraExtraTipo;
import com.franco.dev.repository.rrhh.HoraExtraRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.rrhh.builder.HoraExtraCalculator;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class HoraExtraService extends CrudService<HoraExtra, HoraExtraRepository, Long> {

    private final HoraExtraRepository repository;
    private final ConfiguracionRrhhService configuracionRrhhService;

    @Override
    public HoraExtraRepository getRepository() {
        return repository;
    }

    public List<HoraExtra> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByFechaDesc(funcionarioId);
    }

    public List<HoraExtra> findByFuncionarioIdAndFechaBetween(Long funcionarioId, LocalDate desde, LocalDate hasta) {
        return repository.findByFuncionarioIdAndFechaBetweenAndAnuladaFalse(funcionarioId, desde, hasta);
    }

    public List<HoraExtra> findByJornada(Long jornadaId, Long sucursalId) {
        return repository.findByJornadaIdAndSucursalId(jornadaId, sucursalId);
    }

    public Page<HoraExtra> findPage(Long funcionarioId, LocalDate desde, LocalDate hasta, HoraExtraTipo tipo,
                                    Pageable pageable) {
        return repository.findPage(funcionarioId, desde, hasta, tipo, pageable);
    }

    @Transactional
    public HoraExtra anular(Long id) {
        Optional<HoraExtra> opt = repository.findById(id);
        if (opt.isEmpty()) return null;
        HoraExtra e = opt.get();
        e.setAnulada(true);
        return repository.save(e);
    }

    @Override
    public HoraExtra save(HoraExtra entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getAnulada() == null) entity.setAnulada(false);
        if (entity.getMinutos() == null) entity.setMinutos(BigDecimal.ZERO);

        // valorizar automáticamente si no vino un monto manual y hay datos suficientes
        boolean sinMonto = entity.getMontoCalculado() == null || entity.getMontoCalculado().signum() == 0;
        if (sinMonto && entity.getFuncionario() != null && entity.getFuncionario().getSueldo() != null
                && entity.getMinutos().signum() > 0) {
            BigDecimal sueldo = entity.getFuncionario().getSueldo();
            BigDecimal horasJornada = configuracionRrhhService.getNumber("HORAS_JORNADA_LABORAL", new BigDecimal("8"));
            BigDecimal diasMes = configuracionRrhhService.getNumber("DIAS_MES_PROMEDIO", new BigDecimal("30"));
            BigDecimal recargo = entity.getRecargoPorcentaje() != null
                    ? entity.getRecargoPorcentaje() : recargoPorTipo(entity.getTipo());
            entity.setRecargoPorcentaje(recargo);
            entity.setMontoCalculado(HoraExtraCalculator.calcularMonto(sueldo, horasJornada, entity.getMinutos(), recargo, diasMes));
        }
        if (entity.getMontoCalculado() == null) entity.setMontoCalculado(BigDecimal.ZERO);
        if (entity.getObservacion() != null) entity.setObservacion(entity.getObservacion().toUpperCase());
        return super.save(entity);
    }

    /** Recargo por defecto según el tipo de hora extra (parametrizable en config RRHH). */
    private BigDecimal recargoPorTipo(HoraExtraTipo tipo) {
        if (tipo == HoraExtraTipo.NOCTURNA)
            return configuracionRrhhService.getNumber("RECARGO_HE_NOCTURNA", new BigDecimal("100"));
        if (tipo == HoraExtraTipo.FERIADO)
            return configuracionRrhhService.getNumber("RECARGO_HE_FERIADO", new BigDecimal("100"));
        return configuracionRrhhService.getNumber("RECARGO_HE_DIURNA", new BigDecimal("50"));
    }
}
