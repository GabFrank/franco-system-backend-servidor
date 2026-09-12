package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import com.franco.dev.service.personas.event.FuncionarioInactivadoEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Cuando a un funcionario lo dan de baja, lo que no alcanzo a cobrar deja de corresponderle:
 * se anulan sus bonos pendientes y se apaga la plantilla que los repite.
 *
 * <p>Decision de negocio (issue #276): el finiquito NO paga bonos. Sin esto el bono del mes
 * queda con {@code liquidacion_id} nulo para siempre -- no lo cobra nadie, porque la
 * generacion masiva saltea a los inactivos, pero figura como pendiente en la grilla.</p>
 *
 * <p><b>Por que un listener y no una llamada dentro de egresar():</b> el camino que RRHH usa
 * a diario es el pago del finiquito, que marca {@code activo=false} llamando derecho a
 * {@code funcionarioService.save()} sin pasar por {@code egresar()}
 * ({@code LiquidacionFinalService:623} y {@code :725}). Colgado del evento de transicion, el
 * comportamiento cubre esos caminos y los que se agreguen despues. Ver issue #295.</p>
 *
 * <p><b>Por que {@code @EventListener} y no {@code @TransactionalEventListener}:</b> los
 * listeners de {@code fmc/listener} usan AFTER_COMMIT porque mandan notificaciones, que no
 * deben salir si la transaccion revierte. Aca es al reves: esto es parte de la baja, y si el
 * pago del finiquito falla y revierte, los bonos no pueden quedar anulados. Sincrono y en la
 * misma transaccion.</p>
 */
@Component
@lombok.extern.slf4j.Slf4j
public class BonosAlInactivarFuncionarioListener {

    private final BonoRepository bonoRepository;
    private final BonoRecurrenteRepository bonoRecurrenteRepository;

    public BonosAlInactivarFuncionarioListener(BonoRepository bonoRepository,
                                               BonoRecurrenteRepository bonoRecurrenteRepository) {
        this.bonoRepository = bonoRepository;
        this.bonoRecurrenteRepository = bonoRecurrenteRepository;
    }

    /** Un bono ya liquidado no se toca: es plata que se pago. */
    @EventListener
    public void onFuncionarioInactivado(FuncionarioInactivadoEvent evento) {
        Long funcionarioId = evento.getFuncionarioId();
        if (funcionarioId == null) return;

        int bonos = 0;
        for (Bono b : bonoRepository.findByFuncionarioIdOrderByFechaDesc(funcionarioId)) {
            if (Boolean.TRUE.equals(b.getAnulado())) continue;
            if (b.getLiquidacionId() != null) continue;
            b.setAnulado(true);
            bonoRepository.save(b);
            bonos++;
        }
        int plantillas = 0;
        for (BonoRecurrente p : bonoRecurrenteRepository.findByFuncionarioIdAndActivoTrue(funcionarioId)) {
            p.setActivo(false);
            bonoRecurrenteRepository.save(p);
            plantillas++;
        }
        if (bonos > 0 || plantillas > 0) {
            log.info("Baja de funcionario={}: {} bono/s pendiente/s anulado/s, {} plantilla/s recurrente/s apagada/s",
                    funcionarioId, bonos, plantillas);
        }
    }
}
