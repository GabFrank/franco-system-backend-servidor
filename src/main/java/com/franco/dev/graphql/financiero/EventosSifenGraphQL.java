package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.EventoCancelacionDE;
import com.franco.dev.domain.financiero.EventoInutilizacionDE;
import com.franco.dev.domain.financiero.EventoNominacionDE;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.service.financiero.EventoCancelacionDEService;
import com.franco.dev.service.financiero.EventoInutilizacionDEService;
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
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

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
    private EventoInutilizacionDEService eventoInutilizacionDEService;

    @Autowired
    private EventoNominacionDEService eventoNominacionDEService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private TimbradoService timbradoService;

    // ========== QUERIES ==========

    public EventoCancelacionDE eventoCancelacion(Long id, Long sucursalId) {
        return eventoCancelacionDEService.findByIdAndSucursalId(id, sucursalId).orElse(null);
    }

    public List<EventoCancelacionDE> eventosCancelacionPorDocumento(Long documentoId, Long sucursalId) {
        return eventoCancelacionDEService.findByDocumentoElectronicoId(documentoId, sucursalId);
    }

    public List<EventoCancelacionDE> eventosCancelacionPorEstado(EstadoEvento estado, Long sucursalId) {
        return eventoCancelacionDEService.findByEstado(estado, sucursalId);
    }

    public EventoNominacionDE eventoNominacion(Long id, Long sucursalId) {
        return eventoNominacionDEService.findByIdAndSucursalId(id, sucursalId).orElse(null);
    }

    public List<EventoNominacionDE> eventosNominacionPorDocumento(Long documentoId, Long sucursalId) {
        return eventoNominacionDEService.findByDocumentoElectronicoId(documentoId, sucursalId);
    }

    public List<EventoNominacionDE> eventosNominacionPorEstado(EstadoEvento estado) {
        return eventoNominacionDEService.findByEstado(estado);
    }

    public EventoInutilizacionDE eventoInutilizacion(Long id, Long sucursalId) {
        return eventoInutilizacionDEService.findByIdAndSucursalId(id, sucursalId).orElse(null);
    }

    public List<EventoInutilizacionDE> eventosInutilizacionPorTimbrado(Long timbradoId, Long sucursalId) {
        return eventoInutilizacionDEService.findByTimbradoIdAndSucursalId(timbradoId, sucursalId);
    }

    public List<EventoInutilizacionDE> eventosInutilizacionPorEstado(EstadoEvento estado, Long sucursalId) {
        return eventoInutilizacionDEService.findByEstadoAndSucursalId(estado, sucursalId);
    }

    public List<EventoInutilizacionDE> eventosInutilizacionPorSucursal(Long sucursalId) {
        return eventoInutilizacionDEService.findBySucursalIdOrderByCreadoEnDesc(sucursalId);
    }

    public Page<EventoInutilizacionDE> eventosInutilizacionConFiltros(
            Long sucursalId,
            Long timbradoId,
            EstadoEvento estado,
            String fechaInicio,
            String fechaFin,
            Integer page,
            Integer size) {
        LocalDateTime fechaInicioLocal = fechaInicio != null && !fechaInicio.isEmpty() ? stringToDate(fechaInicio) : null;
        LocalDateTime fechaFinLocal = fechaFin != null && !fechaFin.isEmpty() ? stringToDate(fechaFin) : null;
        
        // Si fechaFin está definida, establecerla al final del día
        if (fechaFinLocal != null) {
            fechaFinLocal = fechaFinLocal.withHour(23).withMinute(59).withSecond(59);
        }
        
        int pageNum = page != null ? page : 0;
        int sizeNum = size != null ? size : 25;
        
        return eventoInutilizacionDEService.findWithFilters(
                sucursalId,
                timbradoId,
                estado,
                fechaInicioLocal,
                fechaFinLocal,
                pageNum,
                sizeNum
        );
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
    public EventoInutilizacionDE inutilizarNumeros(
            Long timbradoId,
            String establecimiento,
            String puntoExpedicion,
            Integer numeroInicio,
            Integer numeroFin,
            String motivo,
            Long sucursalId,
            Long timbradoDetalleId) {
        
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
            
            // El servicio envía la inutilización a SIFEN y crea el evento
            // Nota: El servicio puede lanzar excepción solo en casos de error real (ERROR_ENVIO)
            // Los rechazos de SIFEN se guardan con estado RECHAZADO pero no lanzan excepción
            try {
                sifenEventoService.inutilizarNumeros(
                    timbrado,
                    establecimiento,
                    puntoExpedicion,
                    numeroInicio,
                    numeroFin,
                    tipoDocumento,
                    motivo,
                    sucursalId,
                    timbradoDetalleId
                );
            } catch (IllegalStateException e) {
                // Si es un error de envío, propagar la excepción
                log.error("Error al enviar evento a SIFEN: {}", e.getMessage(), e);
                throw new RuntimeException("Error al enviar evento a SIFEN: " + e.getMessage(), e);
            }
            
            // Buscar el evento recién creado en la BD (puede estar APROBADO, RECHAZADO o PENDIENTE)
            EventoInutilizacionDE evento = eventoInutilizacionDEService.findByTimbradoIdAndSucursalId(timbradoId, sucursalId)
                .stream()
                .filter(e -> e.getNumeroInicio().equals(numeroInicio) && e.getNumeroFin().equals(numeroFin))
                .sorted((e1, e2) -> e2.getCreadoEn().compareTo(e1.getCreadoEn())) // Ordenar por fecha descendente
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No se pudo recuperar el evento de inutilización creado"));
            
            log.info("Inutilización procesada. Estado: {}, Código: {}", 
                evento.getEstado(), evento.getCodigoRespuesta());
            
            return evento;
            
        } catch (IllegalArgumentException e) {
            log.error("Error de validación: {}", e.getMessage(), e);
            throw e;
        } catch (RuntimeException e) {
            log.error("Error al inutilizar números: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al inutilizar números: {}", e.getMessage(), e);
            throw new RuntimeException("Error inesperado al inutilizar números: " + e.getMessage(), e);
        }
    }
}

