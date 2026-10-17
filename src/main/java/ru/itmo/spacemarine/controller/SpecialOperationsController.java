package ru.itmo.spacemarine.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.spacemarine.dto.ChapterDto;
import ru.itmo.spacemarine.dto.SpaceMarineDto;
import ru.itmo.spacemarine.dto.WeaponGroupDto;
import ru.itmo.spacemarine.service.SpecialOperationsService;

import java.util.List;

@Path("/operations")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SpecialOperationsController {

    @Inject
    private SpecialOperationsService service;

    @GET
    @Path("/heart-count-sum")
    public JsonObject sumHeartCount() {
        return Json.createObjectBuilder().add("sum", service.sumHeartCount()).build();
    }

    @GET
    @Path("/group-by-melee-weapon")
    public List<WeaponGroupDto> groupByMeleeWeapon() {
        return service.groupByMeleeWeapon();
    }

    @GET
    @Path("/name-contains")
    public List<SpaceMarineDto> findByName(@QueryParam("substring") String substring) {
        return service.findByNameSubstring(substring);
    }

    @POST
    @Path("/chapters")
    public Response createChapter(@NotNull(message = "Пустое тело запроса") @Valid ChapterDto dto) {
        return Response.status(Response.Status.CREATED).entity(service.createChapter(dto)).build();
    }

    @DELETE
    @Path("/chapters/{id}")
    public JsonObject disbandChapter(@PathParam("id") int id) {
        return Json.createObjectBuilder().add("deletedMarines", service.disbandChapter(id)).build();
    }
}
