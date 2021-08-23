package io.quarkus.it.hibernate.reactive.reproducer;

import java.net.URI;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import io.smallrye.mutiny.Uni;

@Path("/tests")
public class ReproducerTestEndpoint {

    @Inject
    Repository repository;

    @GET
    @Path("/animals/{id}")
    public Uni<Response> getPersisted(Integer id) {
        return repository.byId(id)
                .map(pig -> {
                    return pig == null
                            ? Response.status(Response.Status.NOT_FOUND)
                            : Response.ok(pig.getName());
                })
                .map(Response.ResponseBuilder::build);
    }

    @POST
    @Path("/animals/{name}")
    public Uni<Response> persist(String name) {
        return repository.create(name)
                .map(nothing -> Response.created(URI.create("animals")))
                .map(Response.ResponseBuilder::build);
    }
}
