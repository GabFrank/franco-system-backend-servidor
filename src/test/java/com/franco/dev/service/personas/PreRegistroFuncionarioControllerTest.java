package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.PreRegistroFuncionario;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/** El pre-registro sin login solo crea: no edita ni da por verificado uno existente. */
class PreRegistroFuncionarioControllerTest {

    @Test
    void ignoraIdVerificadoYFuncionarioDelCuerpo() {
        PreRegistroFuncionarioService service = Mockito.mock(PreRegistroFuncionarioService.class);
        Mockito.when(service.save(any())).thenAnswer(i -> i.getArgument(0));
        PreRegistroFuncionarioController c = new PreRegistroFuncionarioController();
        ReflectionTestUtils.setField(c, "service", service);

        PreRegistroFuncionario in = new PreRegistroFuncionario();
        in.setId(12L);
        in.setVerificado(true);
        in.setFuncionario(new Funcionario());
        in.setNombreCompleto("juan perez");
        c.savePreRegistroFuncionario(in);

        ArgumentCaptor<PreRegistroFuncionario> guardado = ArgumentCaptor.forClass(PreRegistroFuncionario.class);
        Mockito.verify(service).save(guardado.capture());
        assertNull(guardado.getValue().getId());
        assertNull(guardado.getValue().getFuncionario());
        assertEquals(Boolean.FALSE, guardado.getValue().getVerificado());
        assertEquals("juan perez", guardado.getValue().getNombreCompleto());
    }
}
