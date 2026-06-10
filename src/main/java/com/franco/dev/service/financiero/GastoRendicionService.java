package com.franco.dev.service.financiero;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.financiero.GastoRendicion;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.repository.financiero.GastoRendicionRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.utils.ImageService;
import graphql.GraphQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GastoRendicionService extends CrudService<GastoRendicion, GastoRendicionRepository, Long> {

    private static final String RENDICION_IMAGE_FOLDER = "financiero" + File.separator + "gasto_rendicion";

    private final GastoRendicionRepository repository;
    private final PreGastoService preGastoService;
    private final ImageService imageService;
    private final ObjectMapper objectMapper;

    @Override
    public GastoRendicionRepository getRepository() {
        return repository;
    }

    @Override
    @Transactional
    public GastoRendicion save(GastoRendicion entity) {
        if (entity.getCreadoEn() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }

        PreGasto preGasto = null;
        if (entity.getPreGasto() != null) {
            preGasto = preGastoService.findByIdAndSucursalId(
                    entity.getPreGasto().getId(), entity.getPreGasto().getSucursalId());
            if (preGasto != null) {
                entity.setPreGasto(preGasto);
                if (entity.getEnte() == null) {
                    entity.setEnte(preGasto.getEnte());
                }
                if (entity.getTipoGasto() == null && preGasto.getTipoGasto() != null) {
                    entity.setTipoGasto(preGasto.getTipoGasto());
                }
            }
        }

        if (preGasto != null) {
            entity.setFotoFacturaUrl(serializeFilenames(
                    persistImages(entity.getFotoFacturaUrl(), "factura", preGasto.getId(), preGasto.getSucursalId())));
            entity.setFotoProductoUrl(serializeFilenames(
                    persistImages(entity.getFotoProductoUrl(), "producto", preGasto.getId(), preGasto.getSucursalId())));
        }

        GastoRendicion saved = super.save(entity);

        if (saved.getPreGasto() != null) {
            actualizarMontoPreGasto(saved.getPreGasto().getId(), saved.getPreGasto().getSucursalId());
        }

        return saved;
    }

    @Transactional
    public void actualizarMontoPreGasto(Long preGastoId, Long sucursalId) {
        PreGasto preGasto = preGastoService.findByIdAndSucursalId(preGastoId, sucursalId);
        if (preGasto != null) {
            List<GastoRendicion> rendiciones = repository.findByPreGastoIdAndSucursalId(preGastoId, sucursalId);
            BigDecimal totalGastado = rendiciones.stream()
                    .map(r -> r.getMontoTotal() != null ? r.getMontoTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            preGasto.setMontoGastado(totalGastado);
            BigDecimal montoRetirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
            preGasto.setSaldoDevolver(montoRetirado.subtract(totalGastado).max(BigDecimal.ZERO));
            if (totalGastado.compareTo(BigDecimal.ZERO) > 0) {
                preGasto.setFechaRendicion(LocalDateTime.now());
            }
            preGastoService.recalcularEstadoRendicionPublico(preGasto);
            preGastoService.save(preGasto);
        }
    }

    public List<GastoRendicion> findByPreGasto(Long preGastoId, Long sucursalId) {
        return repository.findByPreGastoIdAndSucursalId(preGastoId, sucursalId);
    }

    public String resolveImageAsDataUrl(String storedValue, String tipo) {
        List<String> urls = resolveImagesAsDataUrls(storedValue, tipo);
        return urls.isEmpty() ? null : urls.get(0);
    }

    public List<String> resolveImagesAsDataUrls(String storedValue, String tipo) {
        List<String> filenames = deserializeFilenames(storedValue);
        if (filenames.isEmpty()) {
            return Collections.emptyList();
        }

        return filenames.stream()
                .map(filename -> resolveSingleImageAsDataUrl(filename, tipo))
                .filter(url -> url != null && !url.isBlank())
                .collect(Collectors.toList());
    }

    private String resolveSingleImageAsDataUrl(String storedValue, String tipo) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        if (storedValue.startsWith("data:image")) {
            return storedValue;
        }
        File file = new File(getImageDirectory(tipo) + storedValue);
        if (!file.exists()) {
            return null;
        }
        return imageService.fileToBase64(file);
    }

    private List<String> persistImages(String imagesData, String tipo, Long preGastoId, Long sucursalId) {
        List<String> images = deserializeFilenames(imagesData);
        if (images.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> persisted = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            String persistedImage = persistImage(images.get(i), tipo, preGastoId, sucursalId, i);
            if (persistedImage != null && !persistedImage.isBlank()) {
                persisted.add(persistedImage);
            }
        }
        return persisted;
    }

    private String persistImage(String imageData, String tipo, Long preGastoId, Long sucursalId, int index) {
        if (imageData == null || imageData.isBlank()) {
            return null;
        }
        if (!imageData.startsWith("data:image")) {
            return imageData;
        }
        String fileName = preGastoId + "_" + sucursalId + "_" + tipo + "_" + index + "_" + System.currentTimeMillis() + ".jpg";
        String directory = getImageDirectory(tipo);
        String thumbDirectory = directory + "thumb" + File.separator;
        try {
            Boolean saved = imageService.saveImageToPath(imageData, fileName, directory, thumbDirectory, true);
            if (!Boolean.TRUE.equals(saved)) {
                throw new GraphQLException("No se pudo guardar la imagen de la rendición.");
            }
            return fileName;
        } catch (IOException e) {
            throw new GraphQLException("No se pudo guardar la imagen de la rendición.");
        }
    }

    private String getImageDirectory(String tipo) {
        return imageService.getImagePath() + RENDICION_IMAGE_FOLDER + File.separator + tipo + File.separator;
    }

    private List<String> deserializeFilenames(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = storedValue.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<String> filenames = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
                return filenames == null ? Collections.emptyList()
                        : filenames.stream().filter(name -> name != null && !name.isBlank()).collect(Collectors.toList());
            } catch (IOException e) {
                return Collections.emptyList();
            }
        }
        return Collections.singletonList(trimmed);
    }

    private String serializeFilenames(List<String> filenames) {
        if (filenames == null || filenames.isEmpty()) {
            return null;
        }
        List<String> validFilenames = filenames.stream()
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList());
        if (validFilenames.isEmpty()) {
            return null;
        }
        if (validFilenames.size() == 1) {
            return validFilenames.get(0);
        }
        try {
            return objectMapper.writeValueAsString(validFilenames);
        } catch (IOException e) {
            throw new GraphQLException("No se pudo guardar las imágenes de la rendición.");
        }
    }

    public String serializeIncomingImagePayload(List<String> images, String legacyImage) {
        List<String> merged = new ArrayList<>();
        if (images != null) {
            merged.addAll(images.stream()
                    .filter(image -> image != null && !image.isBlank())
                    .collect(Collectors.toList()));
        }
        if (merged.isEmpty() && legacyImage != null && !legacyImage.isBlank()) {
            merged.add(legacyImage);
        }
        if (merged.isEmpty()) {
            return null;
        }
        if (merged.size() == 1) {
            return merged.get(0);
        }
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (IOException e) {
            throw new GraphQLException("No se pudo procesar las imágenes de la rendición.");
        }
    }
}
