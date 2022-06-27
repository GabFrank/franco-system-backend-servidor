package com.franco.dev.config;

import com.franco.dev.domain.configuracion.SincronizacionStatus;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.empresarial.SucursalService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.Serializable;
import java.util.List;

@RestController
@RequestMapping("/config")
@CrossOrigin
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SucursalService service;

    @PostMapping
    @RequestMapping(value = "/verificar")
    @ResponseBody
    public ResponseEntity<Boolean> verificar() {
        log.info("enviando verificacion ok");
        return ResponseEntity.ok(true);

    }

    @RequestMapping(value = "/sucursales")
    @ResponseBody
    public ResponseEntity<SucursalesDto> getSucursales() {
        log.info("Enviando sucursales");
        List<Sucursal> sucursalList = service.findAllNotConfigured();
        return ResponseEntity.ok(new SucursalesDto(sucursalList));
    }

    @RequestMapping(value = "/all-sucursales")
    @ResponseBody
    public ResponseEntity<SucursalesDto> getAllSucursales() {
        log.info("Enviando sucursales");
        List<Sucursal> sucursalList = service.findAll2();
        return ResponseEntity.ok(new SucursalesDto(sucursalList));
    }
}

@Data
@AllArgsConstructor
class SucursalesDto implements Serializable {
    private List<Sucursal> sucursalList;
}
