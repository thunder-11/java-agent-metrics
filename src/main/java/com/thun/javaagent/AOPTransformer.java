package com.thun.javaagent;

import com.thun.javaagent.agent.AgentConfig;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;

/**
 * Javassist-based bytecode transformer that injects:
 * <ul>
 *   <li>Method-entry console logging (for the target class)</li>
 *   <li>Per-method timing and metrics collection</li>
 *   <li>Error tracking — catches and records exceptions</li>
 *   <li>Call tree tracing — enter/exit hooks for parent→child tracking</li>
 * </ul>
 * <p>
 * Respects {@link AgentConfig} for runtime enable/disable and filtering.
 *
 * @author Ali
 */
public class AOPTransformer implements ClassFileTransformer {

    /** The fully-qualified target class for console logging (dot-separated). */
    private final String targetClassName;

    /** Prefixes that must never be instrumented. */
    private static final Set<String> EXCLUDED_PREFIXES = new HashSet<>(Arrays.asList(
            "java/", "javax/", "sun/", "com/sun/", "jdk/",
            "javassist/",
            "com/thun/javaagent/metrics/",
            "com/thun/javaagent/ui/",
            "com/thun/javaagent/agent/",
            "com/thun/javaagent/tracing/",
            "com/thun/javaagent/export/"
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

        // Check if agent is enabled
        if (!AgentConfig.getInstance().isEnabled()) {
            return null;
        }

        // Skip JDK / agent-internal classes
        for (String prefix : EXCLUDED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return null;
            }
        }

        // Apply class exclusions from config
        String dotClassName = className.replace('/', '.');
        List<String> classExclusions = AgentConfig.getInstance().getClassExclusions();
        for (String excl : classExclusions) {
            if (dotClassName.equals(excl) || dotClassName.endsWith("." + excl)) {
                return null;
            }
        }

        // Apply package filters from config (if any are set, only instrument matching packages)
        List<String> pkgFilters = AgentConfig.getInstance().getPackageFilters();
        if (!pkgFilters.isEmpty()) {
            boolean matches = false;
            for (String filter : pkgFilters) {
                if (dotClassName.startsWith(filter)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) return null;
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

            // Skip interfaces
            if (ctClass.isInterface()) {
                return null;
            }

            boolean modified = false;
            int counter = 0;

            // Get method exclusions from config
            List<String> methodExclusions = AgentConfig.getInstance().getMethodExclusions();

            for (CtMethod method : ctClass.getDeclaredMethods()) {
                // Skip abstract / native methods
                if (method.isEmpty()
                        || javassist.Modifier.isNative(method.getModifiers())
                        || javassist.Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }

                String methodName = method.getName();

                // Check method exclusions
                boolean excluded = false;
                for (String excl : methodExclusions) {
                    if (methodName.equals(excl)) {
                        excluded = true;
                        break;
                    }
                }
                if (excluded) continue;

                String methodKey = dotClassName + "#" + methodName;

                if (isTarget) {
                    System.out.println("[javaagent] instrumenting method: " + methodKey);
                }

                // Rename original method and create a wrapper
                String originalName = methodName;
                String renamedName = originalName + "$impl$" + (counter++);
                method.setName(renamedName);

                CtMethod newMethod = javassist.CtNewMethod.copy(method, originalName, ctClass, null);

                StringBuilder body = new StringBuilder();
                body.append("{\n");

                if (isTarget) {
                    body.append("  System.out.println(\"[javaagent] >> enter ").append(originalName).append("\");\n");
                }

                // Call tracing — enter
                body.append("  com.thun.javaagent.tracing.CallTracer.getInstance().enterMethod(\"")
                    .append(methodKey).append("\");\n");

                body.append("  long $__start = System.nanoTime();\n");
                body.append("  try {\n");

                if (method.getReturnType() == CtClass.voidType) {
                    body.append("    ").append(renamedName).append("($$);\n");
                } else {
                    body.append("    ").append(method.getReturnType().getName())
                        .append(" $__result = ($r) ").append(renamedName).append("($$);\n");
                }

                body.append("    long $__dur = System.nanoTime() - $__start;\n");
                body.append("    com.thun.javaagent.metrics.MetricsRegistry.getInstance().updateMetric(\"")
                    .append(methodKey).append("\", $__dur);\n");
                body.append("    com.thun.javaagent.tracing.CallTracer.getInstance().exitMethod(\"")
                    .append(methodKey).append("\", $__dur);\n");

                if (method.getReturnType() != CtClass.voidType) {
                    body.append("    return $__result;\n");
                }

                body.append("  } catch (Throwable $__ex) {\n");
                body.append("    long $__dur = System.nanoTime() - $__start;\n");
                body.append("    com.thun.javaagent.metrics.MetricsRegistry.getInstance().updateMetric(\"")
                    .append(methodKey).append("\", $__dur);\n");
                body.append("    com.thun.javaagent.metrics.MetricsRegistry.getInstance().recordError(\"")
                    .append(methodKey).append("\", $__ex.getClass().getName());\n");
                body.append("    com.thun.javaagent.tracing.CallTracer.getInstance().exitMethod(\"")
                    .append(methodKey).append("\", $__dur);\n");
                body.append("    throw $__ex;\n");
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
