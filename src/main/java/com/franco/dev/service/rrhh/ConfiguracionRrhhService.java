package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.ConfiguracionRrhh;
import com.franco.dev.domain.rrhh.ConfiguracionRrhhHistorico;
import com.franco.dev.domain.rrhh.enums.ConfiguracionRrhhTipo;
import com.franco.dev.repository.rrhh.ConfiguracionRrhhHistoricoRepository;
import com.franco.dev.repository.rrhh.ConfiguracionRrhhRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class ConfiguracionRrhhService extends CrudService<ConfiguracionRrhh, ConfiguracionRrhhRepository, Long> {

    private final ConfiguracionRrhhRepository repository;
    private final ConfiguracionRrhhHistoricoRepository historicoRepository;

    @Override
    public ConfiguracionRrhhRepository getRepository() {
        return repository;
    }

    /**
     * Registra el cambio de valor de un parametro. Se llama desde el resolver ANTES
     * de persistir, porque despues ya no se puede saber cual era el valor anterior.
     * No audita altas ni cambios que no toquen el valor (descripcion, activo).
     */
    public void auditarCambio(ConfiguracionRrhh anterior, String valorNuevo, Usuario usuario) {
        if (anterior == null || anterior.getId() == null) return;
        String valorAnterior = anterior.getValor();
        if (valorAnterior == null ? valorNuevo == null : valorAnterior.equals(valorNuevo)) return;

        ConfiguracionRrhhHistorico h = new ConfiguracionRrhhHistorico();
        h.setConfiguracionRrhhId(anterior.getId());
        h.setClave(anterior.getClave());
        h.setValorAnterior(valorAnterior);
        h.setValorNuevo(valorNuevo);
        h.setUsuario(usuario);
        h.setCreadoEn(LocalDateTime.now());
        historicoRepository.save(h);
    }

    public List<ConfiguracionRrhhHistorico> findHistoricoPorClave(String clave) {
        if (clave == null) return java.util.Collections.emptyList();
        return historicoRepository.findByClaveOrderByCreadoEnDesc(clave.toUpperCase());
    }

    public Optional<ConfiguracionRrhh> findByClave(String clave) {
        if (clave == null) return Optional.empty();
        return repository.findByClave(clave.toUpperCase());
    }

    public List<ConfiguracionRrhh> findByAll(String texto) {
        if (texto == null) texto = "";
        texto = texto.replace(' ', '%').toUpperCase();
        return repository.findByAll(texto);
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<ConfiguracionRrhh> findPage(String texto, ConfiguracionRrhhTipo tipo, Pageable pageable) {
        return repository.findPage(texto, tipo, pageable);
    }

    /** Devuelve el valor numerico de una clave, o el default si no existe / no parsea. */
    public java.math.BigDecimal getNumber(String clave, java.math.BigDecimal porDefecto) {
        Optional<ConfiguracionRrhh> c = findByClave(clave);
        if (c.isPresent() && c.get().getValor() != null) {
            try {
                return new java.math.BigDecimal(c.get().getValor().trim());
            } catch (NumberFormatException e) {
                return porDefecto;
            }
        }
        return porDefecto;
    }

    /** Devuelve el valor booleano de una clave, o el default si no existe. */
    public boolean getBoolean(String clave, boolean porDefecto) {
        Optional<ConfiguracionRrhh> c = findByClave(clave);
        if (c.isPresent() && c.get().getValor() != null) {
            return "true".equalsIgnoreCase(c.get().getValor().trim());
        }
        return porDefecto;
    }

    @Override
    public ConfiguracionRrhh save(ConfiguracionRrhh entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getActivo() == null) entity.setActivo(true);
        if (entity.getTipo() == null) entity.setTipo(ConfiguracionRrhhTipo.STRING);
        if (entity.getClave() != null) entity.setClave(entity.getClave().toUpperCase());
        return super.save(entity);
    }
}
