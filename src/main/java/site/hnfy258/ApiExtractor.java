package site.hnfy258;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ApiExtractor {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<String> recordedApis = ConcurrentHashMap.newKeySet();
    private static final String OUTPUT_FILE = "./output/api_info.json";

    public static void extractApiInfo(String path, String method, Map<String, Object> params, String returnType) {
        Map<String, Object> apiInfo = new HashMap<>();
        apiInfo.put("Web API 路径", path);
        apiInfo.put("HTTP 方法", method);
        apiInfo.put("请求参数", params);
        apiInfo.put("返回值", returnType);

        String apiKey = path + ":" + method;
        if (!recordedApis.contains(apiKey)) {
            recordedApis.add(apiKey);
            synchronized (ApiExtractor.class) {
                try {
                    String jsonOutput = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(apiInfo);
                    Files.write(Paths.get(OUTPUT_FILE), jsonOutput.getBytes());
                    System.out.println("已记录 API: " + path + " [" + method + "]");
                } catch (IOException e) {
                    System.err.println("写入 API 信息失败: " + e.getMessage());
                }
            }
        }
    }
}