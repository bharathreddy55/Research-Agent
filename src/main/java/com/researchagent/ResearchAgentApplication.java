package com.researchagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

@SpringBootApplication
public class ResearchAgentApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ResearchAgentApplication.class);
        boolean isCli = Arrays.stream(args).anyMatch(arg -> 
                arg.equals("-q") || arg.startsWith("--query") || 
                arg.equals("-h") || arg.equals("--help") || arg.equals("-V") || arg.equals("--version")
        );
        boolean isServer = Arrays.stream(args).anyMatch(arg -> arg.equals("--server"));

        if (isCli && !isServer) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }
}
