package com.composerai.api.service.email;

import com.composerai.api.model.EmailMessage;
import com.composerai.api.service.EmailParsingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DataDirectoryEmailMessageProvider implements EmailMessageProvider {

    private final EmailParsingService emailParsingService;
    private final Path inboxDirectory;

    public DataDirectoryEmailMessageProvider(
        EmailParsingService emailParsingService,
        @Value("${app.email-inbox.directory:data/eml}") String inboxDirectory
    ) {
        this.emailParsingService = emailParsingService;
        this.inboxDirectory = Path.of(inboxDirectory).toAbsolutePath().normalize();
    }

    @Override
    public List<EmailMessage> loadEmails() {
        if (!Files.exists(inboxDirectory) || !Files.isDirectory(inboxDirectory)) {
            log.debug("Email inbox directory does not exist: {}", inboxDirectory);
            return List.of();
        }

        try (var stream = Files.list(inboxDirectory)) {
            return stream
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    return name.endsWith(".eml") || name.endsWith(".txt");
                })
                .sorted(byLastModifiedDescending())
                .map(this::parseSafely)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to load email messages from directory: {}", inboxDirectory, e);
            return List.of();
        }
    }

    private Comparator<Path> byLastModifiedDescending() {
        return (a, b) -> {
            try {
                FileTime timeA = Files.getLastModifiedTime(a);
                FileTime timeB = Files.getLastModifiedTime(b);
                return timeB.compareTo(timeA);
            } catch (Exception e) {
                return 0;
            }
        };
    }

    private EmailMessage parseSafely(Path path) {
        try {
            EmailParsingService.ParsedEmail parsed = emailParsingService.parseEmail(path, path.getFileName().toString());
            return parsed.toEmailMessage();
        } catch (Exception e) {
            log.warn("Failed to parse email file: {}", path, e);
            return null;
        }
    }
}
