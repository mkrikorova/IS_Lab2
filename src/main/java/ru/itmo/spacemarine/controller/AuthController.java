package ru.itmo.spacemarine.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.spacemarine.dto.AuthResponse;
import ru.itmo.spacemarine.dto.LoginRequest;
import ru.itmo.spacemarine.dto.RegisterRequest;
import ru.itmo.spacemarine.dto.UserDto;
import ru.itmo.spacemarine.security.CurrentUser;
import ru.itmo.spacemarine.service.AuthService;
import ru.itmo.spacemarine.service.exception.NotAuthenticatedException;

/**
 * POST /api/auth/register  {login, password} -> 201 {token, user}
 * POST /api/auth/login     {login, password} -> 200 {token, user}
 * POST /api/auth/logout    (Authorization: Bearer ...) -> 204
 * GET  /api/auth/me        -> 200 {id, login, role} или 401
 */
@Path("/auth")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthController {

    @Inject
    private AuthService auth;
    @Inject
    private CurrentUser currentUser;

    @POST
    @Path("/register")
    public Response register(@NotNull(message = "Пустое тело запроса") @Valid RegisterRequest req) {
        return Response.status(Response.Status.CREATED).entity(auth.register(req)).build();
    }

    @POST
    @Path("/login")
    public AuthResponse login(@NotNull(message = "Пустое тело запроса") @Valid LoginRequest req) {
        return auth.login(req);
    }

    @POST
    @Path("/logout")
    public Response logout() {
        auth.logout(currentUser.token());
        return Response.noContent().build();
    }

    @GET
    @Path("/me")
    public UserDto me() {
        return currentUser.get()
                .map(AuthService::toDto)
                .orElseThrow(() -> new NotAuthenticatedException("Вход не выполнен"));
    }
}
