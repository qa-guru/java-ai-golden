RAG: test-negative, po-locators, po-step, cfg-stands

```java
@Layer("e2e")
class LoginTests {
    @Test
    @Tag("e2e")
    @Tag("negative")
    void shouldShowErrorWhenPasswordIsWrongWithoutRawSelectors() {
        loginPage.openPage()
                .typeUsername("user1")
                .typePassword("wrongpassword")
                .submitExpectingError()
                .shouldHaveErrorMessage("Wrong login or password");
    }
}
```
