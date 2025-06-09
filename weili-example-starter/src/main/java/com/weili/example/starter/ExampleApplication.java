package com.weili.example.starter;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import com.weili.basic.framework.annotation.EnableFeignClientsPlus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalTime;

@Slf4j
@EnableApolloConfig
@EnableFeignClientsPlus
@AutoConfiguration
@SpringBootApplication
public class ExampleApplication {
    public static void main(String[] args) {
        long begin = System.currentTimeMillis();
        log.info("==========开始启动: " + System.currentTimeMillis());
        SpringApplication app = new SpringApplication(ExampleApplication.class);
        Environment env = app.run(args).getEnvironment();
        String protocol = "http";
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("The host name could not be determined, using `localhost` as fallback");
        }
        log.info("""

                        ----------------------------------------------------------
                        \tApplication '{}' is running! Access URLs:
                        \tLocal: \t\t{}://localhost:{}
                        \tExternal: \t{}://{}:{}
                        \tProfile(s): \t{}
                        ----------------------------------------------------------""",
                env.getProperty("spring.application.name"),
                protocol,
                env.getProperty("server.port", "8080"),
                protocol,
                hostAddress,
                env.getProperty("server.port", "8080"),
                env.getActiveProfiles());
        long time = System.currentTimeMillis() - begin;
        log.info("==========启动完成: " + System.currentTimeMillis() + "; 共花费: " + LocalTime.ofSecondOfDay(time / 1000));
    }
}