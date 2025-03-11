package site.hnfy258;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ApiExtractor {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<String> recordedApis = ConcurrentHashMap.newKeySet();
    private static final String OUTPUT_DIR = System.getProperty("user.dir") + File.separator + "output";
    private static final String OUTPUT_FILE = OUTPUT_DIR + File.separator + "api_info.json";
    private static final AtomicInteger processedCount = new AtomicInteger(0);

    public static void extractApiInfo(String path, String method, Map<String, Object> params, String returnType) {
        try {
            System.out.println("ApiExtractor: 开始处理API - " + path + " [" + method + "]");

            // 确保输出目录存在
            File outputDir = new File(OUTPUT_DIR);
            if (!outputDir.exists()) {
                boolean created = outputDir.mkdirs();
                System.out.println("ApiExtractor: 创建输出目录: " + (created ? "成功" : "失败"));
                System.out.println("ApiExtractor: 输出目录绝对路径: " + outputDir.getAbsolutePath());

                if (!created) {
                    System.out.println("ApiExtractor: 创建目录失败，检查权限...");
                    System.out.println("ApiExtractor: 父目录可写: " + outputDir.getParentFile().canWrite());
                }
            }

            System.out.println("ApiExtractor: 输出目录存在: " + outputDir.exists());
            System.out.println("ApiExtractor: 输出目录可写: " + outputDir.canWrite());

            Map<String, Object> apiInfo = new HashMap<>();
            apiInfo.put("Web API 路径", path);
            apiInfo.put("HTTP 方法", method);
            apiInfo.put("请求参数", params);
            apiInfo.put("返回值", returnType);
            String apiKey = path + ":" + method;

            System.out.println("ApiExtractor: 处理API键值: " + apiKey);

            if (!recordedApis.contains(apiKey)) {
                recordedApis.add(apiKey);
                int currentCount = processedCount.incrementAndGet();
                System.out.println("ApiExtractor: API键值添加到记录集合 (总计: " + currentCount + ")");

                synchronized (ApiExtractor.class) {
                    try {
                        // 读取现有内容
                        Map<String, Object> existingData = new HashMap<>();
                        File jsonFile = new File(OUTPUT_FILE);
                        System.out.println("ApiExtractor: 输出文件绝对路径: " + jsonFile.getAbsolutePath());

                        if (Files.exists(Paths.get(OUTPUT_FILE))) {
                            System.out.println("ApiExtractor: 读取现有JSON文件");
                            try {
                                existingData = mapper.readValue(jsonFile, Map.class);
                                System.out.println("ApiExtractor: 成功读取现有数据，包含 " + existingData.size() + " 个API");
                            } catch (IOException e) {
                                System.err.println("ApiExtractor: 读取JSON文件失败，将创建新文件: " + e.getMessage());
                                e.printStackTrace();
                                // 如果读取失败，使用空的数据继续
                                existingData = new HashMap<>();
                            }
                        } else {
                            System.out.println("ApiExtractor: JSON文件不存在，将创建新文件");
                        }

                        // 添加新内容
                        existingData.put(apiKey, apiInfo);

                        // 写回文件 - 使用 try-with-resources 确保文件正确关闭
                        System.out.println("ApiExtractor: 准备写入JSON文件");
                        try (FileWriter writer = new FileWriter(jsonFile)) {
                            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, existingData);
                            System.out.println("ApiExtractor: 成功写入JSON文件 (大小: " + jsonFile.length() + " 字节)");
                            System.out.println("ApiExtractor: 已记录 API: " + path + " [" + method + "]");
                        } catch (IOException e) {
                            System.err.println("ApiExtractor: 写入JSON文件失败: " + e.getMessage());
                            e.printStackTrace();

                            // 尝试备用写入方法
                            try {
                                System.out.println("ApiExtractor: 尝试备用写入方法...");
                                String jsonOutput = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(existingData);
                                Files.write(Paths.get(OUTPUT_FILE), jsonOutput.getBytes());
                                System.out.println("ApiExtractor: 备用方法成功写入JSON文件");
                            } catch (IOException e2) {
                                System.err.println("ApiExtractor: 备用写入方法也失败: " + e2.getMessage());
                                e2.printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("ApiExtractor: 处理API信息时发生异常: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } else {
                System.out.println("ApiExtractor: API已存在，跳过记录");
            }
        } catch (Exception e) {
            System.err.println("ApiExtractor: 提取API时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

