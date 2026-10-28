package kr.ject.naspi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NaspiApplicationTests {

    @Test
    void contextLoads() {
        System.out.println("Hello World");
    }

}
