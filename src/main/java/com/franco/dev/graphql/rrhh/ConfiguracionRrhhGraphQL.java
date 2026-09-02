package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.ConfiguracionRrhh;
import com.franco.dev.domain.rrhh.ConfiguracionRrhhHistorico;
import com.franco.dev.domain.rrhh.enums.ConfiguracionRrhhTipo;
import com.franco.dev.graphql.rrhh.input.ConfiguracionRrhhInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.ConfiguracionRrhhService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ConfiguracionRrhhGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ConfiguracionRrhhService service;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private com.franco.dev.service.rrhh.AjusteSalarioMinimoService ajusteSalarioMinimoService;

    public Optional<ConfiguracionRrhh> configuracionRrhh(Long id) {
        seg.requireVer();
        return service.findById(id);
    }

    public Optional<ConfiguracionRrhh> configuracionRrhhPorClave(String clave) {
        seg.requireVer();
        return service.findByClave(clave);
    }

    public List<ConfiguracionRrhh> configuracionesRrhh(int page, int size) {
        seg.requireVer();
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<ConfiguracionRrhh> configuracionesRrhhSearch(String texto) {
        seg.requireVer();
        return service.findByAll(texto);
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<ConfiguracionRrhh> configuracionesRrhhPage(int page, int size, String texto, ConfiguracionRrhhTipo tipo) {
        seg.requireVer();
        return service.findPage(texto, tipo, PageRequest.of(page, size));
    }

    public Long countConfiguracionRrhh() {
        seg.requireVer();
        return service.count();
    }

// ---- TODO-8: impacto de un cambio de configuracion sobre datos ya materializados ----

    /** Vista previa: funcionarios activos que quedan por debajo del minimo indicado. */
    public List<Funcionario> funcionariosBajoSalarioMinimo(java.math.BigDecimal minimo) {
        seg.requireVer();
        return ajusteSalarioMinimoService.findAfectadosPorMinimo(minimo);
    }

    /** Historial de cambios de un parametro (auditoria de nomina). */
    public List<ConfiguracionRrhhHistorico> configuracionRrhhHistorico(String clave) {
        seg.requireVer();
        return service.findHistoricoPorClave(clave);
    }

    /**
     * Sube al minimo los sueldos elegidos por el usuario. Nunca se dispara solo:
     * el desktop muestra la lista y el usuario tilda a quienes ajustar.
     */
    public Integer ajustarSalariosAlMinimo(List<Integer> funcionarioIds, java.math.BigDecimal minimo,
                                           Long usuarioId) {
        seg.requireAnyRole(seg.CONFIG);
        // [Int] de GraphQL llega como List<Integer>; kickstart no convierte los elementos.
        // Declarar List<Long> aca no sirve: el borrado de tipos deja pasar los Integer y el
        // unboxing del for-each en ajustarAlMinimo tira ClassCastException. Mismo patron que
        // LiquidacionSueldoGraphQL.generarLiquidacionesLote.
        List<Long> ids = funcionarioIds == null ? null
                : funcionarioIds.stream().filter(java.util.Objects::nonNull)
                        .map(Integer::longValue).collect(java.util.stream.Collectors.toList());
        Usuario u = usuarioId != null ? usuarioService.findById(usuarioId).orElse(null) : null;
        return ajusteSalarioMinimoService.ajustarAlMinimo(ids, minimo, u);
    }

    public ConfiguracionRrhh saveConfiguracionRrhh(ConfiguracionRrhhInput input) {
        seg.requireAnyRole(seg.CONFIG);
        ModelMapper m = new ModelMapper();
        // En update, m.map(input, e) sobre la entidad cargada: los campos null del
        // input (ej. creadoEn, que el form no envia) NO deben pisar los valores
        // existentes; si no, creado_en queda null y viola el NOT NULL al guardar.
        m.getConfiguration().setSkipNullEnabled(true);
        ConfiguracionRrhh e;
        if (input.getId() != null) {
            e = service.findById(input.getId()).orElse(new ConfiguracionRrhh());
            // Se audita ANTES del map: despues el valor anterior ya se perdio.
            service.auditarCambio(e, input.getValor(),
                    input.getUsuarioId() != null
                            ? usuarioService.findById(input.getUsuarioId()).orElse(null)
                            : null);
            e.setUsuario(null);
            m.map(input, e);
        } else {
            e = m.map(input, ConfiguracionRrhh.class);
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        } else if (input.getId() == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                usuarioService.findByNickname(authentication.getName()).ifPresent(e::setUsuario);
            }
        }
        return service.save(e);
    }

    public Boolean deleteConfiguracionRrhh(Long id) {
        seg.requireAnyRole(seg.CONFIG);
        return service.deleteById(id);
    }
}
