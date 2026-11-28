package kr.ject.naspi.contents.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportTest {

    private static final List<Class<?>> TYPES =
            List.of(Report.class, ReportBody.class, ReportImage.class);

    @Test
    @DisplayName("제보는 수정할 수 없다")
    void reportIsImmutable() {
        for (Class<?> type : TYPES) {
            List<Field> fields = Arrays.stream(type.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .filter(field -> !field.isAnnotationPresent(Id.class))
                    .filter(field -> !field.isAnnotationPresent(Embedded.class))
                    .toList();

            assertThat(fields).as(type.getSimpleName()).isNotEmpty().allSatisfy(field -> {
                Column column = field.getAnnotation(Column.class);
                assertThat(column).as(field.getName()).isNotNull();
                assertThat(column.updatable()).as(field.getName()).isFalse();
            });
            assertThat(Arrays.stream(type.getMethods()).map(Method::getName))
                    .as(type.getSimpleName()).noneMatch(name -> name.startsWith("set"));
        }
    }
}
