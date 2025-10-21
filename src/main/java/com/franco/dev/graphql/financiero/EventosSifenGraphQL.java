package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.EventoCancelacionDE;
import com.franco.dev.domain.financiero.EventoNominacionDE;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.service.financiero.EventoCancelacionDEService;
import com.franco.dev.service.financiero.EventoNominacionDEService;
import com.franco.dev.service.financiero.TimbradoService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.sifen.SifenEventoService;
import com.roshka.sifen.core.types.TTiDE;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.List;

/**
 * GraphQL Resolver para eventos de SIFEN (cancelación, nominación, inutilización).
 */
@Slf4j
@Component
@AllArgsConstructor
public class EventosSifenGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SifenEventoService sifenEventoService;

    @Autowired
    private EventoCancelacionDEService eventoCancelacionDEService;

    @Autowired
    private EventoNominacionDEService eventoNominacionDEService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private TimbradoService timbradoService;

    // ========== QUERIES ==========

    public EventoCancelacionDE eventoCancelacion(Long id, Long sucursalId) {
        return eventoCancelacionDEService.findById(new EmbebedPrimaryKey(id, sucursalId)).orElse(null);
    }

    public List<EventoCancelacionDE> eventosCancelacionPorDocumento(Long documentoId, Long sucursalId) {
        return eventoCancelacionDEService.findByDocumentoElectronicoId(documentoId, sucursalId);
    }

    public List<EventoCancelacionDE> eventosCancelacionPorEstado(EstadoEvento estado, Long sucursalId) {
        return eventoCancelacionDEService.findByEstado(estado, sucursalId);
    }

    public EventoNominacionDE eventoNominacion(Long id, Long sucursalId) {
        return eventoNominacionDEService.findById(new EmbebedPrimaryKey(id, sucursalId)).orElse(null);
    }

    public List<EventoNominacionDE> eventosNominacionPorDocumento(Long documentoId, Long sucursalId) {
        return eventoNominacionDEService.findByDocumentoElectronicoId(documentoId, sucursalId);
    }

    public List<EventoNominacionDE> eventosNominacionPorEstado(EstadoEvento estado) {
        return eventoNominacionDEService.findByEstado(estado);
    }

    // ========== MUTATIONS ==========

    @Transactional
    public EventoCancelacionDE cancelarDocumentoElectronico(String cdc, String motivo) {
        try {
            log.info("Iniciando cancelación de DE con CDC: {}", cdc);
            
            // El servicio crea el evento, lo envía a SIFEN y actualiza su estado
            sifenEventoService.cancelarDE(cdc, motivo);
            
            // Buscar el evento recién creado en la BD
            EventoCancelacionDE evento = eventoCancelacionDEService.findActivosByCdcDocumento(cdc)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No se pudo recuperar el evento de cancelación creado"));
            
            log.info("Cancelación completada. Estado: {}, Código: {}", 
                evento.getEstado(), evento.getCodigoRespuesta());
            
            return evento;
            
        } catch (Exception e) {
            log.error("Error al cancelar DE: {}", e.getMessage(), e);
            throw new RuntimeException("Error al cancelar documento electrónico: " + e.getMessage(), e);
        }
    }

    @Transactional
    public EventoNominacionDE nominarReceptorDocumento(String cdc, Long clienteId) {
        try {
            log.info("Iniciando nominación de receptor para DE con CDC: {}", cdc);
            
            Cliente cliente = clienteService.findById(clienteId).orElse(null);
            if (cliente == null) {
                throw new IllegalArgumentException("Cliente no encontrado con ID: " + clienteId);
            }
            
            // El servicio crea el evento, lo envía a SIFEN y actualiza su estado
            sifenEventoService.nominarReceptor(cdc, cliente);
            
            // Buscar el evento recién creado en la BD
            EventoNominacionDE evento = eventoNominacionDEService.findActivosByCdcDocumento(cdc)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No se pudo recuperar el evento de nominación creado"));
            
            log.info("Nominación completada. Estado: {}, Código: {}", 
                evento.getEstado(), evento.getCodigoRespuesta());
            
            return evento;
            
        } catch (Exception e) {
            log.error("Error al nominar receptor: {}", e.getMessage(), e);
            throw new RuntimeException("Error al nominar receptor: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Boolean inutilizarNumeros(
            Long timbradoId,
            String establecimiento,
            String puntoExpedicion,
            Integer numeroInicio,
            Integer numeroFin,
            String motivo) {
        
        try {
            log.info("Iniciando inutilización de números del {} al {} para timbrado ID: {}", 
                numeroInicio, numeroFin, timbradoId);
            
            // Buscar el timbrado
            Timbrado timbrado = timbradoService.findById(timbradoId).orElse(null);
            if (timbrado == null) {
                throw new IllegalArgumentException("Timbrado no encontrado con ID: " + timbradoId);
            }
            
            // Determinar el tipo de documento (por defecto FACTURA)
            TTiDE tipoDocumento = TTiDE.FACTURA_ELECTRONICA;
            
            // El servicio envía la inutilización a SIFEN
            sifenEventoService.inutilizarNumeros(
                timbrado,
                establecimiento,
                puntoExpedicion,
                numeroInicio,
                numeroFin,
                tipoDocumento,
                motivo
            );
            
            log.info("Inutilización completada exitosamente");
            return true;
            
        } catch (Exception e) {
            log.error("Error al inutilizar números: {}", e.getMessage(), e);
            throw new RuntimeException("Error al inutilizar números: " + e.getMessage(), e);
        }
    }
}

