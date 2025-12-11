package com.weili.iot_portal.starter;

import com.weili.basic.starter.WeiLiSpringApplication;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author WL
 */
@Slf4j
@MapperScan({
        "com.weili.iot_portal.dal.mapper"
})
@ComponentScan(
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASPECTJ,
                pattern = "com.weili.iot_portal.dal.mapper..*"
        ))
@EnableTransactionManagement
@EnableAsync
@SpringBootApplication
@ComponentScan(basePackages = {"com.weili.iot_portal"})
public class IotPortalApplication extends WeiLiSpringApplication {
    public static void main(String[] args) {
        WeiLiSpringApplication.run(IotPortalApplication.class, args);
    }
}