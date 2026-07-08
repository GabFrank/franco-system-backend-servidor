package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.MovimientoPersonas;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.TipoMovimientoPersonas;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Prestamo;
import com.franco.dev.domain.rrhh.PrestamoCuota;
import com.franco.dev.domain.rrhh.enums.PrestamoCuotaEstado;
import com.franco.dev.domain.rrhh.enums.PrestamoEstado;
import com.franco.dev.repository.rrhh.PrestamoCuotaRepository;
import com.franco.dev.repository.rrhh.PrestamoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.financiero.MovimientoPersonasService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class PrestamoService extends CrudService<Prestamo, PrestamoRepository, Long> {

    private final PrestamoRepository repository;
    private final PrestamoCuotaRepository cuotaRepository;
    private final CajaVirtualService cajaVirtualService;
    private final MovimientoCajaVirtualService movimientoCajaVirtualService;
    private final MovimientoPersonasService movimientoPersonasService;

    @Override
    public PrestamoRepository getRepository() {
        return repository;
    }

    public List<Prestamo> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByFechaInicioDesc(funcionarioId);
    }

    public List<Prestamo> findByEstado(PrestamoEstado estado) {
        return repository.findByEstadoOrderByFechaInicioDesc(estado);
    }

    public List<PrestamoCuota> findCuotas(Long prestamoId) {
        return cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamoId);
    }

    /**
     * Crea el prestamo con sus N cuotas mensuales y desembolsa el monto:
     * EGRESO en la Caja Mayor + MovimientoPersonas(PRESTAMO). Atomico.
     */
    @Transactional
    public Prestamo crearConDesembolso(Prestamo prestamo, Long cajaVirtualId) {
        if (prestamo.getCantidadCuotas() == null || prestamo.getCantidadCuotas() < 1) {
            throw new GraphQLException("La cantidad de cuotas debe ser al menos 1");
        }
        if (prestamo.getMontoTotal() == null || prestamo.getMontoTotal().signum() <= 0) {
            throw new GraphQLException("El monto total debe ser positivo");
        }
        prestamo.setEstado(PrestamoEstado.ACTIVO);
        prestamo.setMontoPagado(BigDecimal.ZERO);
        if (prestamo.getFechaInicio() == null) prestamo.setFechaInicio(LocalDate.now());
        if (prestamo.getCreadoEn() == null) prestamo.setCreadoEn(LocalDateTime.now());
        if (prestamo.getDescripcion() != null) prestamo.setDescripcion(prestamo.getDescripcion().toUpperCase());
        prestamo = repository.save(prestamo);

        generarCuotas(prestamo);
        desembolsar(prestamo, cajaVirtualId);

        return repository.save(prestamo);
    }

    private void generarCuotas(Prestamo prestamo) {
        int n = prestamo.getCantidadCuotas();
        BigDecimal total = prestamo.getMontoTotal();
        BigDecimal cuotaBase = total.divide(new BigDecimal(n), 2, RoundingMode.HALF_UP);
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i = 1; i <= n; i++) {
            PrestamoCuota c = new PrestamoCuota();
            c.setPrestamo(prestamo);
            c.setNumero(i);
            c.setFechaVencimiento(prestamo.getFechaInicio().plusMonths(i));
            // la ultima cuota absorbe el redondeo
            BigDecimal monto = (i == n) ? total.subtract(acumulado) : cuotaBase;
            acumulado = acumulado.add(monto);
            c.setMonto(monto);
            c.setMontoPagado(BigDecimal.ZERO);
            c.setEstado(PrestamoCuotaEstado.PENDIENTE);
            c.setCreadoEn(LocalDateTime.now());
            cuotaRepository.save(c);
        }
    }

    private void desembolsar(Prestamo prestamo, Long cajaVirtualId) {
        CajaVirtual caja = cajaVirtualService.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja Mayor no encontrada"));
        MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
        mov.setCajaVirtual(caja);
        mov.setTipoMovimiento(CajaVirtualTipoMovimiento.EGRESO);
        mov.setCantidad(prestamo.getMontoTotal().doubleValue());
        mov.setMoneda(prestamo.getMoneda());
        mov.setReferenciaId(prestamo.getId());
        mov.setDescripcion("DESEMBOLSO PRESTAMO FUNCIONARIO #" + prestamo.getId());
        mov.setUsuario(prestamo.getUsuario());
        mov.setActivo(true);
        mov = movimientoCajaVirtualService.registrarMovimiento(mov);
        prestamo.setCajaVirtualId(cajaVirtualId);
        prestamo.setMovimientoCajaVirtualId(mov.getId());

        Funcionario f = prestamo.getFuncionario();
        if (f != null && f.getPersona() != null) {
            MovimientoPersonas mp = new MovimientoPersonas();
            mp.setPersona(f.getPersona());
            mp.setTipo(TipoMovimientoPersonas.PRESTAMO);
            mp.setReferenciaId(prestamo.getId());
            mp.setValorTotal(prestamo.getMontoTotal().doubleValue());
            mp.setActivo(true);
            mp.setObservacion("PRESTAMO FUNCIONARIO #" + prestamo.getId());
            mp.setUsuario(prestamo.getUsuario());
            mp = movimientoPersonasService.save(mp);
            prestamo.setMovimientoPersonaId(mp.getId());
        }
    }

    /**
     * Cobra (total o parcialmente) una cuota directamente por caja:
     * INGRESO en la Caja Mayor. Actualiza estado de cuota y prestamo.
     */
    @Transactional
    public PrestamoCuota cobrarCuota(Long cuotaId, Long cajaVirtualId, BigDecimal montoPago) {
        PrestamoCuota cuota = cuotaRepository.findById(cuotaId)
                .orElseThrow(() -> new GraphQLException("Cuota no encontrada"));
        if (cuota.getEstado() == PrestamoCuotaEstado.PAGADA) {
            throw new GraphQLException("La cuota ya esta pagada");
        }
        Prestamo prestamo = cuota.getPrestamo();
        BigDecimal pendienteCuota = cuota.getMonto().subtract(
                cuota.getMontoPagado() != null ? cuota.getMontoPagado() : BigDecimal.ZERO);
        BigDecimal pago = (montoPago != null && montoPago.signum() > 0 && montoPago.compareTo(pendienteCuota) < 0)
                ? montoPago : pendienteCuota;

        CajaVirtual caja = cajaVirtualService.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja Mayor no encontrada"));
        MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
        mov.setCajaVirtual(caja);
        mov.setTipoMovimiento(CajaVirtualTipoMovimiento.INGRESO);
        mov.setCantidad(pago.doubleValue());
        mov.setMoneda(prestamo.getMoneda());
        mov.setReferenciaId(prestamo.getId());
        mov.setDescripcion("COBRO CUOTA #" + cuota.getNumero() + " PRESTAMO #" + prestamo.getId());
        mov.setUsuario(prestamo.getUsuario());
        mov.setActivo(true);
        movimientoCajaVirtualService.registrarMovimiento(mov);

        cuota.setMontoPagado((cuota.getMontoPagado() != null ? cuota.getMontoPagado() : BigDecimal.ZERO).add(pago));
        cuota.setEstado(cuota.getMontoPagado().compareTo(cuota.getMonto()) >= 0
                ? PrestamoCuotaEstado.PAGADA : PrestamoCuotaEstado.PARCIAL);
        if (cuota.getEstado() == PrestamoCuotaEstado.PAGADA) cuota.setFechaPago(LocalDate.now());
        cuota = cuotaRepository.save(cuota);

        prestamo.setMontoPagado((prestamo.getMontoPagado() != null ? prestamo.getMontoPagado() : BigDecimal.ZERO).add(pago));
        if (prestamo.getMontoPagado().compareTo(prestamo.getMontoTotal()) >= 0) {
            prestamo.setEstado(PrestamoEstado.PAGADO);
        }
        repository.save(prestamo);
        return cuota;
    }

    /** Marca como VENCIDA las cuotas PENDIENTE/PARCIAL cuya fecha de vencimiento ya paso. */
    @Transactional
    public int marcarVencidas(LocalDate fecha) {
        int n = 0;
        List<PrestamoCuota> pend = cuotaRepository.findByEstadoAndFechaVencimientoBefore(PrestamoCuotaEstado.PENDIENTE, fecha);
        List<PrestamoCuota> parc = cuotaRepository.findByEstadoAndFechaVencimientoBefore(PrestamoCuotaEstado.PARCIAL, fecha);
        for (PrestamoCuota c : pend) { c.setEstado(PrestamoCuotaEstado.VENCIDA); cuotaRepository.save(c); n++; }
        for (PrestamoCuota c : parc) { c.setEstado(PrestamoCuotaEstado.VENCIDA); cuotaRepository.save(c); n++; }
        return n;
    }

    @Override
    public Prestamo save(Prestamo entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getEstado() == null) entity.setEstado(PrestamoEstado.ACTIVO);
        if (entity.getMontoPagado() == null) entity.setMontoPagado(BigDecimal.ZERO);
        return super.save(entity);
    }

    public Optional<PrestamoCuota> findCuotaById(Long id) {
        return cuotaRepository.findById(id);
    }
}
