package org.dobi.app.service;

import org.dobi.dto.CreateUserRequest;
import org.dobi.dto.UserDto;
import org.dobi.entities.UserAccount;
import org.dobi.entities.UserLoginData;
import org.dobi.entities.UserRole;
import org.dobi.app.repository.UserAccountRepository;
import org.dobi.app.repository.UserLoginDataRepository;
import org.dobi.app.repository.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate; // <-- AJOUT NÉCESSAIRE
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserAccountRepository userAccountRepository;
    private final UserLoginDataRepository userLoginDataRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserAccountRepository userAccountRepository,
            UserLoginDataRepository userLoginDataRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.userLoginDataRepository = userLoginDataRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserDto> findAllUsers() {
        return userAccountRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        if (userLoginDataRepository.findByLoginNameWithRoles(request.loginName()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        UserAccount userAccount = new UserAccount();
        userAccount.setFirstName(request.firstName());
        userAccount.setLastName(request.lastName());
        userAccount.setGenre("U"); // 'U' pour "Unknown"
        userAccount.setDateOfBirth(LocalDate.now()); // Date du jour par défaut

        UserLoginData loginData = new UserLoginData();
        loginData.setLoginName(request.loginName());
        loginData.setEmail(request.email());
        loginData.setPasswordHash(passwordEncoder.encode(request.password()));

        userAccount.setLoginData(loginData); // Lie les deux entités
        
        Set<UserRole> roles = userRoleRepository.findByNameIn(request.roles());
        userAccount.setRoles(roles);

        UserAccount savedUser = userAccountRepository.save(userAccount);
        return convertToDto(savedUser);
    }


    private UserDto convertToDto(UserAccount user) {
        return new UserDto(
                user.getId(),
                user.getLoginData().getLoginName(),
                user.getFirstName(),
                user.getLastName(),
                user.getLoginData().getEmail(),
                user.getRoles().stream().map(UserRole::getName).collect(Collectors.toSet())
        );
    }
}
