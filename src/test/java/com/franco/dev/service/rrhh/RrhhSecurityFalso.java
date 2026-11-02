package com.franco.dev.service.rrhh;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Sesión fija para tests de resolvers: usa los require* reales con los roles que se le pasan. */
public class RrhhSecurityFalso extends RrhhSecurityService {

    private final Set<String> roles;

    public RrhhSecurityFalso(String... roles) {
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
