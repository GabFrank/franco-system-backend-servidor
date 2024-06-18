package com.franco.dev.config;

import com.franco.dev.config.multitenant.TenantContext;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.service.operaciones.VentaService;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class MultitenantTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private TenantContext tenantContext;

    @Test
    void contextLoads() {
        List<Venta> ventaList = ventaService.findAll2();

        for(Venta v: ventaList){
            System.out.println("ID: " + v.getId() + ", Total: " + v.getTotalGs());
        }

        tenantContext.setCurrentTenant("filial4_bkp");

        ventaList = ventaService.findAll2();

        for(Venta v: ventaList){
            System.out.println("ID: " + v.getId() + ", Total: " + v.getTotalGs());
        }

        tenantContext.clear();
    }
}
