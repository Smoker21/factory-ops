package com.factoryops

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.Test

@QuarkusTest
class AuthResourceTest {

    @Test
    fun `login with valid seed credentials returns token pair`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"taichung-fab","accountName":"admin.system","password":"Admin@123456789"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .body("tokenType", equalTo("Bearer"))
    }

    @Test
    fun `login with wrong password returns 401`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"taichung-fab","accountName":"admin.system","password":"wrongpassword"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(401)
    }

    @Test
    fun `login with blank orgCode returns 422`() {
        // Bean validation (no MongoDB needed) — orgCode is now required
        given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"","accountName":"admin.system","password":"anypassword"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(422)
    }

    @Test
    fun `login with missing orgCode returns 422`() {
        // Bean validation — orgCode field absent defaults to blank
        given()
            .contentType(ContentType.JSON)
            .body("""{"accountName":"admin.system","password":"anypassword"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(422)
    }

    @Test
    fun `login with non-existent user returns 401`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"taichung-fab","accountName":"unknown.user","password":"Password@123456"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(401)
    }

    @Test
    fun `protected endpoint without token returns 401`() {
        // JWT validation (no MongoDB needed for this check)
        given()
            .`when`().get("/v1/orgs")
            .then()
            .statusCode(401)
    }
}
