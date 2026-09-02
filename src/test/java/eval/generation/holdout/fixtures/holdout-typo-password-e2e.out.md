RAG: test-negative, po-locators, po-step, cfg-stands

```java
@Layer("e2e")
class LoginTests {
    @Test
    @Tag("e2e")
    @Tag("negative")
    void shouldShowErrorWhenPasswordHasTypo() {
        loginPage.openPage()
                .typeUsername("user1")
                .typePassword("passw0rd")
                .submitExpectingError()
                .shouldHaveErrorMessage("Wrong login or password");
    }
}
```
