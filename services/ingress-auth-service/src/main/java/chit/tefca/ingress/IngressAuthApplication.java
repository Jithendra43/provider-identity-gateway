package chit.tefca.ingress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@org.springframework.context.annotation.Profile("!prod")
@EnableScheduling
@EntityScan(basePackages = {
        "chit.tefca.ingress",
        "chit.tefca.common.audit"
})
@EnableJpaRepositories(basePackages = {
        "chit.tefca.ingress",
        "chit.tefca.common.audit"
})
public class IngressAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngressAuthApplication.class, args);
    }
}
