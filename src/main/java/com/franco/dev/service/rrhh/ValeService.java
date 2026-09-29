package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.ValeCuota;
import com.franco.dev.domain.rrhh.enums.ValeCuotaEstado;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import com.franco.dev.repository.rrhh.ValeCuotaRepository;
import com.franco.dev.repository.rrhh.ValeRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.builder.CuotaCalculator;
import graphql.GraphQLException;
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
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ValeService extends CrudService<Vale, ValeRepository, Long> {

    /** Vales de un funcionario, del mas reciente al mas antiguo, paginados. */
    public java.util.List<com.franco.dev.domain.rrhh.Vale> findByFuncionarioId(
            Long funcionarioId, Integer page, Integer size) {
        int pagina = page != null && page >= 0 ? page : 0;
        int tamano = size != null && size > 0 ? size : 10;
        return repository.findByFuncionarioIdOrderByFechaDescIdDesc(
                funcionarioId, org.springframework.data.domain.PageRequest.of(pagina, tamano));
    }


    private final ValeRepository repository;
    private final CajaVirtualService cajaVirtualService;
    private final MovimientoCajaVirtualService movimientoCajaVirtualService;
    private final UsuarioService usuarioService;
    private final com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository pagoSolicitudDetalleRepository;
    private final ValeCuotaRepository cuotaRepository;
    private final LiquidacionItemRepository liquidacionItemRepository;
    private final LiquidacionFinalItemRepository liquidacionFinalItemRepository;

    /** referenciaTipo de los items de liquidacion/finiquito que descuentan una cuota de vale. */
    public static final String REFERENCIA_CUOTA = "VALE_CUOTA";
    public static final int MAX_CUOTAS = 12;

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

    public Page<Vale> findPage(Long funcionarioId, ValeEstado estado, LocalDate desde, LocalDate hasta,
                               Pageable pageable) {
        return repository.findPage(funcionarioId, estado, desde, hasta, pageable);
    }

    public List<ValeCuota> findCuotas(Long valeId) {
        return cuotaRepository.findByValeIdOrderByNumeroAsc(valeId);
    }

    @Override
    @Transactional
    public Vale save(Vale entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getEstado() == null) entity.setEstado(ValeEstado.SOLICITADO);
        if (entity.getEsAdelanto() == null) entity.setEsAdelanto(false);
        if (entity.getCantidadCuotas() == null) entity.setCantidadCuotas(1);
        if (entity.getEnEspecie() == null) entity.setEnEspecie(false);
        if (entity.getObservacion() != null) entity.setObservacion(entity.getObservacion().toUpperCase());
        validarCuotas(entity);
        Vale guardado = super.save(entity);
        sincronizarCuotas(guardado);
        return guardado;
    }

    /**
     * Crea un vale entregado en bienes (ej. uniforme): nace CONFIRMADO, sin egreso de caja, y se
     * descuenta en la liquidacion como cualquier vale (entero o en cuotas).
     */
    @Transactional
    public Vale crearEnEspecie(Vale vale, Long autorizadoPorId) {
        if (vale.getId() != null) throw new GraphQLException("Un vale en especie se crea nuevo, no se edita");
        if (vale.getFuncionario() == null) throw new GraphQLException("Seleccione el funcionario del vale");
        if (vale.getMonto() == null || vale.getMonto().signum() <= 0)
            throw new GraphQLException("El monto del vale debe ser positivo");
        if (vale.getFecha() == null) vale.setFecha(LocalDate.now());
        vale.setEnEspecie(true);
        vale.setEstado(ValeEstado.CONFIRMADO);
        vale.setCajaVirtualId(null);
        vale.setMovimientoCajaVirtualId(null);
        if (autorizadoPorId != null) {
            vale.setAutorizadoPor(usuarioService.findById(autorizadoPorId).orElse(null));
        }
        return save(vale);
    }

    /** true si el vale se descuenta por cuotas (rrhh.vale_cuota) y no entero. */
    public static boolean tieneCuotas(Vale vale) {
        return vale != null && vale.getCantidadCuotas() != null && vale.getCantidadCuotas() > 1;
    }

    /**
     * Lo que falta descontar del vale: 0 si ya se desconto o se anulo; la suma de las cuotas
     * PENDIENTE si va en cuotas; el monto entero si no.
     */
    @Transactional(readOnly = true)
    public BigDecimal saldoPendiente(Vale vale) {
        if (vale == null || vale.getEstado() == null
                || vale.getEstado() == ValeEstado.ANULADO || vale.getEstado() == ValeEstado.DESCONTADO) {
            return BigDecimal.ZERO;
        }
        if (tieneCuotas(vale)) {
            List<ValeCuota> cuotas = cuotaRepository.findByValeIdOrderByNumeroAsc(vale.getId());
            if (!cuotas.isEmpty()) {
                return cuotas.stream()
                        .filter(c -> c.getEstado() == ValeCuotaEstado.PENDIENTE)
                        .map(c -> c.getMonto() != null ? c.getMonto() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        }
        return vale.getMonto() != null ? vale.getMonto() : BigDecimal.ZERO;
    }

    /**
     * Por que no se pueden tocar las cuotas del vale (anular el vale, anular su pago): alguna ya se
     * desconto o esta en una liquidacion/finiquito no anulado. Null si no hay impedimento.
     */
    @Transactional(readOnly = true)
    public String motivoCuotasBloqueadas(Vale vale) {
        if (!tieneCuotas(vale) || vale.getId() == null) return null;
        List<ValeCuota> cuotas = cuotaRepository.findByValeIdOrderByNumeroAsc(vale.getId());
        if (cuotas.isEmpty()) return null;
        int n = cuotas.size();
        for (ValeCuota c : cuotas) {
            if (c.getEstado() == ValeCuotaEstado.DESCONTADA) {
                String donde = c.getLiquidacionId() != null ? " en la liquidacion #" + c.getLiquidacionId()
                        : c.getLiquidacionFinalId() != null ? " en el finiquito #" + c.getLiquidacionFinalId() : "";
                return "la cuota " + c.getNumero() + "/" + n + " ya se desconto" + donde;
            }
        }
        List<Long> ids = cuotas.stream().map(ValeCuota::getId).collect(Collectors.toList());
        List<Long> liqs = liquidacionItemRepository.findLiquidacionesVivasConCuotasDeVale(ids);
        if (!liqs.isEmpty()) {
            return "tiene cuotas en la liquidacion #" + liqs.get(0) + " (anulela o vuelva a generarla antes)";
        }
        List<Long> fins = liquidacionFinalItemRepository.findFiniquitosVivosConCuotasDeVale(ids);
        if (!fins.isEmpty()) {
            return "tiene cuotas en el finiquito #" + fins.get(0) + " (anulelo o vuelva a generarlo antes)";
        }
        return null;
    }

    /**
     * Confirma un vale SOLICITADO: registra el egreso real en la Caja Mayor
     * (MovimientoCajaVirtual EGRESO).
     *
     * Hasta 2026-07 tambien escribia un MovimientoPersonas (la "cuenta corriente" del
     * empleado). Se desvinculo: esa tabla resulto write-only — nadie la lee, la
     * liquidacion descuenta leyendo rrhh.vale y rrhh.prestamo_cuota directamente, y
     * mezclaba criterios de signo con VENTA_CREDITO. Ver issue #159.
     */
    @Transactional
    public Vale confirmar(Long valeId, Long cajaVirtualId, Long autorizadoPorId) {
        Vale vale = repository.findById(valeId)
                .orElseThrow(() -> new GraphQLException("Vale no encontrado"));
        if (Boolean.TRUE.equals(vale.getEnEspecie())) {
            throw new GraphQLException("El vale en especie nace confirmado: no se confirma contra una caja");
        }
        if (vale.getEstado() != ValeEstado.SOLICITADO) {
            throw new GraphQLException("Solo se puede confirmar un vale en estado SOLICITADO");
        }
        registrarEgresoCaja(vale, cajaVirtualId);
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
        vale.setEnEspecie(false);
        // save() y no repository.save(): genera las cuotas si el vale va en cuotas.
        vale = save(vale);
        return confirmar(vale.getId(), cajaVirtualId, autorizadoPorId);
    }

    /**
     * Anula un vale. Si estaba CONFIRMADO, genera un contra-asiento AJUSTE en
     * la caja (nunca borra el original).
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
        String bloqueo = motivoCuotasBloqueadas(vale);
        if (bloqueo != null) {
            throw new GraphQLException("No se puede anular el vale #" + vale.getId() + ": " + bloqueo);
        }
        // Vale pagado desde tesoreria: la plata salio por un evento de pago consolidado
        // (PAGO_CPP), no por el egreso directo de este servicio. Revertirlo aca dejaria el
        // dinero fuera de la caja (revertirEgresoCaja hace return si no hay cajaVirtualId) o
        // duplicaria la reversion. Se anula el pago, y el hook de sincronizacion devuelve el
        // vale a SOLICITADO.
        if (vale.getEstado() == ValeEstado.CONFIRMADO && vale.getSolicitudPagoId() != null) {
            throw new GraphQLException("Este vale se pago desde tesoreria: anula el pago desde el "
                    + "movimiento de caja (Anular pago) y el vale vuelve a quedar pendiente");
        }
        if (vale.getEstado() == ValeEstado.CONFIRMADO) {
            revertirEgresoCaja(vale);
        }
        for (ValeCuota c : cuotaRepository.findByValeIdOrderByNumeroAsc(vale.getId())) {
            if (c.getEstado() == ValeCuotaEstado.PENDIENTE) {
                c.setEstado(ValeCuotaEstado.ANULADA);
                cuotaRepository.save(c);
            }
        }
        vale.setEstado(ValeEstado.ANULADO);
        return repository.save(vale);
    }

    /**
     * true si esta obligacion de pago pertenece a un vale. Lo usa el motor de pago para
     * saber que concepto esta pagando y etiquetar el movimiento de caja con su origen real.
     */
    @Transactional(readOnly = true)
    public boolean tieneSolicitud(Long solicitudPagoId) {
        return solicitudPagoId != null && repository.findBySolicitudPagoId(solicitudPagoId) != null;
    }

    /**
     * Sincroniza el vale con el estado de su obligacion de pago (SolicitudPago tipo RRHH).
     * Espejo de {@code PreGastoService.sincronizarDesdeSolicitudPago}: lo llama el motor de
     * pago tanto al pagar como al anular un evento.
     *
     * <p>Solicitud CONCLUIDA ⇒ el vale queda CONFIRMADO (es lo que mira la liquidacion para
     * descontarlo) y se linkean caja + movimiento consolidado, que es el FK inverso que usa
     * la trazabilidad del ledger. <b>No postea nada en caja</b>: el movimiento ya lo genero
     * el motor de pago. Cualquier otro estado (se anulo el pago) ⇒ vuelve a SOLICITADO.</p>
     */
    @Transactional
    public void sincronizarDesdeSolicitudPago(com.franco.dev.domain.operaciones.SolicitudPago sp) {
        if (sp == null || sp.getTipo() != com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.RRHH) return;
        Vale vale = repository.findBySolicitudPagoId(sp.getId());
        if (vale == null || vale.getEstado() == ValeEstado.ANULADO
                || vale.getEstado() == ValeEstado.DESCONTADO) return;

        boolean pagado = sp.getEstado() == com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado.CONCLUIDO;
        if (!pagado) {
            // Si una cuota ya se desconto, devolver el vale a SOLICITADO lo dejaria pagable otra vez
            // por el monto entero desde tesoreria.
            String bloqueo = motivoCuotasBloqueadas(vale);
            if (bloqueo != null) {
                throw new GraphQLException("No se puede anular el pago del vale #" + vale.getId() + ": " + bloqueo);
            }
        }
        ValeEstado destino = pagado ? ValeEstado.CONFIRMADO : ValeEstado.SOLICITADO;
        if (pagado) {
            // Linkear el movimiento de caja del pago (si lo hubo: un pago 100% bancario o con
            // cheque no genera fila de caja).
            for (com.franco.dev.domain.financiero.PagoSolicitudDetalle d
                    : pagoSolicitudDetalleRepository.findBySolicitudPagoIdOrderByCreadoEnAsc(sp.getId())) {
                if (Boolean.TRUE.equals(d.getAnulado())) continue;
                if (d.getMovimientoCajaVirtualId() != null) {
                    vale.setMovimientoCajaVirtualId(d.getMovimientoCajaVirtualId());
                    vale.setCajaVirtualId(d.getCajaVirtualId());
                    break;
                }
            }
        } else {
            vale.setMovimientoCajaVirtualId(null);
            vale.setCajaVirtualId(null);
        }
        if (vale.getEstado() != destino || pagado) {
            vale.setEstado(destino);
            repository.save(vale);
        }
    }

    // ---- helpers ----

    private void validarCuotas(Vale vale) {
        int n = vale.getCantidadCuotas();
        if (n < 1 || n > MAX_CUOTAS) {
            throw new GraphQLException("La cantidad de cuotas del vale debe estar entre 1 y " + MAX_CUOTAS);
        }
        if (n > 1) {
            if (vale.getFecha() == null) {
                throw new GraphQLException("Un vale en cuotas necesita fecha: la primera cuota se descuenta"
                        + " en la liquidacion del periodo de esa fecha");
            }
            if (vale.getMonto() == null || vale.getMonto().signum() <= 0) {
                throw new GraphQLException("El monto del vale debe ser positivo");
            }
        }
    }

    /**
     * Genera las cuotas del vale. Solo se regeneran mientras el vale esta SOLICITADO: en ese estado
     * ninguna liquidacion las puede referenciar (solo descuenta vales CONFIRMADO). Desde CONFIRMADO
     * quedan congeladas; si todavia no existen (vale que nace confirmado), se generan una vez.
     */
    private void sincronizarCuotas(Vale vale) {
        List<ValeCuota> actuales = cuotaRepository.findByValeIdOrderByNumeroAsc(vale.getId());
        if (!actuales.isEmpty() && vale.getEstado() != ValeEstado.SOLICITADO) return;
        if (!actuales.isEmpty()) {
            cuotaRepository.deleteAll(actuales);
            // Sin flush, Hibernate inserta antes de borrar y choca con UNIQUE (vale_id, numero).
            cuotaRepository.flush();
        }
        if (!tieneCuotas(vale)) return;

        int n = vale.getCantidadCuotas();
        // Monto entero (guaranies) => cuotas enteras; la ultima absorbe el redondeo.
        int escala = vale.getMonto().stripTrailingZeros().scale() <= 0 ? 0 : 2;
        List<BigDecimal> montos = CuotaCalculator.calcularCuotas(vale.getMonto(), n, escala);
        for (int i = 1; i <= n; i++) {
            ValeCuota c = new ValeCuota();
            c.setVale(vale);
            c.setNumero(i);
            c.setMonto(montos.get(i - 1));
            c.setFechaDescuento(vale.getFecha().plusMonths(i - 1));
            c.setEstado(ValeCuotaEstado.PENDIENTE);
            c.setCreadoEn(LocalDateTime.now());
            cuotaRepository.save(c);
        }
    }

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
        mov.setOrigenTipo(OrigenMovimientoTipo.RRHH_VALE);
        mov.setOrigenId(vale.getId());
        mov.setDescripcion("VALE #" + vale.getId() + nombreFuncionario(vale.getFuncionario()));
        mov.setUsuario(vale.getUsuario());
        mov.setActivo(true);
        mov = movimientoCajaVirtualService.registrarMovimiento(mov);
        vale.setMovimientoCajaVirtualId(mov.getId());
    }

    private void revertirEgresoCaja(Vale vale) {
        if (vale.getCajaVirtualId() == null) return;
        if (vale.getMovimientoCajaVirtualId() == null) {
            throw new GraphQLException("El vale #" + vale.getId() + " esta confirmado contra una caja"
                    + " pero no tiene movimiento asociado: no se puede revertir sin dejar la caja descuadrada.");
        }
        // La reversa la arma tesoreria, que recalcula el efecto del movimiento original, lo niega
        // y marca el original como inactivo. Copiar el monto a mano en un AJUSTE solo revierte
        // cuando el monto es positivo, y deja el movimiento original sin tachar.
        movimientoCajaVirtualService.revertirMovimiento(vale.getMovimientoCajaVirtualId(),
                "ANULACION VALE #" + vale.getId(), vale.getUsuario());
    }

    /** Sufijo " - NOMBRE" para las descripciones de los movimientos (vacio si no hay). */
    private static String nombreFuncionario(Funcionario f) {
        return (f != null && f.getPersona() != null && f.getPersona().getNombre() != null)
                ? " - " + f.getPersona().getNombre() : "";
    }
}
