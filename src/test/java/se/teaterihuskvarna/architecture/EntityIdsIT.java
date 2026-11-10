package se.teaterihuskvarna.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import se.teaterihuskvarna.IntegrationTestSupport;

/// Reading the id of a lazy reference never loads its row.
///
/// `account.getMember()` is a reference Hibernate fills on first use. Its id
/// is known without a query, and code relies on that: `HouseholdService`
/// reads the member's id from the account and then locks the member's row,
/// and the lock has to be what reads the row first. If asking for the id
/// loaded the row, the lock would hand back the copy read before it, and two
/// requests at once would both see a member without a household.
///
/// Hibernate answers `getId` from the reference only while it returns the
/// field's own type. So an entity whose id has a type of its own keeps
/// `getId` as the number and adds a final `id()` that calls it. When
/// `getId` itself returned a `MemberId`, in the first version of the id
/// types, `MemberHouseholdIT.concurrentCreationMakesExactlyOneHousehold`
/// made two households.
class EntityIdsIT extends IntegrationTestSupport {

    @Autowired
    private EntityManagerFactory entities;

    @Test
    void theIdOfALazyReferenceIsReadWithoutLoadingIt() throws ReflectiveOperationException {
        int typed = 0;
        try (EntityManager manager = entities.createEntityManager()) {
            for (EntityType<?> entity : entities.getMetamodel().getEntities()) {
                Class<?> type = entity.getJavaType();
                Object reference = manager.getReference(type, 42L);

                assertThat(type.getMethod("getId").invoke(reference)).isEqualTo(42L);
                assertThat(Hibernate.isInitialized(reference))
                        .as("%s.getId() left the reference unloaded", type.getSimpleName()).isFalse();

                Method id = typedId(type);
                if (id != null) {
                    typed++;
                    assertThat(Modifier.isFinal(id.getModifiers()))
                            .as("%s.id() is final, so the reference cannot override it", type.getSimpleName())
                            .isTrue();
                    assertThat(id.invoke(reference)).hasToString("42");
                    assertThat(Hibernate.isInitialized(reference))
                            .as("%s.id() left the reference unloaded", type.getSimpleName()).isFalse();
                }
            }
        }

        assertThat(typed).as("entities with a typed id(): Member, Account and Administrator").isEqualTo(3);
    }

    private static @Nullable Method typedId(Class<?> entity) {
        try {
            return entity.getMethod("id");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
