package ru.itmo.spacemarine.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.spacemarine.dto.PageDto;
import ru.itmo.spacemarine.dto.SpaceMarineDto;
import ru.itmo.spacemarine.dto.SpaceMarineFilter;
import ru.itmo.spacemarine.dto.SpaceMarineRequest;
import ru.itmo.spacemarine.service.SpaceMarineService;

import java.util.List;

@Path("/space-marines")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SpaceMarineController {

    @Inject
    private SpaceMarineService service;

    /** Таблица на главном экране: пагинация + фильтр + сортировка. */
    @GET
    public PageDto<SpaceMarineDto> list(@BeanParam SpaceMarineFilter filter) {
        return service.findPage(filter);
    }

    /** Все десантники для карты: GET /api/space-marines/all */
    @GET
    @Path("/all")
    public List<SpaceMarineDto> all() {
        return service.findAll();
    }

    @GET
    @Path("/{id}")
    public SpaceMarineDto get(@PathParam("id") Integer id) {
        return service.findById(id);
    }

    @POST
    public Response create(@NotNull(message = "Пустое тело запроса") @Valid SpaceMarineRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.create(request)).build();
    }

    @PUT
    @Path("/{id}")
    public SpaceMarineDto update(@PathParam("id") Integer id,
                                 @NotNull(message = "Пустое тело запроса") @Valid SpaceMarineRequest request) {
        return service.update(id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Integer id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
