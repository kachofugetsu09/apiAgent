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
                agentPath = args[1];
            } else {
                // 自动查找当前运行的 jar 文件目录
                try {
                    File currentJarFile = new File(AgentAttacher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    File parentDir = currentJarFile.getParentFile();
                    File agentJarFile = new File(parentDir, "ApiExecutor-1.0-SNAPSHOT-jar-with-dependencies.jar");
                    if (!agentJarFile.exists()) {
                        agentJarFile = new File(parentDir, "target/ApiExecutor-1.0-SNAPSHOT-jar-with-dependencies.jar");
                    }
                    agentPath = agentJarFile.getAbsolutePath();
                } catch (URISyntaxException e) {
                    agentPath = new File("target/ApiExecutor-1.0-SNAPSHOT-jar-with-dependencies.jar").getAbsolutePath();
                    System.out.println("警告: 无法确定当前 JAR 路径，将使用默认路径: " + agentPath);
                }
            }

            System.out.println("正在附加到进程: " + pid);
            System.out.println("使用代理路径: " + agentPath);

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