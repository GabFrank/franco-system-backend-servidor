package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionSistema;
import com.franco.dev.repository.empresarial.ConfiguracionSistemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Configuracion runtime key/value. Soporta valores encriptados (API keys, secretos).
 * Encripcion: Blowfish con Base64 wrapping para preservar bytes en TEXT column.
 * No usar StringUtils.encryptString/decryptString legacy — rompe round-trip al hacer new String(bytes) sin Base64.
 */
@Service
public class ConfiguracionSistemaService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionSistemaService.class);
    private static final String MASCARA = "***";

    @Autowired
    private ConfiguracionSistemaRepository repository;

    @Value("${app.config.encryption.master-key:default-dev-key-not-for-prod}")
    private String masterKey;

    /**
     * Retorna el valor crudo de la configuracion (encriptado si la fila tiene encriptado=true).
     * Para obtener el valor real desencriptado de una entrada encriptada, usar getDecrypted().
     */
    public Optional<ConfiguracionSistema> findByClave(String clave) {
        return repository.findById(clave);
    }

    /**
     * Retorna el valor desencriptado. Si la fila no es encriptada, retorna valor tal cual.
     * Si no existe la fila o el valor es null/vacio, retorna Optional.empty().
     */
    public Optional<String> getDecrypted(String clave) {
        Optional<ConfiguracionSistema> opt = repository.findById(clave);
        if (!opt.isPresent()) return Optional.empty();
        ConfiguracionSistema cs = opt.get();
        if (cs.getValor() == null || cs.getValor().isEmpty()) return Optional.empty();
        if (Boolean.TRUE.equals(cs.getEncriptado())) {
            try {
                return Optional.of(decrypt(cs.getValor()));
            } catch (Exception e) {
                log.error("Fallo al desencriptar clave {}: {}", clave, e.getMessage());
                return Optional.empty();
            }
        }
        return Optional.of(cs.getValor());
    }

    /**
     * Retorna la entidad con el valor enmascarado ("***") si encriptado=true y tiene valor.
     * Usado por resolvers GraphQL para nunca exponer secretos al frontend.
     */
    public Optional<ConfiguracionSistema> findByClaveMasked(String clave) {
        return repository.findById(clave).map(cs -> {
            if (Boolean.TRUE.equals(cs.getEncriptado()) && cs.getValor() != null && !cs.getValor().isEmpty()) {
                ConfiguracionSistema copia = new ConfiguracionSistema(
                        cs.getClave(), MASCARA, cs.getDescripcion(), cs.getEncriptado(), cs.getModificadoEn());
                return copia;
            }
            return cs;
        });
    }

    /**
     * Set del valor. Si la fila tiene encriptado=true, encripta el valor antes de persistir.
     * Si el valor recibido es null o vacio, persiste null (clearing).
     * Si el valor recibido es exactamente "***", se ignora el cambio (idempotente con la query masked).
     */
    @Transactional
    public ConfiguracionSistema set(String clave, String valor) {
        ConfiguracionSistema cs = repository.findById(clave)
                .orElseThrow(() -> new IllegalArgumentException("Clave no encontrada: " + clave));

        if (MASCARA.equals(valor)) {
            return cs;
        }

        String valorAGuardar = null;
        if (valor != null && !valor.isEmpty()) {
            if (Boolean.TRUE.equals(cs.getEncriptado())) {
                try {
                    valorAGuardar = encrypt(valor);
                } catch (Exception e) {
                    throw new RuntimeException("Fallo al encriptar valor para clave " + clave, e);
                }
            } else {
                valorAGuardar = valor;
            }
        }
        cs.setValor(valorAGuardar);
        cs.setModificadoEn(LocalDateTime.now());
        return repository.save(cs);
    }

    String encrypt(String plain) throws Exception {
        SecretKeySpec skey = new SecretKeySpec(masterKey.getBytes(StandardCharsets.UTF_8), "Blowfish");
        Cipher cipher = Cipher.getInstance("Blowfish");
        cipher.init(Cipher.ENCRYPT_MODE, skey);
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    String decrypt(String encryptedBase64) throws Exception {
        SecretKeySpec skey = new SecretKeySpec(masterKey.getBytes(StandardCharsets.UTF_8), "Blowfish");
        Cipher cipher = Cipher.getInstance("Blowfish");
        cipher.init(Cipher.DECRYPT_MODE, skey);
        byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);
        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
