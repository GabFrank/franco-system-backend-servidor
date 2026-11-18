package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.repository.personas.ClienteRepository;
import com.franco.dev.repository.personas.ProveedorRepository;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.ProveedorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #582, tanda personas. proveedorSearchByPersonaPage y la busqueda de clientes reciben Integer sin
 * default: sin page/size daban NullPointerException. funcionariosWithPage recibe int: un size faltante
 * llegaba como 0 (graphql-java-kickstart) y PageRequest.of tiraba IllegalArgumentException.
 */
class PaginacionPorDefectoPersonasTest {

    @Mock private ProveedorService proveedorService;
    @Mock private ProveedorRepository proveedorRepository;
    @Mock private FuncionarioService funcionarioService;

    @InjectMocks private ProveedorGraphQL proveedorGraphQL;
    @InjectMocks private FuncionarioGraphQL funcionarioGraphQL;

    @Mock private ClienteRepository clienteRepository;
    @Mock private PersonaService personaService;

    private ClienteService clienteService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(proveedorService.getRepository()).thenReturn(proveedorRepository);
        clienteService = new ClienteService(clienteRepository, personaService, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static void esPrimeraPaginaDe15(Pageable p) {
        assertEquals(0, p.getPageNumber());
        assertEquals(15, p.getPageSize());
    }

    @Test
    void proveedorSearchByPersonaPageSinPageNiSize() {
        proveedorGraphQL.proveedorSearchByPersonaPage("coca", null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(proveedorRepository).findByPersonaNombreOrApodoOrDocumentoIgnoreCase(eq("%coca%"), eq("%coca%"),
                eq("%coca%"), p.capture());
        esPrimeraPaginaDe15(p.getValue());
        // el orden por nombre se mantiene
        assertEquals(Sort.by("persona.nombre"), p.getValue().getSort());
    }

    @Test
    void proveedorSearchByPersonaPageRespetaPageYSize() {
        proveedorGraphQL.proveedorSearchByPersonaPage(null, 2, 10);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(proveedorRepository).findAll(p.capture());
        assertEquals(2, p.getValue().getPageNumber());
        assertEquals(10, p.getValue().getPageSize());
        assertEquals(Sort.by("persona.nombre"), p.getValue().getSort());
    }

    @Test
    void funcionariosWithPageSinSize() {
        funcionarioGraphQL.funcionariosWithPage(0, null, null, "juan perez", null, null, null, null, null, null,
                null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(funcionarioService).findAllWithPage(isNull(), eq("juan%perez"), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void clientesConFiltrosSinPageNiSize() {
        clienteService.findByAll2("ana", TipoCliente.NORMAL, null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(clienteRepository).findByAll(eq("ANA"), eq(TipoCliente.NORMAL), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }
}
