package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;

import com.franco.dev.domain.financiero.EnteCuota;
import com.franco.dev.domain.financiero.EnteFinanciero;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.PreGastoDetalleFinanzas;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.repository.financiero.PreGastoRepository;
import com.franco.dev.service.administrativo.AutorizacionAuditService;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.activos.InmuebleService;
import com.franco.dev.service.activos.VehiculoService;
import com.franco.dev.service.activos.util.ActivoPagoNormalizer;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.domain.financiero.enums.TipoNaturalezaGasto;
import com.franco.dev.domain.financiero.enums.TipoPadreGastoModulo;
import com.franco.dev.service.financiero.dto.EnteFinancialSummaryDTO;
import com.franco.dev.service.financiero.dto.LineaRetiroSugeridaDTO;
import com.franco.dev.service.financiero.dto.MontosRetiroPayloadDTO;
import com.franco.dev.service.financiero.dto.PreGastoStatusMetadataDTO;
import com.franco.dev.service.financiero.dto.QrRetiroPreGastoPayloadDTO;
import com.franco.dev.graphql.financiero.input.ConfirmarRetiroFuncionarioInput;
import com.franco.dev.graphql.financiero.input.DevolucionSaldoPreGastoInput;
import com.franco.dev.graphql.financiero.input.EjecutarRetiroPreGastoInput;
import com.franco.dev.graphql.financiero.input.RetiroPreGastoLineaInput;
import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.Inmueble;
import com.franco.dev.domain.activos.Mueble;
import com.franco.dev.domain.activos.Vehiculo;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.domain.equipos.Equipo;
import com.franco.dev.domain.equipos.EquipoFinanciero;
import com.franco.dev.service.activos.MuebleService;
import com.franco.dev.service.equipos.EquipoService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Service
@RequiredArgsConstructor
public class PreGastoService extends CrudService<PreGasto, PreGastoRepository, EmbebedPrimaryKey> {

    private final PreGastoRepository repository;
    private final EnteFinancieroService enteFinancieroService;
    private final EnteCuotaService enteCuotaService;
    private final SolicitudPagoService solicitudPagoService;
    private final ProveedorService proveedorService;
    private final UsuarioService usuarioService;
    private final PersonaService personaService;
    private final FuncionarioService funcionarioService;
    private final AutorizacionAuditService autorizacionAuditService;
    private final MuebleService muebleService;
    private final EnteService enteService;
    private final InmuebleService inmuebleService;
    private final VehiculoService vehiculoService;
    private final EquipoService equipoService;
    private final GastoRepository gastoRepository;
    private final PreGastoDetalleFinanzasService preGastoDetalleFinanzasService;
    private final PdvCajaService pdvCajaService;
    private final MonedaService monedaService;
    private final TipoGastoService tipoGastoService;
    private final TipoGastoModuloReglasService moduloReglasService;

    @Value("${sucursalId:0}")
    private Long currentSucursalId;

    @Override
    public PreGastoRepository getRepository() {
        return repository;
    }

