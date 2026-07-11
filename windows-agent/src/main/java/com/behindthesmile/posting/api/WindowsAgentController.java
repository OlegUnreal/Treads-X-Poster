package com.behindthesmile.posting.api;

import com.behindthesmile.posting.service.ChromeProfileLauncherService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class WindowsAgentController {
    private final ChromeProfileLauncherService chromeProfileLauncherService;

    public WindowsAgentController(ChromeProfileLauncherService chromeProfileLauncherService) {
        this.chromeProfileLauncherService = chromeProfileLauncherService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("app", "windows-agent");
        response.put("storageMode", "local-files");
        return response;
    }

    @PostMapping("/actions/chrome-profiles/start-all")
    public Map<String, Object> startAllChromeProfiles(@RequestBody(required = false) ChromeProfilesLaunchRequest request) throws Exception {
        return chromeProfileLauncherService.startAll(request);
    }

    @GetMapping("/actions/chrome-profiles/status")
    public Map<String, Object> chromeProfileStatus() throws Exception {
        return chromeProfileLauncherService.status();
    }

    @GetMapping("/actions/chrome-profiles/runtime")
    public Map<String, Object> chromeProfileRuntimeStatus() {
        return chromeProfileLauncherService.runtimeStatus();
    }

    @GetMapping(value = "/actions/chrome-profiles/profiles-env", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> chromeProfilesEnv() throws Exception {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(chromeProfileLauncherService.profilesEnvContent());
    }

    @PutMapping(value = "/actions/chrome-profiles/profiles-env", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Map<String, Object>> updateChromeProfilesEnv(@RequestBody String content) throws Exception {
        return ResponseEntity.ok(chromeProfileLauncherService.updateProfilesEnvContent(content));
    }

    @GetMapping(value = "/actions/chrome-profiles/proxy-capabilities", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> chromeProxyCapabilities() throws Exception {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(chromeProfileLauncherService.proxyCapabilitiesContent());
    }

    @PutMapping(value = "/actions/chrome-profiles/proxy-capabilities", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Map<String, Object>> updateChromeProxyCapabilities(@RequestBody String content) throws Exception {
        return ResponseEntity.ok(chromeProfileLauncherService.updateProxyCapabilitiesContent(content));
    }

    @PostMapping("/actions/chrome-profiles/check-url")
    public Map<String, Object> checkChromeProfilesUrl(@RequestBody(required = false) ChromeProfilesUrlCheckRequest request) throws Exception {
        return chromeProfileLauncherService.checkUrl(request);
    }

    @PostMapping("/actions/chrome-profiles/check-url/start")
    public Map<String, Object> startChromeProfilesUrlCheck(@RequestBody(required = false) ChromeProfilesUrlCheckRequest request) throws Exception {
        return chromeProfileLauncherService.startUrlCheck(request);
    }

    @GetMapping("/actions/chrome-profiles/check-url/status")
    public Map<String, Object> chromeProfilesUrlCheckStatus() {
        return chromeProfileLauncherService.currentUrlCheckStatus();
    }

    @PostMapping("/actions/chrome-profiles/bulk")
    public Map<String, Object> bulkChromeProfiles(@RequestBody(required = false) ChromeProfilesBulkActionRequest request) throws Exception {
        return chromeProfileLauncherService.bulkAction(request);
    }

    @PutMapping("/actions/chrome-profiles/{profileName}/login-status")
    public Map<String, Object> updateChromeProfileLoginStatus(
            @PathVariable String profileName,
            @RequestBody ChromeProfileLoginStatusRequest request
    ) throws Exception {
        return chromeProfileLauncherService.updateLoginStatus(profileName, request);
    }

    @PutMapping("/actions/chrome-profiles/{profileName}/proxy-capability")
    public Map<String, Object> updateChromeProfileProxyCapability(
            @PathVariable String profileName,
            @RequestBody ChromeProfileProxyCapabilityRequest request
    ) throws Exception {
        return chromeProfileLauncherService.updateProxyCapability(profileName, request);
    }

    @PostMapping("/actions/chrome-profiles/{profileName}/focus")
    public Map<String, Object> focusChromeProfile(@PathVariable String profileName) throws Exception {
        return chromeProfileLauncherService.focusProfile(profileName);
    }

    @PostMapping("/actions/chrome-profiles/{profileName}/close")
    public Map<String, Object> closeChromeProfile(@PathVariable String profileName) throws Exception {
        return chromeProfileLauncherService.closeProfile(profileName);
    }

    @PostMapping("/actions/chrome-profiles/{profileName}/restart")
    public Map<String, Object> restartChromeProfile(
            @PathVariable String profileName,
            @RequestBody(required = false) ChromeProfileActionRequest request
    ) throws Exception {
        return chromeProfileLauncherService.restartProfile(profileName, request);
    }

    @PostMapping("/actions/chrome-profiles/{profileName}/login")
    public Map<String, Object> openChromeProfileLogin(@PathVariable String profileName) throws Exception {
        return chromeProfileLauncherService.openLoginProfile(profileName);
    }
}
