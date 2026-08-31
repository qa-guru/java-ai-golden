RAG: test-api-layer, test-negative

```java
@Layer("api")
class AuthApiTests {
    @Test
    @Tag("api")
    @Tag("negative")
    void loginWithInvalidPassword() {
        authApi.login("user1", "wrong")
                .statusCode(401);
    }
}
```
