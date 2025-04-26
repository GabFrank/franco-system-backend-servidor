package com.franco.dev.service.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.dto.ExcelFacturasDto;
import com.franco.dev.domain.financiero.dto.ResumenFacturasDto;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.utils.ImageService;
import lombok.AllArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.dateToStringWithFormat;
import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Service
@AllArgsConstructor
public class FacturaLegalService extends CrudService<FacturaLegal, FacturaLegalRepository, EmbebedPrimaryKey> {

    private final FacturaLegalRepository repository;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private ImageService imageService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Override
    public FacturaLegalRepository getRepository() {
        return repository;
    }

    public List<FacturaLegal> findByCajaId(Long id) {
        return repository.findByCajaId(id);
    }

    public FacturaLegal findByIdAndSucursalId(Long id, Long sucId){
        return repository.findByIdAndSucursalId(id, sucId);
    }

    public FacturaLegal findByVentaIdAndSucursalId(Long id, Long sucId) {
        return repository.findByVentaIdAndSucursalId(id, sucId);
    }

    public Page<FacturaLegal> findByAll(Integer page, Integer size, String fechaInicio, String fechaFin, List<Long> sucId, String ruc, String nombre, Boolean iva5, Boolean iva10) {
        LocalDateTime inicio = stringToDate(fechaInicio);
        LocalDateTime fin = stringToDate(fechaFin);
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByCreadoEnBetweenAndSucursalId(inicio, fin, sucId, nombre, ruc, pageable);
    }

    public ResumenFacturasDto findResumenFacturas(String fechaInicio, String fechaFin, List<Long> sucId, String ruc, String nombre, Boolean iva5, Boolean iva10) {
        LocalDateTime inicio = stringToDate(fechaInicio);
        LocalDateTime fin = stringToDate(fechaFin);
        return repository.findResumenFacturas(inicio, fin, sucId, nombre, ruc);
    }

