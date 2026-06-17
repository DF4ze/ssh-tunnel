package fr.ses10doigts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SshTunnelGatewayApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SshTunnelGatewayApplication.class);
        // Indispensable pour AWT/Swing (systray)
        app.setHeadless(false);
        app.run(args);
    }
}
