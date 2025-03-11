package site.hnfy258;

import javassist.*;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ApiTransformer implements java.lang.instrument.ClassFileTransformer {
    private final AtomicInteger processedClassCount = new AtomicInteger(0);
    private final AtomicInteger processedMethodCount = new AtomicInteger(0);

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || className.startsWith("java/") || className.startsWith("javax/") ||
                className.startsWith("sun/") || className.equals("site/hnfy258/ApiExtractor")) {
            return classfileBuffer;
        }

        try {
            // 更改检测逻辑，包含更多可能的控制器标识
            if (className.contains("controller") || className.contains("rest") ||
                    className.contains("resource") || className.contains("endpoint") ||
                    className.contains("api")) {

                System.out.println("ApiTransformer: 处理类: " + className);
                ClassPool pool = ClassPool.getDefault();

                // 确保能找到需要的类
                try {
                    pool.appendClassPath(new LoaderClassPath(loader));
                    // 添加系统类路径，确保能找到常用类
                    pool.appendSystemPath();
                } catch (Exception e) {
                    System.err.println("ApiTransformer: 添加类路径时出错: " + e.getMessage());
                    e.printStackTrace();
                }

                try {
                    CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));

                    boolean methodProcessed = false;
                    int methodCount = 0;

                    for (CtMethod method : ctClass.getDeclaredMethods()) {
                        if (processMethod(ctClass, method)) {
                            methodProcessed = true;
                            methodCount++;
                        }
                    }

                    if (methodProcessed) {
                        processedClassCount.incrementAndGet();
                        processedMethodCount.addAndGet(methodCount);
                        System.out.println("ApiTransformer: 类 " + className + " 已被转换, 处理了 " + methodCount + " 个方法");
                        System.out.println("ApiTransformer: 总计已处理 " + processedClassCount.get() + " 个类, "
                                + processedMethodCount.get() + " 个方法");
                        return ctClass.toBytecode();
                    }
                } catch (Exception e) {
                    System.err.println("ApiTransformer: 处理类 " + className + " 时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("ApiTransformer: 转换类 " + className + " 时出错: " + e.getMessage());
            e.printStackTrace();
        }
        return classfileBuffer;
    }

    private boolean processMethod(CtClass ctClass, CtMethod method) throws Exception {
        String methodName = method.getName();
        String className = ctClass.getName();

        try {
            // 检查方法是否有 Spring 注解
            Object[] annotations = method.getAnnotations();
            for (Object anno : annotations) {
                String annoStr = anno.toString();
                if (annoStr.contains("Mapping")) {
                    String httpMethod = extractHttpMethod(annoStr);
                    String path = extractPath(annoStr, className, methodName);

                    System.out.println("ApiTransformer: 识别到 API: " + path + " [" + httpMethod + "]");
                    insertApiExtractionCode(method, path, httpMethod);
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("ApiTransformer: 处理方法 " + methodName + " 时出错: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    private String extractHttpMethod(String annotation) {
        if (annotation.contains("GetMapping")) return "GET";
        if (annotation.contains("PostMapping")) return "POST";
        if (annotation.contains("PutMapping")) return "PUT";
        if (annotation.contains("DeleteMapping")) return "DELETE";
        if (annotation.contains("PatchMapping")) return "PATCH";
        if (annotation.contains("RequestMapping")) {
            if (annotation.contains("method = RequestMethod.GET")) return "GET";
            if (annotation.contains("method = RequestMethod.POST")) return "POST";
            if (annotation.contains("method = RequestMethod.PUT")) return "PUT";
            if (annotation.contains("method = RequestMethod.DELETE")) return "DELETE";
            if (annotation.contains("method = RequestMethod.PATCH")) return "PATCH";
            return "MULTIPLE";
        }
        return "UNKNOWN";
    }

    private String extractPath(String annotation, String className, String methodName) {
        try {
            // 尝试提取value属性
            if (annotation.contains("value=")) {
                int start = annotation.indexOf("value=\"") + 7;
                if (start > 7) { // 确保找到了value="
                    int end = annotation.indexOf("\"", start);
                    if (end > start) {
                        return annotation.substring(start, end);
                    }
                }
            }

            // 尝试提取path属性
            if (annotation.contains("path=")) {
                int start = annotation.indexOf("path=\"") + 6;
                if (start > 6) { // 确保找到了path="
                    int end = annotation.indexOf("\"", start);
                    if (end > start) {
                        return annotation.substring(start, end);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ApiTransformer: 提取路径时出错: " + e.getMessage());
            e.printStackTrace();
        }

        // 默认路径
        return "/" + className.replace('.', '/') + "/" + methodName;
    }

    private void insertApiExtractionCode(CtMethod method, String path, String httpMethod) throws Exception {
        StringBuilder code = new StringBuilder();
        code.append("{\n");
        code.append("  try {\n");
        code.append("    java.util.Map params = new java.util.HashMap();\n");

        CtClass[] paramTypes = method.getParameterTypes();

        // 获取参数名称（如果有注解）
        for (int i = 0; i < paramTypes.length; i++) {
            String paramName = "param" + i;
            code.append("    try {\n");
            code.append("      params.put(\"").append(paramName).append("\", ($").append(i + 1).append(" == null ? \"null\" : $").append(i + 1).append(".toString()));\n");
            code.append("    } catch (Exception e) {\n");
            code.append("      params.put(\"").append(paramName).append("\", \"[").append(paramTypes[i].getName()).append(" type]\");\n");
            code.append("    }\n");
        }

        code.append("    site.hnfy258.ApiExtractor.extractApiInfo(\"").append(path)
                .append("\", \"").append(httpMethod).append("\", params, \"")
                .append(method.getReturnType().getName()).append("\");\n");

        code.append("  } catch (Exception e) {\n");
        code.append("    System.err.println(\"API提取过程中出错: \" + e.getMessage());\n");
        code.append("    e.printStackTrace();\n");  // Added stack trace for better debugging
        code.append("  }\n");
        code.append("}\n");

        try {
            method.insertBefore(code.toString());
            System.out.println("ApiTransformer: 成功插入代码到方法 " + method.getName());
        } catch (Exception e) {
            System.err.println("ApiTransformer: 插入代码到方法 " + method.getName() + " 时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

