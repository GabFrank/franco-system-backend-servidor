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
import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
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
    private MultiTenantService multiTenantService;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private NotificationTemplateService notificationTemplateService;

    @Autowired
    private InicioSesionService inicioSesionService;

    public Optional<Cambio> cambio(Long id) {
        return service.findById(id);
    }

    private static final java.text.DecimalFormat df = new java.text.DecimalFormat("#,###.##");

    public List<Cambio> cambios(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Cambio ultimoCambioPorMonedaId(Long id) {
        return service.findLastByMonedaId(id);
    }

    @Autowired
    private org.springframework.context.ApplicationEventPublisher publisher;

    public Cambio saveCambio(CambioInput input, List<Long> sucursalesIdList) {
        ModelMapper m = new ModelMapper();
        Cambio e = m.map(input, Cambio.class);
        if (input.getMonedaId() != null) {
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        e = service.save(e);

        try {
            enviarNotificacionCotizacion(e);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return e;
    }

    private void enviarNotificacionCotizacion(Cambio cambio) {
        if (cambio == null) {
            return;
        }

        try {
            // Recargar entidad moneda para evitar LazyInitializationException
            Moneda moneda = cambio.getMoneda();
            if (moneda != null && moneda.getId() != null) {
                moneda = monedaService.findById(moneda.getId()).orElse(moneda);
            }

            if (moneda == null) {
                return;
            }

            String denominacion = moneda.getDenominacion() != null ? moneda.getDenominacion() : "Moneda";
            String simbolo = moneda.getSimbolo() != null ? moneda.getSimbolo() : "";

            PushNotificationRequest request = notificationTemplateService.cotizacionActualizada(
                    denominacion,
                    simbolo,
                    cambio.getValorEnGs());

            if (request == null) {
                return;
            }

            List<com.franco.dev.domain.configuracion.InicioSesion> sesionesActivas = inicioSesionService
                    .findSessionsWithValidTokens();

            List<Long> usuariosIds = sesionesActivas.stream()
                    .filter(s -> s.getUsuario() != null)
                    .map(s -> s.getUsuario().getId())
                    .distinct()
                    .collect(Collectors.toList());

            if (!usuariosIds.isEmpty()) {
                request.setUsuarioIds(usuariosIds);
                pushNotificationService.sendPushNotificationToToken(request);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Cambio> cambioPorFecha(String start, String end) {
        if (end == null) {
            end = start;
        }
        return service.findByDate(start, end);
    }

    public Boolean deleteCambio(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countCambio() {
        return service.count();
    }

}
