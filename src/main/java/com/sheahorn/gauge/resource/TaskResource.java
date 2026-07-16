package com.sheahorn.gauge.resource;

import com.sheahorn.gauge.domain.PatchTaskRequest;
import com.sheahorn.gauge.domain.Task;
import com.sheahorn.gauge.domain.UpdateTaskStatusRequest;
import com.sheahorn.gauge.service.TaskService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/api/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaskResource {

    @Inject
    TaskService service;

    @Operation(
        operationId = "tracker_list_tasks",
        summary = "List all tasks, optionally filtered by ?q=searchPhrase (case-insensitive match on title/description)"
    )
    @GET
    public Response list(@QueryParam("q") String q) {
        if (q != null && !q.isBlank()) {
            return Response.ok(service.search(q)).build();
        }
        return Response.ok(service.findAll()).build();
    }

    @Operation(
        operationId = "tracker_get_task",
        summary = "Get a task by ID"
    )
    @GET
    @Path("/{taskId}")
    public Response get(@PathParam("taskId") String taskId) {
        return service.findById(taskId)
            .map(task -> Response.ok(task).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_patch_task",
        summary = "Edit simple fields: title, description"
    )
    @PATCH
    @Path("/{taskId}")
    public Response patch(@PathParam("taskId") String taskId, PatchTaskRequest body) {
        return service.patch(taskId, body.title(), body.description())
            .map(task -> Response.ok(task).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_update_task_status",
        summary = "Update the status of a task"
    )
    @PATCH
    @Path("/{taskId}/status")
    public Response updateStatus(@PathParam("taskId") String taskId, UpdateTaskStatusRequest body) {
        return service.updateStatus(taskId, body.status())
            .map(task -> Response.ok(task).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_delete_task",
        summary = "Delete a task by ID"
    )
    @DELETE
    @Path("/{taskId}")
    public Response delete(@PathParam("taskId") String taskId) {
        return service.deleteById(taskId)
            ? Response.noContent().build()
            : Response.status(Response.Status.NOT_FOUND).build();
    }
}