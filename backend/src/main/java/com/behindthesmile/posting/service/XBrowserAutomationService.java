package com.behindthesmile.posting.service;

import com.behindthesmile.posting.config.AppProperties;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class XBrowserAutomationService {
    private static final Logger log = LoggerFactory.getLogger(XBrowserAutomationService.class);

    private final AccountConfigService accountConfigService;
    private final HttpService httpService;

    public XBrowserAutomationService(AccountConfigService accountConfigService, HttpService httpService) {
        this.accountConfigService = accountConfigService;
        this.httpService = httpService;
    }

    public Map<String, Object> publishToX(String text) throws Exception {
        return publishToX(text, null);
    }

    public Map<String, Object> publishToX(String text, String imageUrl) throws Exception {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Tweet text is required.");
        }

        Path downloadedImage = null;
        WebDriver driver = createDriver();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
            AppProperties.Account account = accountConfigService.activeAccount();
            log.info("X Selenium publish started. Account={}, Browser={}, headless={}",
                    account.id(),
                    account.x().browser(),
                    account.x().browserHeadless());
            driver.get("https://x.com/compose/post");
            log.info("Opened X compose page. Current URL={}", driver.getCurrentUrl());

            if (driver.getCurrentUrl().contains("login") || driver.getCurrentUrl().contains("flow")) {
                log.warn("X Selenium publish stopped because login is required. URL={}", driver.getCurrentUrl());
                throw new IllegalStateException(
                        "X login is required in the Selenium browser profile. Sign in once and retry."
                );
            }

            WebElement editor = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='tweetTextarea_0'], div[role='textbox']")
            ));
            log.info("Found X compose editor.");
            editor.click();
            editor.sendKeys(text);
            log.info("Inserted text into X compose editor. Length={}", text.length());

            if (imageUrl != null && !imageUrl.isBlank()) {
                downloadedImage = downloadImage(imageUrl);
                attachImage(driver, wait, downloadedImage);
                log.info("Attached image to X compose. Source={}", imageUrl);
            }

            WebElement postButton = waitForEnabledPostButton(wait);
            logButtonState(postButton, "before-click");
            waitForStableReadyState();
            clickPostButton(driver, wait, editor, postButton);
            log.info("Executed X post click flow.");

            waitForPostSubmission(wait);
            log.info("X Selenium publish confirmed successfully.");

            return Map.of(
                    "platform", "x",
                    "accountId", accountConfigService.activeAccount().id(),
                    "mode", "selenium",
                    "browser", accountConfigService.activeAccount().x().browser(),
                    "status", "posted",
                    "imageAttached", downloadedImage != null
            );
        } finally {
            driver.quit();
            cleanupTempFile(downloadedImage);
        }
    }

    public Map<String, Object> openLoginBrowser() throws Exception {
        WebDriver driver = createDriver(true);
        driver.get("https://x.com/home");
        return Map.of(
                "platform", "x",
                "accountId", accountConfigService.activeAccount().id(),
                "mode", "selenium-login",
                "browser", accountConfigService.activeAccount().x().browser(),
                "status", "opened",
                "message", "Selenium browser opened for X login. Keep it open and sign in there."
        );
    }

    private WebDriver createDriver() throws Exception {
        return createDriver(false);
    }

    private WebDriver createDriver(boolean detachBrowser) throws Exception {
        AppProperties.X x = accountConfigService.activeAccount().x();
        String browser = x.browser() == null ? "chrome" : x.browser().trim().toLowerCase();
        Path profilePath = Path.of(x.browserProfileDir()).toAbsolutePath().normalize();
        Files.createDirectories(profilePath);

        if ("edge".equals(browser)) {
            EdgeOptions options = new EdgeOptions();
            options.addArguments("--user-data-dir=" + profilePath);
            options.addArguments("--profile-directory=Default");
            options.addArguments("--start-maximized");
            options.addArguments("--disable-blink-features=AutomationControlled");
            if (detachBrowser) {
                options.setExperimentalOption("detach", true);
            }
            if (x.browserHeadless()) {
                options.addArguments("--headless=new");
            }
            return new EdgeDriver(options);
        }

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--user-data-dir=" + profilePath);
        options.addArguments("--profile-directory=Default");
        options.addArguments("--start-maximized");
        options.addArguments("--disable-blink-features=AutomationControlled");
        if (detachBrowser) {
            options.setExperimentalOption("detach", true);
        }
        if (x.browserHeadless()) {
            options.addArguments("--headless=new");
        }
        return new ChromeDriver(options);
    }

    private Path downloadImage(String imageUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                .header("User-Agent", "BehindTheSmileSocialPosting/1.0")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<InputStream> response = httpService.client()
                .send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Could not download image for X post. HTTP " + response.statusCode());
        }

        Path tempFile = Files.createTempFile("bts-x-upload-", guessExtension(response, imageUrl));
        try (InputStream body = response.body()) {
            Files.copy(body, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private void attachImage(WebDriver driver, WebDriverWait wait, Path imageFile) {
        WebElement fileInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("input[type='file'][accept*='image'], input[data-testid='fileInput']")
        ));
        ((JavascriptExecutor) driver).executeScript("arguments[0].style.display='block';", fileInput);
        fileInput.sendKeys(imageFile.toAbsolutePath().toString());
        waitForImagePreview(wait);
    }

    private void waitForImagePreview(WebDriverWait wait) {
        try {
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='attachments']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='tweetPhoto']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[aria-label*='Image'] img")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("img[src^='blob:']"))
            ));
        } catch (TimeoutException ex) {
            throw new IllegalStateException("X composer did not finish attaching the image.", ex);
        }
    }

    private void waitForStableReadyState() {
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for X composer to become ready.", ex);
        }
    }

    private void waitForPostSubmission(WebDriverWait wait) {
        try {
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("[data-testid='tweetTextarea_0']")),
                    ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("[data-testid='tweetButtonInline'], [data-testid='tweetButton']")),
                    ExpectedConditions.urlContains("/home"),
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='toast']"))
            ));
        } catch (TimeoutException ex) {
            log.warn("X Selenium publish could not confirm submission after click.");
            throw new IllegalStateException(
                    "The Selenium browser could not confirm tweet submission. Check whether X showed a login, challenge, or validation message.",
                    ex
            );
        }
    }

    private WebElement waitForEnabledPostButton(WebDriverWait wait) {
        List<By> buttonSelectors = List.of(
                By.cssSelector("[data-testid='tweetButtonInline']"),
                By.cssSelector("[data-testid='tweetButton']"),
                By.xpath("//button[@data-testid='tweetButtonInline' or @data-testid='tweetButton']"),
                By.xpath("//button[.//span[normalize-space()='Post']]"),
                By.xpath("//div[@role='button'][.//span[normalize-space()='Post']]")
        );

        TimeoutException lastException = null;
        for (By selector : buttonSelectors) {
            try {
                WebElement button = wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
                wait.until(driver -> isPostButtonEnabled(button));
                log.info("Found enabled X Post button using selector={}", selector);
                return button;
            } catch (TimeoutException ex) {
                lastException = ex;
            }
        }

        throw new IllegalStateException("Could not find an enabled X Post button in the composer.", lastException);
    }

    private void clickPostButton(WebDriver driver, WebDriverWait wait, WebElement editor, WebElement postButton) {
        try {
            log.info("Trying Selenium native click on X Post button.");
            wait.until(ExpectedConditions.elementToBeClickable(postButton)).click();
            return;
        } catch (Exception ex) {
            log.warn("Native click on X Post button failed: {}", ex.getMessage());
        }

        try {
            log.info("Trying Actions click on X Post button.");
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", postButton);
            new Actions(driver).moveToElement(postButton).pause(Duration.ofMillis(200)).click().perform();
            return;
        } catch (Exception ex) {
            log.warn("Actions click on X Post button failed: {}", ex.getMessage());
        }

        try {
            log.info("Trying Ctrl+Enter shortcut in X compose editor.");
            editor.sendKeys(Keys.chord(Keys.CONTROL, Keys.ENTER));
            return;
        } catch (Exception ex) {
            log.warn("Ctrl+Enter shortcut in X compose editor failed: {}", ex.getMessage());
        }

        try {
            log.info("Trying click on inner span inside X Post button.");
            WebElement innerSpan = postButton.findElement(By.xpath(".//span[normalize-space()='Post']"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", innerSpan);
            new Actions(driver).moveToElement(innerSpan).pause(Duration.ofMillis(200)).click().perform();
            return;
        } catch (NoSuchElementException ex) {
            log.warn("Inner span for X Post button was not found.");
        } catch (Exception ex) {
            log.warn("Inner span click for X Post button failed: {}", ex.getMessage());
        }

        try {
            log.info("Trying JavaScript click on X Post button.");
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", postButton);
            ((JavascriptExecutor) driver).executeScript("arguments[0].focus();", postButton);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", postButton);
        } catch (Exception ex) {
            log.error("All X Post button click strategies failed.");
            throw new IllegalStateException("Found the X Post button, but Selenium could not click it.", ex);
        }
    }

    private void logButtonState(WebElement postButton, String phase) {
        try {
            log.info(
                    "X Post button state [{}]: displayed={}, enabled={}, text='{}', class='{}', aria-disabled={}",
                    phase,
                    postButton.isDisplayed(),
                    postButton.isEnabled(),
                    postButton.getText(),
                    postButton.getAttribute("class"),
                    postButton.getAttribute("aria-disabled")
            );
        } catch (Exception ex) {
            log.warn("Could not log X Post button state [{}]: {}", phase, ex.getMessage());
        }
    }

    private boolean isPostButtonEnabled(WebElement postButton) {
        try {
            String ariaDisabled = postButton.getAttribute("aria-disabled");
            String disabled = postButton.getAttribute("disabled");
            String className = String.valueOf(postButton.getAttribute("class"));
            boolean enabled = postButton.isDisplayed()
                    && postButton.isEnabled()
                    && !"true".equalsIgnoreCase(ariaDisabled)
                    && disabled == null
                    && !className.toLowerCase().contains("disabled");
            if (!enabled) {
                log.info(
                        "X Post button not ready yet: displayed={}, enabled={}, aria-disabled={}, disabled={}, class={}",
                        postButton.isDisplayed(),
                        postButton.isEnabled(),
                        ariaDisabled,
                        disabled,
                        className
                );
            }
            return enabled;
        } catch (Exception ex) {
            log.warn("Failed while checking whether X Post button is enabled: {}", ex.getMessage());
            return false;
        }
    }

    private String guessExtension(HttpResponse<?> response, String imageUrl) {
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
        if (contentType.contains("png")) {
            return ".png";
        }
        if (contentType.contains("webp")) {
            return ".webp";
        }
        if (contentType.contains("gif")) {
            return ".gif";
        }
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return ".jpg";
        }

        String normalized = imageUrl == null ? "" : imageUrl.toLowerCase();
        if (normalized.contains(".png")) {
            return ".png";
        }
        if (normalized.contains(".webp")) {
            return ".webp";
        }
        if (normalized.contains(".gif")) {
            return ".gif";
        }
        return ".jpg";
    }

    private void cleanupTempFile(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("Could not delete temporary X upload image {}: {}", file, ex.getMessage());
        }
    }
}
