package chit.tefca.routing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.context.annotation.Profile("!prod")
public class RoutingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutingServiceApplication.class, args);
    }
}
