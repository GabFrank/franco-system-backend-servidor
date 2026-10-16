package com.franco.dev.service.personas;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CaptchaServiceTest {

    private final CaptchaService service = new CaptchaService();

    private String respuesta(String id) {
        Map<String, ?> vigentes = (Map<String, ?>) ReflectionTestUtils.getField(service, "vigentes");
        return (String) ReflectionTestUtils.getField(vigentes.get(id), "respuesta");
    }

    @Test
    void generaUnPngYAceptaLaRespuestaSinDistinguirMayusculas() {
        CaptchaService.Captcha c = service.generar();
        byte[] png = Base64.getDecoder().decode(c.getImagen());
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertTrue(service.validar(c.getId(), respuesta(c.getId()).toLowerCase()));
    }

    @Test
    void sirveUnaSolaVezAunqueSeAcierte() {
        CaptchaService.Captcha c = service.generar();
        String r = respuesta(c.getId());
        assertTrue(service.validar(c.getId(), r));
        assertFalse(service.validar(c.getId(), r));
    }

    @Test
    void unaRespuestaEquivocadaLoConsume() {
        CaptchaService.Captcha c = service.generar();
        String r = respuesta(c.getId());
        assertFalse(service.validar(c.getId(), "?????"));
        assertFalse(service.validar(c.getId(), r));
    }

    @Test
    void unIdInventadoNoValida() {
        assertFalse(service.validar("no-existe", "ABCDE"));
        assertFalse(service.validar(null, null));
    }
}
