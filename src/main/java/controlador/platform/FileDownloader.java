package controlador.platform;

import java.io.File;
import java.io.IOException;

@FunctionalInterface
public interface FileDownloader {
    void download(String url, File destination) throws IOException;
}
