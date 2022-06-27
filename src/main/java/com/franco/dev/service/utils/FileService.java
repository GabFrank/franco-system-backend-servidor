package com.franco.dev.service.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileService {

    private Path root;

    @Autowired
    private PathService pathService;

    public void save(MultipartFile file) {
        try {
            root = Paths.get(this.pathService.updatePath);
            Files.copy(file.getInputStream(), root.resolve(file.getOriginalFilename()));
        } catch (Exception e) {
            throw new RuntimeException("Could not store the file. Error: " + e.getMessage());
        }
    }

    public Resource load(String filename) {
        try {
            root = Paths.get(this.pathService.updatePath);
            Path file = root.resolve(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read the file!");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }

//    /Users/gabfranck/FRC/frc-app-v1.0.3.exe

    public void deleteAll() {
        root = Paths.get(this.pathService.updatePath);
        FileSystemUtils.deleteRecursively(root.toFile());
    }

}