    @Override
    public PreGasto save(PreGasto entity) {
        if (entity.getEnte() != null && entity.getEnte().getId() != null) {
            boolean isPagoCuota = (entity.getTipoGasto() != null
                    && Boolean.TRUE.equals(entity.getTipoGasto().getEsPagoCuotaActivo())) ||
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

                    validarMontoSolicitadoActivo(entity.getMontoSolicitado(), financiero);
                }
            }
        }

        if (entity.getId() == null) {
            Long sucursalId = entity.getSucursalId() != null ? entity.getSucursalId() : currentSucursalId; // Default
                                                                                                           // sucursal
                                                                                                           // from
                                                                                                           // config
            if (sucursalId == null || sucursalId <= 0) {
                throw new RuntimeException("No se puede crear la solicitud sin una sucursal válida.");
            }
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

    public Page<PreGasto> filterPreGastos(Long id, Long cajaId, String estado,
            List<String> estados, String inicio, String fin, Pageable pageable) {
        Specification<PreGasto> spec = buildFilterSpecification(id, cajaId, estado, estados, inicio, fin);
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "id"));
        return repository.findAll(spec, sorted);
    }

    private Specification<PreGasto> buildFilterSpecification(Long id, Long cajaId, String estado,
            List<String> estados, String inicio, String fin) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (id != null) {
                predicates.add(cb.equal(root.get("id"), id));
            } else if (cajaId != null) {
                predicates.add(cb.equal(root.get("cajaId"), cajaId));
            }

            if (estado != null && !estado.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("estado"), EstadoPreGasto.valueOf(estado.trim())));
            }

            if (estados != null && !estados.isEmpty()) {
                List<EstadoPreGasto> estadosEnum = estados.stream()
                        .filter(e -> e != null && !e.trim().isEmpty())
                        .map(e -> EstadoPreGasto.valueOf(e.trim()))
                        .collect(Collectors.toList());
                if (!estadosEnum.isEmpty()) {
                    predicates.add(root.get("estado").in(estadosEnum));
                }
            }

            LocalDateTime inicioDt = stringToDate(inicio);
            if (inicioDt != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("creadoEn"), inicioDt));
            }

            LocalDateTime finDt = stringToDate(fin);
            if (finDt != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("creadoEn"), finDt));
            }

            if (query != null) {
                query.orderBy(cb.desc(root.get("id")));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional
    public PreGasto autorizar(Long id, Long autorizadorId, Long usuarioId, Long sucId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null)
            return null;
        Persona autorizador = autorizadorId != null ? personaService.findById(autorizadorId).orElse(null) : null;
        preGasto.setEstado(EstadoPreGasto.AUTORIZADO);
        preGasto.setAutorizadoPor(autorizador);
        preGasto.setAutorizadoEn(LocalDateTime.now());
        preGasto.setRechazadoPor(null);
        preGasto.setRechazadoEn(null);
        preGasto.setMotivoRechazo(null);
        if (preGasto.getQrToken() == null || preGasto.getQrToken().isEmpty()) {
            preGasto.setQrToken(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        PreGasto saved = super.save(preGasto);
        autorizacionAuditService.registrarPreGastoAutorizado(
                obtenerFuncionarioIdParaAutorizacion(saved),
                autorizadorId,
                usuarioId,
                saved.getSucursalId(),
                "Autorizacion de pre-gasto " + saved.getId() + "-" + saved.getSucursalId());
        return saved;
    }

    @Transactional
    public PreGasto rechazar(Long id, String motivo, Long rechazadorId, Long usuarioId, Long sucId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null)
            return null;
        Persona rechazador = rechazadorId != null ? personaService.findById(rechazadorId).orElse(null) : null;
        preGasto.setEstado(EstadoPreGasto.RECHAZADO);
        preGasto.setMotivoRechazo(motivo);
        preGasto.setRechazadoPor(rechazador);
        preGasto.setRechazadoEn(LocalDateTime.now());
        preGasto.setAutorizadoPor(null);
        preGasto.setAutorizadoEn(null);
        PreGasto saved = super.save(preGasto);
        autorizacionAuditService.registrarPreGastoRechazado(
                obtenerFuncionarioIdParaAutorizacion(saved),
                rechazadorId,
                usuarioId,
                saved.getSucursalId(),
                "Rechazo de pre-gasto " + saved.getId() + "-" + saved.getSucursalId() + ". Motivo: " + motivo);
        return saved;
    }

    private Long obtenerFuncionarioIdParaAutorizacion(PreGasto preGasto) {
        if (preGasto == null || preGasto.getFuncionario() == null || preGasto.getFuncionario().getId() == null) {
            return null;
        }
        Funcionario funcionario = funcionarioService.findByPersonaId(preGasto.getFuncionario().getId());
        return funcionario != null ? funcionario.getId() : null;
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
        if (preGasto.getMontoSolicitado() == null || preGasto.getMontoSolicitado().signum() <= 0) {
            throw new RuntimeException("El gasto no tiene un monto válido para enviar a tesorería.");
        }

        // Beneficiario del gasto: proveedor > persona (find/create Proveedor) > null.
        // Ya NO se secuestra al funcionario como beneficiario (un gasto puede no tener proveedor).
        Proveedor beneficiario = preGasto.getBeneficiarioProveedor();
        if (beneficiario == null && preGasto.getBeneficiarioPersona() != null) {
            beneficiario = proveedorService.findByPersonaId(preGasto.getBeneficiarioPersona().getId());
            if (beneficiario == null) {
                beneficiario = new Proveedor();
                beneficiario.setPersona(preGasto.getBeneficiarioPersona());
                beneficiario.setCredito(false);
                beneficiario.setCreadoEn(LocalDateTime.now());
                beneficiario = proveedorService.save(beneficiario);
            }
        }

        Usuario usuario = null;
        if (usuarioId != null) {
            usuario = usuarioService.findById(usuarioId).orElse(null);
        }

        // Crea la deuda pagable unificada como GASTO/SOLICITADO (aparece en 'Pagar Gasto'),
        // llevando categoría, descripción y vencimiento del PreGasto.
        String descripcion = (preGasto.getDescripcion() != null && !preGasto.getDescripcion().isEmpty())
                ? preGasto.getDescripcion() : ("Gasto PreGasto #" + preGasto.getId());
        SolicitudPago solicitudPago = solicitudPagoService.crearSolicitudGasto(
                beneficiario, preGasto.getTipoGasto(), preGasto.getMoneda(),
                preGasto.getMontoSolicitado().doubleValue(), descripcion,
                preGasto.getFechaVencimiento(), usuario);

        preGasto.setEstado(EstadoPreGasto.ENVIADO_A_TESORERIA);
        preGasto.setSolicitudPagoId(solicitudPago.getId());
        return save(preGasto);
    }

    /**
     * Sincroniza el estado del PreGasto según su SolicitudPago de tipo GASTO (si existe uno vinculado).
     * Lo invoca el motor de pago: al pagar (CONCLUIDO) → PAGADO; al anular (reabre la solicitud) →
     * vuelve a ENVIADO_A_TESORERIA. No-op para gastos directos de tesorería (sin PreGasto).
     */
    @Transactional
    public void sincronizarDesdeSolicitudPago(com.franco.dev.domain.operaciones.SolicitudPago sp) {
        if (sp == null || sp.getTipo() != com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.GASTO) return;
        PreGasto pg = repository.findBySolicitudPagoId(sp.getId());
        if (pg == null) return;
        EstadoPreGasto destino = (sp.getEstado() == com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado.CONCLUIDO)
                ? EstadoPreGasto.PAGADO
                : EstadoPreGasto.ENVIADO_A_TESORERIA;
        if (pg.getEstado() != destino) {
            pg.setEstado(destino);
            repository.save(pg);
        }
    }

    public PreGasto completar(Long id, Long sucId, Boolean rindioGasto, Double montoGastadoInformado,
            Double montoGastadoGsInformado, Double montoGastadoRsInformado, Double montoGastadoDsInformado) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null)
            return null;
        // El retiro vincula la solicitud con el gasto de caja via
        // preGasto.gastoCajaRegistroId (ver ejecutarRetiro). El gasto no guarda
        // pre_gasto_id, asi que buscamos primero por ese registro y dejamos el
        // query por preGastoId como fallback por compatibilidad.
        Gasto gastoAsociado = null;
        if (preGasto.getGastoCajaRegistroId() != null) {
            gastoAsociado = gastoRepository.findByIdAndSucursalId(preGasto.getGastoCajaRegistroId(), sucId);
        }
        if (gastoAsociado == null) {
            gastoAsociado = gastoRepository.findFirstByPreGastoIdAndPreGastoSucursalIdOrderByCreadoEnDescIdDesc(id,
                    sucId);
        }
        if (gastoAsociado == null) {
            throw new RuntimeException(
                    "No se puede completar la solicitud de gasto #" + id +
                            " porque no tiene un gasto (retiro de dinero) asociado. " +
                            "Primero debe realizar el retiro de dinero desde caja.");
        }

        preGasto.setEstado(EstadoPreGasto.COMPLETADO);

        if (preGasto.getEnte() != null && preGasto.getEnte().getId() != null) {
            boolean isPagoCuota = (preGasto.getTipoGasto() != null
                    && Boolean.TRUE.equals(preGasto.getTipoGasto().getEsPagoCuotaActivo())) ||
                    (preGasto.getDescripcion() != null && preGasto.getDescripcion().toUpperCase().startsWith("PAGO -"));
            if (isPagoCuota) {
                descontarCuota(preGasto);
            }
        }

        if (preGasto.getTipoGasto() != null && preGasto.getTipoGasto().getDescripcion() != null &&
                preGasto.getTipoGasto().getDescripcion().toUpperCase().contains("COMPRA DE ACTIVO")) {
            crearActivoAutomatico(preGasto);
        }

        Gasto gasto = gastoAsociado;

        BigDecimal retiroGs = toBigDecimal(gasto.getRetiroGs());
        BigDecimal retiroRs = toBigDecimal(gasto.getRetiroRs());
        BigDecimal retiroDs = toBigDecimal(gasto.getRetiroDs());

        BigDecimal rendidoGs = null;
        BigDecimal rendidoRs = null;
        BigDecimal rendidoDs = null;

        if (montoGastadoGsInformado != null || montoGastadoRsInformado != null || montoGastadoDsInformado != null) {
            rendidoGs = normalizarMontoRendido(montoGastadoGsInformado, retiroGs, null);
            rendidoRs = normalizarMontoRendido(montoGastadoRsInformado, retiroRs, null);
            rendidoDs = normalizarMontoRendido(montoGastadoDsInformado, retiroDs, null);
        } else if (montoGastadoInformado != null) {
            BigDecimal montoRetirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado()
                    : BigDecimal.ZERO;
            BigDecimal montoGastadoNormalizado = normalizarMontoRendido(montoGastadoInformado, montoRetirado,
                    preGasto.getMontoSolicitado());
            preGasto.setMontoGastado(montoGastadoNormalizado);
        } else if (!Boolean.TRUE.equals(rindioGasto)) {
            preGasto.setMontoGastado(BigDecimal.ZERO);
            rendidoGs = BigDecimal.ZERO;
            rendidoRs = BigDecimal.ZERO;
            rendidoDs = BigDecimal.ZERO;
        }

        if (rendidoGs != null && rendidoRs != null && rendidoDs != null) {
            if (gasto != null) {
                gasto.setVueltoGs(retiroGs.subtract(rendidoGs).max(BigDecimal.ZERO).doubleValue());
                gasto.setVueltoRs(retiroRs.subtract(rendidoRs).max(BigDecimal.ZERO).doubleValue());
                gasto.setVueltoDs(retiroDs.subtract(rendidoDs).max(BigDecimal.ZERO).doubleValue());
                gastoRepository.save(gasto);
            }

            BigDecimal montoRetiradoMoneda = obtenerMontoMoneda(
                    preGasto, retiroGs.doubleValue(), retiroRs.doubleValue(), retiroDs.doubleValue());
            BigDecimal montoGastadoMoneda = obtenerMontoMoneda(
                    preGasto, rendidoGs.doubleValue(), rendidoRs.doubleValue(), rendidoDs.doubleValue());

            preGasto.setMontoRetirado(montoRetiradoMoneda);
            preGasto.setMontoGastado(montoGastadoMoneda);
            preGasto.setSaldoDevolver(montoRetiradoMoneda.subtract(montoGastadoMoneda).max(BigDecimal.ZERO));
        } else {
            BigDecimal montoRetirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado()
                    : BigDecimal.ZERO;
            BigDecimal montoGastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
            preGasto.setSaldoDevolver(montoRetirado.subtract(montoGastado).max(BigDecimal.ZERO));
        }

        BigDecimal montoGastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
        if (montoGastado.compareTo(BigDecimal.ZERO) > 0) {
            preGasto.setFechaRendicion(LocalDateTime.now());
        } else {
            preGasto.setFechaRendicion(null);
        }
        recalcularEstadoRendicion(preGasto);
        return super.save(preGasto);
    }

    public PreGasto completar(Long id, Long sucId, Boolean rindioGasto, Double montoGastadoInformado) {
        return completar(id, sucId, rindioGasto, montoGastadoInformado, null, null, null);
    }

    @Transactional
    public void actualizarRetiroDesdeGasto(Gasto gasto) {
        if (gasto == null || gasto.getPreGasto() == null) {
            return;
        }
        PreGasto preGasto = repository.findByIdAndSucursalId(
                gasto.getPreGasto().getId(), gasto.getPreGasto().getSucursalId());
        if (preGasto == null) {
            return;
        }

        BigDecimal montoRetirado = obtenerMontoMoneda(preGasto, gasto.getRetiroGs(), gasto.getRetiroRs(),
                gasto.getRetiroDs());
        BigDecimal montoGastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;

        preGasto.setMontoRetirado(montoRetirado);
        preGasto.setSaldoDevolver(montoRetirado.subtract(montoGastado).max(BigDecimal.ZERO));
        preGasto.setEstado(EstadoPreGasto.TRAMITE);
        preGasto.setEstadoRendicion("NO_RENDIDO");
        preGasto.setRindioGasto(false);
        super.save(preGasto);
    }

    public List<PreGasto> buscarAutorizadosParaRetiro(Long sucursalCajaId) {
        if (sucursalCajaId == null || sucursalCajaId <= 0) {
            return new ArrayList<>();
        }
        return repository.buscarAutorizadosParaRetiro(sucursalCajaId);
    }

    public QrRetiroPreGastoPayloadDTO construirQrRetiro(Long preGastoId, Long sucursalId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(preGastoId, sucursalId);
        if (preGasto == null) {
            throw new RuntimeException("Solicitud de gasto no encontrada.");
        }
        if (preGasto.getEstado() != EstadoPreGasto.AUTORIZADO) {
            throw new RuntimeException("La solicitud no está autorizada para retiro.");
        }
        if (preGasto.getQrToken() == null || preGasto.getQrToken().trim().isEmpty()) {
            preGasto.setQrToken(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            super.save(preGasto);
        }
        Long sucursalCajaId = preGasto.getSucursalCaja() != null ? preGasto.getSucursalCaja().getId() : sucursalId;
        String timestamp = String.valueOf(System.currentTimeMillis());
        String codigoQr = "frc-" + sucursalCajaId + "-PRE_GASTO_RETIRO-" + preGastoId + "-" + sucursalId + "-"
                + preGasto.getQrToken() + "-" + timestamp;
        return new QrRetiroPreGastoPayloadDTO(codigoQr, preGastoId, sucursalId, preGasto.getQrToken());
    }

    @Transactional
    public PreGasto confirmarRetiroFuncionario(ConfirmarRetiroFuncionarioInput input) {
        PreGasto preGasto = repository.findByIdAndSucursalId(input.getPreGastoId(), input.getSucursalId());
        if (preGasto == null) {
            throw new RuntimeException("Solicitud de gasto no encontrada.");
        }
        if (preGasto.getEstado() != EstadoPreGasto.AUTORIZADO) {
            throw new RuntimeException("La solicitud no está autorizada para retiro.");
        }
        if (preGasto.getQrToken() == null || !preGasto.getQrToken().equalsIgnoreCase(input.getQrToken())) {
            throw new RuntimeException("Código QR inválido o expirado.");
        }
        if (preGasto.getFuncionario() == null || preGasto.getFuncionario().getId() == null
                || !preGasto.getFuncionario().getId().equals(input.getFuncionarioPersonaId())) {
            throw new RuntimeException("Solo el funcionario autorizado puede confirmar el retiro.");
        }
        preGasto.setRetiroConfirmadoEn(LocalDateTime.now());
        preGasto.setRetiroConfirmadoFuncionario(personaService.findById(input.getFuncionarioPersonaId()).orElse(null));
        return super.save(preGasto);
    }

    @Transactional
    public PreGasto ejecutarRetiro(EjecutarRetiroPreGastoInput input) {
        PreGasto preGasto = repository.findByIdAndSucursalId(input.getPreGastoId(), input.getSucursalId());
        if (preGasto == null) {
            throw new RuntimeException("Solicitud de gasto no encontrada.");
        }
        if (preGasto.getEstado() != EstadoPreGasto.AUTORIZADO) {
            throw new RuntimeException("La solicitud no está autorizada para retiro.");
        }
        if (preGasto.getRetiroConfirmadoEn() == null) {
            throw new RuntimeException("El funcionario aún no confirmó el retiro desde su dispositivo móvil.");
        }
        if (preGasto.getGastoCajaRegistroId() != null) {
            throw new RuntimeException("Esta solicitud ya tiene un retiro registrado en caja.");
        }
        if (input.getGastoRegistroId() == null || input.getGastoRegistroId() <= 0) {
            throw new RuntimeException("Debe registrar el gasto en la caja local antes de finalizar el retiro.");
        }

        double[] montos = input.getLineas() != null && !input.getLineas().isEmpty()
                ? resolverMontosRetiroArrayDesdeLineas(input.getLineas())
                : calcularMontosRetiro(preGasto);
        aplicarRetiroEnPreGasto(preGasto, montos[0], montos[1], montos[2]);
        preGasto.setCajaId(input.getCajaId());
        preGasto.setGastoCajaRegistroId(input.getGastoRegistroId());
        super.save(preGasto);
        // Vincular el gasto (retiro de caja) con esta solicitud en sentido
        // inverso (gasto.preGasto -> pre_gasto_id / pre_gasto_sucursal_id) para
        // que completar() y la navegacion gasto -> solicitud puedan resolverla.
        Gasto gastoRetiro = gastoRepository.findByIdAndSucursalId(input.getGastoRegistroId(), input.getSucursalId());
        if (gastoRetiro != null && gastoRetiro.getPreGasto() == null) {
            gastoRetiro.setPreGasto(preGasto);
            gastoRepository.save(gastoRetiro);
        }
        return repository.findByIdAndSucursalId(input.getPreGastoId(), input.getSucursalId());
    }

    public boolean preGastoRetiroConfirmado(Long preGastoId, Long sucursalId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(preGastoId, sucursalId);
        return preGasto != null && preGasto.getRetiroConfirmadoEn() != null;
    }

    public List<LineaRetiroSugeridaDTO> obtenerLineasRetiroSugeridas(Long preGastoId, Long sucursalId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(preGastoId, sucursalId);
        if (preGasto == null) {
            throw new RuntimeException("Solicitud de gasto no encontrada.");
        }
        List<LineaRetiroSugeridaDTO> lineas = new ArrayList<>();
        List<PreGastoDetalleFinanzas> finanzas = preGastoDetalleFinanzasService
                .findByPreGastoIdAndSucursalId(preGastoId, sucursalId);
        if (finanzas != null && !finanzas.isEmpty()) {
            for (PreGastoDetalleFinanzas fin : finanzas) {
                if (fin == null || fin.getMonto() == null || fin.getMoneda() == null || fin.getMoneda().getId() == null) {
                    continue;
                }
                double monto = fin.getMonto().doubleValue();
                if (monto > 0) {
                    lineas.add(new LineaRetiroSugeridaDTO(fin.getMoneda().getId(), monto));
                }
            }
            return lineas;
        }
        if (preGasto.getMoneda() != null && preGasto.getMoneda().getId() != null
                && preGasto.getMontoSolicitado() != null && preGasto.getMontoSolicitado().doubleValue() > 0) {
            lineas.add(new LineaRetiroSugeridaDTO(
                    preGasto.getMoneda().getId(),
                    preGasto.getMontoSolicitado().doubleValue()));
        }
        return lineas;
    }

    public MontosRetiroPayloadDTO calcularMontosRetiroDesdeLineas(List<RetiroPreGastoLineaInput> lineas) {
        double[] montos = resolverMontosRetiroArrayDesdeLineas(lineas);
        return new MontosRetiroPayloadDTO(montos[0], montos[1], montos[2]);
    }

    private double[] resolverMontosRetiroArrayDesdeLineas(List<RetiroPreGastoLineaInput> lineas) {
        double retiroGs = 0d;
        double retiroRs = 0d;
        double retiroDs = 0d;
        if (lineas == null || lineas.isEmpty()) {
            return new double[] { retiroGs, retiroRs, retiroDs };
        }
        for (RetiroPreGastoLineaInput linea : lineas) {
            if (linea == null || linea.getMonedaId() == null || linea.getMonto() == null || linea.getMonto() <= 0) {
                continue;
            }
            Moneda moneda = monedaService.findById(linea.getMonedaId()).orElse(null);
            if (moneda == null) {
                continue;
            }
            String simbolo = moneda.getSimbolo() != null ? moneda.getSimbolo().trim().toUpperCase() : "";
            String denominacion = moneda.getDenominacion() != null ? moneda.getDenominacion().trim().toUpperCase() : "";
            double monto = linea.getMonto();
            if ("GUARANI".equals(denominacion) || simbolo.contains("GS")) {
                retiroGs += monto;
            } else if (denominacion.contains("REAL") || simbolo.contains("R$") || simbolo.contains("RS")) {
                retiroRs += monto;
            } else {
                retiroDs += monto;
            }
        }
        return new double[] { retiroGs, retiroRs, retiroDs };
    }

    private double[] calcularMontosRetiro(PreGasto preGasto) {
        double retiroGs = 0d;
        double retiroRs = 0d;
        double retiroDs = 0d;
        List<PreGastoDetalleFinanzas> finanzas = preGastoDetalleFinanzasService
                .findByPreGastoIdAndSucursalId(preGasto.getId(), preGasto.getSucursalId());
        if (finanzas != null && !finanzas.isEmpty()) {
            for (PreGastoDetalleFinanzas fin : finanzas) {
                if (fin == null || fin.getMonto() == null || fin.getMoneda() == null) {
                    continue;
                }
                String simbolo = fin.getMoneda().getSimbolo() != null
                        ? fin.getMoneda().getSimbolo().trim().toUpperCase() : "";
                String denominacion = fin.getMoneda().getDenominacion() != null
                        ? fin.getMoneda().getDenominacion().trim().toUpperCase() : "";
                double monto = fin.getMonto().doubleValue();
                if (simbolo.contains("GS") || denominacion.contains("GUARANI")) {
                    retiroGs += monto;
                } else if (simbolo.contains("R$") || simbolo.contains("RS") || denominacion.contains("REAL")) {
                    retiroRs += monto;
                } else {
                    retiroDs += monto;
                }
            }
        } else if (preGasto.getMontoSolicitado() != null) {
            BigDecimal monto = preGasto.getMontoSolicitado();
            String simbolo = preGasto.getMoneda() != null && preGasto.getMoneda().getSimbolo() != null
                    ? preGasto.getMoneda().getSimbolo().trim().toUpperCase() : "";
            String denominacion = preGasto.getMoneda() != null && preGasto.getMoneda().getDenominacion() != null
                    ? preGasto.getMoneda().getDenominacion().trim().toUpperCase() : "";
            if (simbolo.contains("GS") || denominacion.contains("GUARANI")) {
                retiroGs = monto.doubleValue();
            } else if (simbolo.contains("R$") || simbolo.contains("RS") || denominacion.contains("REAL")) {
                retiroRs = monto.doubleValue();
            } else {
                retiroDs = monto.doubleValue();
            }
        }
        return new double[] { retiroGs, retiroRs, retiroDs };
    }

    private void aplicarRetiroEnPreGasto(PreGasto preGasto, double retiroGs, double retiroRs, double retiroDs) {
        BigDecimal montoRetirado = obtenerMontoMoneda(preGasto, retiroGs, retiroRs, retiroDs);
        BigDecimal montoGastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
        preGasto.setMontoRetirado(montoRetirado);
        preGasto.setSaldoDevolver(montoRetirado.subtract(montoGastado).max(BigDecimal.ZERO));
        preGasto.setEstado(EstadoPreGasto.TRAMITE);
        preGasto.setEstadoRendicion("NO_RENDIDO");
        preGasto.setRindioGasto(false);
    }

    @Transactional
    public PreGasto registrarDevolucionSaldo(DevolucionSaldoPreGastoInput input) {
        PreGasto preGasto = repository.findByIdAndSucursalId(input.getPreGastoId(), input.getSucursalId());
        if (preGasto == null) {
            throw new RuntimeException("Solicitud de gasto no encontrada.");
        }
        if (preGasto.getEstado() != EstadoPreGasto.TRAMITE) {
            throw new RuntimeException("Solo se puede devolver saldo en solicitudes en trámite.");
        }
        BigDecimal saldo = preGasto.getSaldoDevolver() != null ? preGasto.getSaldoDevolver() : BigDecimal.ZERO;
        if (saldo.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("No hay saldo pendiente de devolución.");
        }

        double vueltoGs = input.getVueltoGs() != null ? input.getVueltoGs() : 0d;
        double vueltoRs = input.getVueltoRs() != null ? input.getVueltoRs() : 0d;
        double vueltoDs = input.getVueltoDs() != null ? input.getVueltoDs() : 0d;
        if (vueltoGs + vueltoRs + vueltoDs <= 0d) {
            throw new RuntimeException("Debe indicar el monto devuelto.");
        }

        BigDecimal devuelto = obtenerMontoMoneda(preGasto, vueltoGs, vueltoRs, vueltoDs);
        BigDecimal nuevoSaldo = saldo.subtract(devuelto);
        preGasto.setSaldoDevolver(nuevoSaldo.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : nuevoSaldo);
        return super.save(preGasto);
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
        if (simbolo.contains("USD") || simbolo.contains("US$") || "$".equals(simbolo)
                || denominacion.contains("DOLAR")) {
            return toBigDecimal(ds);
        }
        if (toBigDecimal(gs).compareTo(BigDecimal.ZERO) > 0)
            return toBigDecimal(gs);
        if (toBigDecimal(rs).compareTo(BigDecimal.ZERO) > 0)
            return toBigDecimal(rs);
        return toBigDecimal(ds);
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private BigDecimal normalizarMontoRendido(Double informado, BigDecimal retiro, BigDecimal solicitado) {
        BigDecimal valor = informado == null ? BigDecimal.ZERO : BigDecimal.valueOf(informado);
        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            valor = BigDecimal.ZERO;
        }
        BigDecimal limite = BigDecimal.ZERO;
        if (retiro != null && retiro.compareTo(BigDecimal.ZERO) > 0) {
            limite = retiro;
        } else if (solicitado != null && solicitado.compareTo(BigDecimal.ZERO) > 0) {
            limite = solicitado;
        }
        if (limite.compareTo(BigDecimal.ZERO) > 0 && valor.compareTo(limite) > 0) {
            valor = limite;
        }
        return valor;
    }

    public void recalcularEstadoRendicionPublico(PreGasto preGasto) {
        recalcularEstadoRendicion(preGasto);
    }

    private void recalcularEstadoRendicion(PreGasto preGasto) {
        BigDecimal retirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
        BigDecimal gastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
        BigDecimal solicitado = preGasto.getMontoSolicitado() != null ? preGasto.getMontoSolicitado() : BigDecimal.ZERO;
        BigDecimal baseRendicion = retirado.compareTo(BigDecimal.ZERO) > 0 ? retirado : solicitado;

        if (baseRendicion.compareTo(BigDecimal.ZERO) <= 0 || gastado.compareTo(BigDecimal.ZERO) <= 0) {
            preGasto.setEstadoRendicion("NO_RENDIDO");
            preGasto.setRindioGasto(false);
            return;
        }
        if (gastado.compareTo(baseRendicion) >= 0) {
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

    private void validarMontoSolicitadoActivo(BigDecimal montoSolicitado, EnteFinanciero financiero) {
        if (montoSolicitado == null) {
            return;
        }

        List<EnteCuota> cuotasPendientes = enteCuotaService.findPendientesByEnteFinancieroId(financiero.getId());
        if (!cuotasPendientes.isEmpty()) {
            EnteCuota siguiente = cuotasPendientes.get(0);
            BigDecimal montoCuota = siguiente.getMonto() != null ? siguiente.getMonto() : BigDecimal.ZERO;
            if (montoSolicitado.compareTo(montoCuota) > 0) {
                throw new RuntimeException(
                        "El monto solicitado supera el monto de la cuota pendiente (" + montoCuota + ").");
            }
            return;
        }

        BigDecimal pendiente = ActivoPagoNormalizer.calcularMontoPendiente(
                financiero.getMontoTotal(), financiero.getMontoYaPagado());
        if (montoSolicitado.compareTo(pendiente) > 0) {
            throw new RuntimeException(
                    "El monto solicitado supera el saldo pendiente del activo (" + pendiente + ").");
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
        return getFinancialSummary(enteId, null);
    }

    public EnteFinancialSummaryDTO getFinancialSummary(Long enteId, Long tipoGastoId) {
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
                    if (m.getProveedor() != null) {
                        asignarProveedorDesdeEntidad(dto, m.getProveedor());
                    }
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
                    if (i.getProveedor() != null) {
                        asignarProveedorDesdeEntidad(dto, i.getProveedor());
                    } else if (i.getAlquilerProveedor() != null) {
                        asignarProveedorDesdePersona(dto, i.getAlquilerProveedor());
                    }
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
                    if (v.getProveedor() != null) {
                        asignarProveedorDesdeEntidad(dto, v.getProveedor());
                    }
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
            } else if (ente.getTipoEnte() == TipoEnte.EQUIPO) {
                Equipo eq = equipoService.findById(ente.getReferenciaId()).orElse(null);
                if (eq != null) {
                    EquipoFinanciero fin = equipoService.resolverFinanciero(eq);
                    dto.setDescripcion(eq.getDescripcion() != null ? eq.getDescripcion() : eq.getIdentificador());
                    if (fin != null) {
                        if (fin.getProveedor() != null) {
                            asignarProveedorDesdeEntidad(dto, fin.getProveedor());
                        }
                        dto.setSituacionPago(fin.getSituacionPago());

                        if (dto.getMontoTotal() == null) {
                            dto.setMontoTotal(fin.getMontoTotal());
                        }
                        if (dto.getMontoYaPagado() == null) {
                            dto.setMontoYaPagado(fin.getMontoYaPagado());
                        }
                        if (dto.getMonedaId() == null && fin.getMoneda() != null) {
                            dto.setMonedaId(fin.getMoneda().getId());
                            dto.setMonedaSimbolo(fin.getMoneda().getSimbolo());
                        }

                        llenarDatosCuotas(dto, fin.getCantidadCuotas(), fin.getCantidadCuotasPagadas(), fin.getDiaVencimiento());
                    }
                }
                dto.setTipoGastoSugeridoId("VARIABLE");
            }
        }
        BigDecimal total = dto.getMontoTotal() != null ? dto.getMontoTotal() : BigDecimal.ZERO;
        BigDecimal pagado = dto.getMontoYaPagado() != null ? dto.getMontoYaPagado() : BigDecimal.ZERO;
        BigDecimal pendiente = total.subtract(pagado);
        dto.setMontoPendiente(pendiente.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : pendiente);
        ActivoPagoNormalizer.normalizarResumenSiPagadoCompleto(dto);

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

        enriquecerConCuotaPendiente(dto, optFinanciero);

        if (dto.getProveedorId() == null && optFinanciero.isPresent()) {
            asignarProveedorDesdePersona(dto, optFinanciero.get().getProveedor());
        }

        if (tipoGastoId != null) {
            tipoGastoService.findById(tipoGastoId).ifPresent(tipo -> aplicarContextoTipoGasto(dto, tipo));
        }

        if (dto.getFechaVencimientoSugerida() == null && dto.getDiaVencimiento() != null) {
            dto.setFechaVencimientoSugerida(calcularProximaFechaVencimiento(dto.getDiaVencimiento()));
        }

        return dto;
    }

    private void enriquecerConCuotaPendiente(EnteFinancialSummaryDTO dto, Optional<EnteFinanciero> optFinanciero) {
        if (!optFinanciero.isPresent()) {
            return;
        }
        List<EnteCuota> cuotasPendientes = enteCuotaService
                .findPendientesByEnteFinancieroId(optFinanciero.get().getId());
        if (cuotasPendientes.isEmpty()) {
            return;
        }
        EnteCuota siguiente = cuotasPendientes.get(0);
        if (siguiente.getMonto() != null) {
            dto.setMontoSugerido(siguiente.getMonto());
        }
        if (siguiente.getNumeroCuota() != null) {
            dto.setNumeroCuotaActual(siguiente.getNumeroCuota());
        }
        if (siguiente.getFechaVencimiento() != null) {
            dto.setFechaVencimientoSugerida(siguiente.getFechaVencimiento());
        }
        BigDecimal sumaPendiente = cuotasPendientes.stream()
                .map(c -> c.getMonto() != null ? c.getMonto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumaPendiente.compareTo(BigDecimal.ZERO) > 0) {
            dto.setMontoPendiente(sumaPendiente);
            dto.setCuotasFaltantes(cuotasPendientes.size());
        }
    }

    private void aplicarContextoTipoGasto(EnteFinancialSummaryDTO dto, TipoGasto tipo) {
        TipoPadreGastoModulo modulo = tipo.getModuloPadre();
        TipoNaturalezaGasto naturaleza = tipo.getTipoNaturaleza();
        boolean esCuota = Boolean.TRUE.equals(tipo.getEsPagoCuotaActivo());

        if (moduloReglasService.montoVariableEnContinuo(modulo, naturaleza)) {
            dto.setAutocompletarMonto(false);
            dto.setMontoSugerido(null);
            String etiqueta = moduloReglasService.etiquetaModulo(modulo);
            dto.setDescripcionSugerida("Pago " + etiqueta + " - "
                    + (dto.getDescripcion() != null ? dto.getDescripcion() : ""));
            return;
        }

        if (esCuota) {
            dto.setAutocompletarMonto(true);
            if (dto.getNumeroCuotaActual() == null && dto.getCuotasPagadas() != null
                    && dto.getCuotasTotales() != null && dto.getCuotasTotales() > 0) {
                dto.setNumeroCuotaActual(dto.getCuotasPagadas() + 1);
            }
            if (dto.getNumeroCuotaActual() != null && dto.getCuotasTotales() != null
                    && dto.getDescripcion() != null) {
                dto.setDescripcionSugerida(String.format("Cuota %d/%d - %s",
                        dto.getNumeroCuotaActual(), dto.getCuotasTotales(), dto.getDescripcion()));
            }
            return;
        }

        dto.setAutocompletarMonto(false);
    }

    private LocalDate calcularProximaFechaVencimiento(int diaVencimiento) {
        LocalDate hoy = LocalDate.now();
        int dia = Math.min(Math.max(diaVencimiento, 1), 31);
        LocalDate candidato = hoy.withDayOfMonth(Math.min(dia, hoy.lengthOfMonth()));
        if (!candidato.isAfter(hoy)) {
            LocalDate siguienteMes = hoy.plusMonths(1);
            candidato = siguienteMes.withDayOfMonth(Math.min(dia, siguienteMes.lengthOfMonth()));
        }
        return candidato;
    }

    private void asignarProveedorDesdeEntidad(EnteFinancialSummaryDTO dto, Proveedor proveedor) {
        if (proveedor == null) {
            return;
        }
        dto.setProveedorId(proveedor.getId());
        if (proveedor.getPersona() != null && proveedor.getPersona().getNombre() != null) {
            dto.setProveedorNombre(proveedor.getPersona().getNombre());
        }
    }

    private void asignarProveedorDesdePersona(EnteFinancialSummaryDTO dto, Persona persona) {
        if (persona == null) {
            return;
        }
        if (persona.getNombre() != null) {
            dto.setProveedorNombre(persona.getNombre());
        }
        Proveedor proveedor = proveedorService.findByPersonaId(persona.getId());
        if (proveedor != null) {
            dto.setProveedorId(proveedor.getId());
        }
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
        return Arrays.asList(
                new PreGastoStatusMetadataDTO("PENDIENTE", "Pendiente", "hourglass_empty", "#ffa726"),
                new PreGastoStatusMetadataDTO("TRAMITE", "En Trámite", "swap_horiz", "#42a5f5"),
                new PreGastoStatusMetadataDTO("AUTORIZADO", "Autorizado", "check_circle", "#66bb6a"),
                new PreGastoStatusMetadataDTO("ENVIADO_A_TESORERIA", "Enviado a Tesorería", "send", "#26a69a"),
                new PreGastoStatusMetadataDTO("PAGADO", "Pagado", "paid", "#4caf50"),
                new PreGastoStatusMetadataDTO("RECHAZADO", "Rechazado", "cancel", "#ef5350"),
                new PreGastoStatusMetadataDTO("COMPLETADO", "Completado", "task_alt", "#78909c"));
    }
}
