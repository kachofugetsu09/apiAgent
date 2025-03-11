package site.hnfy258;

import javassist.*;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

public class ApiTransformer implements java.lang.instrument.ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || className.startsWith("java/") || className.startsWith("javax/")) {
            return classfileBuffer;
        }

        try {
            if (className.contains("controller") || className.contains("rest")) {
                System.out.println("处理类: " + className);
                ClassPool pool = ClassPool.getDefault();
                CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));

                for (CtMethod method : ctClass.getDeclaredMethods()) {
                    processMethod(ctClass, method);
                }
                return ctClass.toBytecode();
            }
        } catch (Exception e) {
            System.err.println("转换类 " + className + " 时出错: " + e.getMessage());
        }
        return classfileBuffer;
    }

    private void processMethod(CtClass ctClass, CtMethod method) throws Exception {
        String methodName = method.getName();
        String className = ctClass.getName();

        // 检查方法是否有 Spring 注解
        Object[] annotations = method.getAnnotations();
        for (Object anno : annotations) {
            String annoStr = anno.toString();
            if (annoStr.contains("Mapping")) {
                String httpMethod = extractHttpMethod(annoStr);
                String path = extractPath(annoStr, className, methodName);

                System.out.println("识别到 API: " + path + " [" + httpMethod + "]");
                insertApiExtractionCode(method, path, httpMethod);
            }
        }
    }

    private String extractHttpMethod(String annotation) {
        if (annotation.contains("GetMapping")) return "GET";
        if (annotation.contains("PostMapping")) return "POST";
        if (annotation.contains("PutMapping")) return "PUT";
        if (annotation.contains("DeleteMapping")) return "DELETE";
        return "UNKNOWN";
    }

    private String extractPath(String annotation, String className, String methodName) {
        if (annotation.contains("value=")) {
            int start = annotation.indexOf("value=\"") + 7;
            int end = annotation.indexOf("\"", start);
            return annotation.substring(start, end);
        }
        return "/" + className + "/" + methodName;
    }

    private void insertApiExtractionCode(CtMethod method, String path, String httpMethod) throws Exception {
        StringBuilder code = new StringBuilder();
        code.append("{\n");
        code.append("  java.util.Map params = new java.util.HashMap();\n");
        CtClass[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            code.append("  params.put(\"param").append(i).append("\", $").append(i + 1).append(");\n");
        }
        code.append("  site.hnfy258.ApiExtractor.extractApiInfo(\"").append(path)
                .append("\", \"").append(httpMethod).append("\", params, \"").append(method.getReturnType().getName()).append("\");\n");
        code.append("}\n");
        method.insertBefore(code.toString());
    }
}