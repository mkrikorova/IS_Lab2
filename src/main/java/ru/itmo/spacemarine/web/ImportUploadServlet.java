package ru.itmo.spacemarine.web;

import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import ru.itmo.spacemarine.controller.mapper.ErrorMapping;
import ru.itmo.spacemarine.dto.ErrorDto;
import ru.itmo.spacemarine.dto.ImportResultDto;
import ru.itmo.spacemarine.service.imports.ImportFacade;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Загрузка файла импорта: POST /api/imports/upload, multipart/form-data, поле «file».
 * Отдельный сервлет со стандартным @MultipartConfig (Servlet API) — самый надёжный
 * способ принять файл в Payara без сторонних библиотек. Точное совпадение пути
 * имеет приоритет над маппингом JAX-RS /api/*, поэтому остальные /api/... не затронуты.
 * Вся логика — в ImportFacade; здесь только HTTP: достать файл и записать JSON-ответ.
 */
@WebServlet(urlPatterns = "/api/imports/upload")
@MultipartConfig(maxFileSize = ImportUploadServlet.MAX_FILE_BYTES,
        maxRequestSize = ImportUploadServlet.MAX_FILE_BYTES + 64 * 1024)
public class ImportUploadServlet extends HttpServlet {

    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final Jsonb JSON = JsonbBuilder.create();

    @Inject
    private ImportFacade facade;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Part file;
        try {
            file = req.getPart("file");
        } catch (IllegalStateException e) { // превышен размер
            write(resp, new ErrorDto(413, "Файл слишком большой, максимум " + MAX_FILE_BYTES / (1024 * 1024) + " МБ", null));
            return;
        } catch (ServletException e) { // не multipart
            write(resp, new ErrorDto(400, "Ожидается multipart/form-data с полем file", null));
            return;
        }
        if (file == null) {
            write(resp, new ErrorDto(400, "Не передан файл (поле file)", null));
            return;
        }
        byte[] content;
        try (InputStream in = file.getInputStream()) {
            content = in.readAllBytes();
        }
        try {
            ImportResultDto result = facade.importFile(fileName(file), content);
            resp.setStatus(HttpServletResponse.SC_CREATED);
            writeBody(resp, result);
        } catch (RuntimeException e) {
            write(resp, ErrorMapping.describe(e));
        }
    }

    private static String fileName(Part file) {
        String name = file.getSubmittedFileName();
        if (name == null || name.isBlank()) return null;
        name = name.replaceAll(".*[/\\\\]", ""); // старые браузеры присылают полный путь
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static void write(HttpServletResponse resp, ErrorDto error) throws IOException {
        resp.setStatus(error.status);
        writeBody(resp, error);
    }

    private static void writeBody(HttpServletResponse resp, Object body) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.getWriter().write(JSON.toJson(body));
    }
}
