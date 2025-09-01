package com.inventario1.Inventario.files;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.Locale;

@Service
public class FileStorageService {

    private final Path root = Paths.get("uploads").resolve("products");

    public FileStorageService() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path saveForCodigo(String codigo, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IOException("Archivo vacío");
        String cleanName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
        String ext = "";
        int dot = cleanName.lastIndexOf('.');
        if (dot >= 0) ext = cleanName.substring(dot).toLowerCase(Locale.ROOT); // .jpg, .png, .webp
        if (!ext.matches("\\.(jpg|jpeg|png|webp)")) ext = ".jpg"; // default a jpg

        Path target = root.resolve(codigo + ext);
        Files.createDirectories(root);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    public Path findExisting(String codigo) {
        String[] exts = {".jpg", ".jpeg", ".png", ".webp"};
        for (String e : exts) {
            Path p = root.resolve(codigo + e);
            if (Files.exists(p)) return p;
        }
        return null;
    }
}
