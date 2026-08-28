package org.leavesmc.leaves.util;

import com.destroystokyo.paper.PaperVersionFetcher;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.util.JarManifests;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.apache.logging.log4j.LogManager;
import org.bukkit.craftbukkit.CraftServer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.jar.Manifest;
import java.util.stream.StreamSupport;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.TextColor.color;

public class LeavesVersionFetcher extends PaperVersionFetcher {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final ComponentLogger COMPONENT_LOGGER = ComponentLogger.logger(LogManager.getRootLogger().getName());

    private static final int DISTANCE_ERROR = -1;
    private static final int DISTANCE_UNKNOWN = -2;
    private static final String DEFAULT_REPO = "LeavesMC/Leaves";
    private static final String DEFAULT_DOWNLOAD_PAGE = "https://leavesmc.org/downloads/leaves";

    @NotNull
    @Override
    public Component getVersionMessage() {
        final Component updateMessage;
        final ServerBuildInfo build = ServerBuildInfo.buildInfo();
        if (build.buildNumber().isEmpty() && build.gitCommit().isEmpty()) {
            updateMessage = text("You are running a development version without access to version information", color(0xFF5300));
        } else if (build.buildNumber().isEmpty()) {
            updateMessage = text("You are running a development build (no release build number). This is expected for local or PR CI jars.", color(0xFF5300));
        } else {
            updateMessage = getUpdateStatusMessage(githubRepo(), build);
        }
        final @Nullable Component history = this.getHistory();

        return history != null ? Component.textOfChildren(updateMessage, Component.newline(), history) : updateMessage;
    }

    public static void logStartupVersionStatus() {
        final ServerBuildInfo build = ServerBuildInfo.buildInfo();
        if (build.buildNumber().isEmpty()) {
            COMPONENT_LOGGER.info(text("This is a development Leaves build without a release number; skipping official version lookup."));
            return;
        }

        int distance = fetchDistanceFromLeavesApiV2Build(build);
        if (distance == DISTANCE_ERROR) {
            distance = fetchDistanceFromLeavesApiV2Hash(build);
        }
        if (distance == DISTANCE_ERROR) {
            final Optional<String> gitBranch = build.gitBranch();
            final Optional<String> gitCommit = build.gitCommit();
            if (gitBranch.isPresent() && gitCommit.isPresent() && !"HEAD".equals(gitBranch.get())) {
                distance = fetchDistanceFromGitHub(githubRepo(), gitBranch.get(), gitCommit.get());
            }
        }

        switch (distance) {
            case DISTANCE_ERROR -> COMPONENT_LOGGER.info(text("Could not query Leaves version metadata (this fork may not publish to api.leavesmc.org)."));
            case 0 -> COMPONENT_LOGGER.info(text("You are running the latest Leaves build for this version."));
            case DISTANCE_UNKNOWN -> COMPONENT_LOGGER.info(text("Could not match this commit against the configured GitHub repository."));
            default -> COMPONENT_LOGGER.info(text("This Leaves build is " + distance + " version(s) behind the latest recorded build."));
        }
    }

    private static Component getUpdateStatusMessage(@NotNull final String repo, @NotNull final ServerBuildInfo build) {
        int distance = fetchDistanceFromLeavesApiV2Build(build);

        if (distance == DISTANCE_ERROR) {
            distance = fetchDistanceFromLeavesApiV2Hash(build);
        }

        if (distance == DISTANCE_ERROR) {
            final Optional<String> gitBranch = build.gitBranch();
            final Optional<String> gitCommit = build.gitCommit();
            if (gitBranch.isPresent() && gitCommit.isPresent() && !"HEAD".equals(gitBranch.get())) {
                distance = fetchDistanceFromGitHub(repo, gitBranch.get(), gitCommit.get());
            }
        }

        final String downloadPage = downloadPage(repo);
        return switch (distance) {
            case DISTANCE_ERROR -> Component.text("Could not query official Leaves version metadata for this fork", NamedTextColor.YELLOW);
            case 0 -> Component.text("You are running the latest version", NamedTextColor.GREEN);
            case DISTANCE_UNKNOWN -> Component.text("Unknown version", NamedTextColor.YELLOW);
            default -> Component.text("You are " + distance + " version(s) behind", NamedTextColor.YELLOW)
                .append(Component.newline())
                .append(Component.text("Download the new version at: ")
                    .append(Component.text(downloadPage, NamedTextColor.GOLD)
                        .hoverEvent(Component.text("Click to open", NamedTextColor.WHITE))
                        .clickEvent(ClickEvent.openUrl(downloadPage))));
        };
    }

    private static int fetchDistanceFromLeavesApiV2Build(final ServerBuildInfo build) {
        OptionalInt buildNumber = build.buildNumber();
        if (buildNumber.isEmpty()) {
            return DISTANCE_ERROR;
        }

        try {
            try (final BufferedReader reader = Resources.asCharSource(
                URI.create("https://api.leavesmc.org/v2/projects/leaves/versions/" + build.minecraftVersionId()).toURL(),
                Charsets.UTF_8
            ).openBufferedStream()) {
                final JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                final JsonArray builds = json.getAsJsonArray("builds");
                final int latest = StreamSupport.stream(builds.spliterator(), false)
                    .mapToInt(JsonElement::getAsInt)
                    .max()
                    .orElseThrow();
                return latest - buildNumber.getAsInt();
            } catch (final JsonSyntaxException ex) {
                LOGGER.error("Error parsing json from Leaves's downloads API", ex);
                return DISTANCE_ERROR;
            }
        } catch (final IOException e) {
            LOGGER.debug("Leaves downloads API is unavailable for this version", e);
            return DISTANCE_ERROR;
        }
    }

    private static int fetchDistanceFromLeavesApiV2Hash(final ServerBuildInfo build) {
        if (build.gitCommit().isEmpty()) {
            return DISTANCE_ERROR;
        }

        try {
            try (BufferedReader reader = Resources.asCharSource(
                URI.create("https://api.leavesmc.org/v2/projects/leaves/versions/" + build.minecraftVersionId() + "/differ/" + build.gitCommit().get()).toURL(),
                Charsets.UTF_8
            ).openBufferedStream()) {
                return Integer.parseInt(reader.readLine());
            }
        } catch (IOException e) {
            LOGGER.debug("Leaves hash differ API is unavailable for this version", e);
            return DISTANCE_ERROR;
        }
    }

    static String githubRepo() {
        final Manifest manifest = JarManifests.manifest(CraftServer.class);
        if (manifest != null) {
            final String repo = manifest.getMainAttributes().getValue("Git-Repo");
            if (repo != null && !repo.isBlank()) {
                return repo;
            }
        }
        return DEFAULT_REPO;
    }

    private static String downloadPage(final String repo) {
        if (DEFAULT_REPO.equals(repo)) {
            return DEFAULT_DOWNLOAD_PAGE;
        }
        return "https://github.com/" + repo + "/releases";
    }
}
