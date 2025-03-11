package site.hnfy258;

import java.lang.instrument.Instrumentation;
import java.io.File;

public class ApiAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("API Agent - 启动时加载");
        setupAgent(inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("API Agent - 动态加载");
        setupAgent(inst);
    }

    private static void setupAgent(Instrumentation inst) {
        try {
            System.out.println("开始初始化 Agent...");

            // 创建输出目录 - 使用绝对路径
            File outputDir = new File(System.getProperty("user.dir"), "output");
            System.out.println("输出目录绝对路径: " + outputDir.getAbsolutePath());

            if (!outputDir.exists()) {
                boolean created = outputDir.mkdirs();
                System.out.println("创建输出目录: " + (created ? "成功" : "失败"));
                if (!created) {
                    System.out.println("创建目录失败，尝试检查权限...");
                    System.out.println("父目录可写: " + outputDir.getParentFile().canWrite());
                }
            }

            System.out.println("输出目录存在: " + outputDir.exists());
            System.out.println("输出目录可写: " + outputDir.canWrite());

            // 检查是否需要清空 API 文件
            boolean shouldClearApiInfo = Boolean.getBoolean("clear.api.info"); // 从系统属性读取配置
            File apiFile = new File(outputDir, "api_info.json");
            System.out.println("API文件绝对路径: " + apiFile.getAbsolutePath());

            if (shouldClearApiInfo && apiFile.exists()) {
                boolean deleted = apiFile.delete();
                System.out.println("清除旧的 API 信息文件: " + (deleted ? "成功" : "失败"));
            }

            // 添加转换器
            inst.addTransformer(new ApiTransformer(), true);

            // 如果是动态加载，重新转换已加载的类
            if (inst.isRetransformClassesSupported()) {
                Class<?>[] loadedClasses = inst.getAllLoadedClasses();
                int transformed = 0;
                for (Class<?> clazz : loadedClasses) {
                    String className = clazz.getName();
                    if ((className.contains("controller") || className.contains("rest") ||
                            className.contains("resource") || className.contains("endpoint") ||
                            className.contains("api")) &&
                            !className.startsWith("java.") && !className.startsWith("javax.")) {
                        try {
                            inst.retransformClasses(clazz);
                            System.out.println("重新转换类: " + className);
                            transformed++;
                        } catch (Exception e) {
                            System.err.println("重新转换类失败 " + className + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
                System.out.println("总共重新转换类数量: " + transformed);
            }
            System.out.println("Agent 初始化完成!");
        } catch (Exception e) {
            System.err.println("设置代理时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

