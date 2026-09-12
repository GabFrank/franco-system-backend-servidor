package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
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
    private final LiquidacionItemRepository liquidacionItemRepository;

    public BonoService(BonoRepository repository, BonoRecurrenteRepository plantillaRepository,
                        LiquidacionItemRepository liquidacionItemRepository) {
        this.repository = repository;
        this.plantillaRepository = plantillaRepository;
        this.liquidacionItemRepository = liquidacionItemRepository;
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
        // Un bono nuevo para alguien que ya se fue no lo cobra nadie: el finiquito no paga
        // bonos y la generacion masiva saltea a los inactivos, asi que quedaria pendiente
        // para siempre. Solo el alta: editar o anular uno viejo tiene que seguir siendo
        // posible, es la unica forma de corregir uno mal cargado. Ver issue #296.
        if (entity.getId() == null && entity.getFuncionario() != null
                && Boolean.FALSE.equals(entity.getFuncionario().getActivo())) {
            throw new GraphQLException("El funcionario esta inactivo: no se le puede cargar un bono nuevo."
                    + " Un bono de alguien que ya egreso no lo paga el finiquito ni la liquidacion mensual.");
        }

        if (entity.getId() != null) {
            Bono previo = repository.findById(entity.getId()).orElse(null);
            String motivo = motivoNoEditable(previo);
            if (motivo != null) {
                throw new GraphQLException(motivo);
            }
            if (previo != null && previo.getPeriodo() != null && entity.getFecha() != null) {
                YearMonth periodoPrevio = YearMonth.parse(previo.getPeriodo());
                if (!periodoPrevio.equals(YearMonth.from(entity.getFecha()))) {
                    throw new GraphQLException("Un bono recurrente no puede cambiar de mes.");
                }
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

    /**
     * Motivo por el que este bono no se puede editar, o null si se puede.
     *
     * Es la unica fuente de verdad: la usa saveConRecurrencia para rechazar, y
     * BonoResolver para que la pantalla abra el dialogo en solo lectura en vez de
     * ofrecer una accion que el backend va a rechazar.
     */
    public String motivoNoEditable(Bono b) {
        if (b == null || b.getId() == null) return null;

        if (b.getLiquidacionId() != null) {
            return "Este bono ya fue liquidado y pagado.";
        }

        if (liquidacionItemRepository.existeEnLiquidacionCerrada(b.getId())) {
            return "Este bono esta incluido en una liquidacion ya aprobada.";
        }

        YearMonth mes = null;
        if (b.getPeriodo() != null) {
            mes = YearMonth.parse(b.getPeriodo());
        } else if (b.getFecha() != null) {
            mes = YearMonth.from(b.getFecha());
        }
        if (mes != null && mes.isBefore(YearMonth.now())) {
            return "Este bono es de un periodo anterior. Solo se edita el bono del mes corriente.";
        }

        return null;
    }
}
