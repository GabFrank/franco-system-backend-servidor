package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.Aguinaldo;
import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.AguinaldoEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.rrhh.AguinaldoRepository;
import com.franco.dev.repository.rrhh.LiquidacionFinalRepository;
import com.franco.dev.repository.rrhh.LiquidacionSueldoRepository;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.rrhh.dto.PagoRrhhPendienteDto;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pago de liquidacion mensual, finiquito y aguinaldo desde tesoreria, con el <b>mismo</b>
 * motor de pago que CPP (caja mayor / cuenta bancaria / cheque, multi-moneda).
 *
 * <p>Extiende a esos tres conceptos el puente que {@link ValeTesoreriaService} abrio para el
 * vale: la obligacion de pago se representa con una {@link SolicitudPago} de tipo
 * {@code RRHH}, el documento de RRHH sigue siendo la verdad del modulo, y cuando la solicitud
 * queda CONCLUIDA cada servicio de RRHH lo marca PAGADO desde su
 * {@code sincronizarDesdeSolicitudPago}.</p>
 *
 * <p>Los documentos pagados por el atajo viejo ({@code pagar(id, cajaVirtualId)}, egreso
 * directo EGRESO/RRHH_*) no tienen solicitud y no aparecen aca: ya estan pagados.</p>
 *
 * <p><b>Pago 1 por 1.</b> El motor acepta un lote porque asi paga CPP, pero en RRHH cada
 * documento se paga por separado (nunca se paga la nomina entera en una operacion). El lote
 * se usa igual para no duplicar el motor, y el pago parcial esta prohibido en los tres
 * conceptos por el mismo motivo que en el vale: el documento no lleva saldo, lo que no se
 * entrega no se recupera despues.</p>
 */
@Service
@AllArgsConstructor
public class PagoRrhhTesoreriaService {

    /** Concepto de RRHH pagable. El desktop lo manda tal cual en el input. */
    public enum ConceptoRrhh {
        LIQUIDACION,
        FINIQUITO,
        AGUINALDO
    }

    /** Igual criterio que el motor de pago, para que "pago total" signifique lo mismo de los dos lados. */
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.005");

    private final LiquidacionSueldoRepository liquidacionRepository;
    private final LiquidacionFinalRepository finiquitoRepository;
    private final AguinaldoRepository aguinaldoRepository;
    private final SolicitudPagoService solicitudPagoService;
    private final PagoProveedorService pagoProveedorService;
    private final MonedaService monedaService;

    /** Un documento de RRHH a pagar con su reparto de formas de pago. */
    @lombok.Data
    public static class PagoRrhhConLineas {
        private ConceptoRrhh concepto;
        private Long documentoId;
        private List<PagoProveedorService.LineaPago> lineas;
    }

    // ─────────────────────────── listados de pendientes ───────────────────────────

    /** Liquidaciones mensuales APROBADAS: calculadas y aprobadas, todavia sin pagar. */
    @Transactional(readOnly = true)
    public List<PagoRrhhPendienteDto> listarLiquidacionesPendientes() {
        List<PagoRrhhPendienteDto> out = new ArrayList<>();
        for (LiquidacionSueldo l : liquidacionRepository.findByEstadoOrderByPeriodoDesc(LiquidacionSueldoEstado.APROBADA)) {
            out.add(toDto(l));
        }
        return out;
    }

    /** Liquidaciones finales (finiquitos) APROBADAS y todavia sin pagar. */
    @Transactional(readOnly = true)
    public List<PagoRrhhPendienteDto> listarFiniquitosPendientes() {
        List<PagoRrhhPendienteDto> out = new ArrayList<>();
        for (LiquidacionFinal f : finiquitoRepository.findByEstadoOrderByCreadoEnDesc(LiquidacionFinalEstado.APROBADA)) {
            out.add(toDto(f));
        }
        return out;
    }

    /**
     * Aguinaldos APROBADOS y todavia sin pagar.
     *
     * <p>Se listan solo los APROBADO y no los CALCULADO a proposito: aprobar es lo que congela
     * el monto (ver {@code AguinaldoService.aprobar}, que ademas impide aprobar antes del mes
     * de aguinaldo). Pagar un CALCULADO seria pagar un devengado parcial.</p>
     */
    @Transactional(readOnly = true)
    public List<PagoRrhhPendienteDto> listarAguinaldosPendientes() {
        List<PagoRrhhPendienteDto> out = new ArrayList<>();
        for (Aguinaldo a : aguinaldoRepository.findByEstadoOrderByAnioDescIdDesc(AguinaldoEstado.APROBADO)) {
            out.add(toDto(a));
        }
        return out;
    }

