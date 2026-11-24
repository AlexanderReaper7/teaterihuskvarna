package se.teaterihuskvarna.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

/// A doc comment on a public method, a public constructor or a record names
/// every parameter with `@param` and nothing that is not one, and says what a
/// method returns with `@return`.
///
/// Checkstyle reads a `///` comment as an ordinary comment and checks nothing
/// inside it, and `javadoc -Xdoclint` cannot ask for the tags without also
/// asking for a comment on every member
/// (`docs/decisions/0013-three-static-analysis-gates.md`). This reads the
/// comments with the compiler's own parser instead, and leaves a member
/// without a comment alone.
class DocCommentsTest {

    @Test
    void aCommentOnAPublicMemberNamesItsParametersAndWhatItReturns() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("the JDK's compiler, which a JRE does not have").isNotNull();
        Set<String> wrong = new TreeSet<>();
        int[] read = {0};
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null);
                Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            List<Path> java = sources.filter(path -> path.toString().endsWith(".java")).toList();
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostic -> { }, null, null,
                    files.getJavaFileObjectsFromPaths(java));
            DocTrees comments = DocTrees.instance(task);
            for (CompilationUnitTree unit : task.parse()) {
                String file = Path.of(unit.getSourceFile().toUri()).getFileName().toString();
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitClass(ClassTree type, Void unused) {
                        DocCommentTree comment = comments.getDocCommentTree(getCurrentPath());
                        if (comment != null && type.getKind() == Tree.Kind.RECORD) {
                            read[0]++;
                            check(file + " record " + type.getSimpleName(), comment, components(type), wrong);
                        }
                        return super.visitClass(type, unused);
                    }

                    @Override
                    public Void visitMethod(MethodTree method, Void unused) {
                        DocCommentTree comment = comments.getDocCommentTree(getCurrentPath());
                        ClassTree owner = (ClassTree) getCurrentPath().getParentPath().getLeaf();
                        boolean open = method.getModifiers().getFlags().contains(Modifier.PUBLIC)
                                || owner.getKind() == Tree.Kind.INTERFACE;
                        List<String> parameters = method.getParameters().stream()
                                .map(parameter -> parameter.getName().toString()).toList();
                        if (comment != null && open && !overrides(method) && !isCanonical(method, owner, parameters)) {
                            read[0]++;
                            String name = file + " " + owner.getSimpleName() + "." + method.getName();
                            check(name, comment, parameters, wrong);
                            boolean returns = method.getReturnType() != null
                                    && !(method.getReturnType() instanceof PrimitiveTypeTree primitive
                                            && primitive.getPrimitiveTypeKind() == TypeKind.VOID);
                            boolean says = comment.getBlockTags().stream()
                                    .anyMatch(tag -> tag.getKind() == DocTree.Kind.RETURN);
                            if (returns && !says) {
                                wrong.add(name + " has no @return");
                            } else if (!returns && says) {
                                wrong.add(name + " has a @return and returns nothing");
                            }
                        }
                        return super.visitMethod(method, unused);
                    }
                }.scan(unit, null);
            }
        }

        assertThat(wrong).isEmpty();
        assertThat(read[0]).as("comments read, so the test cannot pass by finding none").isGreaterThan(200);
    }

    /// The tags of a method that overrides another are on the one it overrides.
    private static boolean overrides(MethodTree method) {
        return method.getModifiers().getAnnotations().stream()
                .anyMatch(annotation -> annotation.getAnnotationType().toString().equals("Override"));
    }

    /// A record's comment names its components, so its canonical constructor
    /// does not name them again.
    private static boolean isCanonical(MethodTree method, ClassTree owner, List<String> parameters) {
        return owner.getKind() == Tree.Kind.RECORD && method.getName().contentEquals("<init>")
                && parameters.equals(components(owner));
    }

    private static List<String> components(ClassTree record) {
        List<String> components = new ArrayList<>();
        for (Tree member : record.getMembers()) {
            if (member instanceof VariableTree field && !field.getModifiers().getFlags().contains(Modifier.STATIC)) {
                components.add(field.getName().toString());
            }
        }
        return components;
    }

    private static void check(String name, DocCommentTree comment, List<String> parameters, Set<String> wrong) {
        Set<String> named = new LinkedHashSet<>();
        for (DocTree tag : comment.getBlockTags()) {
            if (tag instanceof ParamTree param && !param.isTypeParameter()) {
                named.add(param.getName().getName().toString());
            }
        }
        for (String parameter : parameters) {
            if (!named.contains(parameter)) {
                wrong.add(name + " has no @param " + parameter);
            }
        }
        for (String tag : named) {
            if (!parameters.contains(tag)) {
                wrong.add(name + " has a @param " + tag + " and no such parameter");
            }
        }
    }
}
