package se.teaterihuskvarna.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// The id types in the code: every record named `…Id` that holds one `long`.
///
/// Found and not listed, so a fourth id type comes under the same rules as
/// `MemberId`, `AccountId` and `AdministratorId` the moment it exists.
final class IdTypes {

    private IdTypes() {
    }

    /// @param production the classes to look in
    /// @return the id types, in the order of their names
    static List<Class<?>> in(JavaClasses production) {
        List<Class<?>> found = new ArrayList<>();
        for (JavaClass imported : production) {
            Class<?> type = imported.reflect();
            RecordComponent[] components = type.getRecordComponents();
            if (components != null && components.length == 1 && components[0].getType() == long.class
                    && type.getSimpleName().endsWith("Id")) {
                found.add(type);
            }
        }
        found.sort(Comparator.comparing(Class::getName));
        return found;
    }
}
