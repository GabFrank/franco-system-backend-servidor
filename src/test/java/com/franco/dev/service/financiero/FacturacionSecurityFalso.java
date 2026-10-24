package com.franco.dev.service.financiero;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Sesion fija para tests de resolvers: los require* reales de facturacion con los roles dados. */
public class FacturacionSecurityFalso extends FacturacionSecurityService {

    private final Set<String> roles;

    public FacturacionSecurityFalso(String... roles) {
        this.roles = new HashSet<>(Arrays.asList(roles));
    }

    @Override
    public Set<String> currentRoles() { return roles; }

    @Override
    public boolean hasAnyRole(String... pedidos) {
        if (roles.contains(ADMIN)) return true;
        for (String r : pedidos) if (roles.contains(r)) return true;
        return false;
    }
}
