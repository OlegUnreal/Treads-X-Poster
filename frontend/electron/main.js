const { app, BrowserWindow, dialog, shell } = require('electron');
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
const profilesEnvSyncUrl = process.env.BTS_PROFILES_ENV_SYNC_URL || 'http://167.233.93.6/api/actions/chrome-profiles/profiles-env';
const profilesEnvSyncToken = process.env.BTS_PROFILES_ENV_SYNC_TOKEN || '';
const profilesRuntimeDir = path.join(app.getPath('home'), 'chrome-proxy-profiles');
const profilesEnvFile = path.join(profilesRuntimeDir, 'profiles.env');
const appRuntimeRoot = path.join(profilesRuntimeDir, 'app');
const backendPidFile = path.join(profilesRuntimeDir, 'behind-the-smile-backend.pid');

let backendProcess = null;
let staticServer = null;
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

function requestBackendJson(pathname) {
  return new Promise((resolve, reject) => {
    const request = http.get({
      hostname: '127.0.0.1',
      port: backendPort,
      path: pathname,
      timeout: 10000
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
  const status = await requestBackendJson('/api/actions/chrome-profiles/status');
  const expectedRoot = path.normalize(appRuntimeRoot).toLowerCase();
  const launcherScript = path.normalize(status.script || '').toLowerCase();
  if (!launcherScript.startsWith(expectedRoot) || !status.scriptExists) {
    throw new Error(`Wrong backend is running. Expected launcher under ${appRuntimeRoot}, got ${status.script || 'unknown'}`);
  }
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

function startStaticServer() {
  staticServer = http.createServer((req, res) => {
    if (req.url.startsWith('/api/')) {
      proxyApi(req, res);
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

  backendProcess = childProcess.spawn(javaExecutable, ['-jar', backendJar], {
    cwd: backendDir,
    env,
    stdio: 'ignore',
    windowsHide: true
  });
  fs.mkdirSync(path.dirname(backendPidFile), { recursive: true });
  fs.writeFileSync(backendPidFile, String(backendProcess.pid), 'utf8');
}

function stopOldWindowsProcesses() {
  if (!app.isPackaged || process.platform !== 'win32') {
    return;
  }
  const script = `
$ErrorActionPreference = 'SilentlyContinue'
$ports = @(${backendPort}, ${frontendPort}) | Select-Object -Unique
foreach ($port in $ports) {
  Get-NetTCPConnection -LocalPort $port -State Listen |
    Where-Object { $_.OwningProcess -and $_.OwningProcess -ne $PID } |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
  netstat -ano -p tcp |
    Select-String ":$port\\s+.*LISTENING\\s+(\\d+)$" |
    ForEach-Object {
      $processId = [int]$_.Matches[0].Groups[1].Value
      if ($processId -and $processId -ne $PID) {
        Stop-Process -Id $processId -Force
      }
    }
}
$pidFile = '${backendPidFile.replace(/'/g, "''")}'
if (Test-Path -LiteralPath $pidFile) {
  $oldPid = 0
  [void][int]::TryParse((Get-Content -LiteralPath $pidFile -Raw).Trim(), [ref]$oldPid)
  if ($oldPid -gt 0 -and $oldPid -ne $PID) {
    Stop-Process -Id $oldPid -Force
  }
}
Get-CimInstance Win32_Process -Filter "name = 'java.exe' or name = 'javaw.exe'" |
  Where-Object { $_.ProcessId -ne $PID -and ($_.CommandLine -match '[\\\\/]backend[\\\\/]app\\.jar' -or $_.CommandLine -match 'social-posting.*\\.jar') } |
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
  for (let attempt = 0; attempt < 2; attempt++) {
    stopOldWindowsProcesses();
    startBackend();
    await waitForPort(backendPort);
    try {
      await verifyBackendRuntime();
      return;
    } catch (error) {
      lastError = error;
      if (backendProcess) {
        backendProcess.kill();
        backendProcess = null;
      }
      stopOldWindowsProcesses();
    }
  }
  throw lastError || new Error('Could not start verified local backend.');
}

function copyRequiredFile(source, destination) {
  if (!fs.existsSync(source)) {
    throw new Error(`Bundled runtime file is missing: ${source}`);
  }
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.copyFileSync(source, destination);
}

function copyOptionalFile(source, destination) {
  if (!fs.existsSync(source)) {
    return;
  }
  fs.mkdirSync(path.dirname(destination), { recursive: true });
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
  await syncProfilesEnv();
  startProfilesEnvWatcher();
  prepareAppRuntimeFiles();
  await Promise.all([startVerifiedBackend(), startStaticServer()]);

  const win = new BrowserWindow({
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

  win.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });
  await win.loadURL(`http://127.0.0.1:${frontendPort}/playback?desktop=1`);
}

app.whenReady().then(createWindow).catch((error) => {
  dialog.showErrorBox('Behind The Smile Playback failed to start', error?.message || String(error));
  app.quit();
});

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
