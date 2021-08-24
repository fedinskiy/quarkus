package io.quarkus.it.hibernate.reactive.reproducer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.NativeImageTest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;

@QuarkusTest
@NativeImageTest
public class ReproducerITCase {
    @Test
    public void persisted() {
        Response response = RestAssured.when().get("/tests/animals/1");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("Sir Fluffy", response.body().asString());
    }

    @Test
    public void unexisting() {
        Response response = RestAssured.when().get("/tests/animals/2");
        Assertions.assertEquals("", response.body().asString());
        Assertions.assertEquals(404, response.statusCode());
    }

    @Test
    public void create() {
        Response check = RestAssured.when().get("/tests/animals/5");
        Assertions.assertEquals(404, check.statusCode());

        Response created = RestAssured.when().post("/tests/animals/Emily");
        Assertions.assertEquals(201, created.statusCode());

        Response get = RestAssured.when().get("/tests/animals/5");
        Assertions.assertEquals(200, get.statusCode());
        Assertions.assertEquals("Emily", get.body().asString());
    }
}
