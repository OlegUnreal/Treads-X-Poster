const { app, BrowserWindow, Menu, dialog, shell } = require('electron');
const childProcess = require('child_process');
const fs = require('fs');
const http = require('http');
const net = require('net');
const path = require('path');

const repoRoot = path.resolve(__dirname, '..', '..');
const resourcesRoot = app.isPackaged ? process.resourcesPath : repoRoot;
const backendDir = app.isPackaged ? path.join(resourcesRoot, 'backend') : path.join(repoRoot, 'backend');
const backendJar = app.isPackaged
  ? path.join(resourcesRoot, 'backend', 'app.jar')
  : path.join(backendDir, 'target', 'app.jar');
const frontendDist = app.isPackaged
  ? path.join(resourcesRoot, 'frontend')
  : path.join(__dirname, '..', 'dist', 'behind-the-smile-admin');
const frontendRoot = !app.isPackaged && fs.existsSync(path.join(frontendDist, 'browser'))
  ? path.join(frontendDist, 'browser')
  : frontendDist;
const bundledJava = path.join(resourcesRoot, 'runtime', 'java', 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
const bundledPython = path.join(resourcesRoot, 'runtime', 'python', process.platform === 'win32' ? 'python.exe' : 'python');
const backendPort = Number(process.env.BTS_BACKEND_PORT || 8081);
const frontendPort = Number(process.env.BTS_DESKTOP_PORT || 4311);
const profilesEnvSyncUrl = process.env.BTS_PROFILES_ENV_SYNC_URL || 'http://167.233.93.6:4301/api/actions/chrome-profiles/profiles-env';
const profilesEnvSyncToken = process.env.BTS_PROFILES_ENV_SYNC_TOKEN || '';
const profilesRuntimeDir = path.join(app.getPath('home'), 'chrome-proxy-profiles');
const profilesEnvFile = path.join(profilesRuntimeDir, 'profiles.env');
const appRuntimeRoot = path.join(profilesRuntimeDir, 'app');
const backendPidFile = path.join(profilesRuntimeDir, 'behind-the-smile-backend.pid');
const startupLogFile = path.join(profilesRuntimeDir, 'behind-the-smile-startup.log');
const backendStdoutLogFile = path.join(profilesRuntimeDir, 'behind-the-smile-backend.out.log');
const backendStderrLogFile = path.join(profilesRuntimeDir, 'behind-the-smile-backend.err.log');
const versionFile = path.join(__dirname, 'version.txt');
const currentDesktopVersion = fs.existsSync(versionFile) ? fs.readFileSync(versionFile, 'utf8').trim() : 'local';

let backendProcess = null;
let staticServer = null;
let mainWindow = null;
let profilesEnvWatcher = null;
let profilesEnvUploadTimer = null;
let profilesEnvLastUploaded = '';
let profilesEnvSyncing = false;

function waitForPort(port, timeoutMs = 60000) {
  const startedAt = Date.now();
  return new Promise((resolve, reject) => {
    const check = () => {
      const socket = net.connect(port, '127.0.0.1');
      socket.on('connect', () => {
        socket.destroy();
        resolve();
      });
      socket.on('error', () => {
        socket.destroy();
        if (Date.now() - startedAt > timeoutMs) {
          reject(new Error(`Timed out waiting for port ${port}`));
        } else {
          setTimeout(check, 600);
        }
      });
    };
    check();
  });
}

function waitForBackendPort(timeoutMs = 90000) {
  return new Promise((resolve, reject) => {
    let settled = false;
    const cleanup = () => {
      if (backendProcess) {
        backendProcess.off('exit', onExit);
        backendProcess.off('error', onError);
      }
    };
    const finish = (callback, value) => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      callback(value);
    };
    const onExit = (code, signal) => {
      finish(reject, new Error(`Backend exited before opening port ${backendPort}. Exit code: ${code ?? 'unknown'}, signal: ${signal || 'none'}.\n${startupDiagnostics()}`));
    };
    const onError = (error) => {
      finish(reject, new Error(`Backend process could not start: ${error.message}\n${startupDiagnostics()}`));
    };
    if (backendProcess) {
      backendProcess.once('exit', onExit);
      backendProcess.once('error', onError);
    }
    waitForPort(backendPort, timeoutMs)
      .then(() => finish(resolve))
      .catch((error) => finish(reject, new Error(`${error.message}\n${startupDiagnostics()}`)));
  });
}

