const { app, BrowserWindow, shell } = require('electron');
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
const backendPort = Number(process.env.BTS_BACKEND_PORT || 8081);
const frontendPort = Number(process.env.BTS_DESKTOP_PORT || 4311);

let backendProcess = null;
let staticServer = null;

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

  return new Promise((resolve) => {
    staticServer.listen(frontendPort, '127.0.0.1', resolve);
  });
}

function startBackend() {
  if (!fs.existsSync(backendJar)) {
    throw new Error(`Backend jar is missing: ${backendJar}`);
  }

  const env = {
    ...process.env,
    APP_REPO_DIR: resourcesRoot,
    SERVER_PORT: String(backendPort),
    DATA_DIR: path.join(app.getPath('userData'), 'data'),
    MEDIA_DIR: path.join(app.getPath('userData'), 'data', 'media'),
    CONTENT_PLAN_FILE: path.join(resourcesRoot, 'backend', 'config', 'content-plan.json'),
    PUBLIC_BASE_URL: `http://127.0.0.1:${frontendPort}`
  };

  backendProcess = childProcess.spawn('java', ['-jar', backendJar], {
    cwd: backendDir,
    env,
    stdio: 'ignore',
    windowsHide: true
  });
}

async function createWindow() {
  startBackend();
  await Promise.all([waitForPort(backendPort), startStaticServer()]);

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
  await win.loadURL(`http://127.0.0.1:${frontendPort}/playback`);
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
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
