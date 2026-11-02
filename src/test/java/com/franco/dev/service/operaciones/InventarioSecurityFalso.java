package com.franco.dev.service.operaciones;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Sesión fija para tests de resolvers: usa los require* reales con los roles que se le pasan. */
public class InventarioSecurityFalso extends InventarioSecurityService {

    private final Set<String> roles;

    public InventarioSecurityFalso(String... roles) {
        this.roles = new HashSet<>(Arrays.asList(roles));
    }

    @Override
    public boolean hasAnyRole(String... pedidos) {
        if (roles.contains(ADMIN)) return true;
        for (String r : pedidos) if (roles.contains(r)) return true;
        return false;
    }
}
