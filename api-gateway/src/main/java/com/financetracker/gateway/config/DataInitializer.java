package com.financetracker.gateway.config;

import com.financetracker.gateway.model.User;
import com.financetracker.gateway.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = Logger.getLogger(DataInitializer.class.getName());
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Create default admin user if not exists
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@financetracker.com");
            admin.setFullName("System Administrator");
            admin.setRole("ADMIN");
            admin.setActive(true);
            admin.setVerified(true);
            
            userRepository.save(admin);
            logger.info("Default admin user created: username=admin, password=admin123");
        }
        
        // Create default regular user if not exists
        if (!userRepository.existsByUsername("user")) {
            User user = new User();
            user.setUsername("user");
            user.setPassword(passwordEncoder.encode("user123"));
            user.setEmail("user@financetracker.com");
            user.setFullName("Demo User");
            user.setRole("USER");
            user.setActive(true);
            user.setVerified(true);
            
            userRepository.save(user);
            logger.info("Default user created: username=user, password=user123");
        }
    }
}
