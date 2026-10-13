package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.personas.PersonaRepository;
import com.franco.dev.repository.personas.RoleRepository;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.service.utils.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Clave al guardar un usuario (issue #477 del desktop): al crear se respeta la elegida y al
 * editar sin clave se conserva la que tenia.
 */
class UsuarioServiceSavePasswordTest {

    private UsuarioRepository repository;
    private UsuarioService service;

    @BeforeEach
    void setUp() {
        repository = mock(UsuarioRepository.class);
        service = new UsuarioService(repository, mock(RoleRepository.class), mock(UsuarioRoleService.class),
                mock(RoleService.class), mock(PersonaRepository.class), mock(ImageService.class),
                mock(UsuarioEmbeddingCacheService.class), mock(EmbeddingGaleriaService.class));
        when(repository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Usuario usuario(Long id, String password) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNickname("bot-loom");
        u.setPassword(password);
        return u;
    }

    @Test
    void alCrearRespetaLaClaveElegida() {
        assertEquals("SECRETA", service.save(usuario(null, "secreta")).getPassword());
    }

    @Test
    void alCrearSinClaveQuedaLaInicial() {
        assertEquals("123", service.save(usuario(null, null)).getPassword());
        assertEquals("123", service.save(usuario(null, "  ")).getPassword());
    }

    @Test
    void alEditarSinClaveConservaLaGuardada() {
        when(repository.findById(5L)).thenReturn(Optional.of(usuario(5L, "VIEJA")));

        assertEquals("VIEJA", service.save(usuario(5L, null)).getPassword());
    }

    @Test
    void alEditarConClaveLaCambia() {
        assertEquals("123", service.save(usuario(5L, "123")).getPassword());
    }
}
