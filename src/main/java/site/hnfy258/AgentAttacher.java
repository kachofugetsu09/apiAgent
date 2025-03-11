package site.hnfy258;

import com.sun.tools.attach.VirtualMachine;
import java.io.File;
import java.net.URISyntaxException;

public class AgentAttacher {
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.out.println("用法: java -jar agent-attacher.jar <PID> [agentPath]");
                return;
            }

            String pid = args[0];
            String agentPath;

            // 检查是否提供了代理路径参数
            if (args.length > 1) {
                // 使用命令行参数中提供的路径
                agentPath = args[1];
            } else {
                // 尝试自动查找当前运行的jar文件的目录
                try {
                    File currentJarFile = new File(AgentAttacher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    File parentDir = currentJarFile.getParentFile();

                    // 在同级目录中查找agent jar
                    File agentJarFile = new File(parentDir, "ApiExecutor-1.0-SNAPSHOT-jar-with-dependencies.jar");

                    if (agentJarFile.exists()) {
                        agentPath = agentJarFile.getAbsolutePath();
                    } else {
                        // 如果在同级目录未找到，尝试查找target目录
                        agentJarFile = new File(parentDir, "target/ApiExecutor-1.0-SNAPSHOT-jar-with-dependencies.jar");

                        if (agentJarFile.exists()) {
                            agentPath = agentJarFile.getAbsolutePath();
                        } else {
                            // 尝试在当前目录下查找
                            agentJarFile = new File("ApiExecutor-1.0-SNAPSHOT-jar-with-dependencies.jar");

                            if (agentJarFile.exists()) {
                                agentPath = agentJarFile.getAbsolutePath();
                            } else {
                                // 回退到原来的路径
                                agentPath = new File("target/ApiExecutor-1.0-SNAPSHOT-jar-with-dependencies.jar").getAbsolutePath();
                                System.out.println("警告: 未找到代理JAR文件，将使用默认路径: " + agentPath);
                            }
                        }
                    }
                } catch (URISyntaxException e) {
                    // 如果获取当前jar路径失败，回退到默认路径
                    agentPath = new File("target/ApiExecutor-1.0-SNAPSHOT-jar-with-dependencies.jar").getAbsolutePath();
                    System.out.println("警告: 无法确定当前JAR路径，将使用默认路径: " + agentPath);
                }
            }

            System.out.println("正在附加到进程: " + pid);
            System.out.println("使用代理路径: " + agentPath);

            if (!new File(agentPath).exists()) {
                System.out.println("警告: 代理JAR文件不存在: " + agentPath);
            }

            VirtualMachine vm = VirtualMachine.attach(pid);
            vm.loadAgent(agentPath);
            vm.detach();

            System.out.println("代理附加成功!");
        } catch (Exception e) {
            System.err.println("代理附加失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}