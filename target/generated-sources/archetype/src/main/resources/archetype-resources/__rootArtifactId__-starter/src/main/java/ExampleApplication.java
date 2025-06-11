#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

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
                        ${symbol_escape}tApplication '{}' is running! Access URLs:
                        ${symbol_escape}tLocal: ${symbol_escape}t${symbol_escape}t{}://localhost:{}
                        ${symbol_escape}tExternal: ${symbol_escape}t{}://{}:{}
                        ${symbol_escape}tProfile(s): ${symbol_escape}t{}
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