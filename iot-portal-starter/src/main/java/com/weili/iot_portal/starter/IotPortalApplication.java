package com.weili.iot_portal.starter;

import com.weili.starter.WeiLiSpringApplication;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author WL
 */
@Slf4j
@MapperScan({
        "com.weili.iot_portal.dal.mapper",
        "com.weili.iot_portal.business.device_mgmt.dal.mapper"
})
@ComponentScan(basePackages = {"com.weili.basic", "com.weili.iot_portal"})
@EnableTransactionManagement
@SpringBootApplication
public class IotPortalApplication  extends WeiLiSpringApplication {
    public static void main(String[] args) {
        WeiLiSpringApplication.run(IotPortalApplication.class, args);
    }
}