package game.server;

public class ServerConfig {
    private final int port;
    private final int httpPort;
    private final int threadPoolSize;
    private final int secretNumberLength;
    private final int maxAttempts;


    public ServerConfig(int port, int httpPort, int threadPoolSize, int secretNumberLength, int maxAttempts) {
        this.port = port;
        this.httpPort = httpPort;
        this.threadPoolSize = threadPoolSize;
        this.secretNumberLength = secretNumberLength;
        this.maxAttempts = maxAttempts;
    }

    public static ServerConfig defaults() {
        return new ServerConfig(5050, 8081, 50, 4, 10);
    }

    public static ServerConfig fromArgsOrDefaults(String[] args) {
        ServerConfig d = defaults();
        int port = d.port;
        int httpPort = d.httpPort;
        int threadPoolSize = d.threadPoolSize;
        int secretNumberLength = d.secretNumberLength;
        int maxAttempts = d.maxAttempts;

        for (int i = 0; i < args.length - 1; i++){
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--httpPort" -> httpPort = Integer.parseInt(args[++i]);
                case "--threadPoolSize" -> threadPoolSize = Integer.parseInt(args[++i]);
                case "--secretNumberLength" -> secretNumberLength = Integer.parseInt(args[++i]);
                case "--maxAttempts" -> maxAttempts = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        return new ServerConfig(port, httpPort, threadPoolSize, secretNumberLength, maxAttempts);
    }

    public int getPort() {
        return port;
    }
    public int getHttpPort() {
        return httpPort;
    }
    public int getThreadPoolSize() {
        return threadPoolSize;
    }
    public int getSecretNumberLength() {
        return secretNumberLength;
    }
    public int getMaxAttempts() {
        return maxAttempts;
    }
}
