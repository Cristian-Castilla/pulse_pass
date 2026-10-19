package com.pulsepass.pass;

import com.pulsepass.pass.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class PulsePassApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulsePassApplication.class, args);
    }

}