function requestBackendJson(pathname, timeoutMs = 20000) {
  return new Promise((resolve, reject) => {
    const request = http.get({
      hostname: '127.0.0.1',
      port: backendPort,
      path: pathname,
      timeout: timeoutMs
    }, (response) => {
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.on('end', () => {
        const body = Buffer.concat(chunks).toString('utf8');
        if ((response.statusCode || 0) < 200 || (response.statusCode || 0) >= 300) {
          reject(new Error(`Backend returned HTTP ${response.statusCode}: ${body.slice(0, 200)}`));
          return;
        }
        try {
          resolve(JSON.parse(body));
        } catch (error) {
          reject(new Error(`Backend returned invalid JSON: ${body.slice(0, 200)}`));
        }
      });
    });
    request.on('timeout', () => request.destroy(new Error('Backend check timed out')));
    request.on('error', reject);
  });
}

async function verifyBackendRuntime() {
  const status = await requestBackendJson('/api/actions/chrome-profiles/runtime', 20000);
  const expectedRoot = path.normalize(appRuntimeRoot).toLowerCase();
  const launcherScript = path.normalize(status.script || '').toLowerCase();
  if (!launcherScript.startsWith(expectedRoot) || !status.scriptExists) {
    throw new Error(`Wrong backend is running. Expected launcher under ${appRuntimeRoot}, got ${status.script || 'unknown'}`);
  }
}

function loadingHtml() {
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Behind The Smile Playback</title>
  <style>
    body {
      margin: 0;
      min-height: 100vh;
      display: grid;
      place-items: center;
      background: #f4f6f8;
      color: #18212f;
      font-family: "Segoe UI", Arial, sans-serif;
    }
    .panel {
      width: min(560px, calc(100vw - 48px));
      background: #ffffff;
      border: 1px solid #dfe5ec;
      border-radius: 12px;
      box-shadow: 0 20px 55px rgba(24, 33, 47, 0.14);
      padding: 30px;
    }
    .top {
      display: flex;
      gap: 18px;
      align-items: center;
    }
    .spinner {
      width: 34px;
      height: 34px;
      border: 4px solid #dbeafe;
      border-top-color: #0d6efd;
      border-radius: 50%;
      animation: spin 0.9s linear infinite;
      flex: 0 0 auto;
    }
    h1 {
      margin: 0;
      font-size: 20px;
      line-height: 1.2;
      letter-spacing: 0;
    }
    .status {
      margin: 8px 0 0;
      color: #526173;
      font-size: 14px;
    }
    .log {
      margin-top: 22px;
      display: grid;
      gap: 8px;
      color: #6b7788;
      font-size: 13px;
      line-height: 1.35;
    }
    .log div::before {
      content: "";
      display: inline-block;
      width: 7px;
      height: 7px;
      margin-right: 9px;
      border-radius: 50%;
      background: #9db3c8;
    }
    .error {
      margin-top: 18px;
      padding: 12px;
      border-radius: 8px;
      background: #fff1f2;
      color: #be123c;
      white-space: pre-wrap;
      font-size: 13px;
      line-height: 1.4;
    }
    body.failed .spinner {
      display: none;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  </style>
</head>
<body>
  <main class="panel">
    <div class="top">
      <div class="spinner" aria-hidden="true"></div>
      <div>
        <h1>Behind The Smile Playback</h1>
        <p id="status" class="status">Opening app...</p>
      </div>
    </div>
    <div id="log" class="log"></div>
    <div id="error" class="error" hidden></div>
  </main>
  <script>
    const log = document.getElementById('log');
    const status = document.getElementById('status');
    const errorBox = document.getElementById('error');
    window.setStatus = (message) => {
      status.textContent = message;
      const row = document.createElement('div');
      row.textContent = message;
      log.prepend(row);
      while (log.children.length > 5) {
        log.removeChild(log.lastElementChild);
      }
    };
    window.setError = (message) => {
      document.body.classList.add('failed');
      status.textContent = 'Startup failed';
      errorBox.hidden = false;
      errorBox.textContent = message;
    };
  </script>
</body>
</html>`;
}

async function createMainWindow() {
  Menu.setApplicationMenu(null);
  mainWindow = new BrowserWindow({
    width: 1120,
    height: 820,
    minWidth: 920,
    minHeight: 680,
    title: 'Behind The Smile Playback',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  await mainWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(loadingHtml())}`);
}

