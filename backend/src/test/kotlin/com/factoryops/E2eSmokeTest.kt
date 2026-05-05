package com.factoryops

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.MethodOrderer

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class E2eSmokeTest {

    @Test
    fun `health endpoint returns UP`() {
        given()
            .`when`().get("/v1/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
    }

    @Test
    fun `swagger ui is accessible`() {
        given()
            .`when`().get("/q/swagger-ui")
            .then()
            .statusCode(200)
    }

    @Test
    fun `openapi spec is accessible`() {
        given()
            .`when`().get("/openapi")
            .then()
            .statusCode(200)
    }

    @Test
    fun `login with invalid credentials returns 401`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"accountName":"nonexistent","password":"wrongpassword"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(401)
    }

    @Test
    fun `login with blank fields returns 422`() {
        // Bean validation fires before MongoDB query — works without Docker
        given()
            .contentType(ContentType.JSON)
            .body("""{"accountName":"","password":""}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(422)
    }

    @Test
    fun `accessing protected endpoint without token returns 401`() {
        given()
            .`when`().get("/v1/projects")
            .then()
            .statusCode(401)
    }

    @Test
    fun `mock HR endpoint returns employee list`() {
        given()
            .`when`().get("/mock-hr/users")
            .then()
            .statusCode(200)
    }

    @Test
    fun `mock HR returns specific employee`() {
        given()
            .`when`().get("/mock-hr/users/admin.system")
            .then()
            .statusCode(200)
            .body("accountName", equalTo("admin.system"))
    }

    @Test
    fun `mock HR returns 404 for unknown employee`() {
        given()
            .`when`().get("/mock-hr/users/unknown.user")
            .then()
            .statusCode(404)
    }
}
