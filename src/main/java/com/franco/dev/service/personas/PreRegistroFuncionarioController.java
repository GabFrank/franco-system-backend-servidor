package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.PreRegistroFuncionario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/config")
@CrossOrigin
public class PreRegistroFuncionarioController {

    private static final Logger log = LoggerFactory.getLogger(PreRegistroFuncionarioController.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private PreRegistroFuncionarioService service;

    @PostMapping("/pre-registro")
    @ResponseBody
    public ResponseEntity<PreRegistroFuncionario> savePreRegistroFuncionario(@RequestBody final PreRegistroFuncionario entity) {
        // Sin login (lo llama «Solicitar nuevo usuario» de la app): solo crea. Editar o verificar
        // un pre-registro es del asistente de funcionarios, por GraphQL y con rol.
        entity.setId(null);
        entity.setFuncionario(null);
        entity.setVerificado(false);
        entity.setCreadoEn(null);
        return ResponseEntity.ok(service.save(entity));
    }
}

