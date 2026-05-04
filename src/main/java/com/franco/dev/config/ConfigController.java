package com.franco.dev.config;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.utils.ImageService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.zeroturnaround.zip.ZipUtil;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

    @Autowired
    private ImageService imageService;

    @Value("${app.timezone:America/Asuncion}")
    private String appTimezone;

    @PostMapping
    @RequestMapping(value = "/verificar")
    @ResponseBody
    public ResponseEntity<Boolean> verificar() {
        log.info("enviando verificacion ok");
        return ResponseEntity.ok(true);

    }

    @GetMapping(value = "/hora-servidor")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> horaServidor() {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        ZoneId zone;
        try {
            zone = ZoneId.of(appTimezone.trim());
        } catch (DateTimeException e) {
            log.warn("app.timezone inválido '{}', usando America/Asuncion", appTimezone);
            zone = ZoneId.of("America/Asuncion");
        }
        long epochMillis = System.currentTimeMillis();
        ZonedDateTime zdt = Instant.ofEpochMilli(epochMillis).atZone(zone);
        String isoConOffset = zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        response.put("epochMillis", epochMillis);
        response.put("timestamp", epochMillis);
        response.put("zoneId", zone.getId());
        response.put("fechaHoraServidor", isoConOffset);
        response.put("horaServidor", isoConOffset);
        return ResponseEntity.ok(response);
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

    @GetMapping("/resources")
    public ResponseEntity<byte[]> downloadZipFile(HttpServletResponse response) {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=download.zip");
        ZipUtil.pack(new File(imageService.getResourcesPath()), new File(imageService.getResourcesPath() + ".zip"));
        byte[] fileContent = new byte[0];
        try {
            fileContent = FileUtils.readFileToByteArray(new File(imageService.getResourcesPath() + ".zip"));
            return ResponseEntity.ok(fileContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            imageService.deleteFile(imageService.getResourcesPath() + ".zip");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}

@Data
@AllArgsConstructor
class SucursalesDto implements Serializable {
    private List<Sucursal> sucursalList;
}