function setStartupStatus(message) {
  appendStartupLog(message);
  if (!mainWindow || mainWindow.isDestroyed()) {
    return;
  }
  mainWindow.webContents.executeJavaScript(`window.setStatus && window.setStatus(${JSON.stringify(message)})`)
    .catch(() => {});
}

function showStartupError(error) {
  const message = error?.message || String(error);
  appendStartupLog(`Startup failed: ${message}`);
  if (!mainWindow || mainWindow.isDestroyed()) {
    dialog.showErrorBox('Behind The Smile Playback failed to start', message);
    app.quit();
    return;
  }
  mainWindow.webContents.executeJavaScript(`window.setError && window.setError(${JSON.stringify(message)})`)
    .catch(() => {
      dialog.showErrorBox('Behind The Smile Playback failed to start', message);
    });
}

function contentType(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  switch (ext) {
    case '.html': return 'text/html; charset=utf-8';
    case '.js': return 'text/javascript; charset=utf-8';
    case '.css': return 'text/css; charset=utf-8';
    case '.json': return 'application/json; charset=utf-8';
    case '.png': return 'image/png';
    case '.jpg':
    case '.jpeg': return 'image/jpeg';
    case '.svg': return 'image/svg+xml';
    case '.ico': return 'image/x-icon';
    default: return 'application/octet-stream';
  }
}

function proxyApi(req, res) {
  const options = {
    hostname: '127.0.0.1',
    port: backendPort,
    path: req.url,
    method: req.method,
    headers: req.headers
  };
  const proxy = http.request(options, (proxyRes) => {
    res.writeHead(proxyRes.statusCode || 502, proxyRes.headers);
    proxyRes.pipe(res);
  });
  proxy.on('error', (error) => {
    res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end(`Backend is not available: ${error.message}`);
  });
  req.pipe(proxy);
}

function getLatestWindowsRelease() {
  const https = require('https');
  return new Promise((resolve) => {
    const request = https.get('https://api.github.com/repos/OlegUnreal/Treads-X-Poster/releases/latest', {
      headers: {
        'User-Agent': 'BehindTheSmilePlayback'
      },
      timeout: 12000
    }, (response) => {
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.on('end', () => {
        try {
          const data = JSON.parse(Buffer.concat(chunks).toString('utf8'));
          const asset = Array.isArray(data.assets)
            ? data.assets.find((item) => item.name && item.name.startsWith('win-BehindTheSmilePlayback-'))
            : null;
          const latestVersion = String(data.tag_name || '').replace(/^win-/, '');
          resolve({
            currentVersion: currentDesktopVersion,
            latestVersion,
            updateAvailable: Boolean(latestVersion && currentDesktopVersion !== 'local' && latestVersion !== currentDesktopVersion),
            releaseUrl: data.html_url || '',
            downloadUrl: asset?.browser_download_url || '',
            error: ''
          });
        } catch (error) {
          resolve({
            currentVersion: currentDesktopVersion,
            latestVersion: '',
            updateAvailable: false,
            releaseUrl: '',
            downloadUrl: '',
            error: `Could not parse update response: ${error.message}`
          });
        }
      });
    });
    request.on('timeout', () => {
      request.destroy();
      resolve({
        currentVersion: currentDesktopVersion,
        latestVersion: '',
        updateAvailable: false,
        releaseUrl: '',
        downloadUrl: '',
        error: 'Update check timed out.'
      });
    });
    request.on('error', (error) => {
      resolve({
        currentVersion: currentDesktopVersion,
        latestVersion: '',
        updateAvailable: false,
        releaseUrl: '',
        downloadUrl: '',
        error: `Could not check updates: ${error.message}`
      });
    });
  });
}

