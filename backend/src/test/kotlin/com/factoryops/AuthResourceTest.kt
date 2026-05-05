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
        // This test depends on seed data; will pass if seed data is enabled
        // In test mode, seed data is enabled via quarkus test profile
        given()
            .contentType(ContentType.JSON)
            .body("""{"accountName":"admin.system","password":"Admin@123456789"}""")
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
            .body("""{"accountName":"admin.system","password":"wrongpassword"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(401)
    }

    @Test
    fun `login with blank accountName returns 422`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"accountName":"","password":"anypassword"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(422)
    }

    @Test
    fun `login with non-existent user returns 401`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"accountName":"unknown.user","password":"Password@123456"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(401)
    }

    @Test
    fun `protected endpoint without token returns 401`() {
        given()
            .`when`().get("/v1/orgs")
            .then()
            .statusCode(401)
    }
}
