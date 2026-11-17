import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.io.*;
import java.util.*;
public class JavaSyntaxSmoke {
  public static void main(String[] args) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diag = new DiagnosticCollector<>();
    StandardJavaFileManager files = compiler.getStandardFileManager(diag, null, null);
    List<File> java = new ArrayList<>();
    for (String a : args) java.add(new File(a));
    Iterable<? extends JavaFileObject> objects = files.getJavaFileObjectsFromFiles(java);
    JavacTask task = (JavacTask) compiler.getTask(null, files, diag, List.of("--release","21"), null, objects);
    task.parse();
    for (Diagnostic<? extends JavaFileObject> d : diag.getDiagnostics()) {
      if (d.getKind() == Diagnostic.Kind.ERROR) {
        System.err.println(d);
        System.exit(1);
      }
    }
    System.out.println("OK Java 21 AST parse: " + java.size() + " source files");
  }
}
