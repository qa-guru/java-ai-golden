RAG: test-negative, po-fluent

```java
@Layer("e2e")
class LoginTests {
    @Test
    @Tag("e2e")
    @Tag("negative")
    void shouldShowErrorWhenPasswordIsWrong() {
        loginPage.openPage()
                .fillAndSubmitForm("user1", "wrong")
                .shouldSeeError();
    }
}
```
