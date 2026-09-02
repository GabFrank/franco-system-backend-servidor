package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.CategoriaDiferenciaRetiro;
import com.franco.dev.domain.financiero.enums.EstadoCasoRetiro;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.financiero.enums.ResultadoVerificacionRetiro;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.*;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Verificación de un retiro de PDV al recibirlo en tesorería.
 *
 * <p>El que recibe cuenta la plata contra lo que declaró el PDV, y <b>a la caja mayor entra lo
 * contado</b>. El retiro y su detalle no se tocan nunca: son la declaración del origen y son la
 * evidencia. La diferencia vive en la verificación, y si la hay se abre un caso para que otro
 * lo investigue — el que recibe no investiga.</p>
 *
 * <p>Todo ocurre en una transacción: verificar, acreditar y marcar el retiro. Es el mismo
 * patrón atómico de {@link RetiroIngresoService}, y es lo que evita que el poller heredado se
 * cuele entre la asignación de caja y el posteo.</p>
 */
@Service
@AllArgsConstructor
public class RetiroVerificacionService {

    private final RetiroRepository retiroRepository;
    private final RetiroDetalleService retiroDetalleService;
    private final RetiroVerificacionRepository verificacionRepository;
    private final RetiroCasoRepository casoRepository;
    private final MovimientoCajaVirtualRepository movimientoRepository;
    private final CajaVirtualService cajaVirtualService;
    private final MonedaService monedaService;
    private final TesoreriaService tesoreriaService;
    private final TesoreriaSecurityService seguridad;
    private final com.franco.dev.service.empresarial.SucursalService sucursalService;

    /** Lo que el usuario contó de una moneda. */
    @Data
    public static class ConteoMoneda {
        private Long monedaId;
        private BigDecimal contado;
        private CategoriaDiferenciaRetiro categoria;
    }

