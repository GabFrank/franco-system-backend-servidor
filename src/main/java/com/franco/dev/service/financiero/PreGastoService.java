package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;

import com.franco.dev.domain.financiero.EnteCuota;
import com.franco.dev.domain.financiero.EnteFinanciero;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.PreGastoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.activos.InmuebleService;
import com.franco.dev.service.activos.VehiculoService;
import com.franco.dev.service.financiero.dto.EnteFinancialSummaryDTO;
import com.franco.dev.service.financiero.dto.PreGastoStatusMetadataDTO;
import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.Inmueble;
import com.franco.dev.domain.activos.Mueble;
import com.franco.dev.domain.activos.Vehiculo;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.service.activos.MuebleService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final EnteService enteService;
    private final InmuebleService inmuebleService;
    private final VehiculoService vehiculoService;

    @Value("${sucursalId:0}")
    private Long currentSucursalId;

    @Override
    public PreGastoRepository getRepository() {
        return repository;
    }

    @Override
    public PreGasto save(PreGasto entity) {
        if (entity.getEnte() != null && entity.getEnte().getId() != null) {
            boolean isPagoCuota = (entity.getTipoGasto() != null && Boolean.TRUE.equals(entity.getTipoGasto().getEsPagoCuotaActivo())) || 
                                  (entity.getDescripcion() != null && entity.getDescripcion().toUpperCase().startsWith("PAGO -"));
            if (isPagoCuota) {
                Optional<EnteFinanciero> optFinanciero = enteFinancieroService.findByEnteId(entity.getEnte().getId());
                if (optFinanciero.isPresent()) {
                    EnteFinanciero financiero = optFinanciero.get();

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

    public org.springframework.data.domain.Page<PreGasto> filterPreGastos(Long id, Long cajaId, String estado, List<String> estados, String inicio,
            String fin, org.springframework.data.domain.Pageable pageable) {
        List<String> estadosFiltro = estados != null ? estados : new ArrayList<>();
        if (estadosFiltro.isEmpty()) {
            return repository.filterPreGastosSinEstados(id, cajaId, estado, inicio, fin, pageable);
        }
        return repository.filterPreGastos(id, cajaId, estado, estadosFiltro, inicio, fin, pageable);
    }

    public PreGasto autorizar(Long id, Long autorizadorId, Long sucId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null)
            return null;
        preGasto.setEstado(EstadoPreGasto.AUTORIZADO);
        preGasto.setQrToken(UUID.randomUUID().toString().substring(0, 8).toUpperCase());

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

        Proveedor proveedor = proveedorService.findByPersonaId(preGasto.getFuncionario().getId());
        if (proveedor == null) {
            proveedor = new Proveedor();
            proveedor.setPersona(preGasto.getFuncionario());
            proveedor.setCredito(false);
            proveedor.setCreadoEn(LocalDateTime.now());
            proveedor = proveedorService.save(proveedor);
        }

        Usuario usuario = null;
        if (usuarioId != null) {
            usuario = usuarioService.findById(usuarioId).orElse(null);
        }

        SolicitudPago solicitudPago = solicitudPagoService.crearSolicitudPago(proveedor, null, preGasto.getMoneda(),
                null, LocalDateTime.now(), "Generado desde PreGasto " + preGasto.getId(), usuario);

        solicitudPago.setMontoTotal(preGasto.getMontoSolicitado().doubleValue());
        solicitudPago = solicitudPagoService.save(solicitudPago);

        preGasto.setEstado(EstadoPreGasto.ENVIADO_A_TESORERIA);
        preGasto.setSolicitudPagoId(solicitudPago.getId());
        return save(preGasto);
    }

    public PreGasto completar(Long id, Long sucId, Boolean rindioGasto, Double montoGastadoInformado) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null)
            return null;
        preGasto.setEstado(EstadoPreGasto.COMPLETADO);

        if (preGasto.getEnte() != null && preGasto.getEnte().getId() != null) {
            boolean isPagoCuota = (preGasto.getTipoGasto() != null && Boolean.TRUE.equals(preGasto.getTipoGasto().getEsPagoCuotaActivo())) || 
                                  (preGasto.getDescripcion() != null && preGasto.getDescripcion().toUpperCase().startsWith("PAGO -"));
            if (isPagoCuota) {
                descontarCuota(preGasto);
            }
        }

        if (preGasto.getTipoGasto() != null && preGasto.getTipoGasto().getDescripcion() != null &&
                preGasto.getTipoGasto().getDescripcion().toUpperCase().contains("COMPRA DE ACTIVO")) {
            crearActivoAutomatico(preGasto);
        }

        BigDecimal montoRetirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
        if (montoGastadoInformado != null) {
            BigDecimal montoGastadoNormalizado = BigDecimal.valueOf(montoGastadoInformado);
            if (montoGastadoNormalizado.compareTo(BigDecimal.ZERO) < 0) {
                montoGastadoNormalizado = BigDecimal.ZERO;
            }
            if (montoGastadoNormalizado.compareTo(montoRetirado) > 0) {
                montoGastadoNormalizado = montoRetirado;
            }
            preGasto.setMontoGastado(montoGastadoNormalizado);
        } else if (!Boolean.TRUE.equals(rindioGasto)) {
            preGasto.setMontoGastado(BigDecimal.ZERO);
        }
        BigDecimal montoGastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
        preGasto.setSaldoDevolver(montoRetirado.subtract(montoGastado));
        if (montoGastado.compareTo(BigDecimal.ZERO) > 0) {
            preGasto.setFechaRendicion(LocalDateTime.now());
        } else {
            preGasto.setFechaRendicion(null);
        }
        recalcularEstadoRendicion(preGasto);
        return super.save(preGasto);
    }

    public void actualizarRendicionDesdeGasto(Gasto gasto) {
        if (gasto == null || gasto.getPreGasto() == null) {
            return;
        }
        PreGasto preGasto = repository.findByIdAndSucursalId(gasto.getPreGasto().getId(), gasto.getPreGasto().getSucursalId());
        if (preGasto == null) {
            return;
        }

        BigDecimal montoRetirado = obtenerMontoMoneda(preGasto, gasto.getRetiroGs(), gasto.getRetiroRs(), gasto.getRetiroDs());
        BigDecimal montoVuelto = obtenerMontoMoneda(preGasto, gasto.getVueltoGs(), gasto.getVueltoRs(), gasto.getVueltoDs());
        if (montoVuelto.compareTo(BigDecimal.ZERO) < 0) {
            montoVuelto = BigDecimal.ZERO;
        }
        BigDecimal montoRendido = montoRetirado.subtract(montoVuelto);
        if (montoRendido.compareTo(BigDecimal.ZERO) < 0) {
            montoRendido = BigDecimal.ZERO;
        }

        preGasto.setMontoRetirado(montoRetirado);
        preGasto.setMontoGastado(montoRendido);
        preGasto.setSaldoDevolver(montoVuelto);
        preGasto.setFechaRendicion(gasto.getFinalizado() != null && gasto.getFinalizado()
                ? LocalDateTime.now()
                : preGasto.getFechaRendicion());
        recalcularEstadoRendicion(preGasto);
        super.save(preGasto);
    }

    private BigDecimal obtenerMontoMoneda(PreGasto preGasto, Double gs, Double rs, Double ds) {
        String simbolo = preGasto.getMoneda() != null && preGasto.getMoneda().getSimbolo() != null
                ? preGasto.getMoneda().getSimbolo().trim().toUpperCase()
                : "";
        String denominacion = preGasto.getMoneda() != null && preGasto.getMoneda().getDenominacion() != null
                ? preGasto.getMoneda().getDenominacion().trim().toUpperCase()
                : "";
        if (simbolo.contains("GS") || denominacion.contains("GUARANI")) {
            return toBigDecimal(gs);
        }
        if (simbolo.contains("R$") || simbolo.contains("RS") || denominacion.contains("REAL")) {
            return toBigDecimal(rs);
        }
        if (simbolo.contains("USD") || simbolo.contains("US$") || "$".equals(simbolo) || denominacion.contains("DOLAR")) {
            return toBigDecimal(ds);
        }
        if (toBigDecimal(gs).compareTo(BigDecimal.ZERO) > 0) return toBigDecimal(gs);
        if (toBigDecimal(rs).compareTo(BigDecimal.ZERO) > 0) return toBigDecimal(rs);
        return toBigDecimal(ds);
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private void recalcularEstadoRendicion(PreGasto preGasto) {
        BigDecimal retirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
        BigDecimal gastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;

        if (retirado.compareTo(BigDecimal.ZERO) <= 0 || gastado.compareTo(BigDecimal.ZERO) <= 0) {
            preGasto.setEstadoRendicion("NO_RENDIDO");
            preGasto.setRindioGasto(false);
            return;
        }
        if (gastado.compareTo(retirado) >= 0) {
            preGasto.setEstadoRendicion("RENDIDO_COMPLETO");
            preGasto.setRindioGasto(true);
            return;
        }
        preGasto.setEstadoRendicion("RENDIDO_PARCIAL");
        preGasto.setRindioGasto(true);
    }

    private void crearActivoAutomatico(PreGasto preGasto) {
        try {
            Mueble mueble = new Mueble();
            mueble.setDescripcion(preGasto.getDescripcion());
            mueble.setMontoTotal(preGasto.getMontoGastado());
            mueble.setMoneda(preGasto.getMoneda());
            mueble.setSituacionPago("PAGADO");
            mueble.setCreadoEn(LocalDateTime.now());
            mueble.setDescripcion(preGasto.getDescripcion() + " (Pendiente de Etiquetado)");
            muebleService.save(mueble);
        } catch (Exception e) {
            System.err.println("Error al crear activo automático: " + e.getMessage());
        }
    }

    private void descontarCuota(PreGasto preGasto) {
        Optional<EnteFinanciero> optFinanciero = enteFinancieroService.findByEnteId(preGasto.getEnte().getId());
        if (!optFinanciero.isPresent())
            return;

        EnteFinanciero financiero = optFinanciero.get();

        List<EnteCuota> cuotasPendientes = enteCuotaService.findPendientesByEnteFinancieroId(financiero.getId());
        if (cuotasPendientes.isEmpty())
            return;

        EnteCuota cuota = cuotasPendientes.get(0);
        cuota.setPagado(true);
        enteCuotaService.save(cuota);

        BigDecimal montoYaPagado = financiero.getMontoYaPagado() != null ? financiero.getMontoYaPagado()
                : BigDecimal.ZERO;
        BigDecimal montoCuota = cuota.getMonto() != null ? cuota.getMonto() : BigDecimal.ZERO;
        financiero.setMontoYaPagado(montoYaPagado.add(montoCuota));
        enteFinancieroService.save(financiero);
    }

    public EnteFinancialSummaryDTO getFinancialSummary(Long enteId) {
        Ente ente = enteService.findById(enteId).orElse(null);
        if (ente == null)
            return null;

        EnteFinancialSummaryDTO dto = new EnteFinancialSummaryDTO();
        dto.setEnteId(enteId);
        Optional<EnteFinanciero> optFinanciero = enteFinancieroService.findByEnteId(enteId);
        if (optFinanciero.isPresent()) {
            EnteFinanciero f = optFinanciero.get();
            dto.setMontoTotal(f.getMontoTotal());
            dto.setMontoYaPagado(f.getMontoYaPagado());
            if (f.getMoneda() != null) {
                dto.setMonedaId(f.getMoneda().getId());
                dto.setMonedaSimbolo(f.getMoneda().getSimbolo());
            }
        }
        if (ente.getTipoEnte() != null && ente.getReferenciaId() != null) {
            if (ente.getTipoEnte() == TipoEnte.MUEBLE) {
                Mueble m = muebleService.findById(ente.getReferenciaId()).orElse(null);
                if (m != null) {
                    dto.setDescripcion(m.getDescripcion() != null ? m.getDescripcion() : m.getIdentificador());
                    if (m.getProveedor() != null && m.getProveedor().getPersona() != null)
                        dto.setProveedorNombre(m.getProveedor().getPersona().getNombre());
                    dto.setSituacionPago(m.getSituacionPago());

                    if (dto.getMontoTotal() == null)
                        dto.setMontoTotal(m.getMontoTotal());
                    if (dto.getMontoYaPagado() == null)
                        dto.setMontoYaPagado(m.getMontoYaPagado());
                    if (dto.getMonedaId() == null && m.getMoneda() != null) {
                        dto.setMonedaId(m.getMoneda().getId());
                        dto.setMonedaSimbolo(m.getMoneda().getSimbolo());
                    }

                    llenarDatosCuotas(dto, m.getCantidadCuotas(), m.getCantidadCuotasPagadas(), m.getDiaVencimiento());
                }
                dto.setTipoGastoSugeridoId("VARIABLE");
            } else if (ente.getTipoEnte() == TipoEnte.INMUEBLE) {
                Inmueble i = inmuebleService.findById(ente.getReferenciaId()).orElse(null);
                if (i != null) {
                    dto.setDescripcion(i.getNombreAsignado() != null ? i.getNombreAsignado() : i.getDireccion());
                    if (i.getProveedor() != null && i.getProveedor().getPersona() != null)
                        dto.setProveedorNombre(i.getProveedor().getPersona().getNombre());
                    dto.setSituacionPago(i.getSituacionPago());

                    if (dto.getMontoTotal() == null)
                        dto.setMontoTotal(i.getMontoTotal());
                    if (dto.getMontoYaPagado() == null)
                        dto.setMontoYaPagado(i.getMontoYaPagado());
                    if (dto.getMonedaId() == null && i.getMoneda() != null) {
                        dto.setMonedaId(i.getMoneda().getId());
                        dto.setMonedaSimbolo(i.getMoneda().getSimbolo());
                    }

                    llenarDatosCuotas(dto, i.getCantidadCuotas(), i.getCantidadCuotasPagadas(), i.getDiaVencimiento());
                }
                dto.setTipoGastoSugeridoId("CONTINUO");
            } else if (ente.getTipoEnte() == TipoEnte.VEHICULO) {
                Vehiculo v = vehiculoService.findById(ente.getReferenciaId()).orElse(null);
                if (v != null) {
                    dto.setDescripcion(v.getChapa() != null ? "Chapa: " + v.getChapa()
                            : (v.getModelo() != null ? v.getModelo().getDescripcion() : "Vehículo #" + v.getId()));
                    if (v.getProveedor() != null && v.getProveedor().getPersona() != null)
                        dto.setProveedorNombre(v.getProveedor().getPersona().getNombre());
                    dto.setSituacionPago(v.getSituacionPago());

                    // Fallback para montos
                    if (dto.getMontoTotal() == null)
                        dto.setMontoTotal(v.getMontoTotal());
                    if (dto.getMontoYaPagado() == null)
                        dto.setMontoYaPagado(v.getMontoYaPagado());
                    if (dto.getMonedaId() == null && v.getMoneda() != null) {
                        dto.setMonedaId(v.getMoneda().getId());
                        dto.setMonedaSimbolo(v.getMoneda().getSimbolo());
                    }

                    llenarDatosCuotas(dto, v.getCantidadCuotas(), v.getCantidadCuotasPagadas(), v.getDiaVencimiento());
                }
                dto.setTipoGastoSugeridoId("VARIABLE");
            }
        }
        BigDecimal total = dto.getMontoTotal() != null ? dto.getMontoTotal() : BigDecimal.ZERO;
        BigDecimal pagado = dto.getMontoYaPagado() != null ? dto.getMontoYaPagado() : BigDecimal.ZERO;
        BigDecimal pendiente = total.subtract(pagado);
        dto.setMontoPendiente(pendiente.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : pendiente);

        if (total.compareTo(BigDecimal.ZERO) > 0) {
            dto.setPorcentajePagado(pagado.multiply(new BigDecimal(100))
                    .divide(total, 2, java.math.RoundingMode.HALF_UP).doubleValue());

            if (dto.getCuotasTotales() != null && dto.getCuotasTotales() > 0) {
                dto.setMontoSugerido(
                        total.divide(new BigDecimal(dto.getCuotasTotales()), 2, java.math.RoundingMode.HALF_UP));
            }
        }

        if (dto.getDescripcion() != null) {
            dto.setDescripcionSugerida("Pago - " + dto.getDescripcion());
        }

        return dto;
    }

    private void llenarDatosCuotas(EnteFinancialSummaryDTO dto, Integer total, Integer pagadas, Integer diaVenc) {
        if (total != null)
            dto.setCuotasTotales(total);
        if (pagadas != null)
            dto.setCuotasPagadas(pagadas);
        if (total != null && pagadas != null)
            dto.setCuotasFaltantes(Math.max(total - pagadas, 0));
        if (diaVenc != null) {
            dto.setDiaVencimiento(diaVenc);
            calcularEstadoVencimiento(dto, diaVenc);
        }
    }

    private void calcularEstadoVencimiento(EnteFinancialSummaryDTO dto, Integer diaVencimiento) {
        int diaActual = java.time.LocalDate.now().getDayOfMonth();
        int dias = diaVencimiento - diaActual;
        dto.setDiasParaVencer(dias);
        if (dias < 0) {
            dto.setEstadoCuota("VENCIDO");
        } else if (dias <= 10) {
            dto.setEstadoCuota("POR VENCER");
        } else {
            dto.setEstadoCuota("AL DIA");
        }
    }

    public List<PreGastoStatusMetadataDTO> getStatusMetadataList() {
        return List.of(
                new PreGastoStatusMetadataDTO("PENDIENTE", "Pendiente", "hourglass_empty", "#ffa726"),
                new PreGastoStatusMetadataDTO("TRAMITE", "En Trámite", "swap_horiz", "#42a5f5"),
                new PreGastoStatusMetadataDTO("AUTORIZADO", "Autorizado", "check_circle", "#66bb6a"),
                new PreGastoStatusMetadataDTO("ENVIADO_A_TESORERIA", "Enviado a Tesorería", "send", "#26a69a"),
                new PreGastoStatusMetadataDTO("RECHAZADO", "Rechazado", "cancel", "#ef5350"),
                new PreGastoStatusMetadataDTO("COMPLETADO", "Completado", "task_alt", "#78909c"));
    }
}
