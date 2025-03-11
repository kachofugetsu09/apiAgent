package site.hnfy258;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ApiExtractor implements ApplicationRunner {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<String> recordedApis = ConcurrentHashMap.newKeySet(); // 线程安全的去重集合
    private static final String OUTPUT_FILE = "./output/api_info.json";
    private static ApplicationContext applicationContext;

    @Autowired
    public ApiExtractor(ApplicationContext applicationContext) {
        ApiExtractor.applicationContext = applicationContext;
        mapper.configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
// 在应用启动时自动提取所有 API 信息
        scanAllApis();
    }

    // 启动时自动扫描所有 API
    private void scanAllApis() {
        try {
            RequestMappingHandlerMapping mapping = applicationContext.getBean(RequestMappingHandlerMapping.class);
            Map<RequestMappingInfo, HandlerMethod> handlerMethods = mapping.getHandlerMethods();


            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
                RequestMappingInfo mappingInfo = entry.getKey();
                HandlerMethod handlerMethod = entry.getValue();

                // 获取API路径
                Set<String> patterns = mappingInfo.getPatternsCondition().getPatterns();
                String path = patterns.isEmpty() ? "" : patterns.iterator().next();

                // 获取HTTP方法
                Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
                String httpMethod = methods.isEmpty() ? "ANY" : methods.iterator().next().name();

                // 获取请求参数
                Map<String, Object> params = extractMethodParameters(handlerMethod.getMethod());

                // 获取返回类型
                String returnType = handlerMethod.getMethod().getReturnType().getSimpleName();

                // 记录API信息
                extractApiInfo(path, httpMethod, params, returnType);
            }

            System.out.println("API扫描完成，共发现 " + recordedApis.size() + " 个API接口");
        } catch (Exception e) {
            System.err.println("扫描API时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 提取方法参数信息
    private Map<String, Object> extractMethodParameters(Method method) {
        Map<String, Object> paramMap = new HashMap<>();
        Parameter[] parameters = method.getParameters();

        for (Parameter parameter : parameters) {
            String paramName = parameter.getName();
            String paramType = parameter.getType().getSimpleName();


// 检查参数上的注解
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
            RequestBody requestBody = parameter.getAnnotation(RequestBody.class);

            if (requestParam != null && !requestParam.value().isEmpty()) {
                paramName = requestParam.value();
                paramMap.put(paramName, "请求参数: " + paramType);
            } else if (pathVariable != null && !pathVariable.value().isEmpty()) {
                paramName = pathVariable.value();
                paramMap.put(paramName, "路径变量: " + paramType);
            } else if (requestBody != null) {
                paramMap.put(paramName, "请求体: " + paramType);
            } else {
                paramMap.put(paramName, paramType);
            }
        }

        return paramMap;
    }

    // 启动后动态记录API信息的方法
    public static void extractApiInfo(String path, String method, Map<String, Object> params, String returnType) {
        // 创建API信息对象
        Map<String, Object> apiInfo = new HashMap<>();
        apiInfo.put("Web API 路径", path);
        apiInfo.put("HTTP 方法", method);
        apiInfo.put("请求参数", params);
        apiInfo.put("返回值", returnType);

        // 检查是否已记录过该API
        String apiKey = path + ":" + method;
        if (!recordedApis.contains(apiKey)) {
            recordedApis.add(apiKey); // 记录到集合中

            // 确保输出目录存在
            File outputDir = new File("./output");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            // 写入文件，使用同步块避免并发问题
            synchronized (ApiExtractor.class) {
                try {
                    // 使用 Files.write 简化文件写入逻辑
                    String jsonOutput = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(apiInfo);
                    Files.write(Paths.get(OUTPUT_FILE), jsonOutput.getBytes(StandardCharsets.UTF_8));
                    System.out.println("已记录API: " + path + " [" + method + "]");
                } catch (IOException e) {
                    System.err.println("写入API信息失败: " + e.getMessage());
                }
            }
        }
    }

    // 提供一个静态方法用于在Spring上下文之外获取ApplicationContext
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    // 提供一个静态方法用于手动触发API扫描
    public static void manualScanApis() {
        if (applicationContext != null) {
            ApiExtractor extractor = applicationContext.getBean(ApiExtractor.class);
            extractor.scanAllApis();
        } else {
            System.err.println("ApplicationContext未初始化，无法进行API扫描");
        }
    }
}