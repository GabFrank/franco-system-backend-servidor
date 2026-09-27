package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ConteoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ConteoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private ConteoMonedaGraphQL conteoMonedaGraphQL;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private FilialCajaProxyService filialCajaProxyService;

    @Autowired
    private TesoreriaSecurityService tesoreriaSecurityService;

    /** El rol que habilita la pantalla de Cajas del desktop (ROLES.ANALISIS_DE_CAJA). ADMIN pasa siempre. */
    static final String ROL_ANALISIS_DE_CAJA = "ANALISIS DE CAJA";

    private static final Logger log = LoggerFactory.getLogger(ConteoGraphQL.class);

    public Optional<Conteo> conteo(Long id, Long sucId) {
        return service.findById(new EmbebedPrimaryKey(id, sucId));
    }

    public List<Conteo> conteos(int page, int size, Long sucId){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    /**
     * Carga el conteo de apertura o cierre de una caja desde el admin del desktop.
     * <p>
     * El central no escribe el conteo: la caja vive en la filial de su sucursal, que es la que
     * guarda el conteo, lo enlaza y crea los movimientos. Aca se autoriza y se reenvia; el central
     * recibe el resultado por replicacion. Si la filial rechaza la operacion, su mensaje llega tal cual.
     */
    public Conteo saveConteo(ConteoInput input, List<ConteoMonedaInput> conteoMonedaInputList, Long cajaId, Boolean apertura){
        tesoreriaSecurityService.requireAnyRole(ROL_ANALISIS_DE_CAJA);
        if (input == null) {
            throw new GraphQLException("No se recibio el conteo");
        }
        if (input.getSucursalId() == null) {
            throw new GraphQLException("No se recibio la sucursal de la caja");
        }
        if (input.getUsuarioId() == null) {
            // El dialogo de alta no manda el usuario: el conteo queda a nombre de quien lo carga.
            Usuario actual = tesoreriaSecurityService.currentUsuario();
            input.setUsuarioId(actual != null ? actual.getId() : null);
        }
        log.info("[SAVE CONTEO] cajaId={}, sucursalId={}, apertura={}, usuarioId={}",
                cajaId, input.getSucursalId(), apertura, input.getUsuarioId());
        try {
            return filialCajaProxyService.guardarConteoEnFilial(cajaId, input.getSucursalId(), apertura,
                    input, conteoMonedaInputList);
        } catch (ResourceAccessException e) {
            log.error("[SAVE CONTEO] Fallo de conexion con la filial (sucursalId={}). {}", input.getSucursalId(), e.getMessage(), e);
            // Un timeout de lectura puede llegar con el conteo ya confirmado en la filial: no afirmar que no se guardo.
            throw new GraphQLException("No se pudo confirmar el conteo con la sucursal. Vuelva a abrir la caja para ver si quedo guardado.");
        } catch (RestClientException e) {
            log.error("[SAVE CONTEO] Error de comunicacion con la filial (sucursalId={}). {}", input.getSucursalId(), e.getMessage(), e);
            throw new GraphQLException("Error de comunicacion con la sucursal. Vuelva a abrir la caja para ver si quedo guardado.");
        } catch (Exception e) {
            log.error("[SAVE CONTEO] No se pudo guardar el conteo (cajaId={}, sucursalId={}). {}",
                    cajaId, input.getSucursalId(), e.getMessage(), e);
            throw new GraphQLException(e.getMessage());
        }
    }

    public Boolean deleteConteo(Long id, Long sucId){
        return service.deleteById(new EmbebedPrimaryKey(id, sucId));
    }

    public Long countConteo(){
        return service.count();
    }


}
