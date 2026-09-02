RAG: test-api-layer, test-layers

```java
@Layer("api")
class AuthApiTests {
    @Test
    @Tag("api")
    @Tag("negative")
    void loginWithInvalidPassword() {
        given()
                .body(new LoginRequest("user1", "wrongpassword"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("message", equalTo("Wrong login or password"));
    }
}
```
