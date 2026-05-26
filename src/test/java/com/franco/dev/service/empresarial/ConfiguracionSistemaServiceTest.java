package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionSistema;
import com.franco.dev.repository.empresarial.ConfiguracionSistemaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfiguracionSistemaServiceTest {

    @Mock
    private ConfiguracionSistemaRepository repository;

    @InjectMocks
    private ConfiguracionSistemaService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "masterKey", "test-master-key-2026");
    }

    @Test
    void encryptDecrypt_roundTrip_preservaValor() throws Exception {
        String plain = "sk-test-1234567890abcdef-API-KEY!";
        String encrypted = service.encrypt(plain);

        assertNotEquals(plain, encrypted, "valor encriptado debe diferir del plano");
        assertEquals(plain, service.decrypt(encrypted), "decrypt(encrypt(x)) debe retornar x");
    }

    @Test
    void encryptDecrypt_roundTrip_caracteresEspeciales() throws Exception {
        String plain = "ñoño-áéíóú-中文-€-😀-newline\nand\ttab";
        assertEquals(plain, service.decrypt(service.encrypt(plain)));
    }

    @Test
    void getDecrypted_claveNoExiste_retornaEmpty() {
        when(repository.findById("inexistente")).thenReturn(Optional.empty());
        assertFalse(service.getDecrypted("inexistente").isPresent());
    }

    @Test
    void getDecrypted_valorNull_retornaEmpty() {
        ConfiguracionSistema cs = new ConfiguracionSistema("openai.api_key", null, "desc", true, LocalDateTime.now());
        when(repository.findById("openai.api_key")).thenReturn(Optional.of(cs));
        assertFalse(service.getDecrypted("openai.api_key").isPresent());
    }

    @Test
    void getDecrypted_valorEncriptado_retornaPlano() throws Exception {
        String plain = "sk-real-key";
        String encriptado = service.encrypt(plain);
        ConfiguracionSistema cs = new ConfiguracionSistema("openai.api_key", encriptado, "desc", true, LocalDateTime.now());
        when(repository.findById("openai.api_key")).thenReturn(Optional.of(cs));

        Optional<String> result = service.getDecrypted("openai.api_key");
        assertTrue(result.isPresent());
        assertEquals(plain, result.get());
    }

    @Test
    void getDecrypted_valorNoEncriptado_retornaTalCual() {
        ConfiguracionSistema cs = new ConfiguracionSistema("openai.modelo", "gpt-4o", "desc", false, LocalDateTime.now());
        when(repository.findById("openai.modelo")).thenReturn(Optional.of(cs));
        assertEquals("gpt-4o", service.getDecrypted("openai.modelo").get());
    }

    @Test
    void findByClaveMasked_valorEncriptadoConValor_enmascara() throws Exception {
        ConfiguracionSistema cs = new ConfiguracionSistema(
                "openai.api_key", service.encrypt("secret"), "desc", true, LocalDateTime.now());
        when(repository.findById("openai.api_key")).thenReturn(Optional.of(cs));

        ConfiguracionSistema masked = service.findByClaveMasked("openai.api_key").get();
        assertEquals("***", masked.getValor());
        assertEquals(true, masked.getEncriptado());
    }

    @Test
    void findByClaveMasked_valorEncriptadoSinValor_noEnmascaraNull() {
        ConfiguracionSistema cs = new ConfiguracionSistema("openai.api_key", null, "desc", true, LocalDateTime.now());
        when(repository.findById("openai.api_key")).thenReturn(Optional.of(cs));

        ConfiguracionSistema masked = service.findByClaveMasked("openai.api_key").get();
        assertNull(masked.getValor(), "valor null queda null, no se enmascara");
    }

    @Test
    void findByClaveMasked_noEncriptado_retornaValorTalCual() {
        ConfiguracionSistema cs = new ConfiguracionSistema("openai.modelo", "gpt-4o", "desc", false, LocalDateTime.now());
        when(repository.findById("openai.modelo")).thenReturn(Optional.of(cs));
        assertEquals("gpt-4o", service.findByClaveMasked("openai.modelo").get().getValor());
    }

    @Test
    void set_valorMascara_esIdempotente_noModifica() {
        ConfiguracionSistema existente = new ConfiguracionSistema(
                "openai.api_key", "encrypted-value", "desc", true, LocalDateTime.now());
        when(repository.findById("openai.api_key")).thenReturn(Optional.of(existente));

        ConfiguracionSistema result = service.set("openai.api_key", "***");
        assertEquals("encrypted-value", result.getValor(), "no debe sobrescribir con la mascara");
        verify(repository, never()).save(any());
    }

    @Test
    void set_valorEncriptado_persisteEncriptado() throws Exception {
        ConfiguracionSistema existente = new ConfiguracionSistema(
                "openai.api_key", null, "desc", true, LocalDateTime.now());
        when(repository.findById("openai.api_key")).thenReturn(Optional.of(existente));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.set("openai.api_key", "sk-nueva-key");

        ArgumentCaptor<ConfiguracionSistema> captor = ArgumentCaptor.forClass(ConfiguracionSistema.class);
        verify(repository).save(captor.capture());
        ConfiguracionSistema guardado = captor.getValue();
        assertNotEquals("sk-nueva-key", guardado.getValor(), "debe persistirse encriptado, no plano");
        assertEquals("sk-nueva-key", service.decrypt(guardado.getValor()), "debe ser desencriptable");
    }

    @Test
    void set_valorNoEncriptado_persisteTalCual() {
        ConfiguracionSistema existente = new ConfiguracionSistema(
                "openai.modelo", "gpt-4o-mini", "desc", false, LocalDateTime.now());
        when(repository.findById("openai.modelo")).thenReturn(Optional.of(existente));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.set("openai.modelo", "gpt-4o");

        ArgumentCaptor<ConfiguracionSistema> captor = ArgumentCaptor.forClass(ConfiguracionSistema.class);
        verify(repository).save(captor.capture());
        assertEquals("gpt-4o", captor.getValue().getValor());
    }

    @Test
    void set_valorNull_limpia() {
        ConfiguracionSistema existente = new ConfiguracionSistema(
                "openai.api_key", "valor-previo", "desc", true, LocalDateTime.now());
        when(repository.findById("openai.api_key")).thenReturn(Optional.of(existente));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.set("openai.api_key", null);

        ArgumentCaptor<ConfiguracionSistema> captor = ArgumentCaptor.forClass(ConfiguracionSistema.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getValor());
    }

    @Test
    void set_claveInexistente_lanzaException() {
        when(repository.findById("inexistente")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.set("inexistente", "x"));
    }
}