    /**
     * Cuenta un retiro, lo acredita y lo cierra.
     *
     * @param rapida true si se confirmó lo declarado sin contar por denominación. Queda
     *               marcado: si después aparece una diferencia, hay que poder saber que ese
     *               retiro nunca se contó billete por billete.
     */
    @Transactional
    public RetiroVerificacion verificar(Long retiroId, Long sucursalId, Long cajaVirtualId,
                                        List<ConteoMoneda> conteos, boolean rapida,
                                        String observacion, Usuario usuario) {

        // Lock pesimista antes de mirar nada: sin esto dos tesoreros (o un doble click) leen
        // los dos que no hay verificación y acreditan dos veces la misma plata. El índice
        // único de retiro_verificacion es la red de abajo; el lock serializa antes.
        // El ACL de la caja se exige acá y no solo en TesoreriaService.registrar: un retiro
        // contado en cero no postea ningún movimiento, así que ese choke point no se ejecuta y
        // el retiro terminaba asignado a una caja sobre la que el usuario no tiene permiso.
        seguridad.requireEscrituraCaja(cajaVirtualId);

        Retiro retiro = retiroRepository.lockByIdAndSucursalId(retiroId, sucursalId)
                .orElseThrow(() -> new GraphQLException("Retiro no encontrado: " + retiroId + "/" + sucursalId));

        if (retiro.getMovimientoCajaVirtualId() != null) {
            throw new GraphQLException("El retiro #" + retiroId + " ya fue ingresado a una caja mayor");
        }
        verificacionRepository.findVigente(retiroId, sucursalId).ifPresent(v -> {
            throw new GraphQLException("El retiro #" + retiroId + " ya fue verificado");
        });

        CajaVirtual caja = cajaVirtualService.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja mayor no encontrada: " + cajaVirtualId));

        Map<Long, BigDecimal> declaradoPorMoneda = declaradoPorMoneda(retiro);
        Map<Long, ConteoMoneda> contadoPorMoneda = new LinkedHashMap<>();
        for (ConteoMoneda c : conteos != null ? conteos : Collections.<ConteoMoneda>emptyList()) {
            if (c.getMonedaId() == null) continue;
            contadoPorMoneda.put(c.getMonedaId(), c);
        }

        RetiroVerificacion verificacion = new RetiroVerificacion();
        verificacion.setRetiroId(retiroId);
        verificacion.setSucursalId(sucursalId);
        verificacion.setUsuario(usuario);
        verificacion.setCreadoEn(LocalDateTime.now());
        verificacion.setRapida(rapida);
        verificacion.setObservacion(observacion != null ? observacion.toUpperCase() : null);
        verificacion.setAnulada(false);

        // La comparación es por moneda y nunca por total convertido: un retiro puede cerrar en
        // el total y tener 100 R$ de menos con su equivalente de más en guaraníes. Eso es un
        // cambio informal hecho en el camino, no un retiro correcto.
        Set<Long> monedas = new LinkedHashSet<>();
        monedas.addAll(declaradoPorMoneda.keySet());
        monedas.addAll(contadoPorMoneda.keySet());

        boolean hayDiferencia = false;
        for (Long monedaId : monedas) {
            BigDecimal declarado = declaradoPorMoneda.getOrDefault(monedaId, BigDecimal.ZERO);
            ConteoMoneda c = contadoPorMoneda.get(monedaId);
            BigDecimal contado = c != null && c.getContado() != null ? c.getContado() : BigDecimal.ZERO;
            BigDecimal diferencia = contado.subtract(declarado);

            RetiroVerificacionDetalle d = new RetiroVerificacionDetalle();
            d.setMoneda(monedaService.findById(monedaId).orElse(null));
            d.setDeclarado(declarado);
            d.setContado(contado);
            d.setDiferencia(diferencia);
            if (diferencia.signum() != 0) {
                hayDiferencia = true;
                d.setCategoria(c != null && c.getCategoria() != null
                        ? c.getCategoria()
                        : (diferencia.signum() < 0 ? CategoriaDiferenciaRetiro.FALTANTE
                                                   : CategoriaDiferenciaRetiro.SOBRANTE));
            }
            verificacion.agregarDetalle(d);
        }

        verificacion.setResultado(hayDiferencia
                ? ResultadoVerificacionRetiro.CON_DIFERENCIA
                : ResultadoVerificacionRetiro.SIN_DIFERENCIA);
        RetiroVerificacion guardada = verificacionRepository.save(verificacion);

        acreditar(retiro, caja, guardada, usuario);

        // Los estados de verificación existen en el enum desde V0, en central y en toda
        // filial: emitirlos no puede cortar la réplica. Este UPDATE sí baja a la filial
        // (V155.1 habilitó central -> filial para la cabecera del retiro).
        retiro.setEstado(hayDiferencia
                ? com.franco.dev.domain.financiero.enums.EstadoRetiro.VERIFICADO_CONCLUIDO_CON_PROBLEMA
                : com.franco.dev.domain.financiero.enums.EstadoRetiro.VERIFICADO_CONCLUIDO_SIN_PROBLEMA);
        retiro.setCajaVirtualId(cajaVirtualId);
        retiroRepository.save(retiro);

        if (hayDiferencia) abrirCaso(guardada, usuario);

        return guardada;
    }

    /** Lo que declaró el PDV, agrupado por moneda. Se lee del retiro_detalle, que es inmutable. */
    private Map<Long, BigDecimal> declaradoPorMoneda(Retiro retiro) {
        Map<Long, BigDecimal> porMoneda = new LinkedHashMap<>();
        List<RetiroDetalle> detalles = retiroDetalleService.findByRetiroId(retiro.getId(), retiro.getSucursalId());
        for (RetiroDetalle d : detalles) {
            if (d.getMoneda() == null || d.getCantidad() == null) continue;
            porMoneda.merge(d.getMoneda().getId(), BigDecimal.valueOf(d.getCantidad()), BigDecimal::add);
        }
        return porMoneda;
    }

