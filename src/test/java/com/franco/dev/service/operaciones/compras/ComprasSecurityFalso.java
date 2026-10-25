package com.franco.dev.service.operaciones.compras;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Sesión fija para tests de resolvers: usa los exigir* reales con los roles que se le pasan. */
public class ComprasSecurityFalso extends ComprasSecurityService {

    private final Set<String> roles;

    public ComprasSecurityFalso(String... roles) {
        this.roles = new HashSet<>(Arrays.asList(roles));
    }

    @Override
    protected Set<String> rolesActuales() {
        return roles;
    }
}