    // ─────────────────────────────────── pago ────────────────────────────────────

    /**
     * Paga documentos de RRHH con el motor de CPP.
     *
     * <p><b>El pago parcial esta prohibido</b>: ni la liquidacion ni el finiquito ni el
     * aguinaldo llevan saldo pendiente, asi que entregar de menos dejaria una diferencia que
     * nadie reclama despues. Se exige que lo aplicado cubra el saldo entero.</p>
     */
    @Transactional
    public com.franco.dev.domain.operaciones.Pago pagarRrhhMixto(List<PagoRrhhConLineas> pagos, Usuario usuario) {
        if (pagos == null || pagos.isEmpty()) throw new GraphQLException("Seleccione al menos un documento a pagar");

        List<PagoProveedorService.SolicitudConLineas> lote = new ArrayList<>();
        for (PagoRrhhConLineas p : pagos) {
            if (p.getConcepto() == null) throw new GraphQLException("Falta el concepto del documento a pagar");
            if (p.getLineas() == null || p.getLineas().isEmpty()) {
                throw new GraphQLException("Indique al menos una forma de pago para el documento #" + p.getDocumentoId());
            }
            BigDecimal saldo = validarYSaldo(p.getConcepto(), p.getDocumentoId());
            BigDecimal aplicado = BigDecimal.ZERO;
            for (PagoProveedorService.LineaPago l : p.getLineas()) {
                BigDecimal montoSol = l.getMontoSolicitud() != null ? l.getMontoSolicitud() : l.getMonto();
                if (montoSol == null) continue;
                aplicado = aplicado.add(Boolean.TRUE.equals(l.getAumento()) ? montoSol.negate() : montoSol);
            }
            if (saldo.subtract(aplicado).abs().compareTo(TOLERANCIA) > 0) {
                throw new GraphQLException(etiqueta(p.getConcepto()) + " #" + p.getDocumentoId()
                        + " se paga entero o no se paga: falta cubrir "
                        + saldo.subtract(aplicado).toPlainString());
            }

            PagoProveedorService.SolicitudConLineas s = new PagoProveedorService.SolicitudConLineas();
            s.setSolicitudId(asegurarSolicitud(p.getConcepto(), p.getDocumentoId(), usuario));
            s.setLineas(p.getLineas());
            lote.add(s);
        }
        return pagoProveedorService.pagarLoteMixto(lote, usuario);
    }

    // ───────────────────────────── proyecciones a DTO ─────────────────────────────

    private PagoRrhhPendienteDto toDto(LiquidacionSueldo l) {
        PagoRrhhPendienteDto d = base(ConceptoRrhh.LIQUIDACION, l.getId(), l.getFuncionario());
        d.setFecha(l.getFechaFin());
        d.setPeriodo(l.getPeriodo());
        d.setMonto(l.getTotalNeto());
        d.setSaldoPendiente(saldoPendiente(l.getTotalNeto(), l.getSolicitudPagoId()));
        d.setDescripcion(descripcionLiquidacion(l));
        d.setMoneda(l.getMoneda() != null ? l.getMoneda() : monedaPrincipal());
        return d;
    }

    private PagoRrhhPendienteDto toDto(LiquidacionFinal f) {
        PagoRrhhPendienteDto d = base(ConceptoRrhh.FINIQUITO, f.getId(), f.getFuncionario());
        d.setFecha(f.getFechaEgreso());
        d.setPeriodo(f.getFechaEgreso() != null ? f.getFechaEgreso().toString() : null);
        d.setMonto(f.getTotalLiquidado());
        d.setSaldoPendiente(saldoPendiente(f.getTotalLiquidado(), f.getSolicitudPagoId()));
        d.setDescripcion(descripcionFiniquito(f));
        d.setMoneda(f.getMoneda() != null ? f.getMoneda() : monedaPrincipal());
        return d;
    }

    private PagoRrhhPendienteDto toDto(Aguinaldo a) {
        PagoRrhhPendienteDto d = base(ConceptoRrhh.AGUINALDO, a.getId(), a.getFuncionario());
        d.setPeriodo(a.getAnio() != null ? String.valueOf(a.getAnio()) : null);
        d.setMonto(a.getMontoCalculado());
        d.setSaldoPendiente(saldoPendiente(a.getMontoCalculado(), a.getSolicitudPagoId()));
        d.setDescripcion(descripcionAguinaldo(a));
        d.setMoneda(monedaAguinaldo(a));
        return d;
    }

