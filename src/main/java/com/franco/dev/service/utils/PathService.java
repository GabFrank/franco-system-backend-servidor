package com.franco.dev.service.utils;

import org.springframework.stereotype.Service;

@Service
public class PathService {
    String userDirectory = System.getProperty("user.home");
    boolean isWindows = true;
    boolean isMac = false;

    public String storageDirectoryPath = userDirectory+"/FRC/images";
    public String imagePresentaciones = userDirectory+"/FRC/images/productos/presentaciones";
    public String imagePresentacionesThumbPath = userDirectory+"/FRC/images/productos/presentaciones/thumbnails";
    public String storageDirectoryPathReports = userDirectory+"/FRC/reports";
    public String updatePath = userDirectory+"/FRC";

    public PathService(){

        String osName = System.getProperty("os.name");

        isWindows = osName.contains("Windows");
        isMac = osName.contains("Mac");

        if(isWindows) {
            storageDirectoryPath = "C:\\\\FRC\\images\\";
            storageDirectoryPathReports = "C:\\\\FRC\\reports\\";
            imagePresentaciones = "C:\\\\FRC\\images\\productos\\presentaciones\\thumbnails\\";
            updatePath = "C:\\\\FRC\\";
        } else {
            storageDirectoryPath = storageDirectoryPath+"/";
            storageDirectoryPathReports = storageDirectoryPathReports+"/";
            imagePresentaciones = imagePresentaciones+"/";
            updatePath = updatePath+"/";

        }
    }
}
