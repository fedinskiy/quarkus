package io.quarkus.it.hibernate.reactive.mysql;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mssqlclient.MSSQLPool;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/tests")
public class HibernateReactiveMSSQLTestEndpoint {

    @Inject
    Mutiny.Session mutinySession;

    // Injecting a Vert.x Pool is not required, it us only used to
    // independently validate the contents of the database for the test
    @Inject
    MSSQLPool mssqlPool;

    @GET
    @Path("/query")
    public Uni<String> query() {
        return mssqlPool.query("DELETE FROM Pig where id=5").execute()
                .flatMap(junk -> mssqlPool.preparedQuery("INSERT INTO Pig (id, name) VALUES (5, 'Čester Slovník')").execute())
                .chain(() -> mutinySession.find(GuineaPig.class, Integer.valueOf(5)))
                .map(GuineaPig::getName)
                .onFailure().recoverWithItem("Nothing!");
    }

    @GET
    @Path("/sanity")
    public Uni<String> sanity() {
        return Uni.createFrom().item("Křištof Бженчишчикевичさん");
    }

    @GET
    @Path("/persisted")
    public Uni<String> getPersisted() {
        return mutinySession.persist(new GuineaPig(4, "Eliška"))
                .chain(() -> mutinySession.find(GuineaPig.class, Integer.valueOf(4)))
                .map(GuineaPig::getName)
                .onFailure().recoverWithItem("Nothing!");
    }

    @GET
    @Path("/imported")
    public Uni<String> getImported() {
        return mutinySession.find(GuineaPig.class, Integer.valueOf(2))
                .map(GuineaPig::getName)
                .onFailure().recoverWithItem("Nothing!");
    }
}
