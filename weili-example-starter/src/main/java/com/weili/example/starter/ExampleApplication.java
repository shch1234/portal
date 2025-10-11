package com.weili.example.starter;

import com.weili.starter.WeiLiSpringApplication;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Slf4j
@MapperScan({
        "com.weili.example.dal.mapper"
})
@ComponentScan(basePackages = {"com.weili.basic", "com.weili.example"})
@EnableTransactionManagement
@SpringBootApplication
public class ExampleApplication  extends WeiLiSpringApplication {
    public static void main(String[] args) {
        WeiLiSpringApplication.run(ExampleApplication.class, args);
    }
}