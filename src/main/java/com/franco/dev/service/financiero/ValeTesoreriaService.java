package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.MotivoVale;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.rrhh.ValeRepository;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.rrhh.MotivoValeService;
import com.franco.dev.service.rrhh.dto.ValePendienteDto;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pago de vales de RRHH desde tesoreria, con el <b>mismo</b> motor de pago que CPP
 * (caja mayor / cuenta bancaria / cheque, multi-moneda).
 *
 * <p>Puente: la obligacion de pago del vale se representa con una {@link SolicitudPago} de
 * tipo {@code RRHH}. El vale sigue siendo la verdad de RRHH (la liquidacion lo descuenta
 * leyendo {@code rrhh.vale}); la solicitud es solo el documento pagable. Cuando la solicitud
 * queda CONCLUIDA, {@code ValeService.sincronizarDesdeSolicitudPago} deja el vale CONFIRMADO.</p>
 *
 * <p>Los vales creados por el atajo viejo ({@code crearValeConfirmado}, egreso directo
 * EGRESO/RRHH_VALE) no tienen solicitud y no aparecen aca: ya estan pagados.</p>
 */
@Service
@AllArgsConstructor
public class ValeTesoreriaService {

    /** Igual criterio que el motor de pago, para que "pago total" signifique lo mismo de los dos lados. */
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.005");

    private final ValeRepository valeRepository;
    private final SolicitudPagoService solicitudPagoService;
    private final PagoProveedorService pagoProveedorService;
    private final FuncionarioService funcionarioService;
    private final MotivoValeService motivoValeService;
    private final MonedaService monedaService;

    /** Un vale a pagar con su reparto de formas de pago (espejo de SolicitudConLineas). */
    @lombok.Data
    public static class ValeConLineas {
        private Long valeId;
        private List<PagoProveedorService.LineaPago> lineas;
    }

    /** Vales pagables: estado SOLICITADO (registrados y todavia sin entregar la plata). */
    @Transactional(readOnly = true)
    public List<ValePendienteDto> listarValesPendientes() {
        List<ValePendienteDto> out = new ArrayList<>();
        for (Vale v : valeRepository.findByEstadoOrderByFechaDescIdDesc(ValeEstado.SOLICITADO)) {
            out.add(toDto(v));
        }
        return out;
    }

    private ValePendienteDto toDto(Vale v) {
        ValePendienteDto d = new ValePendienteDto();
        d.setId(v.getId());
        d.setFecha(v.getFecha());
        d.setMonto(v.getMonto());
        d.setSaldoPendiente(saldoPendiente(v));
        d.setEsAdelanto(Boolean.TRUE.equals(v.getEsAdelanto()));
        d.setObservacion(v.getObservacion());
        d.setMoneda(v.getMoneda());
        d.setFuncionario(v.getFuncionario());
        d.setFuncionarioNombre(nombreFuncionario(v.getFuncionario()));
        d.setMotivo(v.getMotivo());
        d.setMotivoDescripcion(v.getMotivo() != null ? v.getMotivo().getDescripcion() : null);
        return d;
    }

    /**
     * Saldo a entregar. Si el vale ya tiene solicitud con pagos parciales previos, se descuenta;
     * en la practica el pago parcial esta prohibido (ver pagarValesMixto), asi que esto es
     * defensivo ante datos viejos.
     */
    private BigDecimal saldoPendiente(Vale v) {
        BigDecimal total = v.getMonto() != null ? v.getMonto() : BigDecimal.ZERO;
        if (v.getSolicitudPagoId() == null) return total;
        SolicitudPago sp = solicitudPagoService.findById(v.getSolicitudPagoId()).orElse(null);
        if (sp == null || sp.getMontoPagado() == null) return total;
        BigDecimal saldo = total.subtract(sp.getMontoPagado());
        return saldo.signum() > 0 ? saldo : BigDecimal.ZERO;
    }

    /**
     * Alta de un vale para pagar en el acto: queda SOLICITADO (sin mover plata) junto con su
     * obligacion de pago. El egreso lo hace despues {@link #pagarValesMixto}.
     */
    @Transactional
    public Vale crearValeParaPago(Long funcionarioId, Long motivoId, Long monedaId, BigDecimal monto,
                                  Boolean esAdelanto, String observacion, Usuario usuario) {
        if (funcionarioId == null) throw new GraphQLException("El funcionario es obligatorio");
        if (monto == null || monto.signum() <= 0) throw new GraphQLException("El monto del vale debe ser positivo");
        Funcionario funcionario = funcionarioService.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado"));
        Moneda moneda = monedaService.findById(monedaId)
                .orElseThrow(() -> new GraphQLException("Moneda no encontrada"));
        MotivoVale motivo = motivoId != null ? motivoValeService.findById(motivoId).orElse(null) : null;

        Vale vale = new Vale();
        vale.setFuncionario(funcionario);
        vale.setMotivo(motivo);
        vale.setMoneda(moneda);
        vale.setMonto(monto);
        vale.setFecha(LocalDate.now());
        vale.setEstado(ValeEstado.SOLICITADO);
        vale.setEsAdelanto(Boolean.TRUE.equals(esAdelanto));
        vale.setObservacion(observacion != null ? observacion.toUpperCase() : null);
        vale.setUsuario(usuario);
        vale.setCreadoEn(LocalDateTime.now());
        vale = valeRepository.save(vale);

        vale.setSolicitudPagoId(crearSolicitud(vale, usuario).getId());
        return valeRepository.save(vale);
    }

