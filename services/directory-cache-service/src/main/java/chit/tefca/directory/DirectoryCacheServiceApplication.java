package chit.tefca.directory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@org.springframework.context.annotation.Profile("!prod")
@EnableScheduling
public class DirectoryCacheServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DirectoryCacheServiceApplication.class, args);
    }
}
