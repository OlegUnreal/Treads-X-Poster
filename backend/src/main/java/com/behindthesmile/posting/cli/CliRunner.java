package com.behindthesmile.posting.cli;

import com.behindthesmile.posting.service.SocialPostingService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CliRunner implements CommandLineRunner {
    private final SocialPostingService socialPostingService;

    public CliRunner(SocialPostingService socialPostingService) {
        this.socialPostingService = socialPostingService;
    }

    @Override
    public void run(String... rawArgs) throws Exception {
        if (rawArgs.length == 0) {
            System.out.println("PostingApplication API is running. Use CLI arguments for command mode.");
            return;
        }

        String command = !rawArgs[0].startsWith("--") ? rawArgs[0] : "draft";
        String[] argsOnly = command.equals(rawArgs[0])
                ? java.util.Arrays.copyOfRange(rawArgs, 1, rawArgs.length)
                : rawArgs;
        Map<String, String> args = parseArgs(argsOnly);

        switch (command) {
            case "auto-create" -> System.out.println(socialPostingService.autoCreate(args));
            case "daily" -> System.out.println(socialPostingService.daily(args));
            case "publish-queued-x" -> System.out.println(socialPostingService.publishQueuedX(args));
            case "build-x-links" -> System.out.println(socialPostingService.buildXLinks(args));
            case "publish-queued-threads" -> System.out.println(socialPostingService.publishQueuedThreads(args));
            case "publish" -> System.out.println(socialPostingService.draft(args, true));
            case "draft" -> System.out.println(socialPostingService.draft(args, false));
            default -> throw new IllegalStateException("Unknown command: " + command);
        }
    }

    private Map<String, String> parseArgs(String[] rawArgs) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (int index = 0; index < rawArgs.length; index++) {
            String arg = rawArgs[index];
            if (!arg.startsWith("--")) {
                continue;
            }

            String trimmed = arg.substring(2);
            int equalsIndex = trimmed.indexOf('=');
            if (equalsIndex >= 0) {
                parsed.put(toCamelCase(trimmed.substring(0, equalsIndex)), trimmed.substring(equalsIndex + 1));
                continue;
            }

            String key = toCamelCase(trimmed);
            String nextValue = index + 1 < rawArgs.length ? rawArgs[index + 1] : null;
            if (nextValue != null && !nextValue.startsWith("--")) {
                parsed.put(key, nextValue);
                index++;
            } else {
                parsed.put(key, "true");
            }
        }
        return parsed;
    }

    private String toCamelCase(String value) {
        StringBuilder builder = new StringBuilder();
        boolean uppercaseNext = false;
        for (char current : value.toCharArray()) {
            if (current == '-') {
                uppercaseNext = true;
                continue;
            }
            builder.append(uppercaseNext ? Character.toUpperCase(current) : current);
            uppercaseNext = false;
        }
        return builder.toString();
    }
}