    /**
     * Paga N vales como un unico evento consolidado, con el motor de CPP.
     *
     * <p><b>El pago parcial esta prohibido</b>: la liquidacion descuenta {@code vale.monto}
     * completo, asi que entregar de menos dejaria plata fuera de la caja que nunca se recupera
     * del sueldo. Se exige que lo aplicado cubra el saldo entero del vale.</p>
     */
    @Transactional
    public com.franco.dev.domain.operaciones.Pago pagarValesMixto(List<ValeConLineas> pagos, Usuario usuario) {
        if (pagos == null || pagos.isEmpty()) throw new GraphQLException("Seleccione al menos un vale a pagar");

        List<PagoProveedorService.SolicitudConLineas> lote = new ArrayList<>();
        for (ValeConLineas p : pagos) {
            Vale vale = valeRepository.findById(p.getValeId())
                    .orElseThrow(() -> new GraphQLException("Vale no encontrado: " + p.getValeId()));
            if (vale.getEstado() != ValeEstado.SOLICITADO) {
                throw new GraphQLException("El vale #" + vale.getId() + " no esta pendiente de pago (esta "
                        + vale.getEstado() + ")");
            }
            if (p.getLineas() == null || p.getLineas().isEmpty()) {
                throw new GraphQLException("Indique al menos una forma de pago para el vale #" + vale.getId());
            }
            // Hay vales viejos sin moneda (dato incompleto): sin ella no se puede armar la
            // obligacion de pago ni saber en que moneda sale la plata.
            if (vale.getMoneda() == null) {
                throw new GraphQLException("El vale #" + vale.getId() + " no tiene moneda definida: "
                        + "corregilo desde RRHH antes de pagarlo");
            }
            BigDecimal saldo = saldoPendiente(vale);
            BigDecimal aplicado = BigDecimal.ZERO;
            for (PagoProveedorService.LineaPago l : p.getLineas()) {
                BigDecimal montoSol = l.getMontoSolicitud() != null ? l.getMontoSolicitud() : l.getMonto();
                if (montoSol == null) continue;
                aplicado = aplicado.add(Boolean.TRUE.equals(l.getAumento()) ? montoSol.negate() : montoSol);
            }
            if (saldo.subtract(aplicado).abs().compareTo(TOLERANCIA) > 0) {
                throw new GraphQLException("El vale #" + vale.getId() + " se paga entero o no se paga: "
                        + "falta cubrir " + saldo.subtract(aplicado).toPlainString());
            }

            PagoProveedorService.SolicitudConLineas s = new PagoProveedorService.SolicitudConLineas();
            s.setSolicitudId(asegurarSolicitud(vale, usuario));
            s.setLineas(p.getLineas());
            lote.add(s);
        }
        return pagoProveedorService.pagarLoteMixto(lote, usuario);
    }

    /**
     * Devuelve el id de la obligacion de pago del vale, creandola si el vale es previo a este
     * flujo (los vales que crea el mobile nacen SOLICITADO y sin solicitud).
     */
    private Long asegurarSolicitud(Vale vale, Usuario usuario) {
        if (vale.getSolicitudPagoId() != null) {
            SolicitudPago sp = solicitudPagoService.findById(vale.getSolicitudPagoId()).orElse(null);
            // Si quedo cancelada de un intento anterior, se emite una nueva.
            if (sp != null && sp.getEstado() != SolicitudPagoEstado.CANCELADO) return sp.getId();
        }
        Long id = crearSolicitud(vale, usuario).getId();
        vale.setSolicitudPagoId(id);
        valeRepository.save(vale);
        return id;
    }

    private SolicitudPago crearSolicitud(Vale vale, Usuario usuario) {
        return solicitudPagoService.crearSolicitudVale(
                vale.getMoneda(),
                vale.getMonto() != null ? vale.getMonto().doubleValue() : 0.0,
                descripcion(vale),
                usuario);
    }

    /** Texto que se ve como "descripcion" del vale en la tabla y en el movimiento de caja. */
    private String descripcion(Vale vale) {
        StringBuilder sb = new StringBuilder("VALE");
        if (vale.getId() != null) sb.append(" #").append(vale.getId());
        String nombre = nombreFuncionario(vale.getFuncionario());
        if (nombre != null) sb.append(" - ").append(nombre);
        if (vale.getMotivo() != null && vale.getMotivo().getDescripcion() != null) {
            sb.append(" - ").append(vale.getMotivo().getDescripcion());
        }
        return sb.toString();
    }

    private static String nombreFuncionario(Funcionario f) {
        return (f != null && f.getPersona() != null) ? f.getPersona().getNombre() : null;
    }
}
