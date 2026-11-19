package se.teaterihuskvarna.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/// Checks the ID contract for every entity, including any added later.
class EntityIdTest {

    @TestFactory
    Stream<DynamicTest> idsAreNullableUntilPersistenceAndGettersRequireAnId() {
        return StreamSupport.stream(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("se.teaterihuskvarna").spliterator(), false)
                .filter(type -> type.isAnnotatedWith(Entity.class))
                .map(type -> DynamicTest.dynamicTest(type.getSimpleName(), () -> {
                    Class<?> entity = type.reflect();
                    Constructor<?> constructor = entity.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    Object instance = constructor.newInstance();
                    Method getter = entity.getMethod("getId");
                    assertThatThrownBy(() -> getter.invoke(instance))
                            .isInstanceOf(InvocationTargetException.class)
                            .hasCauseInstanceOf(IllegalStateException.class);

                    Field id = entity.getDeclaredField("id");
                    assertThat(id.getAnnotatedType().getAnnotation(Nullable.class)).isNotNull();
                    id.setAccessible(true);
                    id.set(instance, 42L);
                    assertThat(getter.invoke(instance)).isEqualTo(42L);
                }));
    }
}
