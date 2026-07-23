package com.sheahorn.gauge.resource;

import com.sheahorn.gauge.domain.ApiKey;
import com.sheahorn.gauge.security.ProjectAccessGuard;
import com.sheahorn.gauge.service.ApiKeyService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Map;

@Path("/api/apikeys")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApiKeyResource {

    @Inject
    ApiKeyService service;

    @Inject
    ProjectAccessGuard accessGuard;

    @Context
    SecurityContext securityContext;

    @GET
    public Response list() {
        String userId = getCurrentUserId();
        if (userId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        List<ApiKey> keys = service.findByUserId(userId);
        var result = keys.stream()
                .map(k -> {
                    var m = new java.util.HashMap<String, Object>();
                    m.put("id", k.id);
                    m.put("name", k.name);
                    if (k.restrictedProjectIds != null && !k.restrictedProjectIds.isBlank()) {
                        m.put("restrictedProjectIds", k.restrictedProjectIds);
                    }
                    return m;
                })
                .toList();
        return Response.ok(result).build();
    }

    @POST
    public Response create(Map<String, Object> body) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            name = "API Key";
        }
        String restrictedProjectIds = (String) body.get("restrictedProjectIds");
        ApiKeyService.CreateResult result = service.create(userId, name, restrictedProjectIds);
        var entity = Map.of(
            "id", result.apiKey.id,
            "name", result.apiKey.name,
            "key", result.rawKey
        );
        if (restrictedProjectIds != null && !restrictedProjectIds.isBlank()) {
            entity = new java.util.HashMap<>(entity);
            entity.put("restrictedProjectIds", restrictedProjectIds);
        }
        return Response.status(Response.Status.CREATED)
                .entity(entity)
                .build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if (service.deleteById(id, userId)) {
            return Response.noContent().build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    private String getCurrentUserId() {
        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            return null;
        }
        String username = securityContext.getUserPrincipal().getName();
        com.sheahorn.gauge.domain.User user = com.sheahorn.gauge.domain.User.findByUsername(username);
        return user != null ? user.id : null;
    }
}
