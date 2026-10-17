package ru.itmo.spacemarine.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.spacemarine.dto.ChapterDto;
import ru.itmo.spacemarine.service.ChapterService;

import java.util.List;

@Path("/chapters")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChapterController {

    @Inject
    private ChapterService service;

    /** Для выпадающего списка «выбрать существующий орден». */
    @GET
    public List<ChapterDto> list() {
        return service.findAll();
    }

    @GET
    @Path("/{id}")
    public ChapterDto get(@PathParam("id") Integer id) {
        return service.findById(id);
    }

    @POST
    public Response create(@NotNull(message = "Пустое тело запроса") @Valid ChapterDto dto) {
        return Response.status(Response.Status.CREATED).entity(service.create(dto)).build();
    }

    @PUT
    @Path("/{id}")
    public ChapterDto update(@PathParam("id") Integer id,
                             @NotNull(message = "Пустое тело запроса") @Valid ChapterDto dto) {
        return service.update(id, dto);
    }

    /**
     * DELETE /chapters/5                -> 409, если есть связанные десантники
     * DELETE /chapters/5?reassignTo=7   -> десантники переводятся в орден 7, орден 5 удаляется
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Integer id, @QueryParam("reassignTo") Integer reassignTo) {
        service.delete(id, reassignTo);
        return Response.noContent().build();
    }
}
