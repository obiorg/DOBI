package org.dobi.app.config;

import jakarta.persistence.EntityManagerFactory;
import org.dobi.app.repository.UserAccountRepository;
import org.dobi.app.repository.UserLoginDataRepository;
import org.dobi.app.repository.UserRoleRepository;
import org.dobi.app.service.AdminService;
import org.dobi.app.service.AlarmService;
import org.dobi.app.service.ReportingService;
import org.dobi.app.service.SupervisionService;
import org.dobi.app.service.UserDetailsServiceImpl;
import org.dobi.manager.MachineManagerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@ComponentScan(basePackages = {
    "org.dobi.services", // Pour AlarmEngineService
    "org.dobi.manager", // Pour MachineManagerService
    "org.dobi.kafka", // Pour KafkaManagerService
    "org.dobi.influxdb", // Pour les services InfluxDB
    "org.dobi.core.websocket" // Pour TagWebSocketController
})
public class DobiServiceConfiguration {

    // CORRECTION : Chaque méthode @Bean reçoit maintenant les dépendances dont elle a besoin
    // et les passe au constructeur du service.
    @Bean
    public UserDetailsService userDetailsService(UserLoginDataRepository userLoginDataRepository) {
        return new UserDetailsServiceImpl(userLoginDataRepository);
    }

    @Bean
    public AdminService adminService(UserAccountRepository userAccountRepository,
            UserLoginDataRepository userLoginDataRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder) {
        return new AdminService(userAccountRepository, userLoginDataRepository, userRoleRepository, passwordEncoder);
    }

    @Bean
    public SupervisionService supervisionService(MachineManagerService machineManagerService) {
        return new SupervisionService(machineManagerService);
    }

    @Bean
    public AlarmService alarmService(SimpMessagingTemplate messagingTemplate) {
        return new AlarmService(messagingTemplate);
    }

    @Bean
    public ReportingService reportingService(EntityManagerFactory entityManagerFactory) {
        return new ReportingService(entityManagerFactory);
    }
}
