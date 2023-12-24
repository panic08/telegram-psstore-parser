package ru.kanzstudios.telegrampsstorechecker.util;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.URL;

public class UrlFileDownloader {
    public static File downloadTempFile(String URL, String prefix, String suffix) throws Exception {
        URL fileUrl = new URL(URL);
        InputStream in = fileUrl.openStream();

        File tempFile = File.createTempFile(prefix + System.currentTimeMillis(), suffix);

        Path tempFilePath = tempFile.toPath();
        Files.copy(in, tempFilePath, StandardCopyOption.REPLACE_EXISTING);

        return tempFile;
    }
}