function startStaticServer() {
  staticServer = http.createServer(async (req, res) => {
    if (req.url.startsWith('/api/')) {
      proxyApi(req, res);
      return;
    }
    if (req.url.startsWith('/desktop/update-status')) {
      const status = await getLatestWindowsRelease();
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(status));
      return;
    }

    const urlPath = decodeURIComponent(req.url.split('?')[0]);
    const requestedPath = path.normalize(path.join(frontendRoot, urlPath));
    let filePath = requestedPath.startsWith(frontendRoot) && fs.existsSync(requestedPath) && fs.statSync(requestedPath).isFile()
      ? requestedPath
      : path.join(frontendRoot, 'index.html');

    fs.readFile(filePath, (error, data) => {
      if (error) {
        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('Not found');
        return;
      }
      res.writeHead(200, { 'Content-Type': contentType(filePath) });
      res.end(data);
    });
  });

  return new Promise((resolve, reject) => {
    staticServer.on('error', reject);
    staticServer.listen(frontendPort, '127.0.0.1', resolve);
  });
}

function startBackend() {
  if (!fs.existsSync(backendJar)) {
    throw new Error(`Backend jar is missing: ${backendJar}`);
  }
  const javaExecutable = fs.existsSync(bundledJava) ? bundledJava : 'java';
  const runtimePathEntries = [];
  if (fs.existsSync(path.dirname(bundledJava))) {
    runtimePathEntries.push(path.dirname(bundledJava));
  }
  if (fs.existsSync(path.dirname(bundledPython))) {
    runtimePathEntries.push(path.dirname(bundledPython));
  }

  const env = {
    ...process.env,
    APP_REPO_DIR: appRuntimeRoot,
    APP_PYTHON_EXE: fs.existsSync(bundledPython) ? bundledPython : (process.env.APP_PYTHON_EXE || ''),
    SERVER_PORT: String(backendPort),
    PATH: [...runtimePathEntries, process.env.PATH || ''].filter(Boolean).join(path.delimiter),
    DATA_DIR: path.join(app.getPath('userData'), 'data'),
    MEDIA_DIR: path.join(app.getPath('userData'), 'data', 'media'),
    CONTENT_PLAN_FILE: path.join(resourcesRoot, 'backend', 'config', 'content-plan.json'),
    PUBLIC_BASE_URL: `http://127.0.0.1:${frontendPort}`
  };

  fs.mkdirSync(profilesRuntimeDir, { recursive: true });
  const stdoutFd = fs.openSync(backendStdoutLogFile, 'a');
  const stderrFd = fs.openSync(backendStderrLogFile, 'a');
  fs.appendFileSync(backendStdoutLogFile, `\n${new Date().toISOString()} Starting backend with ${javaExecutable}\n`, 'utf8');
  fs.appendFileSync(backendStderrLogFile, `\n${new Date().toISOString()} Starting backend with ${javaExecutable}\n`, 'utf8');

  backendProcess = childProcess.spawn(javaExecutable, ['-jar', backendJar], {
    cwd: backendDir,
    env,
    stdio: ['ignore', stdoutFd, stderrFd],
    windowsHide: true
  });
  backendProcess.on('exit', (code, signal) => {
    appendStartupLog(`Backend process exited. code=${code ?? 'unknown'} signal=${signal || 'none'}`);
  });
  backendProcess.on('error', (error) => {
    appendStartupLog(`Backend process error: ${error.message}`);
  });
  fs.mkdirSync(path.dirname(backendPidFile), { recursive: true });
  fs.writeFileSync(backendPidFile, String(backendProcess.pid), 'utf8');
}

function appendStartupLog(message) {
  try {
    fs.mkdirSync(path.dirname(startupLogFile), { recursive: true });
    fs.appendFileSync(startupLogFile, `${new Date().toISOString()} ${message}\n`, 'utf8');
  } catch {
    // Startup logging must never block the app.
  }
}

function readFileTail(filePath, maxBytes = 5000) {
  try {
    if (!fs.existsSync(filePath)) {
      return '';
    }
    const stat = fs.statSync(filePath);
    const start = Math.max(0, stat.size - maxBytes);
    const fd = fs.openSync(filePath, 'r');
    try {
      const buffer = Buffer.alloc(stat.size - start);
      fs.readSync(fd, buffer, 0, buffer.length, start);
      return buffer.toString('utf8').trim();
    } finally {
      fs.closeSync(fd);
    }
  } catch (error) {
    return `Could not read ${filePath}: ${error.message}`;
  }
}

