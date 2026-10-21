#!/bin/sh
# Proves that the real Maven compiler configuration accepts safe code and
# rejects unsafe code. Fixtures and logs stay under target/nullability-probe.
set -eu
cd "$(dirname "$0")/.."
probe=target/nullability-probe
mkdir -p "$probe/src"

compile() {
    rm -rf "$probe/classes" "$probe/maven-status"
    sh scripts/maven.sh -Pnullability-probe compiler:compile > "$probe/$1.log" 2>&1
}

cat > "$probe/src/NullabilityProbe.java" <<'JAVA'
package se.teaterihuskvarna.nullabilityprobe;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
@NullMarked
class NullabilityProbe {
    int safe(@Nullable String value) {
        return value == null ? 0 : value.length();
    }
    List<@Nullable String> elements(List<@Nullable String> values) {
        return values;
    }
}
JAVA
if ! compile safe; then
    cat "$probe/safe.log"
    exit 1
fi

cat > "$probe/src/NullabilityProbe.java" <<'JAVA'
package se.teaterihuskvarna.nullabilityprobe;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
@NullMarked
class NullabilityProbe {
    String unsafeReturn() { return null; }
    void requires(String value) { }
    void unsafeArgument() { requires(null); }
    int unsafeDereference(@Nullable String value) { return value.length(); }
    List<String> unsafeElements(List<@Nullable String> values) { return values; }
    void invalidAnnotation() { @Nullable String value = null; }
}
JAVA
if compile unsafe; then
    echo 'ERROR: unsafe nullability fixture compiled successfully'
    exit 1
fi
for diagnostic in 'returning @Nullable' 'passing @Nullable' 'dereferenced expression' \
        'incompatible types' '[JSpecifyUnrecognizedAnnotationLocation]'; do
    if ! grep -F "$diagnostic" "$probe/unsafe.log" > /dev/null; then
        cat "$probe/unsafe.log"
        echo "ERROR: missing compiler diagnostic: $diagnostic"
        exit 1
    fi
done

cat > "$probe/src/NullabilityProbe.java" <<'JAVA'
package se.teaterihuskvarna.nullabilityprobe;
class NullabilityProbe { }
JAVA
if compile unmarked; then
    echo 'ERROR: a package without a nullability default compiled successfully'
    exit 1
fi
if ! grep -F '[RequireExplicitNullMarking]' "$probe/unmarked.log" > /dev/null; then
    cat "$probe/unmarked.log"
    exit 1
fi
echo 'Nullability compiler probes passed: safe code accepted, all unsafe cases rejected'
