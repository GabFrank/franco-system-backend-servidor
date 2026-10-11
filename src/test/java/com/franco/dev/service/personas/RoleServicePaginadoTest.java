package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.repository.personas.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleServicePaginadoTest {

    private RoleRepository repository;
    private RoleService service;

    @BeforeEach
    void setUp() {
        repository = mock(RoleRepository.class);
        service = new RoleService(repository);
        when(repository.findByNombrePaginated(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<Role>(Collections.emptyList()));
    }

    @Test
    void buscaConElTextoEnMayusculasYSinEspaciosSobrantes() {
        service.findByNombrePaginated("  rrhh ver ", 2, 25);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByNombrePaginated(eq("RRHH VER"), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(25, pageable.getValue().getPageSize());
    }

    @Test
    void sinTextoNiPaginaTraeLaPrimeraPaginaDeQuince() {
        service.findByNombrePaginated(null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByNombrePaginated(eq(""), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(15, pageable.getValue().getPageSize());
    }
}