function startupDiagnostics() {
  const details = [
    `Backend jar: ${backendJar} (${fs.existsSync(backendJar) ? 'exists' : 'missing'})`,
    `Bundled Java: ${bundledJava} (${fs.existsSync(bundledJava) ? 'exists' : 'missing'})`,
    `Backend port: ${backendPort}`,
    `Startup log: ${startupLogFile}`,
    `Backend stderr: ${backendStderrLogFile}`,
    readFileTail(backendStderrLogFile),
    `Backend stdout: ${backendStdoutLogFile}`,
    readFileTail(backendStdoutLogFile)
  ].filter(Boolean);
  return details.join('\n');
}

function killProcessTree(pid, reason) {
  const numericPid = Number(pid);
  if (!Number.isInteger(numericPid) || numericPid <= 0 || numericPid === process.pid) {
    return;
  }
  try {
    appendStartupLog(`Killing PID ${numericPid}: ${reason}`);
    childProcess.execFileSync('taskkill.exe', ['/PID', String(numericPid), '/F', '/T'], {
      stdio: 'ignore',
      windowsHide: true
    });
  } catch (error) {
    appendStartupLog(`Could not kill PID ${numericPid}: ${error.message}`);
  }
}

function killProcessesListeningOnPort(port) {
  if (process.platform !== 'win32') {
    return;
  }
  try {
    const output = childProcess.execFileSync('netstat.exe', ['-ano', '-p', 'tcp'], {
      encoding: 'utf8',
      windowsHide: true
    });
    for (const rawLine of output.split(/\r?\n/)) {
      const line = rawLine.trim();
      if (!line || !line.includes(`:${port}`)) {
        continue;
      }
      const parts = line.split(/\s+/);
      if (parts.length < 5 || !/^LISTENING$/i.test(parts[3])) {
        continue;
      }
      killProcessTree(Number(parts[4]), `listening on port ${port}`);
    }
  } catch (error) {
    appendStartupLog(`Could not inspect port ${port}: ${error.message}`);
  }
}

function stopOldWindowsProcesses() {
  if (!app.isPackaged || process.platform !== 'win32') {
    return;
  }
  killProcessesListeningOnPort(backendPort);
  killProcessesListeningOnPort(frontendPort);
  const script = `
$ErrorActionPreference = 'SilentlyContinue'
$currentAppPid = ${process.pid}
$ports = @(${backendPort}, ${frontendPort}) | Select-Object -Unique
foreach ($port in $ports) {
  Get-NetTCPConnection -LocalPort $port -State Listen |
    Where-Object { $_.OwningProcess -and $_.OwningProcess -ne $PID -and $_.OwningProcess -ne $currentAppPid } |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
  netstat -ano -p tcp |
    Select-String ":$port\\s+.*LISTENING\\s+(\\d+)$" |
    ForEach-Object {
      $processId = [int]$_.Matches[0].Groups[1].Value
      if ($processId -and $processId -ne $PID -and $processId -ne $currentAppPid) {
        Stop-Process -Id $processId -Force
      }
    }
}
$pidFile = '${backendPidFile.replace(/'/g, "''")}'
if (Test-Path -LiteralPath $pidFile) {
  $oldPid = 0
  [void][int]::TryParse((Get-Content -LiteralPath $pidFile -Raw).Trim(), [ref]$oldPid)
  if ($oldPid -gt 0 -and $oldPid -ne $PID -and $oldPid -ne $currentAppPid) {
    Stop-Process -Id $oldPid -Force
  }
}
Get-CimInstance Win32_Process -Filter "name = 'java.exe' or name = 'javaw.exe'" |
  Where-Object { $_.ProcessId -ne $PID -and $_.ProcessId -ne $currentAppPid -and ($_.CommandLine -match '[\\\\/]backend[\\\\/]app\\.jar' -or $_.CommandLine -match 'social-posting.*\\.jar') } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Start-Sleep -Milliseconds 700
`;
  try {
    childProcess.execFileSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', script], {
      stdio: 'ignore',
      windowsHide: true
    });
  } catch {
    // The runtime verification below catches stale backends even if cleanup is partial.
  }
}

