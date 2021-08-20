package io.quarkus.it.hibernate.reactive.mysql;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;

@QuarkusTest
public class ReproducerIT {
    @Test
    public void persisted() {
        Response response = RestAssured.when().get("/tests/imported");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("Sir Fluffy", response.body().asString());
    }
}