    /**
     * Postea en la caja mayor <b>lo contado</b>, una línea por moneda.
     *
     * El movimiento lleva {@code origenSucursalId} además del {@code origenId}: el id del
     * retiro no es global (cada filial numera desde 1) y una caja mayor recibe retiros de
     * varias sucursales, así que sin la sucursal no se puede volver a encontrar el movimiento
     * de <i>este</i> retiro.
     */
    private void acreditar(Retiro retiro, CajaVirtual caja, RetiroVerificacion verificacion, Usuario usuario) {
        // Misma descripción que arma el poller, para que el historial de caja se lea igual
        // venga por donde venga.
        String nombreSucursal = sucursalService.findById(retiro.getSucursalId())
                .map(com.franco.dev.domain.empresarial.Sucursal::getNombre)
                .orElse("Sucursal " + retiro.getSucursalId());
        String descripcion = "Retiro #" + retiro.getId() + " - " + nombreSucursal;

        Long ultimoMovId = null;
        for (RetiroVerificacionDetalle d : verificacion.getDetalles()) {
            if (d.getContado() == null || d.getContado().signum() <= 0) continue;
            MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
            mov.setCajaVirtual(caja);
            mov.setTipoMovimiento(CajaVirtualTipoMovimiento.INGRESO);
            mov.setCantidad(d.getContado().doubleValue());
            mov.setMoneda(d.getMoneda());
            mov.setUsuario(usuario);
            mov.setDescripcion(descripcion);
            mov.setReferenciaId(retiro.getId());
            mov.setOrigenTipo(OrigenMovimientoTipo.RETIRO_CAJA);
            mov.setOrigenId(retiro.getId());
            mov.setOrigenSucursalId(retiro.getSucursalId());
            ultimoMovId = tesoreriaService.registrar(mov).getId();
        }
        // -1 marca "verificado y procesado, sin movimientos" (un retiro contado en cero).
        retiro.setMovimientoCajaVirtualId(ultimoMovId != null ? ultimoMovId : -1L);
    }

    private void abrirCaso(RetiroVerificacion verificacion, Usuario usuario) {
        RetiroCaso caso = new RetiroCaso();
        caso.setRetiroId(verificacion.getRetiroId());
        caso.setSucursalId(verificacion.getSucursalId());
        caso.setVerificacion(verificacion);
        caso.setEstado(EstadoCasoRetiro.ABIERTO);
        caso.setAbiertoPor(usuario);
        caso.setCreadoEn(LocalDateTime.now());
        casoRepository.save(caso);
    }

    /**
     * Deshace una verificación ya acreditada: el que recibe también se puede equivocar.
     *
     * Los movimientos se ubican por {@code (origenTipo, origenId, origenSucursalId)} — sin la
     * sucursal se revertirían los de un retiro homónimo de otra filial.
     */
    @Transactional
    public RetiroVerificacion anular(Long verificacionId, String motivo, Usuario usuario) {
        RetiroVerificacion v = verificacionRepository.findById(verificacionId)
                .orElseThrow(() -> new GraphQLException("Verificación no encontrada: " + verificacionId));
        if (Boolean.TRUE.equals(v.getAnulada())) {
            throw new GraphQLException("La verificación ya está anulada");
        }
        Retiro retiro = retiroRepository.lockByIdAndSucursalId(v.getRetiroId(), v.getSucursalId())
                .orElseThrow(() -> new GraphQLException("Retiro no encontrado"));

        List<MovimientoCajaVirtual> movimientos = movimientoRepository
                .findByOrigenTipoAndOrigenIdAndOrigenSucursalIdAndActivoTrue(
                        OrigenMovimientoTipo.RETIRO_CAJA, v.getRetiroId(), v.getSucursalId());
        for (MovimientoCajaVirtual m : movimientos) {
            tesoreriaService.revertir(m, motivo, usuario);
        }

        v.setAnulada(true);
        verificacionRepository.save(v);

        // Vuelve a flotar: sin caja asignada y sin movimiento, listo para verificarse de nuevo.
        retiro.setMovimientoCajaVirtualId(null);
        retiro.setCajaVirtualId(null);
        retiro.setEstado(com.franco.dev.domain.financiero.enums.EstadoRetiro.CONCLUIDO);
        retiroRepository.save(retiro);

        // El caso NO se borra: es el registro de que hubo una diferencia y de quién la miró.
        // Si todavía estaba abierto se cierra sin veredicto — nadie determinó nada, la
        // verificación se deshizo y al recontar se abrirá uno nuevo. Si ya venía resuelto (el
        // investigador concluyó "contó mal tesorería" y pidió anular), se deja intacto.
        casoRepository.findByVerificacionId(v.getId())
                .ifPresent(caso -> {
                    if (caso.getEstado() == EstadoCasoRetiro.RESUELTO) return;
                    caso.setEstado(EstadoCasoRetiro.RESUELTO);
                    caso.setResolucion("CERRADO POR ANULACION DE LA VERIFICACION"
                            + (motivo != null && !motivo.isEmpty() ? ": " + motivo.toUpperCase() : ""));
                    caso.setResueltoPor(usuario);
                    caso.setResueltoEn(LocalDateTime.now());
                    casoRepository.save(caso);
                });

        return v;
    }
}
