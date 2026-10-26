package com.franco.dev.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.empresarial.Sucursal;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Las sucursales que salen sin login llevan solo id y nombre. */
class SucursalesDtoTest {

    @Test
    void soloIdYNombre() throws Exception {
        Sucursal s = new Sucursal();
        s.setId(12L);
        s.setNombre("SUC. KM5");
        s.setIp("172.25.2.12");
        String json = new ObjectMapper().writeValueAsString(SucursalesDto.de(Collections.singletonList(s)));
        assertEquals("{\"sucursalList\":[{\"id\":12,\"nombre\":\"SUC. KM5\"}]}", json);
    }
}