async function startVerifiedBackend() {
  let lastError = null;
  for (let attempt = 0; attempt < 3; attempt++) {
    appendStartupLog(`Backend start attempt ${attempt + 1}`);
    setStartupStatus(`Stopping previous local services (${attempt + 1}/3)...`);
    stopOldWindowsProcesses();
    setStartupStatus('Starting local playback backend...');
    startBackend();
    setStartupStatus('Waiting for local backend...');
    await waitForBackendPort();
    try {
      setStartupStatus('Checking local backend...');
      await verifyBackendRuntime();
      return;
    } catch (error) {
      lastError = error;
      if (backendProcess) {
        backendProcess.kill();
        backendProcess = null;
      }
      killProcessesListeningOnPort(backendPort);
      stopOldWindowsProcesses();
    }
  }
  throw new Error(`${lastError?.message || 'Could not start verified local backend.'}\n${startupDiagnostics()}`);
}

function copyRequiredFile(source, destination) {
  if (!fs.existsSync(source)) {
    throw new Error(`Bundled runtime file is missing: ${source}`);
  }
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  copyFileIfChanged(source, destination);
}

function copyOptionalFile(source, destination) {
  if (!fs.existsSync(source)) {
    return;
  }
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  copyFileIfChanged(source, destination);
}

function copyFileIfChanged(source, destination) {
  if (fs.existsSync(destination)) {
    const sourceStat = fs.statSync(source);
    const destinationStat = fs.statSync(destination);
    if (sourceStat.size === destinationStat.size) {
      const sourceContent = fs.readFileSync(source);
      const destinationContent = fs.readFileSync(destination);
      if (sourceContent.equals(destinationContent)) {
        return;
      }
    }
  }
  fs.copyFileSync(source, destination);
}

function prepareAppRuntimeFiles() {
  const scriptsSource = path.join(resourcesRoot, 'scripts');
  const remoteProfilesSource = path.join(resourcesRoot, 'remote-chrome-profiles');
  const scriptsTarget = path.join(appRuntimeRoot, 'scripts');
  const remoteProfilesTarget = path.join(appRuntimeRoot, 'remote-chrome-profiles');

  copyRequiredFile(
    path.join(scriptsSource, 'start-local-chrome-profiles.ps1'),
    path.join(scriptsTarget, 'start-local-chrome-profiles.ps1')
  );
  copyRequiredFile(
    path.join(scriptsSource, 'start-profiles.ps1'),
    path.join(scriptsTarget, 'start-profiles.ps1')
  );
  copyRequiredFile(
    path.join(remoteProfilesSource, 'proxy-forwarder.py'),
    path.join(remoteProfilesTarget, 'proxy-forwarder.py')
  );
  copyOptionalFile(
    path.join(remoteProfilesSource, 'import-webshare-proxies.py'),
    path.join(remoteProfilesTarget, 'import-webshare-proxies.py')
  );
  copyOptionalFile(
    path.join(remoteProfilesSource, 'profiles.env.example'),
    path.join(remoteProfilesTarget, 'profiles.env.example')
  );
}

function requestText(url, headers = {}) {
  const client = url.startsWith('https://') ? require('https') : require('http');
  return new Promise((resolve, reject) => {
    const request = client.get(url, { headers }, (response) => {
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.on('end', () => {
        const body = Buffer.concat(chunks).toString('utf8');
        if ((response.statusCode || 0) < 200 || (response.statusCode || 0) >= 300) {
          reject(new Error(`profiles.env sync returned HTTP ${response.statusCode}: ${body.slice(0, 160)}`));
          return;
        }
        resolve(body);
      });
    });
    request.setTimeout(20000, () => {
      request.destroy(new Error('profiles.env sync timed out'));
    });
    request.on('error', reject);
  });
}

function sendText(url, content, headers = {}) {
  const client = url.startsWith('https://') ? require('https') : require('http');
  return new Promise((resolve, reject) => {
    const request = client.request(url, {
      method: 'PUT',
      headers: {
        'Content-Type': 'text/plain; charset=utf-8',
        'Content-Length': Buffer.byteLength(content, 'utf8'),
        ...headers
      }
    }, (response) => {
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.on('end', () => {
        const body = Buffer.concat(chunks).toString('utf8');
        if ((response.statusCode || 0) < 200 || (response.statusCode || 0) >= 300) {
          reject(new Error(`profiles.env upload returned HTTP ${response.statusCode}: ${body.slice(0, 160)}`));
          return;
        }
        resolve(body);
      });
    });
    request.setTimeout(20000, () => {
      request.destroy(new Error('profiles.env upload timed out'));
    });
    request.on('error', reject);
    request.write(content);
    request.end();
  });
}

