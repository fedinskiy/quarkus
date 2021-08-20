package io.quarkus.it.hibernate.reactive.mysql;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;

@QuarkusTest
public class HibernateReactiveMsSQLTest {

    @Test
    public void vertqQuery() {
        Response response = RestAssured.when()
                .get("/tests/query");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("Čester Slovník", response.body().asString());
    }

    @Test
    public void sanity() {
        Response response = RestAssured.when()
                .get("/tests/sanity");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("Křištof Бженчишчикевичさん", response.body().asString());
    }

    @Test
    public void persisted() {
        Response response = RestAssured.when()
                .get("/tests/persisted");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("Eliška", response.body().asString());
    }

    @Test
    public void imported() {
        Response response = RestAssured.when()
                .get("/tests/imported");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("Tomáš", response.body().asString());
    }
}
