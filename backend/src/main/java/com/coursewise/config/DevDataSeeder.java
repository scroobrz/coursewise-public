package com.coursewise.config;

import com.coursewise.auth.MagicLink;
import com.coursewise.auth.MagicLinkPurpose;
import com.coursewise.auth.MagicLinkRepository;
import com.coursewise.auth.TokenHasher;
import com.coursewise.auth.TrustedDevice;
import com.coursewise.auth.TrustedDeviceRepository;
import com.coursewise.auth.User;
import com.coursewise.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final MagicLinkRepository magicLinkRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppConfigProperties appConfig;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("[dev-seed] Data already exists — refreshing dev tokens only");
            refreshDevTokens();
        } else {
            log.info("[dev-seed] Seeding development data...");
            List<User> users = seedUsers();
            seedMagicLinks(users);
            seedTrustedDevices(users);
        }
        log.info("[dev-seed] ──────────────────────────────────────────");
        log.info("[dev-seed] Dev credentials (password: Password123):");
        log.info("[dev-seed]   Alice: s1234567@ed.ac.uk");
        log.info("[dev-seed]   Bob:   s7654321@ed.ac.uk");
        log.info("[dev-seed] Dev 2FA tokens (valid for 30 days):");
        log.info("[dev-seed]   Alice → {}/auth/verify-2fa?token=dev-s1234567", appConfig.frontendUrl());
        log.info("[dev-seed]   Bob   → {}/auth/verify-2fa?token=dev-s7654321", appConfig.frontendUrl());
        log.info("[dev-seed] ──────────────────────────────────────────");
    }

    private void refreshDevTokens() {
        OffsetDateTime far = OffsetDateTime.now().plusDays(30);
        magicLinkRepository.findByToken(TokenHasher.hash("dev-s1234567")).ifPresent(ml -> {
            ml.resetForDev(far);
            magicLinkRepository.save(ml);
        });
        magicLinkRepository.findByToken(TokenHasher.hash("dev-s7654321")).ifPresent(ml -> {
            ml.resetForDev(far);
            magicLinkRepository.save(ml);
        });
        log.info("[dev-seed] Dev tokens refreshed");
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    private List<User> seedUsers() {
        String hashedPassword = passwordEncoder.encode("Password123");

        User alice = new User("s1234567@ed.ac.uk", hashedPassword);
        alice.markEmailVerified();
        alice = userRepository.save(alice);

        User bob = new User("s7654321@ed.ac.uk", hashedPassword);
        bob.markEmailVerified();
        bob = userRepository.save(bob);

        log.info("[dev-seed] 2 users created (alice={}, bob={})", alice.getId(), bob.getId());
        return List.of(alice, bob);
    }

    // ── Magic links (long-lived dev tokens for instant login) ─────────────────

    private void seedMagicLinks(List<User> users) {
        User alice = users.get(0);
        User bob   = users.get(1);
        OffsetDateTime far = OffsetDateTime.now().plusDays(30);
        magicLinkRepository.saveAll(List.of(
            new MagicLink(alice.getEmail(), TokenHasher.hash("dev-s1234567"), MagicLinkPurpose.LOGIN_2FA, far),
            new MagicLink(bob.getEmail(),   TokenHasher.hash("dev-s7654321"), MagicLinkPurpose.LOGIN_2FA, far)
        ));
        log.info("[dev-seed] Magic link tokens seeded");
    }

    // ── Trusted devices (Alice has one so devs can test the bypass flow) ──────

    private void seedTrustedDevices(List<User> users) {
        User alice = users.get(0);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(appConfig.shared().trustedDeviceDays());
        TrustedDevice device = new TrustedDevice(
            alice, "dev-trusted-alice", "Dev Browser on macOS", expiresAt
        );
        trustedDeviceRepository.save(device);
        log.info("[dev-seed] Trusted device seeded for Alice");
    }

}
