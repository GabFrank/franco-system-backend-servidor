package com.franco.dev.service.utils;

import graphql.GraphQLException;
import org.apache.commons.io.FileUtils;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.stream.Stream;

@Service
public class ImageService {

    public final Logger log = LoggerFactory.getLogger(ImageService.class);
    boolean isWindows = true;
    boolean isMac = false;
    private String imagePresentaciones = "/FRC/resources/images/productos/presentaciones";
    private String imagePresentacionesThumb = "/FRC/resources/images/productos/presentaciones/thumbnails";
    private String storageDirectoryPathReports = "/FRC/reports";
    private String imagePath = "/FRC/resources/images";
    @Autowired
    private Environment env;

    public ImageService() {

        String osName = System.getProperty("os.name");

        log.warn(osName);

        isWindows = osName.contains("Windows");
        isMac = osName.contains("Mac");

//        if (isWindows) {
//            storageDirectoryPath = "C:\\\\FRC\\images\\";
//            storageDirectoryPathReports = "C:\\\\FRC\\reports\\";
//            imagePresentaciones = "C:\\\\FRC\\images\\productos\\presentaciones\\thumbnails\\";
//        } else {
//            storageDirectoryPath = getUserDirectory() + storageDirectoryPath + "/";
//            storageDirectoryPathReports = getUserDirectory() + storageDirectoryPathReports + "/";
//            imagePresentaciones = getUserDirectory() + imagePresentaciones + "/";
//            System.out.println("storage" + storageDirectoryPath);
//        }
    }

//    public String getStorageDirectoryPath(){
//        if(isWindows){
//            return "C:\\\\FRC\\images\\";
//        } else {
//            return env.getProperty("homepath") + storageDirectoryPath + "/";
//        }
//    }

    public static MultipartFile converter(String source) {
        String[] charArray = source.split(",");
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] bytes = new byte[0];
        bytes = decoder.decode(charArray[1]);
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] < 0) {
                bytes[i] += 256;
            }
        }
        return Base64Decoder.multipartFile(bytes, charArray[0]);
    }

    public String getResourcesPath() {
        if (isWindows) {
            return "C:\\\\FRC\\resources";
        } else {
            return env.getProperty("homepath") + "/FRC/resources";
        }
    }

    public String getStorageDirectoryPathReports() {
        if (isWindows) {
            return "C:\\\\FRC\\reports\\";
        } else {
            return env.getProperty("homepath") + storageDirectoryPathReports + "/";
        }
    }

    public String getImagePresentaciones() {
        if (isWindows) {
            return "C:\\\\FRC\\resources\\images\\productos\\presentaciones\\";
        } else {
            return env.getProperty("homepath") + imagePresentaciones + "/";
        }
    }

    public String getImagePresentacionesThumb() {
        if (isWindows) {
            return "C:\\\\FRC\\resources\\images\\productos\\presentaciones\\thumbnails\\";
        } else {
            return env.getProperty("homepath") + imagePresentacionesThumb + "/";
        }
    }

    public String getImagePath() {
        if (isWindows) {
            return "C:\\\\FRC\\resources\\images\\";
        } else {
            return env.getProperty("homepath") + imagePath + "/";
        }
    }

    public String getImageWithMediaType(String imageName, String imagePath) {
        String filePath;
        filePath = imagePath+imageName;
        String image = fileToBase64(new File(filePath));
        if (image == null) {
            return "data:image/png;base64,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";
        } else {
            return image;
        }
    }

    public String fileToBase64(File file) throws GraphQLException {
        byte[] fileContent = new byte[0];
        try {
            fileContent = FileUtils.readFileToByteArray(file);
            return "data:image/jpg;base64," + Base64.getEncoder().encodeToString(fileContent);
        } catch (IOException e) {
            return null;
        }
    }

    public Boolean saveImageToPath(String imageBase64, String fileName, String filePath, String thumbPath, Boolean thumbnail) throws IOException {
        Boolean res = false;
        Path path = Paths.get(filePath + fileName);
        log.warn("borrando " + path);
        deleteFile(path.toString());
        try {
            Files.copy(converter(imageBase64).getInputStream(), path);
            res = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        saveScaledImage(filePath, thumbPath, fileName, 200);
        return res;
    }

    public Boolean deleteFile(String path) throws IOException {
        FileUtils.touch(new File(path));
        File fileToDelete = FileUtils.getFile(path);
        boolean success = FileUtils.deleteQuietly(fileToDelete);
        if (success) {
            log.warn("borrado conn exito");
        } else {
            log.warn("falla al borrar");
        }

        return success;
    }

    public boolean saveScaledImage(String originalFilePath, String path, String fileNameToSave, int width) {
        File input = new File(originalFilePath + fileNameToSave);
        try {
            BufferedImage image = ImageIO.read(input);
            BufferedImage resized = resize(image, 250, 250);
            File output = new File(path, fileNameToSave);
            deleteFile(output.toString());
            ImageIO.write(resized, "jpeg", output);
            return true;
        } catch (IOException e) {
            // handle
        }
        return false;
    }

    private BufferedImage resize(BufferedImage img, int width, int height) {
        if (img.getColorModel().hasAlpha()) {
            img = dropAlphaChannel(img);
        }
        return Scalr.resize(img, width, height);
    }

    public Boolean crearThumbs() {
        try (Stream<Path> paths = Files.walk(Paths.get(getImagePresentaciones()))) {

            paths
                    .filter(Files::isRegularFile)
                    .forEach(f -> {
                        if (f.toString().contains(".jpg")) {
                            System.out.println("convirtiendo imagen: " + f.getFileName().toString());
                            Boolean res = saveScaledImage(f.toString(), getImagePresentacionesThumb(), f.getFileName().toString(), 200);
                            System.out.println(res);
                        }

                    });
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public BufferedImage dropAlphaChannel(BufferedImage src) {
        BufferedImage convertedImg = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        convertedImg.getGraphics().drawImage(src, 0, 0, null);
        return convertedImg;
    }
}
