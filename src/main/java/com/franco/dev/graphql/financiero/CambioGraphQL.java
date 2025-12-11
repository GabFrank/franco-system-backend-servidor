package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.graphql.financiero.input.CambioInput;
import com.franco.dev.graphql.financiero.input.MonedaInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CambioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CambioService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private NotificationTemplateService notificationTemplateService;

    @Autowired
    private InicioSesionService inicioSesionService;

    public Optional<Cambio> cambio(Long id) {return service.findById(id);}

    public List<Cambio> cambios(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Cambio ultimoCambioPorMonedaId(Long id){
        return service.findLastByMonedaId(id);
    }


    public Cambio saveCambio(CambioInput input, List<Long> sucursalesIdList){
        ModelMapper m = new ModelMapper();
        Cambio e = m.map(input, Cambio.class);
        if(input.getMonedaId()!=null){
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        e = service.save(e);
        
        // Enviar notificación push sobre la actualización de cotización
        try {
            enviarNotificacionCotizacion(e);
        } catch (Exception ex) {
            // Silent notification error - no afecta la operación principal
        }
        
        return e;
    }

    private void enviarNotificacionCotizacion(Cambio cambio) {
        if (cambio == null || cambio.getMoneda() == null) {
            return;
        }

        try {
            Moneda moneda = cambio.getMoneda();
            String denominacion = moneda.getDenominacion() != null ? moneda.getDenominacion() : "Moneda";
            String simbolo = moneda.getSimbolo() != null ? moneda.getSimbolo() : "";
            
            // Usar el template service para crear la notificación
            PushNotificationRequest request = notificationTemplateService.cotizacionActualizada(
                denominacion,
                simbolo,
                cambio.getValorEnGs()
            );

            if (request == null) {
                return;
            }

            // Obtener TODOS los usuarios activos (con sesiones válidas)
            List<com.franco.dev.domain.configuracion.InicioSesion> sesionesActivas = 
                inicioSesionService.findSessionsWithValidTokens();
            
            List<Long> usuariosIds = sesionesActivas.stream()
                .filter(s -> s.getUsuario() != null)
                .map(s -> s.getUsuario().getId())
                .distinct()
                .collect(Collectors.toList());

            if (usuariosIds.isEmpty()) {
                return;
            }

            // Enviar a todos los usuarios activos
            request.setUsuarioIds(usuariosIds);
            pushNotificationService.sendPushNotificationToToken(request);
        } catch (Exception e) {
            // Silent error - no afecta la operación principal
        }
    }

    public List<Cambio> cambioPorFecha(String start, String end){
        if (end == null){
            end = start;
        }
        return service.findByDate(start, end);
    }

    public Boolean deleteCambio(Long id){
        Boolean ok = service.deleteById(id);
        return ok;        }

    public Long countCambio(){
        return service.count();
    }


}
