package com.franco.dev.fmc.service;

import com.franco.dev.domain.configuracion.Notificacion;
import com.franco.dev.domain.configuracion.NotificacionEnvioLog;
import com.franco.dev.domain.configuracion.enums.EstadoEnvio;
import com.franco.dev.fmc.model.DeliveryResult;
import com.franco.dev.repository.configuracion.NotificacionEnvioLogRepository;
import com.franco.dev.repository.configuracion.NotificacionRepository;
import com.franco.dev.service.configuracion.InicioSesionService;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * El presupuesto de reintentos es por destino, no por notificacion: cada token
 * agota sus propios intentos y recien ahi el envio queda CANCELADA.
 */
class NotificationDispatchServiceReintentosTest {

    private static final int MAX_INTENTOS = 5;

    private NotificationDispatchService service;
    private Notificacion notificacion;

    @BeforeEach
    void setUp() {
        service = new NotificationDispatchService(
                mock(NotificacionEnvioLogRepository.class),
                mock(NotificacionRepository.class),
                mock(FCMService.class),
                mock(InicioSesionService.class),
                Optional.empty(),
                mock(PlatformTransactionManager.class));
        ReflectionTestUtils.setField(service, "maxAttempts", MAX_INTENTOS);

        notificacion = new Notificacion();
        notificacion.setId(1L);
        notificacion.setIntentosEnvio(0);
    }

    private NotificacionEnvioLog fila(Integer intentos) {
        NotificacionEnvioLog log = new NotificacionEnvioLog();
        log.setEstadoEnvio(EstadoEnvio.EN_PROCESO);
        log.setTokenFcm("token-x");
        log.setIntentos(intentos);
        return log;
    }

    private DeliveryResult transitorio() {
        return DeliveryResult.transientError("servicio caido", MessagingErrorCode.UNAVAILABLE);
    }

    @Test
    void elPrimerErrorTransitorioVuelveAPendiente() {
        NotificacionEnvioLog fila = fila(0);

        service.handleResult(fila, notificacion, transitorio());

        assertEquals(EstadoEnvio.PENDIENTE, fila.getEstadoEnvio());
        assertEquals(1, fila.getIntentos(), "cada intento tiene que contarse en la fila");
    }

    @Test
    void filaSinContadorArrancaDeCero() {
        NotificacionEnvioLog fila = fila(null);

        service.handleResult(fila, notificacion, transitorio());

        assertEquals(EstadoEnvio.PENDIENTE, fila.getEstadoEnvio());
        assertEquals(1, fila.getIntentos(), "las filas historicas sin contador valen 0");
    }

    @Test
    void alAgotarLosIntentosSeCancela() {
        NotificacionEnvioLog fila = fila(MAX_INTENTOS - 1);

        service.handleResult(fila, notificacion, transitorio());

        assertEquals(MAX_INTENTOS, fila.getIntentos());
        assertEquals(EstadoEnvio.CANCELADA, fila.getEstadoEnvio(),
                "agotados los reintentos el envio queda cancelado, no pendiente");
    }

    @Test
    void reintentaExactamenteCincoVeces() {
        NotificacionEnvioLog fila = fila(0);
        int intentosHechos = 0;

        while (fila.getEstadoEnvio() != EstadoEnvio.CANCELADA) {
            service.handleResult(fila, notificacion, transitorio());
            intentosHechos++;
            if (intentosHechos > 20) {
                break;
            }
        }

        assertEquals(MAX_INTENTOS, intentosHechos, "no debe reintentar ni de mas ni de menos");
    }

    @Test
    void cadaDestinoTieneSuPropioPresupuesto() {
        NotificacionEnvioLog agotada = fila(MAX_INTENTOS - 1);
        NotificacionEnvioLog reciente = fila(0);

        service.handleResult(agotada, notificacion, transitorio());
        service.handleResult(reciente, notificacion, transitorio());

        assertEquals(EstadoEnvio.CANCELADA, agotada.getEstadoEnvio());
        assertEquals(EstadoEnvio.PENDIENTE, reciente.getEstadoEnvio(),
                "un destino agotado no puede cancelar a los otros destinos de la misma notificacion");
    }

    @Test
    void elTokenInvalidoNoEsUnaCancelacion() {
        NotificacionEnvioLog fila = fila(0);

        service.handleResult(fila, notificacion,
                DeliveryResult.invalidToken("token muerto", MessagingErrorCode.UNREGISTERED));

        assertEquals(EstadoEnvio.FALLO_DESTINO, fila.getEstadoEnvio(),
                "el token invalido se limpia, no se reintenta ni se cancela");
    }

    @Test
    void elErrorPermanenteNoEsUnaCancelacion() {
        NotificacionEnvioLog fila = fila(0);

        service.handleResult(fila, notificacion, DeliveryResult.failure("error raro", null));

        assertEquals(EstadoEnvio.FALLO_ENVIO, fila.getEstadoEnvio());
    }

    @Test
    void elEnvioExitosoNoSeCancelaAunqueTengaIntentosPrevios() {
        NotificacionEnvioLog fila = fila(MAX_INTENTOS - 1);

        service.handleResult(fila, notificacion, DeliveryResult.success());

        assertEquals(EstadoEnvio.ENVIADO, fila.getEstadoEnvio());
    }
}
