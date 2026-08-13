package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CajaVirtualConfiguracion;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipo;
import com.franco.dev.graphql.financiero.dto.CajaVirtualSaldoItem;
import com.franco.dev.graphql.financiero.dto.CuentaBancariaResumen;
import com.franco.dev.graphql.financiero.input.CajaVirtualInput;
import com.franco.dev.repository.financiero.AcreditacionPosRepository;
import com.franco.dev.repository.financiero.CajaVirtualConfiguracionRepository;
import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class CajaVirtualGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final CajaVirtualService service;
    private final SucursalService sucursalService;
    private final FuncionarioService funcionarioService;
    private final UsuarioService usuarioService;
    private final CajaVirtualSaldoRepository cajaSaldoRepository;
    private final CajaVirtualConfiguracionRepository configRepository;
    private final AcreditacionPosRepository acreditacionPosRepository;
    private final TesoreriaSecurityService seg;
    private final RrhhSecurityService rrhhSeg;

    public CajaVirtual cajaVirtual(Long id) {
        seg.requireVer();
        return service.findById(id).orElse(null);
    }

    public Page<CajaVirtual> cajaVirtuales(int page, int size) {
        seg.requireVer();
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Page<CajaVirtual> cajaVirtualesFilter(String nombre, CajaVirtualTipo tipo, Long sucursalId, Boolean activo, int page, int size) {
        seg.requireVer();
        return service.filter(nombre, tipo, sucursalId, activo, PageRequest.of(page, size));
    }

    /** Saldo de efectivo por moneda (fuente de verdad: caja_virtual_saldo). */
    public List<CajaVirtualSaldoItem> cajaVirtualSaldos(Long cajaVirtualId) {
        seg.requireVer();
        return cajaSaldoRepository.findByCajaVirtualId(cajaVirtualId).stream()
                .map(s -> new CajaVirtualSaldoItem(s.getMoneda(), s.getSaldo()))
                .collect(Collectors.toList());
    }

    /** Cuentas bancarias visibles (según config) con saldo actual/reservado/futuro, ordenadas. */
    @Transactional(readOnly = true)
    public List<CuentaBancariaResumen> cajaVirtualResumenBancario(Long cajaVirtualId) {
        seg.requireVer();
        CajaVirtualConfiguracion cfg = configRepository.findByCajaVirtualId(cajaVirtualId).orElse(null);
        if (cfg == null || cfg.getCuentasBancariasVisibles() == null || cfg.getCuentasBancariasVisibles().isEmpty()) {
            return new ArrayList<>();
        }
        List<CuentaBancaria> ordenadas = ordenarCuentas(cfg.getCuentasBancariasVisibles(), cfg.getCuentasBancariasOrden());
        List<CuentaBancariaResumen> out = new ArrayList<>();
        for (CuentaBancaria cb : ordenadas) {
            BigDecimal futuro = acreditacionPosRepository.sumEsperadoPendienteByCuenta(cb.getId());
            out.add(new CuentaBancariaResumen(cb, cb.getSaldo(), cb.getSaldoReservado(), futuro));
        }
        return out;
    }

    /** Ordena las cuentas según el JSON de ids persistido; las faltantes van al final por id ascendente. */
    private List<CuentaBancaria> ordenarCuentas(java.util.Set<CuentaBancaria> visibles, String ordenJson) {
        List<CuentaBancaria> lista = new ArrayList<>(visibles);
        final List<Long> orden = parseOrden(ordenJson);
        lista.sort((a, b) -> {
            int ia = orden.indexOf(a.getId());
            int ib = orden.indexOf(b.getId());
            if (ia < 0 && ib < 0) return Long.compare(a.getId(), b.getId());
            if (ia < 0) return 1;
            if (ib < 0) return -1;
            return Integer.compare(ia, ib);
        });
        return lista;
    }

    /** Parsea un JSON array simple de ids ("[3,1,2]") a List<Long>. Tolerante a null/vacío. */
    private List<Long> parseOrden(String ordenJson) {
        List<Long> out = new ArrayList<>();
        if (ordenJson == null) return out;
        String s = ordenJson.replaceAll("[\\[\\]\\s]", "");
        if (s.isEmpty()) return out;
        for (String part : s.split(",")) {
            try { out.add(Long.parseLong(part)); } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    public List<CajaVirtual> cajaVirtualesPorTipo(CajaVirtualTipo tipo) {
        seg.requireVer();
        return service.findByTipo(tipo);
    }

    public List<CajaVirtual> cajaVirtualesPorSucursal(Long sucursalId) {
        seg.requireVer();
        return service.findBySucursalId(sucursalId);
    }

    /**
     * Lectura compartida: la usa Tesorería y también RRHH (para elegir la caja
     * mayor destino en liquidación/aguinaldo). Habilitada para cualquier rol de
     * tesorería o de RRHH (o superusuario).
     */
    public List<CajaVirtual> cajaVirtualesActivas() {
        if (!seg.hasAnyRole(TesoreriaSecurityService.TODOS)
                && !rrhhSeg.hasAnyRole(RrhhSecurityService.TODOS)) {
            throw new GraphQLException("No autorizado: se requiere un rol de Tesorería o de RRHH.");
        }
        return service.findActivas();
    }

    public CajaVirtual saveCajaVirtual(CajaVirtualInput input) {
        seg.requireGestionar();
        CajaVirtual entity = new CajaVirtual();
        if (input.getId() != null) {
            entity = service.findById(input.getId())
                    .orElseThrow(() -> new GraphQLException("Caja virtual no encontrada: " + input.getId()));
        }
        entity.setNombre(input.getNombre());
        entity.setTipo(input.getTipo());
        entity.setDescripcion(input.getDescripcion());
        entity.setLimiteGs(input.getLimiteGs());
        entity.setActivo(input.getActivo());
        if (input.getSucursalId() != null) {
            entity.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        }
        if (input.getResponsableId() != null) {
            entity.setResponsable(funcionarioService.findById(input.getResponsableId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.save(entity);
    }

    public Boolean deleteCajaVirtual(Long id) {
        seg.requireGestionar();
        return service.deleteById(id);
    }
}
