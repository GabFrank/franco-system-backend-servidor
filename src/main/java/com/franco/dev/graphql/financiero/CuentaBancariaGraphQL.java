package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.financiero.input.CuentaBancariaInput;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.CuentaBancariaService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CuentaBancariaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CuentaBancariaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private BancoService bancoService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private PersonaService personaService;


    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<CuentaBancaria> cuentaBancaria(Long id) {
        return service.findById(id);
    }

    public List<CuentaBancaria> cuentasBancarias(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    /** Cuentas propias operables (activas + disponibles para operaciones financieras): para caja mayor y operaciones. */
    public List<CuentaBancaria> cuentasBancariasOperables() {
        return service.getRepository().findOperables();
    }


    public CuentaBancaria saveCuentaBancaria(CuentaBancariaInput input) {
        ModelMapper m = new ModelMapper();
        CuentaBancaria e = m.map(input, CuentaBancaria.class);
        // saldo/saldoReservado los administra el ledger (BancoLedgerService); nunca por el CRUD.
        // En edición se preservan; en alta arrancan en 0.
        if (input.getId() != null) {
            final CuentaBancaria target = e;
            service.findById(input.getId()).ifPresent(prev -> {
                target.setSaldo(prev.getSaldo());
                target.setSaldoReservado(prev.getSaldoReservado());
            });
        } else {
            if (e.getSaldo() == null) e.setSaldo(java.math.BigDecimal.ZERO);
            if (e.getSaldoReservado() == null) e.setSaldoReservado(java.math.BigDecimal.ZERO);
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getBancoId() != null) {
            e.setBanco(bancoService.findById(input.getBancoId()).orElse(null));
        }
        if (input.getMonedaId() != null) {
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if (input.getPersonaId() != null) {
            e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        }
        e = service.save(e);
        return e;
    }

    public List<CuentaBancaria> cuentaBancariasSearch(String texto) {
        return service.findByAll(texto);
    }

    public Boolean deleteCuentaBancaria(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countCuentaBancaria() {
        return service.count();
    }


}
