package ru.itmo.spacemarine.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import ru.itmo.spacemarine.dto.ImportOperationDto;
import ru.itmo.spacemarine.dto.PageDto;
import ru.itmo.spacemarine.service.ImportHistoryService;

/**
 * GET /api/imports?page=0&size=20 — история импорта (новые сверху).
 * Обычный пользователь видит свои операции, администратор — все.
 * Сама загрузка файла: POST /api/imports/upload (web.ImportUploadServlet).
 */
@Path("/imports")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class ImportController {

    @Inject
    private ImportHistoryService history;

    @GET
    public PageDto<ImportOperationDto> list(@QueryParam("page") @DefaultValue("0") int page,
                                            @QueryParam("size") @DefaultValue("20") int size) {
        return history.page(page, size);
    }
}
