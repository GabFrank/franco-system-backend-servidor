package com.franco.dev.fmc.service;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class NotificationRoleService {

    @Autowired
    private RoleService roleService;

    @Autowired
    private UsuarioRoleService usuarioRoleService;

    /**
     * Obtiene los IDs de usuarios que tienen los roles especificados
     * 
     * @param roleNames Lista de nombres de roles
     * @return Lista de IDs de usuarios unicos
     */
    public List<Long> getUserIdsByRoles(List<String> roleNames) {
        Set<Long> userIds = new HashSet<>();

        for (String roleName : roleNames) {
            Role role = roleService.findByNombre(roleName);

            if (role != null) {
                List<UsuarioRole> usuarioRoles = usuarioRoleService.findByRoleId(role.getId());
                usuarioRoles.stream()
                        .filter(ur -> ur.getUser() != null)
                        .map(ur -> ur.getUser().getId())
                        .forEach(userIds::add);
            }
        }

        return new ArrayList<>(userIds);
    }

    public List<String> getRolesForVentaCredito() {
        return Arrays.asList(
                "ADMIN",
                "SOPORTE",
                "ANALISIS FINANCIERO",
                "ANALISIS CONTABLE",
                "ANALISIS DE CAJA",
                "ANALISIS DE VENTA");
    }

    public List<String> getRolesForFacturaAltoValor() {
        return Arrays.asList(
                "ADMIN",
                "ANALISIS FINANCIERO",
                "ANALISIS CONTABLE",
                "ANALISIS DE CAJA",
                "ANALISIS DE VENTA");
    }

    public List<String> getRolesForAjusteStock() {
        return Arrays.asList(
                "ADMIN",
                "SOPORTE",
                "VER PRODUCTOS",
                "CREAR INVENTARIO",
                "VER MOVIMIENTO DE STOCK",
                "EDITAR PRODUCTOS",
                "CREAR PRECIOS",
                "EDITAR PRECIOS",
                "VER PRECIO COSTO");
    }

    public List<String> getRolesForProductoCreado() {
        return Arrays.asList(
                "ADMIN",
                "SOPORTE",
                "VER PRODUCTOS",
                "CREAR INVENTARIO",
                "VER MOVIMIENTO DE STOCK",
                "EDITAR PRODUCTOS",
                "CREAR PRECIOS",
                "EDITAR PRECIOS",
                "VER PRECIO COSTO");
    }

    public List<String> getRolesForTransferenciaIniciada() {
        return Arrays.asList(
                "ADMIN",
                "SOPORTE",
                "VER TRANSFERENCIA",
                "VENTA TOUCH",
                "CREAR TRANSFERENCIA",
                "VER MOVIMIENTO DE STOCK");
    }

    public List<String> getRolesForCambioSucursalPreTransferencia() {
        return Arrays.asList(
                "ADMIN",
                "SOPORTE",
                "VER TRANSFERENCIA",
                "VENTA TOUCH",
                "CREAR TRANSFERENCIA",
                "VER MOVIMIENTO DE STOCK");
    }

    public List<String> getRolesForAjusteCosto() {
        return Arrays.asList(
                "ADMIN",
                "SOPORTE",
                "ANALISIS DE VENTA",
                "VER PRECIO COSTO",
                "EDITAR PRODUCTOS",
                "CREAR PRECIOS",
                "EDITAR PRECIOS",
                "VER PRECIO COSTO");
    }

    public List<String> getRolesForPrecioActualizado() {
        return Arrays.asList(
                "ADMIN",
                "SOPORTE",
                "ANALISIS DE VENTA",
                "VENTA TOUCH",
                "CREAR PRECIOS",
                "EDITAR PRECIOS",
                "VER PRECIO COSTO");
    }

    public List<String> getRolesForInventarioIniciado() {
        return Arrays.asList(
                "ADMIN",
                "SOPORTE",
                "VER INVENTARIO",
                "CREAR INVENTARIO",
                "PARTICIPAR DEL INVENTARIO",
                "VER MOVIMIENTO DE STOCK",
                "EDITAR PRODUCTOS",
                "CREAR PRECIOS",
                "EDITAR PRECIOS",
                "VER PRECIO COSTO");
    }

    public List<String> getRolesForGastoRetiro() {
        return Arrays.asList(
                "ADMIN",
                "SOPORTE",
                "CANCELACION DE VENTA",
                "ANALISIS DE CAJA",
                "ANALISIS DE VENTA ADMIN",
                "ANALISIS FINANCIERO",
                "ANALISIS CONTABLE");
    }
}
