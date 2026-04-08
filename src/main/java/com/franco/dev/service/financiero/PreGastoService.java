package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;

import com.franco.dev.domain.financiero.EnteCuota;
import com.franco.dev.domain.financiero.EnteFinanciero;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.PreGastoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.domain.activos.Mueble;
import com.franco.dev.service.activos.MuebleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PreGastoService extends CrudService<PreGasto, PreGastoRepository, EmbebedPrimaryKey> {

    private final PreGastoRepository repository;
    private final EnteFinancieroService enteFinancieroService;
    private final EnteCuotaService enteCuotaService;
    private final SolicitudPagoService solicitudPagoService;
    private final ProveedorService proveedorService;
    private final UsuarioService usuarioService;
    private final MuebleService muebleService;

    @Value("${sucursalId:0}")
    private Long currentSucursalId;

    @Override
    public PreGastoRepository getRepository() {
        return repository;
    }

    @Override
    public PreGasto save(PreGasto entity) {
        if (entity.getEnte() != null && entity.getEnte().getId() != null) {
            if (entity.getTipoGasto() != null && Boolean.TRUE.equals(entity.getTipoGasto().getAfectaFinanzasActivo())) {
                Optional<EnteFinanciero> optFinanciero = enteFinancieroService.findByEnteId(entity.getEnte().getId());
                if (optFinanciero.isPresent()) {
                    EnteFinanciero financiero = optFinanciero.get();

                    // Validar consistencia de moneda (Punto 2)
                    if (entity.getMoneda() != null && financiero.getMoneda() != null &&
                            !entity.getMoneda().getId().equals(financiero.getMoneda().getId())) {
                        throw new RuntimeException(
                                "La moneda de la solicitud (" + entity.getMoneda().getDenominacion() +
                                        ") no coincide con la moneda del activo ("
                                        + financiero.getMoneda().getDenominacion() + ")");
                    }

                    BigDecimal montoTotal = financiero.getMontoTotal() != null ? financiero.getMontoTotal()
                            : BigDecimal.ZERO;
                    BigDecimal montoYaPagado = financiero.getMontoYaPagado() != null ? financiero.getMontoYaPagado()
                            : BigDecimal.ZERO;
                    BigDecimal pendiente = montoTotal.subtract(montoYaPagado);

                    if (entity.getMontoSolicitado() != null && entity.getMontoSolicitado().compareTo(pendiente) > 0) {
                        throw new RuntimeException(
                                "El monto solicitado supera el saldo pendiente del activo (" + pendiente + ").");
                    }
                }
            }
        }

        if (entity.getId() == null) {
            Long sucursalId = entity.getSucursalId() != null ? entity.getSucursalId() : currentSucursalId; // Default
                                                                                                           // sucursal
                                                                                                           // from
                                                                                                           // config
            Long maxId = repository.findMaxId(sucursalId);
            if (maxId == null)
                maxId = 0L;
            entity.setId(maxId + 1);
            entity.setSucursalId(sucursalId);
            entity.setCreadoEn(LocalDateTime.now());
            entity.setEstado(EstadoPreGasto.PENDIENTE);
        }
        if (entity.getCreadoEn() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }

    public List<PreGasto> buscarPorEstado(EstadoPreGasto estado) {
        return repository.buscarPorEstado(estado.name());
    }

    public PreGasto findByIdAndSucursalId(Long id, Long sucursalId) {
        return repository.findByIdAndSucursalId(id, sucursalId);
    }

    public List<PreGasto> buscarPorEstadoYSucursal(EstadoPreGasto estado, Long sucursalId) {
        return repository.buscarPorEstadoYSucursal(estado.name(), sucursalId);
    }

    public List<PreGasto> buscarPorFuncionario(Long funcionarioId) {
        return repository.buscarPorFuncionario(funcionarioId);
    }

    public List<PreGasto> buscarPorTexto(String texto, Long sucursalId) {
        return repository.buscarPorTexto(texto, sucursalId);
    }

    public org.springframework.data.domain.Page<PreGasto> filterPreGastos(Long id, String estado, String inicio,
            String fin, org.springframework.data.domain.Pageable pageable) {
        return repository.filterPreGastos(id, estado, inicio, fin, pageable);
    }

    public PreGasto autorizar(Long id, Long autorizadorId, Long sucId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null)
            return null;
        preGasto.setEstado(EstadoPreGasto.AUTORIZADO);
        preGasto.setQrToken(UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        // Ya no descontamos cuota en la autorizacion, se hara al completar la operacion
        // (Punto 1)
        // if (preGasto.getEnte() != null && preGasto.getEnte().getId() != null) {
        // if (preGasto.getTipoGasto() != null &&
        // Boolean.TRUE.equals(preGasto.getTipoGasto().getAfectaFinanzasActivo())) {
        // descontarCuota(preGasto);
        // }
        // }

        return super.save(preGasto);
    }

    public PreGasto rechazar(Long id, String motivo, Long sucId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null)
            return null;
        preGasto.setEstado(EstadoPreGasto.RECHAZADO);
        preGasto.setMotivoRechazo(motivo);
        return super.save(preGasto);
    }

    public PreGasto enviarATramite(Long id, Long sucId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null)
            return null;
        preGasto.setEstado(EstadoPreGasto.TRAMITE);
        return super.save(preGasto);
    }

    public PreGasto enviarATesoreria(Long id, Long sucId, Long usuarioId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null)
            return null;

        // Buscar proveedor asociado al funcionario
        Proveedor proveedor = proveedorService.findByPersonaId(preGasto.getFuncionario().getId());
        if (proveedor == null) {
            // Si no existe proveedor, lo creamos automáticamente para permitir el pago
            proveedor = new Proveedor();
            proveedor.setPersona(preGasto.getFuncionario());
            proveedor.setCredito(false);
            proveedor.setCreadoEn(LocalDateTime.now());
            proveedor = proveedorService.save(proveedor);
        }

        // Crear Solicitud de Pago
        Usuario usuario = null;
        if (usuarioId != null) {
            usuario = usuarioService.findById(usuarioId).orElse(null);
        }

        SolicitudPago solicitudPago = solicitudPagoService.crearSolicitudPago(proveedor, null, preGasto.getMoneda(),
                null, LocalDateTime.now(), "Generado desde PreGasto " + preGasto.getId(), usuario);

        // Actualizar monto y guardar (crearSolicitudPago inicializa en 0 si no hay
        // notas)
        solicitudPago.setMontoTotal(preGasto.getMontoSolicitado().doubleValue());
        solicitudPago = solicitudPagoService.save(solicitudPago);

        preGasto.setEstado(EstadoPreGasto.ENVIADO_A_TESORERIA);
        preGasto.setSolicitudPagoId(solicitudPago.getId());
        return save(preGasto);
    }

    public PreGasto completar(Long id, Long sucId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null)
            return null;
        preGasto.setEstado(EstadoPreGasto.COMPLETADO);

        // Si el gasto está vinculado a un ente (activo) y afecta finanzas, descontar la
        // cuota automáticamente (Punto 1)
        if (preGasto.getEnte() != null && preGasto.getEnte().getId() != null) {
            if (preGasto.getTipoGasto() != null
                    && Boolean.TRUE.equals(preGasto.getTipoGasto().getAfectaFinanzasActivo())) {
                descontarCuota(preGasto);
            }
        }

        // Automatización del Inventario (Punto 4)
        if (preGasto.getTipoGasto() != null && preGasto.getTipoGasto().getDescripcion() != null &&
                preGasto.getTipoGasto().getDescripcion().toUpperCase().contains("COMPRA DE ACTIVO")) {
            crearActivoAutomatico(preGasto);
        }

        BigDecimal montoRetirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
        BigDecimal montoGastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
        preGasto.setSaldoDevolver(montoRetirado.subtract(montoGastado));
        return super.save(preGasto);
    }

    private void crearActivoAutomatico(PreGasto preGasto) {
        try {
            Mueble mueble = new Mueble();
            mueble.setDescripcion(preGasto.getDescripcion());
            mueble.setMontoTotal(preGasto.getMontoGastado());
            mueble.setMoneda(preGasto.getMoneda());
            mueble.setSituacionPago("PAGADO");
            mueble.setCreadoEn(LocalDateTime.now());
            // Marcar como "Pendiente de Etiquetado" en la descripción o un campo si
            // existiera
            mueble.setDescripcion(preGasto.getDescripcion() + " (Pendiente de Etiquetado)");
            muebleService.save(mueble);
        } catch (Exception e) {
            // Log error but don't stop completion
            System.err.println("Error al crear activo automático: " + e.getMessage());
        }
    }

    /**
     * Descuenta la siguiente cuota pendiente del EnteFinanciero asociado al ente
     * del PreGasto.
     * Marca la cuota como pagada y actualiza el montoYaPagado del EnteFinanciero.
     */
    private void descontarCuota(PreGasto preGasto) {
        Optional<EnteFinanciero> optFinanciero = enteFinancieroService.findByEnteId(preGasto.getEnte().getId());
        if (!optFinanciero.isPresent())
            return;

        EnteFinanciero financiero = optFinanciero.get();

        // Buscar la próxima cuota pendiente (no pagada)
        List<EnteCuota> cuotasPendientes = enteCuotaService.findPendientesByEnteFinancieroId(financiero.getId());
        if (cuotasPendientes.isEmpty())
            return;

        // Tomar la primera cuota pendiente (están ordenadas por numero_cuota asc)
        EnteCuota cuota = cuotasPendientes.get(0);
        cuota.setPagado(true);
        enteCuotaService.save(cuota);

        // Actualizar el monto ya pagado del EnteFinanciero
        BigDecimal montoYaPagado = financiero.getMontoYaPagado() != null ? financiero.getMontoYaPagado()
                : BigDecimal.ZERO;
        BigDecimal montoCuota = cuota.getMonto() != null ? cuota.getMonto() : BigDecimal.ZERO;
        financiero.setMontoYaPagado(montoYaPagado.add(montoCuota));
        enteFinancieroService.save(financiero);
    }
}
