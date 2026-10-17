package ru.itmo.spacemarine.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.spacemarine.dto.CoordinatesDto;
import ru.itmo.spacemarine.service.CoordinatesService;

import java.util.List;

@Path("/coordinates")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoordinatesController {

    @Inject
    private CoordinatesService service;

    @GET
    public List<CoordinatesDto> list() {
        return service.findAll();
    }

    @GET
    @Path("/{id}")
    public CoordinatesDto get(@PathParam("id") Integer id) {
        return service.findById(id);
    }

    @POST
    public Response create(@NotNull(message = "Пустое тело запроса") @Valid CoordinatesDto dto) {
        return Response.status(Response.Status.CREATED).entity(service.create(dto)).build();
    }

    @PUT
    @Path("/{id}")
    public CoordinatesDto update(@PathParam("id") Integer id,
                                 @NotNull(message = "Пустое тело запроса") @Valid CoordinatesDto dto) {
        return service.update(id, dto);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Integer id, @QueryParam("reassignTo") Integer reassignTo) {
        service.delete(id, reassignTo);
        return Response.noContent().build();
    }
}
