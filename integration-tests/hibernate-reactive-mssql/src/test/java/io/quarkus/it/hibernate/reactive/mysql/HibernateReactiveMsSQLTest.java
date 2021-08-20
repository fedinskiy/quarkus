package io.quarkus.it.hibernate.reactive.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;

@QuarkusTest
public class HibernateReactiveMsSQLTest {

    @Test
    public void vertxQuery() {
        Response response = RestAssured.when()
                .get("/tests/query");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("Čester Slovník", response.body().asString());
    }

    @Test
    public void vertxRetrieval() {
        Response response = RestAssured.when()
                .get("/tests/vertx");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("El Niño", response.body().asString());
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

    @Test
    void jdbc() throws SQLException {
        Properties connectionProps = new Properties();
        connectionProps.put("user", "sa");
        connectionProps.put("password", "QuArKuS_tEsT");
        connectionProps.put("databaseName", "msdb");
        Connection connection = DriverManager
                .getConnection("jdbc:sqlserver://localhost:1455",
                        connectionProps);
        try (Statement statement = connection.createStatement()) {
            ResultSet set = statement.executeQuery("SELECT name from Pig where id=2");
            set.next();
            String title = set.getString("name");
            System.out.println("Hello from jdbc " + title);
            Assertions.assertEquals("Tomáš", title);
        }
    }
}
