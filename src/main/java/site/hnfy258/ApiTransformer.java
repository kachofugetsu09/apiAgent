package site.hnfy258;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiTransformer implements ClassFileTransformer {
    // 用于提取RequestMapping中的路径 - 修复正则表达式中的花括号转义
    private static final Pattern PATH_PATTERN = Pattern.compile("value=\\{(.+?)\\}");
    private static final Pattern METHOD_PATTERN = Pattern.compile("method=\\{(.+?)\\}");

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        // 跳过系统类、第三方库类和自己的类
        if (className == null ||
                className.startsWith("java/") ||
                className.startsWith("javax/") ||
                className.startsWith("sun/") ||
                className.startsWith("jdk/") ||
                className.startsWith("org/springframework/") ||
                className.startsWith("org/apache/") || // 跳过 Apache 类
                className.startsWith("site/hnfy258/")) {
            return classfileBuffer;
        }

        try {
            // 只处理目标类
            if (className.contains("controller") ||
                    className.contains("resource") ||
                    className.contains("rest") ||
                    className.contains("action") ||
                    className.contains("javaweb")) {

                System.out.println("处理类: " + className);

                ClassPool pool = ClassPool.getDefault();
                pool.appendClassPath(new LoaderClassPath(loader));

                // 确保加载需要的类
                try {
                    pool.get("site.hnfy258.ApiExtractor");
                } catch (NotFoundException e) {
                    pool.appendClassPath(new ClassClassPath(this.getClass()));
                }

                CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));

                // 跳过接口和抽象类
                if (ctClass.isInterface() || Modifier.isAbstract(ctClass.getModifiers())) {
                    return classfileBuffer;
                }

                // 处理所有方法
                for (CtMethod method : ctClass.getDeclaredMethods()) {
                    processMethod(ctClass, method, "");
                }

                return ctClass.toBytecode();
            }
        } catch (Exception e) {
            System.err.println("转换类 " + className + " 时出错: " + e.getMessage());
        }

        return classfileBuffer;
    }

    private void processMethod(CtClass ctClass, CtMethod method, String basePath) {
        try {
            // 跳过私有方法、静态方法和空方法
            if (Modifier.isPrivate(method.getModifiers()) ||
                    Modifier.isStatic(method.getModifiers()) ||
                    method.isEmpty()) {
                return;
            }


            System.out.println("检查方法: " + method.getName() + " in " + ctClass.getName());

            // 默认API路径和HTTP方法
            String apiPath = basePath;
            String httpMethod = "POST";  // 默认POST
            boolean isApiMethod = false;

            // 检查Spring注解
            try {
                Object[] annotations = method.getAnnotations();
                for (Object anno : annotations) {
                    String annoStr = anno.toString();

                    // 检查各种Mapping注解
                    if (annoStr.contains("Mapping")) {
                        isApiMethod = true;

                        // 从注解中提取路径
                        Matcher pathMatcher = PATH_PATTERN.matcher(annoStr);
                        if (pathMatcher.find()) {
                            String pathValue = pathMatcher.group(1).replace("\"", "");
                            if (!pathValue.startsWith("/")) {
                                pathValue = "/" + pathValue;
                            }
                            apiPath = (basePath + pathValue).replace("//", "/");
                        }

                        // 确定HTTP方法
                        if (annoStr.contains("GetMapping")) {
                            httpMethod = "GET";
                        } else if (annoStr.contains("PostMapping")) {
                            httpMethod = "POST";
                        } else if (annoStr.contains("PutMapping")) {
                            httpMethod = "PUT";
                        } else if (annoStr.contains("DeleteMapping")) {
                            httpMethod = "DELETE";
                        } else if (annoStr.contains("RequestMapping")) {
                            // 从RequestMapping注解中提取方法
                            Matcher methodMatcher = METHOD_PATTERN.matcher(annoStr);
                            if (methodMatcher.find()) {
                                String methodValue = methodMatcher.group(1);
                                if (methodValue.contains("GET")) {
                                    httpMethod = "GET";
                                } else if (methodValue.contains("POST")) {
                                    httpMethod = "POST";
                                } else if (methodValue.contains("PUT")) {
                                    httpMethod = "PUT";
                                } else if (methodValue.contains("DELETE")) {
                                    httpMethod = "DELETE";
                                }
                            }
                        }

                        break;
                    }
                }
            } catch (Exception e) {
                // 注解处理异常
                System.out.println("处理注解时出错: " + e.getMessage());
            }

            // 如果没有通过注解识别为API方法，使用方法名推断
            if (!isApiMethod) {
                String methodName = method.getName().toLowerCase();
                if (methodName.startsWith("get") || methodName.startsWith("find") ||
                        methodName.startsWith("query") || methodName.startsWith("select")) {
                    isApiMethod = true;
                    httpMethod = "GET";
                } else if (methodName.startsWith("post") || methodName.startsWith("save") ||
                        methodName.startsWith("create") || methodName.startsWith("add")) {
                    isApiMethod = true;
                    httpMethod = "POST";
                } else if (methodName.startsWith("put") || methodName.startsWith("update")) {
                    isApiMethod = true;
                    httpMethod = "PUT";
                } else if (methodName.startsWith("delete") || methodName.startsWith("remove")) {
                    isApiMethod = true;
                    httpMethod = "DELETE";
                }

                // 如果是API方法，构建路径
                if (isApiMethod) {
                    String className = ctClass.getSimpleName();
                    if (className.endsWith("Controller")) {
                        className = className.substring(0, className.length() - 10);
                    }
                    apiPath = "/" + className + "/" + method.getName() + ".do";
                }
            }

            // 如果是API方法，插入代码收集信息
            if (isApiMethod) {
                System.out.println("识别到API: " + apiPath + " [" + httpMethod + "]");

                // 构建代码提取参数并记录API
                String code = buildApiExtractionCode(apiPath, httpMethod, method);
                method.insertBefore(code);
                System.out.println("已插入API信息提取代码");
            }
        } catch (Exception e) {
            System.err.println("处理方法 " + method.getName() + " 时出错: " + e.getMessage());
        }
    }

    private String buildApiExtractionCode(String path, String httpMethod, CtMethod method) {
        StringBuilder code = new StringBuilder();
        code.append("{\n");
        code.append("  try {\n");
        code.append("    java.util.Map params = new java.util.HashMap();\n");

        // 参数提取
        try {
            CtClass[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i++) {
                String paramName = "param" + i;
                String paramType = paramTypes[i].getName();
                code.append("    params.put(\"").append(paramName).append("_type\", \"").append(paramType).append("\");\n");
                code.append("    if ($").append(i + 1).append(" != null) {\n");
                code.append("      params.put(\"").append(paramName).append("\", $").append(i + 1).append(");\n");
                code.append("    }\n");
            }
        } catch (NotFoundException e) {
            code.append("    // 无法获取参数类型\n");
        }

        // 获取返回类型
        String returnType = "void";
        try {
            returnType = method.getReturnType().getName();
        } catch (NotFoundException e) {
            // 保持默认值
        }

        // 记录API信息
        code.append("    site.hnfy258.ApiExtractor.extractApiInfo(\"").append(path).append("\", \"")
                .append(httpMethod).append("\", params, \"").append(returnType).append("\");\n");

        code.append("  } catch (Exception e) {\n");
        code.append("    System.err.println(\"提取API信息时出错: \" + e);\n");
        code.append("  }\n");
        code.append("}\n");

        return code.toString();
    }
}

