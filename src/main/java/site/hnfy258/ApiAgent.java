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
// 创建输出目录
            File outputDir = new File("./output");
            if (!outputDir.exists()) {
                boolean created = outputDir.mkdirs();
                System.out.println("创建输出目录: " + (created ? "成功" : "失败"));
            }


// 清空之前的API文件
            File apiFile = new File("./output/api_info.json");
            if (apiFile.exists()) {
                apiFile.delete();
                System.out.println("清除旧的API信息文件");
            }

// 添加转换器
            inst.addTransformer(new ApiTransformer(), true);
            System.out.println("已添加API转换器");

// 如果是动态加载，重新转换已加载的类
            if (inst.isRetransformClassesSupported()) {
                // 获取所有已加载的类
                Class<?>[] loadedClasses = inst.getAllLoadedClasses();
                System.out.println("准备检查 " + loadedClasses.length + " 个已加载的类");

                // 筛选可以重新转换的类
                for (Class<?> clazz : loadedClasses) {
                    String className = clazz.getName();
                    if ((className.contains("controller") ||
                            className.contains("resource") ||
                            className.contains("rest") ||
                            className.contains("action") ||
                            className.contains("javaweb")) &&
                            !className.startsWith("java.") &&
                            !className.startsWith("javax.") &&
                            !className.startsWith("sun.") &&
                            !className.startsWith("site.hnfy258")) {

                        try {
                            inst.retransformClasses(clazz);
                            System.out.println("重新转换类: " + className);
                        } catch (Exception e) {
                            System.err.println("重新转换类失败 " + className + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("设置代理时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}