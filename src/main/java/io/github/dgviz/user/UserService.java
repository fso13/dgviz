package io.github.dgviz.user;

import io.github.dgviz.mail.MailTemplateService;
import io.github.dgviz.notifications.NotificationsClient;
import io.github.dgviz.settings.AppSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository userRepository;
    private final UserInviteRepository inviteRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppSettingsService settingsService;
    private final NotificationsClient notificationsClient;
    private final MailTemplateService mailTemplates;

    public UserService(
            AppUserRepository userRepository,
            UserInviteRepository inviteRepository,
            PasswordEncoder passwordEncoder,
            AppSettingsService settingsService,
            NotificationsClient notificationsClient,
            MailTemplateService mailTemplates
    ) {
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.passwordEncoder = passwordEncoder;
        this.settingsService = settingsService;
        this.notificationsClient = notificationsClient;
        this.mailTemplates = mailTemplates;
    }

    public List<AppUser> findAll() {
        return userRepository.findAll();
    }

    public AppUser getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    /**
     * Creates a disabled user and emails an invite link to set the password.
     */
    @Transactional
    public InviteResult createWithInvite(
            String username,
            String displayName,
            String email,
            UserRole role,
            boolean canManageProjects,
            boolean canManageRepositories,
            boolean canManageGroups
    ) {
        String normalizedEmail = requireEmail(email);
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Email already used: " + normalizedEmail);
        }

        AppUser user = new AppUser();
        user.setUsername(username.trim());
        user.setDisplayName(displayName.trim());
        user.setEmail(normalizedEmail);
        user.setRole(role == null ? UserRole.USER : role);
        // Unusable random password until invite is accepted.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(false);
        applyPermissions(user, canManageProjects, canManageRepositories, canManageGroups);
        user = userRepository.save(user);

        UserInvite invite = createInvite(user, normalizedEmail);
        boolean emailSent = trySendInviteEmail(user, invite);
        return new InviteResult(user, invite, inviteLink(invite), emailSent);
    }

    @Transactional
    public InviteResult resendInvite(Long userId) {
        AppUser user = getById(userId);
        String email = requireEmail(user.getEmail());
        if (user.isEnabled()) {
            throw new IllegalArgumentException("User already activated; password is already set");
        }
        inviteRepository.invalidateOpenInvites(userId);
        UserInvite invite = createInvite(user, email);
        boolean emailSent = trySendInviteEmail(user, invite);
        return new InviteResult(user, invite, inviteLink(invite), emailSent);
    }

    @Transactional
    public AppUser update(
            Long id,
            String displayName,
            String email,
            UserRole role,
            boolean enabled,
            String rawPassword,
            boolean canManageProjects,
            boolean canManageRepositories,
            boolean canManageGroups
    ) {
        AppUser user = getById(id);
        String normalizedEmail = email == null || email.isBlank() ? null : email.trim();
        if (normalizedEmail != null) {
            userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalArgumentException("Email already used: " + normalizedEmail);
                }
            });
        }
        user.setDisplayName(displayName);
        user.setEmail(normalizedEmail);
        user.setRole(role);
        user.setEnabled(enabled);
        if (rawPassword != null && !rawPassword.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
        }
        applyPermissions(user, canManageProjects, canManageRepositories, canManageGroups);
        return userRepository.save(user);
    }

    public UserInvite requireActiveInvite(String token) {
        UserInvite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invite link is invalid"));
        if (invite.isUsed()) {
            throw new IllegalArgumentException("Invite link was already used");
        }
        if (invite.isExpired()) {
            throw new IllegalArgumentException("Invite link has expired");
        }
        return invite;
    }

    @Transactional
    public AppUser acceptInvite(String token, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank() || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        UserInvite invite = requireActiveInvite(token);
        AppUser user = invite.getUser();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setEnabled(true);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            user.setEmail(invite.getEmail());
        }
        invite.setUsedAt(Instant.now());
        inviteRepository.save(invite);
        log.info("User {} accepted invite and set password", user.getUsername());
        return userRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    private UserInvite createInvite(AppUser user, String email) {
        int ttlDays = settingsService.getInviteTtlDays();
        UserInvite invite = new UserInvite();
        invite.setUser(user);
        invite.setEmail(email);
        invite.setToken(newToken());
        invite.setExpiresAt(Instant.now().plus(ttlDays, ChronoUnit.DAYS));
        invite.setCreatedAt(Instant.now());
        return inviteRepository.save(invite);
    }

    private boolean trySendInviteEmail(AppUser user, UserInvite invite) {
        String link = inviteLink(invite);
        int ttlDays = settingsService.getInviteTtlDays();
        String subject = "Приглашение в DGViz";
        String textBody = """
                Здравствуйте, %s!

                Вас пригласили в DGViz как пользователя '%s'.
                Откройте ссылку, чтобы задать пароль (действует %d дн.):

                %s

                Если вы не ожидали это письмо — просто проигнорируйте его.
                """.formatted(user.getDisplayName(), user.getUsername(), ttlDays, link);

        String htmlBody = mailTemplates.render("mail/invite", Map.of(
                "subject", subject,
                "title", "Приглашение в DGViz",
                "generatedAt", mailTemplates.formatInstant(Instant.now()),
                "recipientName", user.getDisplayName(),
                "intro", "Вас пригласили в систему анализа зависимостей DGViz. Задайте пароль по кнопке ниже — и можно входить.",
                "paragraphs", List.of(
                        "Ссылка действует " + ttlDays + " "
                                + (ttlDays == 1 ? "день" : (ttlDays >= 2 && ttlDays <= 4 ? "дня" : "дней"))
                                + ". После истечения срока администратор сможет отправить приглашение повторно."
                ),
                "details", List.of(
                        new MailTemplateService.Detail("Логин", user.getUsername()),
                        new MailTemplateService.Detail("Email", invite.getEmail()),
                        new MailTemplateService.Detail("Срок ссылки", ttlDays + " дн.")
                ),
                "ctaUrl", link,
                "ctaLabel", "Задать пароль"
        ));

        try {
            var results = notificationsClient.sendHtmlEmail(
                    subject, htmlBody, textBody, List.of(invite.getEmail()), List.of());
            log.info("Invite email for user {} results={}", user.getUsername(), results);
            return results.stream().anyMatch(r -> "SENT".equalsIgnoreCase(r.status()));
        } catch (Exception ex) {
            log.error("Failed to send invite email to {}", invite.getEmail(), ex);
            return false;
        }
    }

    private String inviteLink(UserInvite invite) {
        return settingsService.getInvitePublicBaseUrl() + "/invite/" + invite.getToken();
    }

    private static String requireEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Valid email is required for invitation");
        }
        return email.trim();
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static void applyPermissions(
            AppUser user,
            boolean canManageProjects,
            boolean canManageRepositories,
            boolean canManageGroups
    ) {
        if (user.getRole() == UserRole.ADMIN) {
            user.setCanManageProjects(true);
            user.setCanManageRepositories(true);
            user.setCanManageGroups(true);
            return;
        }
        user.setCanManageProjects(canManageProjects);
        user.setCanManageRepositories(canManageRepositories);
        user.setCanManageGroups(canManageGroups);
    }

    public record InviteResult(AppUser user, UserInvite invite, String inviteLink, boolean emailSent) {
    }
}
