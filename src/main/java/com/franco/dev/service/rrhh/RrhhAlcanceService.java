package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.repository.rrhh.RrhhAlcanceRepository;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Alcance del rol acotado {@code RRHH SUCURSAL}: un gerente que liquida sueldos y finiquitos solo de los
 * funcionarios de las sucursales a su cargo.
 *
 * <p>Las sucursales a cargo son las que cubren las cajas que el usuario puede mover
 * ({@link TesoreriaSecurityService#sucursalesOperablesIds()}): la misma lista que decide de qué sucursales
 * ingresa retiros. Un funcionario sin sucursal no está al alcance de nadie con este rol.</p>
 *
 * <p>El modo acotado se decide <b>por acción</b>, igual que en tesorería: sumar el rol acotado a uno de
 * RRHH no quita nada, y sumar un rol de lectura no habilita a liquidar a toda la empresa.</p>
 */
@Service
public class RrhhAlcanceService {

    private static final String FUERA_DE_ALCANCE =
            "No autorizado: ese funcionario o documento no es de las sucursales a tu cargo.";

    @Autowired private RrhhSecurityService seg;
    @Autowired private TesoreriaSecurityService tesoreria;
    @Autowired private RrhhAlcanceRepository repository;

    // ─────────────────────────── qué habilita el rol ───────────────────────────

    /** Ver liquidaciones y finiquitos: cualquier rol de RRHH o el acotado. */
    public void requireVer() {
        if (!seg.hasAnyRole(RrhhSecurityService.TODOS) && !seg.hasAnyRole(RrhhSecurityService.SUCURSAL)) {
            throw new GraphQLException("No autorizado: se requiere un rol de RRHH para ver estos datos.");
        }
    }

    /** Generar y editar liquidaciones y finiquitos. */
    public void requireLiquidar() {
        seg.requireAnyRole(RrhhSecurityService.LIQUIDAR, RrhhSecurityService.SUCURSAL);
    }

    /** Pagar liquidaciones y finiquitos aprobados. */
    public void requirePagar() {
        seg.requireAnyRole(RrhhSecurityService.PAGAR, RrhhSecurityService.SUCURSAL);
    }

    /** Lee acotado quien no tiene ningún rol de RRHH de los de siempre. */
    public boolean veAcotado() {
        return !seg.hasAnyRole(RrhhSecurityService.TODOS);
    }

    /** Liquida acotado quien no tiene {@code RRHH LIQUIDAR}. */
    public boolean liquidaAcotado() {
        return !seg.hasAnyRole(RrhhSecurityService.LIQUIDAR);
    }

    /** Paga acotado quien no tiene {@code RRHH PAGAR}. */
    public boolean pagaAcotado() {
        return !seg.hasAnyRole(RrhhSecurityService.PAGAR);
    }

    // ─────────────────────────────── alcance ───────────────────────────────

    /** Sucursales a cargo. Nunca null: vacía significa «ninguna», no «todas». */
    public List<Long> sucursales() {
        List<Long> ids = tesoreria.sucursalesOperablesIds();
        return ids != null ? ids : Collections.emptyList();
    }

    public boolean enAlcance(Funcionario f) {
        return f != null && f.getSucursal() != null && sucursales().contains(f.getSucursal().getId());
    }

    public void exigirFuncionario(Long funcionarioId) {
        exigir(funcionarioId == null ? null : repository.sucursalDeFuncionario(funcionarioId));
    }

    public void exigirLiquidacion(Long liquidacionId) {
        exigir(liquidacionId == null ? null : repository.sucursalDeLiquidacion(liquidacionId));
    }

    public void exigirItemDeLiquidacion(Long itemId) {
        exigir(itemId == null ? null : repository.sucursalDeItemDeLiquidacion(itemId));
    }

    public void exigirFiniquito(Long finiquitoId) {
        exigir(finiquitoId == null ? null : repository.sucursalDeFiniquito(finiquitoId));
    }

    public void exigirItemDeFiniquito(Long itemId) {
        exigir(itemId == null ? null : repository.sucursalDeItemDeFiniquito(itemId));
    }

    public boolean liquidacionEnAlcance(Long liquidacionId) {
        return liquidacionId != null && dentro(repository.sucursalDeLiquidacion(liquidacionId));
    }

    /** Ve el documento quien tiene un rol de RRHH de siempre, o el acotado si es de sus sucursales. */
    public void requireVerLiquidacion(Long liquidacionId) {
        requireVer();
        if (veAcotado()) exigirLiquidacion(liquidacionId);
    }

    public void requireVerItemDeLiquidacion(Long itemId) {
        requireVer();
        if (veAcotado()) exigirItemDeLiquidacion(itemId);
    }

    public void requireVerFiniquito(Long finiquitoId) {
        requireVer();
        if (veAcotado()) exigirFiniquito(finiquitoId);
    }

    public void requireVerItemDeFiniquito(Long itemId) {
        requireVer();
        if (veAcotado()) exigirItemDeFiniquito(itemId);
    }

    /**
     * El rechazo no distingue «no existe», «no tiene sucursal» ni «es de otra»: quien está acotado no
     * confirma ids ajenos.
     */
    private void exigir(List<Long> sucursalDelDocumento) {
        if (!dentro(sucursalDelDocumento)) throw new GraphQLException(FUERA_DE_ALCANCE);
    }

    private boolean dentro(List<Long> sucursalDelDocumento) {
        return sucursalDelDocumento != null && !sucursalDelDocumento.isEmpty()
                && sucursalDelDocumento.get(0) != null
                && sucursales().contains(sucursalDelDocumento.get(0));
    }

    /**
     * El pago acotado sale de una caja que el usuario puede mover, con un rol de tesorería que pague: el
     * rol de RRHH solo no alcanza para sacar efectivo.
     */
    public void exigirCajaParaPagar(Long cajaVirtualId) {
        tesoreria.requirePagarCppCaja();
        tesoreria.requireEscrituraCaja(cajaVirtualId);
    }

    // ──────────────────────────── funcionarios ────────────────────────────

    /** Ids de los funcionarios activos de las sucursales a cargo. Nunca null. */
    public List<Long> funcionariosActivosIds() {
        List<Long> sucursales = sucursales();
        if (sucursales.isEmpty()) return Collections.emptyList();
        return repository.funcionariosActivosIds(sucursales);
    }

    /** Funcionarios de las sucursales a cargo, por nombre. Hasta 30: es para un buscador. */
    public List<Funcionario> buscarFuncionarios(String texto, boolean soloActivos) {
        List<Long> sucursales = sucursales();
        if (sucursales.isEmpty()) return Collections.emptyList();
        String t = texto != null && !texto.trim().isEmpty() ? texto.trim() : null;
        return repository.buscarFuncionarios(sucursales, t, soloActivos, PageRequest.of(0, 30));
    }
}
