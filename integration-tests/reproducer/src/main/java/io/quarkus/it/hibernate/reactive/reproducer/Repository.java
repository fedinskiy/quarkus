package io.quarkus.it.hibernate.reactive.reproducer;

import javax.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class Repository implements PanacheRepositoryBase<GuineaPig, Integer> {

    public Uni<GuineaPig> byId(Integer id) {
        return findById(id);
    }

    public Uni<GuineaPig> create(String name) {
        GuineaPig author = new GuineaPig();
        author.setName(name);
        return persistAndFlush(author);
    }

    public Multi<GuineaPig> byName(String name) {
        return find("name", name).stream();
    }
}
