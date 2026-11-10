package se.teaterihuskvarna.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import se.teaterihuskvarna.IntegrationTestSupport;

/// An entity's `@Column(length = ...)` equals the width the migrations gave
/// the column. `ddl-auto: validate` does not compare them
/// (`docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`), so until this test
/// the two were kept equal by hand. A length that is too long lets a value
/// through validation and fails at the insert, and one that is too short
/// refuses a value the database would take.
///
/// It reads every entity Hibernate knows, so a new entity is checked without
/// being listed here.
class EntityColumnsIT extends IntegrationTestSupport {

    @Autowired
    private EntityManagerFactory entities;

    @Test
    void everyDeclaredLengthIsTheColumnsWidth() {
        Map<String, Integer> widths = new HashMap<>();
        jdbc.sql("""
                        SELECT table_name, column_name, character_maximum_length FROM information_schema.columns
                        WHERE table_schema = 'public' AND character_maximum_length IS NOT NULL""")
                .query((row, number) -> widths.put(
                        row.getString("table_name") + "." + row.getString("column_name"),
                        row.getInt("character_maximum_length")))
                .list();
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (EntityType<?> entity : entities.getMetamodel().getEntities()) {
            Class<?> type = entity.getJavaType();
            Table table = type.getAnnotation(Table.class);
            assertThat(table).as("%s names its table with @Table", type.getSimpleName()).isNotNull();
            for (Declared column : columns(type)) {
                Integer width = widths.get(table.name() + "." + column.name());
                if (width == null) {
                    continue;
                }
                checked++;
                if (width != column.length()) {
                    wrong.add("%s declares length %d, and %s.%s is VARCHAR(%d)".formatted(
                            column.field(), column.length(), table.name(), column.name(), width));
                }
            }
        }

        assertThat(wrong).isEmpty();
        assertThat(checked).as("columns compared, so the test cannot pass by finding nothing").isGreaterThan(20);
    }

    /// The `@Column` fields of an entity or of a class it embeds.
    private static List<Declared> columns(Class<?> type) {
        List<Declared> columns = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.isAnnotationPresent(Embedded.class)) {
                columns.addAll(columns(field.getType()));
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                columns.add(new Declared(type.getSimpleName() + "." + field.getName(), column.name(), column.length()));
            }
        }
        return columns;
    }

    private record Declared(String field, String name, int length) {
    }
}
