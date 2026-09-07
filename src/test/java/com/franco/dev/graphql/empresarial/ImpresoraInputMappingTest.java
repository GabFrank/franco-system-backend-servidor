package com.franco.dev.graphql.empresarial;

import com.franco.dev.domain.empresarial.Impresora;
import com.franco.dev.domain.empresarial.enums.PerfilPapel;
import com.franco.dev.domain.empresarial.enums.TipoConexion;
import com.franco.dev.domain.empresarial.enums.TipoImpresora;
import com.franco.dev.domain.empresarial.enums.UsoImpresora;
import com.franco.dev.graphql.empresarial.input.ImpresoraInput;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ImpresoraInput trae {@code smbUsuario} (usuario del share de Windows) y {@code usuarioId}
 * (quien la da de alta). Con la estrategia de matching por defecto de ModelMapper ambos caen
 * sobre el destino {@code Impresora.usuario.usuario} (Usuario se auto-referencia) y guardar
 * explotaba con "matches multiple source property hierarchies".
 */
class ImpresoraInputMappingTest {

    @Test
    void mapeaImpresoraSmbSinAmbiguedadDeUsuario() {
        ImpresoraInput input = new ImpresoraInput();
        input.setNombre("test");
        input.setConexion(TipoConexion.SMB);
        input.setSmbHost("100.64.0.10");
        input.setSmbRecurso("adm_ticket");
        input.setSmbUsuario("user");
        input.setSmbDominio("WORKGROUP");
        input.setUsuarioId(7L);
        input.setSucursalId(0L);

        Impresora e = ImpresoraGraphQL.strictMapper().map(input, Impresora.class);

        assertEquals("test", e.getNombre());
        assertEquals(TipoConexion.SMB, e.getConexion());
        assertEquals("100.64.0.10", e.getSmbHost());
        assertEquals("adm_ticket", e.getSmbRecurso());
        assertEquals("user", e.getSmbUsuario());
        assertEquals("WORKGROUP", e.getSmbDominio());
        // sucursalId/usuarioId los resuelve el resolver contra el service, no el mapper.
        assertNull(e.getUsuario());
        assertNull(e.getSucursal());
    }

    /** El matching STRICT no puede dejar de copiar ningun campo de nombre identico. */
    @Test
    void copiaTodosLosCamposDeNombreIdentico() {
        ImpresoraInput input = new ImpresoraInput();
        input.setId(9L);
        input.setNombre("caja1");
        input.setActivo(true);
        input.setEsPredeterminada(true);
        input.setTipo(TipoImpresora.TERMICA);
        input.setUso(UsoImpresora.TICKET);
        input.setConexion(TipoConexion.USB);
        input.setColaCups("caja1");
        input.setIp("192.168.0.50");
        input.setPuerto(9100);
        input.setPerfilPapel(PerfilPapel.MM_58);
        input.setColumnas(32);
        input.setAnchoMm(58);
        input.setAltoMm(210);
        input.setMarca("EPSON");
        input.setCodepage("CP437");
        input.setCompartidaEnCentral(true);

        ModelMapper m = ImpresoraGraphQL.strictMapper();
        Impresora e = m.map(input, Impresora.class);

        assertEquals(9L, e.getId());
        assertEquals("caja1", e.getNombre());
        assertTrue(e.getActivo());
        assertTrue(e.getEsPredeterminada());
        assertEquals(TipoImpresora.TERMICA, e.getTipo());
        assertEquals(UsoImpresora.TICKET, e.getUso());
        assertEquals(TipoConexion.USB, e.getConexion());
        assertEquals("caja1", e.getColaCups());
        assertEquals("192.168.0.50", e.getIp());
        assertEquals(9100, e.getPuerto());
        assertEquals(PerfilPapel.MM_58, e.getPerfilPapel());
        assertEquals(32, e.getColumnas());
        assertEquals(58, e.getAnchoMm());
        assertEquals(210, e.getAltoMm());
        assertEquals("EPSON", e.getMarca());
        assertEquals("CP437", e.getCodepage());
        assertTrue(e.getCompartidaEnCentral());
    }
}