function validateProfilesEnv(content) {
  if (!content || !content.includes('PROFILE_NAMES=')) {
    throw new Error('Downloaded profiles.env does not contain PROFILE_NAMES');
  }
  if (!content.includes('UPSTREAM_PROXY_') && !content.includes('PROXY_')) {
    throw new Error('Downloaded profiles.env does not contain proxy settings');
  }
}

async function syncProfilesEnv() {
  if (!profilesEnvSyncUrl) {
    return;
  }
  const headers = profilesEnvSyncToken ? { 'X-Profiles-Env-Token': profilesEnvSyncToken } : {};
  try {
    const content = await requestText(profilesEnvSyncUrl, headers);
    validateProfilesEnv(content);
    profilesEnvSyncing = true;
    fs.mkdirSync(profilesRuntimeDir, { recursive: true });
    fs.writeFileSync(profilesEnvFile, content.replace(/\r?\n/g, '\r\n'), 'utf8');
    profilesEnvLastUploaded = fs.readFileSync(profilesEnvFile, 'utf8');
  } catch (error) {
    if (!fs.existsSync(profilesEnvFile)) {
      throw error;
    }
    console.warn(`profiles.env sync failed, using existing local copy: ${error.message}`);
  } finally {
    setTimeout(() => {
      profilesEnvSyncing = false;
    }, 1000);
  }
}

async function uploadProfilesEnv() {
  if (!profilesEnvSyncUrl || !fs.existsSync(profilesEnvFile)) {
    return;
  }
  try {
    const content = fs.readFileSync(profilesEnvFile, 'utf8');
    validateProfilesEnv(content);
    if (content === profilesEnvLastUploaded) {
      return;
    }
    const headers = profilesEnvSyncToken ? { 'X-Profiles-Env-Token': profilesEnvSyncToken } : {};
    await sendText(profilesEnvSyncUrl, content, headers);
    profilesEnvLastUploaded = content;
  } catch (error) {
    console.warn(`profiles.env upload failed: ${error.message}`);
  }
}

function startProfilesEnvWatcher() {
  if (!profilesEnvSyncUrl || profilesEnvWatcher || !fs.existsSync(profilesEnvFile)) {
    return;
  }
  profilesEnvLastUploaded = fs.readFileSync(profilesEnvFile, 'utf8');
  profilesEnvWatcher = fs.watch(profilesEnvFile, () => {
    if (profilesEnvSyncing) {
      return;
    }
    clearTimeout(profilesEnvUploadTimer);
    profilesEnvUploadTimer = setTimeout(uploadProfilesEnv, 2500);
  });
  profilesEnvWatcher.on('error', (error) => {
    console.warn(`profiles.env watcher failed: ${error.message}`);
    profilesEnvWatcher = null;
  });
}

function stopProfilesEnvWatcher() {
  if (profilesEnvUploadTimer) {
    clearTimeout(profilesEnvUploadTimer);
    profilesEnvUploadTimer = null;
  }
  if (profilesEnvWatcher) {
    profilesEnvWatcher.close();
    profilesEnvWatcher = null;
  }
}

async function createWindow() {
  await createMainWindow();
  setStartupStatus('Syncing profile config...');
  await syncProfilesEnv();
  setStartupStatus('Watching local config changes...');
  startProfilesEnvWatcher();
  setStartupStatus('Preparing local runtime files...');
  prepareAppRuntimeFiles();
  setStartupStatus('Starting local services...');
  await startVerifiedBackend();
  setStartupStatus('Starting app window server...');
  await startStaticServer();
  setStartupStatus('Opening playback screen...');
  await mainWindow.loadURL(`http://127.0.0.1:${frontendPort}/playback?desktop=1`);
}

const hasSingleInstanceLock = app.requestSingleInstanceLock();
if (!hasSingleInstanceLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!mainWindow || mainWindow.isDestroyed()) {
      return;
    }
    if (mainWindow.isMinimized()) {
      mainWindow.restore();
    }
    mainWindow.focus();
    setStartupStatus('App is already starting or running.');
  });

  app.whenReady().then(createWindow).catch(showStartupError);
}

app.on('window-all-closed', () => {
  stopProfilesEnvWatcher();
  if (backendProcess) {
    backendProcess.kill();
    backendProcess = null;
  }
  if (staticServer) {
    staticServer.close();
    staticServer = null;
  }
  app.quit();
});