    private PagoRrhhPendienteDto base(ConceptoRrhh concepto, Long id, Funcionario f) {
        PagoRrhhPendienteDto d = new PagoRrhhPendienteDto();
        d.setConcepto(concepto.name());
        d.setId(id);
        d.setFuncionario(f);
        d.setFuncionarioNombre(nombreFuncionario(f));
        return d;
    }

    // ───────────────────────────── validacion y saldo ─────────────────────────────

    /** Verifica que el documento exista y este pagable, y devuelve su saldo a entregar. */
    private BigDecimal validarYSaldo(ConceptoRrhh concepto, Long id) {
        switch (concepto) {
            case LIQUIDACION: {
                LiquidacionSueldo l = liquidacionRepository.findById(id)
                        .orElseThrow(() -> new GraphQLException("Liquidacion no encontrada: " + id));
                if (l.getEstado() != LiquidacionSueldoEstado.APROBADA) {
                    throw new GraphQLException("La liquidacion #" + id + " no esta pendiente de pago (esta "
                            + l.getEstado() + ")");
                }
                return saldoPendiente(l.getTotalNeto(), l.getSolicitudPagoId());
            }
            case FINIQUITO: {
                LiquidacionFinal f = finiquitoRepository.findById(id)
                        .orElseThrow(() -> new GraphQLException("Finiquito no encontrado: " + id));
                if (f.getEstado() != LiquidacionFinalEstado.APROBADA) {
                    throw new GraphQLException("El finiquito #" + id + " no esta pendiente de pago (esta "
                            + f.getEstado() + ")");
                }
                return saldoPendiente(f.getTotalLiquidado(), f.getSolicitudPagoId());
            }
            case AGUINALDO: {
                Aguinaldo a = aguinaldoRepository.findById(id)
                        .orElseThrow(() -> new GraphQLException("Aguinaldo no encontrado: " + id));
                if (a.getEstado() != AguinaldoEstado.APROBADO) {
                    throw new GraphQLException("El aguinaldo #" + id + " no esta pendiente de pago (esta "
                            + a.getEstado() + ")");
                }
                return saldoPendiente(a.getMontoCalculado(), a.getSolicitudPagoId());
            }
            default:
                throw new GraphQLException("Concepto de RRHH no soportado: " + concepto);
        }
    }

    /**
     * Saldo a entregar. Si el documento ya tiene solicitud con pagos parciales previos se
     * descuenta; en la practica el pago parcial esta prohibido, asi que esto es defensivo.
     */
    private BigDecimal saldoPendiente(BigDecimal total, Long solicitudPagoId) {
        BigDecimal base = total != null ? total : BigDecimal.ZERO;
        if (solicitudPagoId == null) return base;
        SolicitudPago sp = solicitudPagoService.findById(solicitudPagoId).orElse(null);
        if (sp == null || sp.getMontoPagado() == null) return base;
        BigDecimal saldo = base.subtract(sp.getMontoPagado());
        return saldo.signum() > 0 ? saldo : BigDecimal.ZERO;
    }

    // ──────────────────────────── obligacion de pago ─────────────────────────────

    /**
     * Devuelve el id de la obligacion de pago del documento, creandola la primera vez (o de
     * nuevo si la anterior quedo cancelada por un intento fallido).
     */
    private Long asegurarSolicitud(ConceptoRrhh concepto, Long id, Usuario usuario) {
        switch (concepto) {
            case LIQUIDACION: {
                LiquidacionSueldo l = liquidacionRepository.findById(id)
                        .orElseThrow(() -> new GraphQLException("Liquidacion no encontrada: " + id));
                Long vigente = solicitudVigente(l.getSolicitudPagoId());
                if (vigente != null) return vigente;
                Long nueva = crearSolicitud(l.getMoneda() != null ? l.getMoneda() : monedaPrincipal(),
                        l.getTotalNeto(), descripcionLiquidacion(l), usuario);
                l.setSolicitudPagoId(nueva);
                liquidacionRepository.save(l);
                return nueva;
            }
            case FINIQUITO: {
                LiquidacionFinal f = finiquitoRepository.findById(id)
                        .orElseThrow(() -> new GraphQLException("Finiquito no encontrado: " + id));
                Long vigente = solicitudVigente(f.getSolicitudPagoId());
                if (vigente != null) return vigente;
                Long nueva = crearSolicitud(f.getMoneda() != null ? f.getMoneda() : monedaPrincipal(),
                        f.getTotalLiquidado(), descripcionFiniquito(f), usuario);
                f.setSolicitudPagoId(nueva);
                finiquitoRepository.save(f);
                return nueva;
            }
            case AGUINALDO: {
                Aguinaldo a = aguinaldoRepository.findById(id)
                        .orElseThrow(() -> new GraphQLException("Aguinaldo no encontrado: " + id));
                Long vigente = solicitudVigente(a.getSolicitudPagoId());
                if (vigente != null) return vigente;
                Long nueva = crearSolicitud(monedaAguinaldo(a), a.getMontoCalculado(),
                        descripcionAguinaldo(a), usuario);
                a.setSolicitudPagoId(nueva);
                aguinaldoRepository.save(a);
                return nueva;
            }
            default:
                throw new GraphQLException("Concepto de RRHH no soportado: " + concepto);
        }
    }

