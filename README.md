# Java API 动态提取工具

基于Java Agent技术的API信息提取工具，支持启动时注入和运行时动态附加两种模式。

---

## 核心功能
- ✅ 自动识别Spring Boot接口（GET/POST）
- ✅ 提取路径、方法、参数、返回值
- ❌ 输出结构化JSON文档
- ✅ 双模式支持：启动注入 & 动态附加

---

## 快速使用

### 1. 启动时注入
```bash
java -javaagent:F:\\ApiExecutor\\target\\ApiExecutor-1.0-SNAPSHOT-jar-with-dependencies.jar -jar vuln-springboot3-3.0.3.jar
```

2. 动态附加模式
# 先启动目标应用
```
java -jar vuln-springboot3-3.0.3.jar

# 新终端窗口执行附加（替换为实际PID）
java -cp ApiExecutor-1.0-SNAPSHOT-jar-with-dependencies.jar site.hnfy258.AgentAttacher 15128
```

---

输出示例
生成路径：./output/api_info.json

```json
{
  "Web API 路径": "/CMD/cookie/cmd.do",
  "HTTP 方法": "POST",
  "请求参数": {
    "cmd": "请求参数: String"
  },
  "返回值": "String"
}
```


技术特性
基于Javassist字节码增强
支持Spring Boot 3.x
自动跳过非业务类
线程安全的数据收集

---

注意事项
需使用JDK环境（含Attach API）
确保输出目录有写入权限
动态附加时需保持Agent JAR路径有效
特殊注解处理可修改ApiTransformer
