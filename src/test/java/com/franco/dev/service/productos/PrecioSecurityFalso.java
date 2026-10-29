package com.franco.dev.service.productos;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Sesión fija para tests de resolvers: usa los require* reales con los roles que se le pasan. */
public class PrecioSecurityFalso extends PrecioSecurityService {

    private final Set<String> roles;

    public PrecioSecurityFalso(String... roles) {
        this.roles = new HashSet<>(Arrays.asList(roles));
    }

    @Override
    public boolean hasAnyRole(String... pedidos) {
        if (roles.contains(ADMIN)) return true;
        for (String r : pedidos) if (roles.contains(r)) return true;
        return false;
    }
}