    /** Id de la solicitud si sigue siendo usable; null si no existe o quedo cancelada. */
    private Long solicitudVigente(Long solicitudPagoId) {
        if (solicitudPagoId == null) return null;
        SolicitudPago sp = solicitudPagoService.findById(solicitudPagoId).orElse(null);
        if (sp == null || sp.getEstado() == SolicitudPagoEstado.CANCELADO) return null;
        return sp.getId();
    }

    private Long crearSolicitud(Moneda moneda, BigDecimal monto, String descripcion, Usuario usuario) {
        if (moneda == null) {
            throw new GraphQLException("No hay moneda principal configurada: no se puede armar la obligacion de pago");
        }
        return solicitudPagoService.crearSolicitudVale(
                moneda,
                monto != null ? monto.doubleValue() : 0.0,
                descripcion,
                usuario).getId();
    }

    /**
     * Moneda del aguinaldo. La entidad no la lleva: se replica el criterio de
     * {@code AguinaldoService.pagar} (moneda del funcionario, si no la principal) para que el
     * monto salga en la misma moneda por los dos caminos de pago.
     */
    private Moneda monedaAguinaldo(Aguinaldo a) {
        if (a.getFuncionario() != null && a.getFuncionario().getMoneda() != null) {
            return a.getFuncionario().getMoneda();
        }
        return monedaPrincipal();
    }

    private Moneda monedaPrincipal() {
        for (Moneda m : monedaService.findAll2()) {
            if (Boolean.TRUE.equals(m.getPrincipal())) return m;
        }
        return null;
    }

    // ──────────────────────────────── descripciones ───────────────────────────────

    private String descripcionLiquidacion(LiquidacionSueldo l) {
        StringBuilder sb = new StringBuilder("LIQUIDACION");
        if (l.getPeriodo() != null) sb.append(" ").append(l.getPeriodo());
        if (l.getId() != null) sb.append(" #").append(l.getId());
        String nombre = nombreFuncionario(l.getFuncionario());
        if (nombre != null) sb.append(" - ").append(nombre);
        return sb.toString();
    }

    private String descripcionFiniquito(LiquidacionFinal f) {
        StringBuilder sb = new StringBuilder("FINIQUITO");
        if (f.getId() != null) sb.append(" #").append(f.getId());
        String nombre = nombreFuncionario(f.getFuncionario());
        if (nombre != null) sb.append(" - ").append(nombre);
        if (f.getFechaEgreso() != null) sb.append(" - EGRESO ").append(f.getFechaEgreso());
        return sb.toString();
    }

    private String descripcionAguinaldo(Aguinaldo a) {
        StringBuilder sb = new StringBuilder("AGUINALDO");
        if (a.getAnio() != null) sb.append(" ").append(a.getAnio());
        if (a.getId() != null) sb.append(" #").append(a.getId());
        String nombre = nombreFuncionario(a.getFuncionario());
        if (nombre != null) sb.append(" - ").append(nombre);
        return sb.toString();
    }

    private static String etiqueta(ConceptoRrhh c) {
        switch (c) {
            case LIQUIDACION: return "La liquidacion";
            case FINIQUITO:   return "El finiquito";
            case AGUINALDO:   return "El aguinaldo";
            default:          return "El documento";
        }
    }

    private static String nombreFuncionario(Funcionario f) {
        return (f != null && f.getPersona() != null) ? f.getPersona().getNombre() : null;
    }
}
