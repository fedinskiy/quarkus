package io.quarkus.it.hibernate.reactive.mysql;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.hibernate.reactive.mutiny.Mutiny;

import io.smallrye.mutiny.Uni;

@Path("/tests")
public class ReproducerTestEndpoint {

    @Inject
    Mutiny.Session mutinySession;

    @GET
    @Path("/imported")
    public Uni<String> getPersisted() {
        return mutinySession.find(GuineaPig.class, Integer.valueOf(1))
                .map(GuineaPig::getName);
    }
}
