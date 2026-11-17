package com.pulsepass.pass;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {

    @Test
    void productionConfigurationBindsPersistenceToSpring() throws Exception {
        var yaml = new YamlPropertySourceLoader().load("main",
                new FileSystemResource("src/main/resources/application.yml")).getFirst();

        assertThat(yaml.containsProperty("spring.datasource.url")).isTrue();
        assertThat(yaml.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(yaml.getProperty("spring.jpa.open-in-view")).isEqualTo(false);
        assertThat(yaml.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(yaml.containsProperty("pulsepass.datasource.url")).isFalse();
    }
}