    @Override
    public FacturaLegal save(FacturaLegal entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCliente() == null) {
            Cliente newCliente = crearCliente(entity.getNombre(), entity.getRuc(), entity.getDireccion(), entity.getUsuario());
            entity.setCliente(newCliente);
        }
        FacturaLegal e = super.save(entity);
        return e;
    }

    public Cliente crearCliente(String nombre, String ruc, String direccion, Usuario usuario) {
        if (nombre != null && ruc != null) {
            Persona foundPersona = personaService.findByDocumento(ruc);
            if (foundPersona == null) {
                foundPersona = new Persona();
                foundPersona.setDocumento(ruc);
                foundPersona.setNombre(nombre);
                foundPersona.setDireccion(direccion);
                foundPersona.setUsuario(usuario);
                foundPersona = personaService.save(foundPersona);
//                propagacionService.propagarEntidad(foundPersona, TipoEntidad.PERSONA);
            }
            Cliente newCliente = new Cliente();
            newCliente.setUsuario(usuario);
            newCliente.setPersona(foundPersona);
            newCliente.setTipo(TipoCliente.NORMAL);
            newCliente = clienteService.save(newCliente);
//            propagacionService.propagarEntidad(newCliente, TipoEntidad.CLIENTE);
            return newCliente;
        } else {
            return null;
        }
    }

    public String createExcelReport(String fechaInicio, String fechaFin, Long sucId) {
        LocalDateTime inicio = stringToDate(fechaInicio);
        LocalDateTime fin = stringToDate(fechaFin);
        Sucursal sucursal = sucursalService.findById(sucId).orElse(null);
        List<ExcelFacturasDto> dataList = getExcelFacturas(inicio, fin, sucId);
        XSSFWorkbook workbook;
        XSSFSheet sheet;

        if (dataList == null || dataList.size() == 0) return "";
        try {
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet(sucursal.getNombre().replace(" ", "_").toLowerCase() + "_" + fechaInicio);
            // Create the header row
            Row headerRow = sheet.createRow(0);
            String[] columnHeaders = {
                    "ven_tipimp", "ven_gra05", "ven_iva05", "ven_disg05", "cta_iva05",
                    "ven_rubgra", "ven_rubg05", "ven_disexe", "ven_numero", "ven_imputa",
                    "ven_sucurs", "generar", "form_pag", "ven_centro", "ven_provee",
                    "ven_cuenta", "ven_prvnom", "ven_tipofa", "ven_fecha", "ven_totfac",
                    "ven_exenta", "ven_gravad", "ven_iva", "ven_retenc", "ven_aux",
                    "ven_ctrl", "ven_con", "ven_cuota", "ven_fecven", "cant_dias",
                    "origen", "cambio", "valor", "moneda", "exen_dolar",
                    "concepto", "cta_iva", "cta_caja", "tkdesde", "tkhasta",
                    "caja", "ven_disgra", "forma_devo", "ven_cuense", "anular",
                    "reproceso", "cuenta_exe", "usu_ide", "rucvennrotim", "clieasi",
                    "ventirptip", "ventirpgra", "ventirpexe", "irpc", "ivasimplificado",
                    "venirprygc", "venbconom", "venbcoctacte", "nofacnotcre", "notimbfacnotcre",
                    "ventipodoc", "ventanoiva", "identifclie", "gdcbienid", "gdctipobien",
                    "gdcimpcosto", "gdcimpventagrav"
            };

            for (int i = 0; i < columnHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnHeaders[i]);
            }

            // Populate data rows
            int rowNum = 1;
            for (ExcelFacturasDto data : dataList) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(data.getVenTipimp());
                row.createCell(1).setCellValue(data.getVenGra05());
                row.createCell(2).setCellValue(data.getVenIva05());
                row.createCell(3).setCellValue(data.getVenDisg05());
                row.createCell(4).setCellValue(data.getCtaIva05());
                row.createCell(5).setCellValue(data.getVenRubgra());
                row.createCell(6).setCellValue(data.getVenRubg05());
                row.createCell(7).setCellValue(data.getVenDisexe());
                row.createCell(8).setCellValue(data.getVenNumero());
                row.createCell(9).setCellValue(data.getVenImputa());
                row.createCell(10).setCellValue(data.getVenSucurs());
                row.createCell(11).setCellValue(data.getGenerar());
                row.createCell(12).setCellValue(data.getFormPag());
                row.createCell(13).setCellValue(data.getVenCentro());
                row.createCell(14).setCellValue(data.getVenProvee());
                row.createCell(15).setCellValue(data.getVenCuenta());
                row.createCell(16).setCellValue(data.getVenPrvnom());
                row.createCell(17).setCellValue(data.getVenTipofa());
                row.createCell(18).setCellValue(data.getVenFecha());
                row.createCell(19).setCellValue(data.getVenTotfac());
                row.createCell(20).setCellValue(data.getVenExenta());
                row.createCell(21).setCellValue(data.getVenGravad());
                row.createCell(22).setCellValue(data.getVenIva());
                row.createCell(23).setCellValue(data.getVenRetenc());
                row.createCell(24).setCellValue(data.getVenAux());
                row.createCell(25).setCellValue(data.getVenCtrl());
                row.createCell(26).setCellValue(data.getVenCon());
                row.createCell(27).setCellValue(data.getVenCuota());
                row.createCell(28).setCellValue(data.getVenFecven());
                row.createCell(29).setCellValue(data.getCantDias());
                row.createCell(30).setCellValue(data.getOrigen());
                row.createCell(31).setCellValue(data.getCambio());
                row.createCell(32).setCellValue(data.getValor());
                row.createCell(33).setCellValue(data.getMoneda());
                row.createCell(34).setCellValue(data.getExenDolar());
                row.createCell(35).setCellValue(data.getConcepto());
                row.createCell(36).setCellValue(data.getCtaIva());
                row.createCell(37).setCellValue(data.getCtaCaja());
                row.createCell(38).setCellValue(data.getTkdesde());
                row.createCell(39).setCellValue(data.getTkhasta());
                row.createCell(40).setCellValue(data.getCaja());
                row.createCell(41).setCellValue(data.getVenDisgra());
                row.createCell(42).setCellValue(data.getFormaDevo());
                row.createCell(43).setCellValue(data.getVenCuense());
                row.createCell(44).setCellValue(data.getAnular());
                row.createCell(45).setCellValue(data.getReproceso());
                row.createCell(46).setCellValue(data.getCuentaExe());
                row.createCell(47).setCellValue(data.getUsuIde());
                row.createCell(48).setCellValue(data.getRucvennrotim());
                row.createCell(49).setCellValue(data.getClieasi());
                row.createCell(50).setCellValue(data.getVentirptip());
                row.createCell(51).setCellValue(data.getVentirpgra());
                row.createCell(52).setCellValue(data.getVentirpexe());
                row.createCell(53).setCellValue(data.getIrpc());
                row.createCell(54).setCellValue(data.getIvasimplificado());
                row.createCell(55).setCellValue(data.getVenirprygc());
                row.createCell(56).setCellValue(data.getVenbconom());
                row.createCell(57).setCellValue(data.getVenbcoctacte());
                row.createCell(58).setCellValue(data.getNofacnotcre());
                row.createCell(59).setCellValue(data.getNotimbfacnotcre());
                row.createCell(60).setCellValue(data.getVentipodoc());
                row.createCell(61).setCellValue(data.getVentanoiva());
                row.createCell(62).setCellValue(data.getIdentifclie());
                row.createCell(63).setCellValue(data.getGdcbienid());
                row.createCell(64).setCellValue(data.getGdctipobien());
                row.createCell(65).setCellValue(data.getGdcimpcosto());
                row.createCell(66).setCellValue(data.getGdcimpventagrav());
            }

            // Write the output to a byte array
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                workbook.write(outputStream);
                String base64String = Base64.getEncoder().encodeToString(outputStream.toByteArray());
                return base64String;
            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public Workbook createExcelWorkbook(String fechaInicio, String fechaFin, Long sucId) {
        LocalDateTime inicio = stringToDate(fechaInicio);
        LocalDateTime fin = stringToDate(fechaFin);
        List<ExcelFacturasDto> dataList = getExcelFacturas(inicio, fin, sucId);
        XSSFWorkbook workbook;
        XSSFSheet sheet;
        Sucursal sucursal = sucursalService.findById(sucId).orElse(null);

        if (dataList == null || dataList.size() == 0) return null;
        try {
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet(sucursal.getNombre().replace(" ", "_").toLowerCase() + "_" + fechaInicio.substring(0, 10));
            // Create the header row
            Row headerRow = sheet.createRow(0);
            String[] columnHeaders = {
                    "ven_tipimp",
                    "ven_gra05",
                    "ven_iva05",
                    "ven_disg05",
                    "cta_iva05",
                    "ven_rubgra",
                    "ven_rubg05",
                    "ven_disexe",
                    "ven_numero",
                    "ven_imputa",
                    "ven_sucurs",
                    "generar",
                    "form_pag",
                    "ven_centro",
                    "ven_provee",
                    "ven_cuenta",
                    "ven_prvnom",
                    "ven_tipofa",
                    "ven_fecha",
                    "ven_totfac",
                    "ven_exenta",
                    "ven_gravad",
                    "ven_iva",
                    "ven_retenc",
                    "ven_aux",
                    "ven_ctrl",
                    "ven_con",
                    "ven_cuota",
                    "ven_fecven",
                    "cant_dias",
                    "origen",
                    "cambio",
                    "valor",
                    "moneda",
                    "exen_dolar",
                    "concepto",
                    "cta_iva",
                    "cta_caja",
                    "tkdesde",
                    "tkhasta",
                    "caja",
                    "ven_disgra",
                    "forma_devo",
                    "ven_cuense",
                    "anular",
                    "reproceso",
                    "cuenta_exe",
                    "usu_ide",
                    "rucvennrotim",
                    "clieasi",
                    "ventirptip",
                    "ventirpgra",
                    "ventirpexe",
                    "irpc",
                    "ivasimplificado",
                    "venirprygc",
                    "venbconom",
                    "venbcoctacte",
                    "nofacnotcre",
                    "notimbfacnotcre",
                    "ventipodoc",
                    "ventanoiva",
                    "identifclie",
                    "gdcbienid",
                    "gdctipobien",
                    "gdcimpcosto",
                    "gdcimpventagrav"
            };

            for (int i = 0; i < columnHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnHeaders[i]);
            }

            // Populate data rows
            int rowNum = 1;
            for (ExcelFacturasDto data : dataList) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(data.getVenTipimp());
                row.createCell(1).setCellValue(data.getVenGra05());
                row.createCell(2).setCellValue(data.getVenIva05());
                row.createCell(3).setCellValue(data.getVenDisg05());
                row.createCell(4).setCellValue(data.getCtaIva05());
                row.createCell(5).setCellValue(data.getVenRubgra());
                row.createCell(6).setCellValue(data.getVenRubg05());
                row.createCell(7).setCellValue(data.getVenDisexe());
                row.createCell(8).setCellValue(data.getVenNumero());
                row.createCell(9).setCellValue(data.getVenImputa());
                row.createCell(10).setCellValue(data.getVenSucurs());
                row.createCell(11).setCellValue(data.getGenerar());
                row.createCell(12).setCellValue(data.getFormPag());
                row.createCell(13).setCellValue(data.getVenCentro());
                row.createCell(14).setCellValue(data.getVenProvee());
                row.createCell(15).setCellValue(data.getVenCuenta());
                row.createCell(16).setCellValue(data.getVenPrvnom());
                row.createCell(17).setCellValue(data.getVenTipofa());
                row.createCell(18).setCellValue(data.getVenFecha());
                row.createCell(19).setCellValue(data.getVenTotfac());
                row.createCell(20).setCellValue(data.getVenExenta());
                row.createCell(21).setCellValue(data.getVenGravad());
                row.createCell(22).setCellValue(data.getVenIva());
                row.createCell(23).setCellValue(data.getVenRetenc());
                row.createCell(24).setCellValue(data.getVenAux());
                row.createCell(25).setCellValue(data.getVenCtrl());
                row.createCell(26).setCellValue(data.getVenCon());
                row.createCell(27).setCellValue(data.getVenCuota());
                row.createCell(28).setCellValue(data.getVenFecven());
                row.createCell(29).setCellValue(data.getCantDias());
                row.createCell(30).setCellValue(data.getOrigen());
                row.createCell(31).setCellValue(data.getCambio());
                row.createCell(32).setCellValue(data.getValor());
                row.createCell(33).setCellValue(data.getMoneda());
                row.createCell(34).setCellValue(data.getExenDolar());
                row.createCell(35).setCellValue(data.getConcepto());
                row.createCell(36).setCellValue(data.getCtaIva());
                row.createCell(37).setCellValue(data.getCtaCaja());
                row.createCell(38).setCellValue(data.getTkdesde());
                row.createCell(39).setCellValue(data.getTkhasta());
                row.createCell(40).setCellValue(data.getCaja());
                row.createCell(41).setCellValue(data.getVenDisgra());
                row.createCell(42).setCellValue(data.getFormaDevo());
                row.createCell(43).setCellValue(data.getVenCuense());
                row.createCell(44).setCellValue(data.getAnular());
                row.createCell(45).setCellValue(data.getReproceso());
                row.createCell(46).setCellValue(data.getCuentaExe());
                row.createCell(47).setCellValue(data.getUsuIde());
                row.createCell(48).setCellValue(data.getRucvennrotim());
                row.createCell(49).setCellValue(data.getClieasi());
                row.createCell(50).setCellValue(data.getVentirptip());
                row.createCell(51).setCellValue(data.getVentirpgra());
                row.createCell(52).setCellValue(data.getVentirpexe());
                row.createCell(53).setCellValue(data.getIrpc());
                row.createCell(54).setCellValue(data.getIvasimplificado());
                row.createCell(55).setCellValue(data.getVenirprygc());
                row.createCell(56).setCellValue(data.getVenbconom());
                row.createCell(57).setCellValue(data.getVenbcoctacte());
                row.createCell(58).setCellValue(data.getNofacnotcre());
                row.createCell(59).setCellValue(data.getNotimbfacnotcre());
                row.createCell(60).setCellValue(data.getVentipodoc());
                row.createCell(61).setCellValue(data.getVentanoiva());
                row.createCell(62).setCellValue(data.getVenProvee().equals("X") ? "15" : data.getVenProvee().contains("-") ? "11" : "12");
                row.createCell(63).setCellValue(data.getGdcbienid());
                row.createCell(64).setCellValue(data.getGdctipobien());
                row.createCell(65).setCellValue(data.getGdcimpcosto());
                row.createCell(66).setCellValue(data.getGdcimpventagrav());
            }

            // Write the output to a byte array
            return workbook;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<ExcelFacturasDto> getExcelFacturas(LocalDateTime inicio, LocalDateTime fin, Long sucursalId) {
        List<FacturaLegal> facturaLegalList = repository.findByCreadoEnBetweenAndSucursalId(inicio, fin, sucursalId);
        return facturaLegalList.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private ExcelFacturasDto convertToDto(FacturaLegal f) {
        ExcelFacturasDto dto = new ExcelFacturasDto();
        Sucursal sucursal = sucursalService.findById(f.getSucursalId()).orElse(null);
        TimbradoDetalle timbradoDetalle = f.getTimbradoDetalle();
        Timbrado timbrado = timbradoDetalle.getTimbrado();
        Double porcentrajeDesc = f.getDescuento() != null ? f.getDescuento() / f.getTotalFinal() : null;
        dto.setVenTipimp("I");
        if (f.getTotalParcial5() != null) {
            Double totalParcial5 = f.getTotalParcial5();
            if(porcentrajeDesc!=null){
                totalParcial5 = totalParcial5 - (totalParcial5 * porcentrajeDesc);
            }
            dto.setVenGra05(totalParcial5 - totalParcial5 / 21);
        } else {
            dto.setVenGra05(0.0);
        }
        if (f.getTotalParcial5() != null) {
            Double totalParcial5 = f.getTotalParcial5();
            if(porcentrajeDesc!=null){
                totalParcial5 = totalParcial5 - (totalParcial5 * porcentrajeDesc);
            }
            dto.setVenIva05(totalParcial5 / 21);
        } else {
            dto.setVenIva05(0.0);
        }
        StringBuilder buildNumeroFactura = new StringBuilder();
        buildNumeroFactura.append(sucursal.getCodigoEstablecimientoFactura())
                .append("-")
                .append(timbradoDetalle.getPuntoExpedicion())
                .append("-");
        switch (f.getNumeroFactura().toString().length()) {
            case 1:
                buildNumeroFactura.append("000000");
                break;
            case 2:
                buildNumeroFactura.append("00000");
                break;
            case 3:
                buildNumeroFactura.append("0000");
                break;
            case 4:
                buildNumeroFactura.append("000");
                break;
            case 5:
                buildNumeroFactura.append("00");
                break;
            case 6:
                buildNumeroFactura.append("0");
                break;
        }
        dto.setVenDisg05("A");
        dto.setVenCuota(Long.valueOf(0));
        dto.setVenDisgra("A");
        dto.setVenNumero(buildNumeroFactura.toString() + f.getNumeroFactura());
        dto.setVenSucurs(Long.valueOf(sucursal.getCodigoEstablecimientoFactura()));
        dto.setFormPag(f.getCredito() == true ? "CREDITO" : "CONTADO");
        dto.setVenProvee(f.getRuc());
        dto.setVenPrvnom(f.getNombre());
        dto.setVenTipofa("FACTURA");
        dto.setVenFecha(dateToStringWithFormat(f.getFecha(), "dd-MM-yyy"));
        dto.setVenTotfac(f.getTotalFinal());
        dto.setVenExenta(0.0);
        if (f.getTotalParcial10() != null) {
            Double totalParcial10 = f.getTotalParcial10();
            if(porcentrajeDesc!=null){
                totalParcial10 = totalParcial10 - (totalParcial10 * porcentrajeDesc);
            }
            dto.setVenGravad(totalParcial10 - totalParcial10 / 11);
        } else {
            dto.setVenGravad(0.0);
        }
        if (f.getTotalParcial10() != null) {
            Double totalParcial10 = f.getTotalParcial10();
            if(porcentrajeDesc!=null){
                totalParcial10 = totalParcial10 - (totalParcial10 * porcentrajeDesc);
            }
            dto.setVenIva(totalParcial10 / 11);
        } else {
            dto.setVenIva(0.0);
        }
        dto.setVenFecven(dateToStringWithFormat(f.getFecha(), "dd-MM-yyy"));
        dto.setRucvennrotim(Long.valueOf(timbrado.getNumero()));
        return dto;
    }

    public Boolean deleteByIdAndSucursalId(Long id, Long sucId){
        return repository.deleteByIdAndSucursalId(id, sucId);
    }
}