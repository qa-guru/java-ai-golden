RAG: po-fluent, testdata-user

```java
@Layer("e2e")
class LoginTests {
    @Test
    @Tag("e2e")
    void shouldWelcomeUserAfterSuccessfulLogin() {
        loginPage.openPage()
                .fillAndSubmitForm("user1", "password1")
                .shouldHaveWelcomeMessage("Welcome, user1!");
    }
}
```
