package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.MovimientoPersonas;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.TipoMovimientoPersonas;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.rrhh.ValeRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.financiero.MovimientoPersonasService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class ValeService extends CrudService<Vale, ValeRepository, Long> {

    private final ValeRepository repository;
    private final CajaVirtualService cajaVirtualService;
    private final MovimientoCajaVirtualService movimientoCajaVirtualService;
    private final MovimientoPersonasService movimientoPersonasService;
    private final UsuarioService usuarioService;

    @Override
    public ValeRepository getRepository() {
        return repository;
    }

    public List<Vale> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByFechaDesc(funcionarioId);
    }

    public List<Vale> findByEstado(ValeEstado estado) {
        return repository.findByEstadoOrderByFechaDesc(estado);
    }

    @Override
    public Vale save(Vale entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getEstado() == null) entity.setEstado(ValeEstado.SOLICITADO);
        if (entity.getEsAdelanto() == null) entity.setEsAdelanto(false);
        if (entity.getObservacion() != null) entity.setObservacion(entity.getObservacion().toUpperCase());
        return super.save(entity);
    }

    /**
     * Confirma un vale SOLICITADO: registra el egreso real en la Caja Mayor
     * (MovimientoCajaVirtual EGRESO) y el asiento en la cuenta corriente del
     * empleado (MovimientoPersonas ANTICIPO), de forma atomica.
     */
    @Transactional
    public Vale confirmar(Long valeId, Long cajaVirtualId, Long autorizadoPorId) {
        Vale vale = repository.findById(valeId)
                .orElseThrow(() -> new GraphQLException("Vale no encontrado"));
        if (vale.getEstado() != ValeEstado.SOLICITADO) {
            throw new GraphQLException("Solo se puede confirmar un vale en estado SOLICITADO");
        }
        registrarEgresoCaja(vale, cajaVirtualId);
        registrarAnticipoPersona(vale);

        vale.setEstado(ValeEstado.CONFIRMADO);
        vale.setCajaVirtualId(cajaVirtualId);
        if (autorizadoPorId != null) {
            vale.setAutorizadoPor(usuarioService.findById(autorizadoPorId).orElse(null));
        }
        return repository.save(vale);
    }

    /**
     * Crea un vale ya CONFIRMADO en una sola transaccion (atajo desde Caja Mayor).
     */
    @Transactional
    public Vale crearConfirmado(Vale vale, Long cajaVirtualId, Long autorizadoPorId) {
        if (vale.getEstado() == null) vale.setEstado(ValeEstado.SOLICITADO);
        if (vale.getEsAdelanto() == null) vale.setEsAdelanto(false);
        if (vale.getCreadoEn() == null) vale.setCreadoEn(LocalDateTime.now());
        vale = repository.save(vale);
        return confirmar(vale.getId(), cajaVirtualId, autorizadoPorId);
    }

    /**
     * Anula un vale. Si estaba CONFIRMADO, genera un contra-asiento AJUSTE en
     * la caja (nunca borra el original) y desactiva el MovimientoPersonas.
     */
    @Transactional
    public Vale anular(Long valeId) {
        Vale vale = repository.findById(valeId)
                .orElseThrow(() -> new GraphQLException("Vale no encontrado"));
        if (vale.getEstado() == ValeEstado.ANULADO) {
            return vale;
        }
        if (vale.getEstado() == ValeEstado.DESCONTADO) {
            throw new GraphQLException("No se puede anular un vale ya descontado en liquidacion");
        }
        if (vale.getEstado() == ValeEstado.CONFIRMADO) {
            revertirEgresoCaja(vale);
            desactivarMovimientoPersona(vale);
        }
        vale.setEstado(ValeEstado.ANULADO);
        return repository.save(vale);
    }

    // ---- helpers ----

    private void registrarEgresoCaja(Vale vale, Long cajaVirtualId) {
        CajaVirtual caja = cajaVirtualService.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja Mayor no encontrada"));
        Moneda moneda = vale.getMoneda();
        MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
        mov.setCajaVirtual(caja);
        mov.setTipoMovimiento(CajaVirtualTipoMovimiento.EGRESO);
        mov.setCantidad(vale.getMonto() != null ? vale.getMonto().doubleValue() : 0.0);
        mov.setMoneda(moneda);
        mov.setReferenciaId(vale.getId());
        mov.setDescripcion("VALE #" + vale.getId() + nombreFuncionario(vale.getFuncionario()));
        mov.setUsuario(vale.getUsuario());
        mov.setActivo(true);
        mov = movimientoCajaVirtualService.registrarMovimiento(mov);
        vale.setMovimientoCajaVirtualId(mov.getId());
    }

    private void revertirEgresoCaja(Vale vale) {
        if (vale.getCajaVirtualId() == null) return;
        CajaVirtual caja = cajaVirtualService.findById(vale.getCajaVirtualId())
                .orElseThrow(() -> new GraphQLException("Caja Mayor no encontrada"));
        MovimientoCajaVirtual rev = new MovimientoCajaVirtual();
        rev.setCajaVirtual(caja);
        rev.setTipoMovimiento(CajaVirtualTipoMovimiento.AJUSTE);
        rev.setCantidad(vale.getMonto() != null ? vale.getMonto().doubleValue() : 0.0);
        rev.setMoneda(vale.getMoneda());
        rev.setReferenciaId(vale.getId());
        rev.setDescripcion("ANULACION VALE #" + vale.getId());
        rev.setUsuario(vale.getUsuario());
        rev.setActivo(true);
        movimientoCajaVirtualService.registrarMovimiento(rev);
    }

    private void registrarAnticipoPersona(Vale vale) {
        Funcionario f = vale.getFuncionario();
        if (f == null || f.getPersona() == null) return;
        MovimientoPersonas mp = new MovimientoPersonas();
        mp.setPersona(f.getPersona());
        // Un vale (sea adelanto de sueldo o no) es siempre un ANTICIPO en la cuenta
        // corriente del empleado; el enum no distingue subtipos.
        mp.setTipo(TipoMovimientoPersonas.ANTICIPO);
        mp.setReferenciaId(vale.getId());
        mp.setValorTotal(vale.getMonto() != null ? vale.getMonto().doubleValue() : 0.0);
        mp.setActivo(true);
        mp.setObservacion("VALE #" + vale.getId() + nombreFuncionario(f));
        mp.setUsuario(vale.getUsuario());
        mp = movimientoPersonasService.save(mp);
        vale.setMovimientoPersonaId(mp.getId());
    }

    private void desactivarMovimientoPersona(Vale vale) {
        if (vale.getMovimientoPersonaId() == null) return;
        Optional<MovimientoPersonas> opt = movimientoPersonasService.findById(vale.getMovimientoPersonaId());
        if (opt.isPresent()) {
            MovimientoPersonas mp = opt.get();
            mp.setActivo(false);
            movimientoPersonasService.save(mp);
        }
    }

    /** Sufijo " - NOMBRE" para las descripciones de los movimientos (vacio si no hay). */
    private static String nombreFuncionario(Funcionario f) {
        return (f != null && f.getPersona() != null && f.getPersona().getNombre() != null)
                ? " - " + f.getPersona().getNombre() : "";
    }
}
