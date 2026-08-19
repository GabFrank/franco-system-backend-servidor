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
    private final com.franco.dev.service.financiero.CajaVirtualAccesoService accesoService;
    private final RrhhSecurityService rrhhSeg;

    // Modelo AND: el rol habilita la capacidad (requireVer), el ACL delimita el alcance.
    // Caja puntual → se rechaza; listados → se filtran (devolver menos, no fallar).

    public CajaVirtual cajaVirtual(Long id) {
        seg.requireVer();
        seg.requireLecturaCaja(id);
        return service.findById(id).orElse(null);
    }

    public Page<CajaVirtual> cajaVirtuales(int page, int size) {
        seg.requireVer();
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(seg.cajasVisiblesIds(), pageable);
    }

    public Page<CajaVirtual> cajaVirtualesFilter(String nombre, CajaVirtualTipo tipo, Long sucursalId, Boolean activo, int page, int size) {
        seg.requireVer();
        return service.filter(seg.cajasVisiblesIds(), nombre, tipo, sucursalId, activo, PageRequest.of(page, size));
    }

    /** Saldo de efectivo por moneda (fuente de verdad: caja_virtual_saldo). */
    public List<CajaVirtualSaldoItem> cajaVirtualSaldos(Long cajaVirtualId) {
        seg.requireVer();
        seg.requireLecturaCaja(cajaVirtualId);
        return cajaSaldoRepository.findByCajaVirtualId(cajaVirtualId).stream()
                .map(s -> new CajaVirtualSaldoItem(s.getMoneda(), s.getSaldo()))
                .collect(Collectors.toList());
    }

    /** Cuentas bancarias visibles (según config) con saldo actual/reservado/futuro, ordenadas. */
    @Transactional(readOnly = true)
    public List<CuentaBancariaResumen> cajaVirtualResumenBancario(Long cajaVirtualId) {
        seg.requireVer();
        seg.requireLecturaCaja(cajaVirtualId);
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
        return service.findByTipo(seg.cajasVisiblesIds(), tipo);
    }

    public List<CajaVirtual> cajaVirtualesPorSucursal(Long sucursalId) {
        seg.requireVer();
        return service.findBySucursalId(seg.cajasVisiblesIds(), sucursalId);
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
        return service.findActivas(seg.cajasVisiblesIds());
    }

    // ── Administracion de accesos ──
    //
    // Todo esto exige ser el responsable de la caja (o superusuario): es lo que decide quien
    // ve y quien mueve plata, asi que no alcanza con TESORERIA GESTIONAR.

    public List<com.franco.dev.domain.financiero.CajaVirtualAcceso> cajaVirtualAccesos(Long cajaVirtualId) {
        seg.requirePropietarioCaja(cajaVirtualId);
        return accesoService.listar(cajaVirtualId);
    }

    public com.franco.dev.domain.financiero.CajaVirtualAcceso otorgarAccesoCaja(
            Long cajaVirtualId, Long usuarioId, Boolean puedeLeer, Boolean puedeEscribir) {
        seg.requirePropietarioCaja(cajaVirtualId);
        return accesoService.otorgar(cajaVirtualId, usuarioId, puedeLeer, puedeEscribir, seg.currentUsuario());
    }

    public Boolean revocarAccesoCaja(Long cajaVirtualId, Long usuarioId) {
        seg.requirePropietarioCaja(cajaVirtualId);
        return accesoService.revocar(cajaVirtualId, usuarioId);
    }

    public CajaVirtual transferirPropiedadCaja(Long cajaVirtualId, Long nuevoPropietarioId) {
        seg.requirePropietarioCaja(cajaVirtualId);
        return accesoService.transferirPropiedad(cajaVirtualId, nuevoPropietarioId);
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
        // Propietario: quien crea la caja, tomado del SecurityContext y NO del input.
        //
        // Se setea solo en el alta: en un update el campo no se toca, para que editar una caja
        // ajena no te vuelva su duenio. El usuarioId del input se ignora a proposito — venia del
        // cliente y cualquiera podia mandar cualquier id. Transferir la propiedad se hace con
        // transferirPropiedadCaja, que exige ser el duenio o ADMIN.
        if (input.getId() == null) {
            entity.setUsuario(seg.currentUsuario());
        }
        return service.save(entity);
    }

    public Boolean deleteCajaVirtual(Long id) {
        seg.requireGestionar();
        return service.deleteById(id);
    }
}
