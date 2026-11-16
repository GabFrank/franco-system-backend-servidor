package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.personas.ConsultaRucResponse;
import com.franco.dev.repository.personas.ClienteRepository;
import com.franco.dev.service.sifen.SifenConsultaRucService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * buscarOCrearClientePorDocumento consulta el RUC por {@link SifenConsultaRucService} (antes por
 * SifenService, que formaba un ciclo de beans con ClienteService, issue #651). La consulta a SIFEN
 * esta simulada: el test no habla con la SET.
 */
class ClienteServiceConsultaRucTest {

    private static final String DOC = "4043581";

    private ClienteRepository repository;
    private PersonaService personaService;
    private SifenConsultaRucService consultaRuc;

    @BeforeEach
    void init() {
        repository = mock(ClienteRepository.class);
        personaService = mock(PersonaService.class);
        consultaRuc = mock(SifenConsultaRucService.class);
        when(personaService.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Cliente cliente(boolean verificado) {
        Persona persona = new Persona();
        persona.setDocumento(DOC);
        Cliente c = new Cliente();
        c.setId(1L);
        c.setPersona(persona);
        c.setVerificadoSet(verificado);
        return c;
    }

    @Test
    void clienteYaVerificado_noConsultaSifen() {
        Cliente existente = cliente(true);
        when(repository.findByPersonaDocumento(DOC)).thenReturn(existente);

        Cliente res = new ClienteService(repository, personaService, consultaRuc).buscarOCrearClientePorDocumento(DOC);

        assertSame(existente, res);
        verifyNoInteractions(consultaRuc);
    }

    @Test
    void sinSifen_devuelveElExistente() {
        Cliente existente = cliente(false);
        when(repository.findByPersonaDocumento(DOC)).thenReturn(existente);

        Cliente res = new ClienteService(repository, personaService, null).buscarOCrearClientePorDocumento(DOC);

        assertSame(existente, res);
        verify(repository, never()).save(any());
    }

    @Test
    void sifenSinRespuesta_devuelveElExistente() {
        Cliente existente = cliente(false);
        when(repository.findByPersonaDocumento(DOC)).thenReturn(existente);
        when(consultaRuc.consultaRuc(DOC)).thenReturn(null);

        Cliente res = new ClienteService(repository, personaService, consultaRuc).buscarOCrearClientePorDocumento(DOC);

        assertSame(existente, res);
        verify(repository, never()).save(any());
    }

    @Test
    void noContribuyente_lanza() {
        when(consultaRuc.consultaRuc(DOC)).thenReturn(new ConsultaRucResponse());

        ClienteService service = new ClienteService(repository, personaService, consultaRuc);

        assertThrows(RuntimeException.class, () -> service.buscarOCrearClientePorDocumento(DOC));
    }

    @Test
    void contribuyente_marcaVerificadoConLosDatosDeSifen() {
        Cliente existente = cliente(false);
        when(repository.findByPersonaDocumento(DOC)).thenReturn(existente);
        ConsultaRucResponse rta = new ConsultaRucResponse();
        rta.setRuc(DOC);
        rta.setRazonSocial("PEREZ, JUAN");
        rta.setEstadoContribuyente("ACTIVO");
        when(consultaRuc.consultaRuc(DOC)).thenReturn(rta);

        Cliente res = new ClienteService(repository, personaService, consultaRuc).buscarOCrearClientePorDocumento(DOC);

        assertTrue(res.getVerificadoSet());
        assertTrue(res.getTributa());
        assertEquals("JUAN PEREZ", res.getPersona().getNombre());
    }
}
