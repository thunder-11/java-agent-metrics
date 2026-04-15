package com.lzy.javaagent;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;

/**
 * Javassist-based bytecode transformer that injects method-entry logging
 * and per-method timing / metrics collection.
 * <p>
 * For the configured target class, the original console logging is preserved.
 * For <b>all</b> loaded application classes (excluding JDK internals and the
 * agent's own packages), timing instrumentation is injected so
 * {@link com.lzy.javaagent.metrics.MetricsRegistry} receives live data.
 *
 * @author Ali
 */
public class AOPTransformer implements ClassFileTransformer {

    /** The fully-qualified target class for console logging (dot-separated). */
    private final String targetClassName;

    /** Prefixes that must never be instrumented. */
    private static final Set<String> EXCLUDED_PREFIXES = new HashSet<>(Arrays.asList(
            "java/", "javax/", "sun/", "com/sun/", "jdk/",
            "javassist/", "com/lzy/javaagent/metrics/", "com/lzy/javaagent/ui/"
    ));

    public AOPTransformer(String targetClassName) {
        this.targetClassName = targetClassName;
    }

    // ------------------------------------------------------------------ core

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {

        if (className == null) {
            return null; // null = no modification
        }

        // Skip JDK / agent-internal classes
        for (String prefix : EXCLUDED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return null;
            }
        }

        boolean isTarget = className.equals(targetClassName.replace('.', '/'));

        try {
            ClassPool classPool = ClassPool.getDefault();
            classPool.appendClassPath(new LoaderClassPath(loader));
            classPool.appendSystemPath();

            CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));

            if (ctClass.isFrozen()) {
                ctClass.defrost();
            }

            // Also check if interface:
            if (ctClass.isInterface()) {
                return null;
            }

            boolean modified = false;
            int counter = 0;

            for (CtMethod method : ctClass.getDeclaredMethods()) {
                // Skip abstract / native methods
                if (method.isEmpty() || javassist.Modifier.isNative(method.getModifiers()) || javassist.Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }

                String methodKey = className.replace('/', '.') + "#" + method.getName();

                if (isTarget) {
                    System.out.println("[javaagent] instrumenting method: " + methodKey);
                }

                // Instead of addLocalVariable, use method delegation to safely track time
                String originalName = method.getName();
                String renamedName = originalName + "$impl$" + (counter++);
                method.setName(renamedName);

                CtMethod newMethod = javassist.CtNewMethod.copy(method, originalName, ctClass, null);

                StringBuilder body = new StringBuilder();
                body.append("{\n");
                
                if (isTarget) {
                    body.append("  System.out.println(\"[javaagent] >> enter ").append(originalName).append("\");\n");
                }
                
                body.append("  long $__start = System.nanoTime();\n");
                body.append("  try {\n");
                
                if (method.getReturnType() == CtClass.voidType) {
                    body.append("    ").append(renamedName).append("($$);\n");
                } else {
                    body.append("    return ($r) ").append(renamedName).append("($$);\n");
                }
                
                body.append("  } finally {\n");
                body.append("    long $__dur = System.nanoTime() - $__start;\n");
                body.append("    com.lzy.javaagent.metrics.MetricsRegistry.getInstance().updateMetric(\"")
                    .append(methodKey).append("\", $__dur);\n");
                body.append("  }\n");
                body.append("}\n");

                newMethod.setBody(body.toString());
                ctClass.addMethod(newMethod);

                modified = true;
            }

            if (modified) {
                byte[] bytecode = ctClass.toBytecode();
                ctClass.detach(); // release CtClass from pool to avoid memory leaks
                return bytecode;
            }
        } catch (Exception e) {
            System.err.println("[javaagent] transform FAILED for " + className + ": " + e.getMessage());
            // Return null so the JVM uses the original bytecode
        }

        return null;
    }
}
