package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.CajaBalance;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.SolicitudAperturaCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.graphql.financiero.input.PdvCajaInput;
import com.franco.dev.service.financiero.ConteoService;
import com.franco.dev.service.financiero.FilialCajaProxyService;
import com.franco.dev.service.financiero.MaletinService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.domain.empresarial.Sucursal;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PdvCajaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PdvCajaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ConteoService conteoService;

    @Autowired
    private MaletinService maletinService;

    @Autowired
    private ConteoGraphQL conteoGraphQL;

    @Autowired
    private ConteoMonedaGraphQL conteoMonedaGraphQL;


    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FilialCajaProxyService filialCajaProxyService;

    private static final Logger log = LoggerFactory.getLogger(PdvCajaGraphQL.class);

    public PdvCaja pdvCaja(Long id, Long sucId) {
        PdvCaja aux = service.findById(id, sucId);
        return aux;
    }

    public List<PdvCaja> pdvCajas(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<PdvCaja> cajasPorFecha(String inicio, String fin, Long sucId) {
        return service.findByDate(inicio, fin, sucId);
    }

    public Page<PdvCaja> cajasWithFilters(Long cajaId, PdvCajaEstado estado, Long maletinId, Long cajeroId, String fechaInicio, String fechaFin, Long sucId, Boolean verificado, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAllWithFilters(cajaId, estado, maletinId, cajeroId, fechaInicio, fechaFin, sucId, verificado, pageable);
    }

    public Page<PdvCaja> cajasAnalisisDiferencias(Long cajaId, Long cajaAnteriorId, PdvCajaEstado estado, Long maletinId, String maletinDescripcion, Long cajeroId, String fechaInicio, String fechaFin, Long sucId, Boolean verificado, String difEstado, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAllForAnalisisDiferencias(cajaId, cajaAnteriorId, estado, maletinId, maletinDescripcion, cajeroId, fechaInicio, fechaFin, sucId, verificado, difEstado, pageable);
    }

    public CajaBalance balancePorFecha(String inicio, String fin, Long sucId) {
        List<PdvCaja> pdvCajaList = service.findByDate(inicio, fin, sucId);
        Double totalGeneral = 0.0;
        for (PdvCaja c : pdvCajaList) {
            CajaBalance cb = service.getBalance(new EmbebedPrimaryKey(c.getId(), sucId));
            totalGeneral += cb.getTotalGeneral();
        }
        CajaBalance cajaBalance = new CajaBalance();
        cajaBalance.setTotalGeneral(totalGeneral);
        return cajaBalance;
    }

    public CajaBalance balancePorCajaIdAndSucursalId(Long id, Long sucId){
        CajaBalance cajaBalance = service.getBalance(new EmbebedPrimaryKey(id, sucId));
        return cajaBalance;
    }

    /**
     * Get a list of balances for all active cajas in a specific sucursal
     * @param sucursalId The ID of the sucursal
     * @return A list of CajaBalance objects, one for each active caja
     */
    public List<CajaBalance> balanceActiveCajasBySucursalId(Long sucursalId) {
        return service.getBalanceActiveCajasBySucursalId(sucursalId);
    }

    public PdvCaja savePdvCaja(PdvCajaInput input) {
        ModelMapper m = new ModelMapper();
        PdvCaja e = m.map(input, PdvCaja.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getVerificadoPorId() != null) {
            e.setVerificadoPor(usuarioService.findById(input.getVerificadoPorId()).orElse(null));
        }
        if (input.getConteoAperturaId() != null)
            e.setConteoApertura(conteoService.findById(new EmbebedPrimaryKey(input.getConteoAperturaId(), input.getSucursalId())).orElse(null));
        if (input.getConteoCierreId() != null)
            e.setConteoCierre(conteoService.findById(new EmbebedPrimaryKey(input.getConteoCierreId(), input.getSucursalId())).orElse(null));
        if (input.getMaletinId() != null) e.setMaletin(maletinService.findById(input.getMaletinId()).orElse(null));
        service.save(e);
        return e;
    }

    public PdvCaja savePdvCajaPorSucursal(PdvCajaInput input) {
        ModelMapper m = new ModelMapper();
        PdvCaja e = m.map(input, PdvCaja.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getConteoAperturaId() != null)
            e.setConteoApertura(conteoService.findById(new EmbebedPrimaryKey(input.getConteoAperturaId(), input.getSucursalId())).orElse(null));
        if (input.getConteoCierreId() != null)
            e.setConteoCierre(conteoService.findById(new EmbebedPrimaryKey(input.getConteoCierreId(), input.getSucursalId())).orElse(null));
        if (input.getMaletinId() != null) e.setMaletin(maletinService.findById(input.getMaletinId()).orElse(null));
        service.save(e);
        return e;
    }

    //    public List<PdvCaja> pdvCajasSearch(String texto){
    //        return service.findByAll(texto);
    //    }

    public Long countPdvCaja() {
        return service.count();
    }

    public List<PdvCaja> cajaAbiertoPorUsuarioId(Long id) {
        return service.findByUsuarioIdAndAbierto(id);
    }

    public PdvCaja cajaAbiertoPorUsuarioIdPorSucursal(Long id, Long sucId) {
        return filialCajaProxyService.cajaAbiertoPorUsuarioIdPorSucursal(id, sucId);
    }

    public List<PdvCaja> cajasAbiertasPorUsuarioDesdeFiliales(Long id) {
        return filialCajaProxyService.cajasAbiertasPorUsuarioDesdeFiliales(id);
    }

    public PdvCaja pdvCajaDesdeFilial(Long id, Long sucursalId) {
        return filialCajaProxyService.pdvCajaDesdeFilial(id, sucursalId);
    }

    /**
     * Find all active cajas for a specific sucursal
     * @param sucursalId The ID of the sucursal
     * @return A list of active PdvCaja objects for the specified sucursal
     */
    public List<PdvCaja> cajaAbiertoPorSucursal(Long sucursalId) {
        return service.findActiveBySucursalId(sucursalId);
    }

    public PdvCaja imprimirBalance(Long id, String printerName, String local, Long sucId) {
        // Ruteo a la filial SOLO cuando el caller no especifica impresora (frc-mobile envia
        // printerName=null y la filial resuelve su propia config). El desktop siempre envia
        // su printerName local y debe imprimir con esa config, igual que reimprimirVenta.
        boolean sinImpresora = printerName == null || printerName.isBlank();
        if (sucId != null && sinImpresora) {
            Sucursal sucursal = sucursalService.findById(sucId).orElse(null);
            if (sucursal != null && sucursal.getIp() != null && !sucursal.getIp().isBlank()) {
                return filialCajaProxyService.imprimirBalanceEnFilial(id, sucId, printerName, local);
            }
        }
        return service.imprimirBalance(new EmbebedPrimaryKey(id, sucId), printerName, local);
    }

    public List<PdvCaja> cajasPorUsuarioId(Long id, Integer page, Integer size) {
        return service.findByUsuarioId(id, page, size);
    }

    public CajaFilialOperacionResult abrirCajaDesdeServidor(PdvCajaInput input, ConteoInput conteoInput, List<ConteoMonedaInput> conteoMonedaInputList, Long cajaId) {
        boolean esCierre = cajaId != null;
        String logPrefix = esCierre ? "CERRAR CAJA" : "ABRIR CAJA";
        long inicioMs = System.currentTimeMillis();
        log.info("====== [{}] INICIO abrirCajaDesdeServidor (esCierre={}) ======", logPrefix, esCierre);
        if (input == null) {
            log.error("[{}] El input de la caja es null. Abortando.", logPrefix);
            return CajaFilialOperacionResult.fail();
        }
        if (esCierre) {
            log.info("[{}] Parametros recibidos -> cajaId={}, sucursalId={}", logPrefix, cajaId, input.getSucursalId());
        } else {
            log.info("[{}] Parametros recibidos -> sucursalId={}, maletinId={}, usuarioId={}, estado={}, activo={}, fechaApertura={}",
                    logPrefix, input.getSucursalId(), input.getMaletinId(), input.getUsuarioId(), input.getEstado(), input.getActivo(), input.getFechaApertura());
        }
        if (conteoInput != null) {
            log.info("[{}] ConteoInput -> usuarioId={}, totalGs={}, totalRs={}, totalDs={}, observacion={}",
                    logPrefix, conteoInput.getUsuarioId(), conteoInput.getTotalGs(), conteoInput.getTotalRs(), conteoInput.getTotalDs(), conteoInput.getObservacion());
        } else {
            log.warn("[{}] conteoInput es null", logPrefix);
            return CajaFilialOperacionResult.fail();
        }
        log.info("[{}] Cantidad de denominaciones en conteoMonedaInputList: {}",
                logPrefix, conteoMonedaInputList != null ? conteoMonedaInputList.size() : "null");
        try {
            if (input.getSucursalId() == null) {
                log.error("[{}] sucursalId es null. El frontend NO envio la sucursal seleccionada. Abortando.", logPrefix);
                return CajaFilialOperacionResult.fail();
            }
            Sucursal sucursal = sucursalService.findById(input.getSucursalId()).orElse(null);
            if (sucursal == null) {
                log.error("[{}] Sucursal no encontrada para id: {}", logPrefix, input.getSucursalId());
                return CajaFilialOperacionResult.fail();
            }
            log.info("[{}] Sucursal encontrada -> id={}, nombre={}, ip={}",
                    logPrefix, sucursal.getId(), sucursal.getNombre(), sucursal.getIp());
            if (sucursal.getIp() == null) {
                log.error("[{}] La sucursal id={} ({}) NO tiene IP configurada. Revisar columna ip en empresarial.sucursal.",
                        logPrefix, sucursal.getId(), sucursal.getNombre());
                return CajaFilialOperacionResult.fail();
            }
            String url = filialCajaProxyService.buildFilialUrl(sucursal);
            log.info("[{}] URL filial a conectar: {}", logPrefix, url);

            HttpHeaders headers = filialCajaProxyService.buildAuthHeaders();
            if (headers.get("Authorization") != null) {
                log.info("[{}] Header Authorization reenviado a la filial", logPrefix);
            } else {
                log.warn("[{}] No se encontro Header Authorization en la peticion original", logPrefix);
            }

            Long cajaIdFilial = cajaId;
            if (esCierre) {
                Long cajaAbiertaFilial = filialCajaProxyService.consultarCajaAbiertaIdEnFilial(
                        url, headers, conteoInput.getUsuarioId(), input.getSucursalId(), logPrefix);
                if (cajaAbiertaFilial == null) {
                    log.error("[{}] No hay caja abierta en filial para usuarioId={}. cajaId solicitado={}",
                            logPrefix, conteoInput.getUsuarioId(), cajaId);
                    return CajaFilialOperacionResult.fail();
                }
                if (!cajaAbiertaFilial.equals(cajaId)) {
                    log.warn("[{}] cajaId del mobile ({}) difiere de la caja abierta en filial ({}). Usando filial.",
                            logPrefix, cajaId, cajaAbiertaFilial);
                }
                cajaIdFilial = cajaAbiertaFilial;
            }
            if (!esCierre) {
                if (input.getActivo() == null) {
                    input.setActivo(true);
                }
                if (input.getFechaApertura() == null) {
                    input.setFechaApertura(java.time.LocalDateTime.now());
                }

                String saveCajaQuery = "mutation($pdvCaja: PdvCajaInput!) { savePdvCaja(pdvCaja: $pdvCaja) { id } }";
                Map<String, Object> variablesCaja = new HashMap<>();

                Map<String, Object> pdvCajaMap = objectMapper.convertValue(input, Map.class);
                pdvCajaMap.remove("tipoEntrada");
                pdvCajaMap.remove("totalGs");
                pdvCajaMap.remove("totalRs");
                pdvCajaMap.remove("totalDs");
                pdvCajaMap.remove("estado");

                variablesCaja.put("pdvCaja", pdvCajaMap);

                Map<String, Object> bodyCaja = new HashMap<>();
                bodyCaja.put("query", saveCajaQuery);
                bodyCaja.put("variables", variablesCaja);

                String requestCajaJson = objectMapper.writeValueAsString(bodyCaja);
                log.info("[{}] >>> Enviando mutation savePdvCaja a filial ({}): {}", logPrefix, url, requestCajaJson);

                HttpEntity<String> requestCaja = new HttpEntity<>(requestCajaJson, headers);
                ResponseEntity<Map> responseCaja = restTemplate.postForEntity(url, requestCaja, Map.class);

                log.info("[{}] <<< Respuesta savePdvCaja de filial. Status: {}, Body: {}",
                        logPrefix, responseCaja.getStatusCode(), responseCaja.getBody());

                if (responseCaja.getBody() == null || responseCaja.getBody().containsKey("errors")) {
                    log.error("[{}] Error al crear caja en filial (mutation 1). Response body: {}", logPrefix, responseCaja.getBody());
                    return CajaFilialOperacionResult.fail();
                }

                Map<String, Object> dataCaja = (Map<String, Object>) responseCaja.getBody().get("data");
                if (dataCaja == null || dataCaja.get("savePdvCaja") == null) {
                    log.error("[{}] Respuesta inesperada al crear caja (sin data.savePdvCaja): {}", logPrefix, responseCaja.getBody());
                    return CajaFilialOperacionResult.fail();
                }
                Map<String, Object> saveCajaResponse = (Map<String, Object>) dataCaja.get("savePdvCaja");
                cajaIdFilial = Long.valueOf(saveCajaResponse.get("id").toString());
                log.info("[{}] Caja creada exitosamente en filial con ID: {} (sucursalId={})", logPrefix, cajaIdFilial, input.getSucursalId());
            }

            String saveConteoQuery = "mutation($conteo: ConteoInput!, $conteoMonedaInputList: [ConteoMonedaInput], $cajaId: Int, $apertura: Boolean, $imprimirBalance: Boolean) { saveConteo(conteo: $conteo, conteoMonedaInputList: $conteoMonedaInputList, cajaId: $cajaId, apertura: $apertura, imprimirBalance: $imprimirBalance) { id } }";
            Map<String, Object> variablesConteo = new HashMap<>();
            variablesConteo.put("conteo", conteoInput);
            variablesConteo.put("conteoMonedaInputList", conteoMonedaInputList);
            variablesConteo.put("cajaId", cajaIdFilial);
            variablesConteo.put("apertura", !esCierre);
            variablesConteo.put("imprimirBalance", false);

            Map<String, Object> bodyConteo = new HashMap<>();
            bodyConteo.put("query", saveConteoQuery);
            bodyConteo.put("variables", variablesConteo);

            String requestConteoJson = objectMapper.writeValueAsString(bodyConteo);
            log.info("[{}] >>> Enviando mutation saveConteo a filial (cajaId={}): {}", logPrefix, cajaIdFilial, requestConteoJson);

            HttpEntity<String> requestConteo = new HttpEntity<>(requestConteoJson, headers);
            ResponseEntity<Map> responseConteo = restTemplate.postForEntity(url, requestConteo, Map.class);

            log.info("[{}] <<< Respuesta saveConteo de filial. Status: {}, Body: {}",
                    logPrefix, responseConteo.getStatusCode(), responseConteo.getBody());

            if (responseConteo.getBody() == null || responseConteo.getBody().containsKey("errors")) {
                log.error("[{}] Error al guardar conteo en filial. cajaId={}. Response body: {}",
                        logPrefix, cajaIdFilial, responseConteo.getBody());
                return CajaFilialOperacionResult.fail();
            }

            log.info("[{}] ====== EXITO: {} de caja completada en filial. cajaId={}, sucursalId={}, tiempo={}ms ======",
                    logPrefix, esCierre ? "cierre" : "apertura", cajaIdFilial, input.getSucursalId(), (System.currentTimeMillis() - inicioMs));
            return CajaFilialOperacionResult.ok(cajaIdFilial);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("[{}] FALLO DE CONEXION con la filial (sucursalId={}). No se pudo alcanzar la IP/puerto. Detalle: {}. Tiempo transcurrido={}ms",
                    logPrefix, input.getSucursalId(), e.getMessage(), (System.currentTimeMillis() - inicioMs), e);
            return CajaFilialOperacionResult.fail();
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("[{}] Error de cliente REST al comunicarse con la filial (sucursalId={}). Detalle: {}",
                    logPrefix, input.getSucursalId(), e.getMessage(), e);
            return CajaFilialOperacionResult.fail();
        } catch (Exception e) {
            log.error("[{}] Excepcion inesperada en proxy abrirCajaDesdeServidor (sucursalId={}). Detalle: {}",
                    logPrefix, input.getSucursalId(), e.getMessage(), e);
            return CajaFilialOperacionResult.fail();
        }
    }

    /**
     * Corrige los montos de un conteo de apertura o cierre ya cargado. Solo para rol ADMIN.
     * <p>
     * La escritura se delega a la filial, que es la duena del dato: alli se crea una nueva version
     * del conteo enlazada a la anterior y el central la recibe por replicacion. Si la filial rechaza
     * la operacion, su mensaje se propaga tal cual para que el usuario sepa por que.
     */
    public CajaFilialOperacionResult editarConteoCajaDesdeServidor(Long cajaId, Long sucursalId, Long conteoAnteriorId,
                                                                  Boolean apertura, ConteoInput conteoInput,
                                                                  List<ConteoMonedaInput> conteoMonedaInputList) {
        String logPrefix = "EDITAR CONTEO CAJA";
        long inicioMs = System.currentTimeMillis();
        log.info("====== [{}] INICIO -> cajaId={}, sucursalId={}, conteoAnteriorId={}, apertura={} ======",
                logPrefix, cajaId, sucursalId, conteoAnteriorId, apertura);

        if (conteoInput == null || conteoInput.getUsuarioId() == null) {
            throw new GraphQLException("No se recibio el usuario que realiza la edicion");
        }
        if (sucursalId == null) {
            throw new GraphQLException("No se recibio la sucursal de la caja");
        }
        Long usuarioId = conteoInput.getUsuarioId();
        if (!usuarioService.tieneRol(usuarioId, "ADMIN")) {
            log.warn("[{}] Usuario id={} sin rol ADMIN intento editar el conteo de la caja id={}", logPrefix, usuarioId, cajaId);
            throw new GraphQLException("Solo un usuario ADMIN puede editar los montos de una caja");
        }
        // Chequeo temprano contra la copia replicada, para no salir a la red si la caja ya esta congelada.
        PdvCaja cajaLocal = service.findById(cajaId, sucursalId);
        if (cajaLocal != null && Boolean.TRUE.equals(cajaLocal.getVerificado())) {
            throw new GraphQLException("La caja ya fue verificada, no se pueden editar sus montos");
        }

        try {
            Long nuevoConteoId = filialCajaProxyService.editarConteoEnFilial(cajaId, sucursalId, conteoAnteriorId,
                    apertura, conteoInput, conteoMonedaInputList, usuarioId);
            log.info("[{}] ====== EXITO. cajaId={}, nuevoConteoId={}, tiempo={}ms ======",
                    logPrefix, cajaId, nuevoConteoId, (System.currentTimeMillis() - inicioMs));
            return CajaFilialOperacionResult.ok(cajaId);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("[{}] FALLO DE CONEXION con la filial (sucursalId={}). Detalle: {}", logPrefix, sucursalId, e.getMessage(), e);
            throw new GraphQLException("No se pudo conectar con la sucursal. No se modifico ningun monto.");
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("[{}] Error de cliente REST con la filial (sucursalId={}). Detalle: {}", logPrefix, sucursalId, e.getMessage(), e);
            throw new GraphQLException("Error de comunicacion con la sucursal. No se modifico ningun monto.");
        } catch (Exception e) {
            log.error("[{}] No se pudo editar el conteo (cajaId={}, sucursalId={}). Detalle: {}",
                    logPrefix, cajaId, sucursalId, e.getMessage(), e);
            throw new GraphQLException(e.getMessage());
        }
    }

    public Boolean verificarCaja(Long cajaId, Long sucursalId, Long usuarioId, Boolean verificado){
        try {
            PdvCaja caja = service.findById(cajaId, sucursalId);
            Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
            caja.setVerificado(verificado);
            caja.setVerificadoPor(usuario);
            service.save(caja);
            return true;
        } catch (Exception e){
            throw new GraphQLException("No se pudo verificar la caja");
        }
    }

    public List<PdvCaja> findCajasWithVentaObservaciones () {
        return service.findCajasWithVentaObservaciones();
    }

}
