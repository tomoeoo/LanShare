const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const net = require('net');
const mdns = require('multicast-dns');
const os = require('os');
// const robot = require('robotjs');   // 暂时禁用远程控制
const path = require('path');

let mainWindow;
let tcpServer;
const deviceName = `${os.hostname()} (Windows)`;
const SERVICE_TYPE = '_lanshare._tcp.local';
let mdnsService;
let onlineDevices = new Map();

function getLocalIP() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) return iface.address;
    }
  }
  return '127.0.0.1';
}

// 一次性授权引导
function requestPermission() {
  const result = dialog.showMessageBoxSync(mainWindow, {
    type: 'question',
    buttons: ['同意并继续', '退出'],
    title: '最终用户许可',
    message: '本软件启动后将允许局域网内其他设备直接查看和控制您的屏幕，是否同意？'
  });
  if (result === 1) app.quit();
}

// mDNS 发现
function startMDNS(port) {
  const mdnsClient = mdns();
  const localIP = getLocalIP();

  mdnsClient.on('query', (query) => {
    if (query.questions.some(q => q.name === SERVICE_TYPE)) {
      mdnsClient.respond({
        answers: [{
          name: SERVICE_TYPE,
          type: 'PTR',
          data: `${deviceName}.${SERVICE_TYPE}`,
          ttl: 300
        }, {
          name: `${deviceName}.${SERVICE_TYPE}`,
          type: 'SRV',
          data: { port: port, target: deviceName },
          ttl: 300
        }, {
          name: deviceName,
          type: 'A',
          data: localIP,
          ttl: 300
        }]
      });
    }
  });

  mdnsClient.on('response', (res) => {
    const devices = {};
    for (const answer of res.answers) {
      if (answer.type === 'SRV' && answer.name !== `${deviceName}.${SERVICE_TYPE}`) {
        devices[answer.name] = { name: answer.name, port: answer.data.port, ip: null };
      }
      if (answer.type === 'A' && devices[answer.name]) {
        devices[answer.name].ip = answer.data;
      }
    }
    onlineDevices = new Map(Object.entries(devices));
    mainWindow?.webContents.send('devices-updated', Array.from(onlineDevices.values()));
  });

  setInterval(() => mdnsClient.query(SERVICE_TYPE, 'PTR'), 5000);
  return mdnsClient;
}

// TCP 信令服务器
function startSignalingServer() {
  tcpServer = net.createServer((socket) => {
    let remoteName = '';

    socket.on('data', (data) => {
      try {
        const msg = JSON.parse(data.toString());
        if (msg.type === 'hello') {
          remoteName = msg.name;
          // 无确认直接应答
          socket.write(JSON.stringify({ type: 'accepted', from: deviceName }));
          mainWindow.webContents.send('webrtc-signal', { type: 'peer-joined', name: remoteName, socketId: socket.remotePort });
        } else if (msg.type === 'offer' || msg.type === 'answer' || msg.type === 'candidate') {
          mainWindow.webContents.send('webrtc-signal', { ...msg, peerId: remoteName });
        } else if (msg.type === 'control') {
          executeControl(msg.command);
        }
      } catch (e) { }
    });

    socket.on('close', () => {
      mainWindow.webContents.send('webrtc-signal', { type: 'peer-left', name: remoteName });
    });
  });

  return new Promise((resolve) => {
    tcpServer.listen(0, () => {
      resolve(tcpServer.address().port);
    });
  });
}

// 远程控制执行（暂时禁用 robotjs 调用）
function executeControl(cmd) {
  try {
    switch (cmd.type) {
      case 'mousemove': // robot.moveMouse(cmd.x, cmd.y); break;
      case 'mousedown': // robot.mouseToggle('down', cmd.button || 'left'); break;
      case 'mouseup':   // robot.mouseToggle('up', cmd.button || 'left'); break;
      case 'keydown':   // robot.keyToggle(cmd.key, 'down'); break;
      case 'keyup':     // robot.keyToggle(cmd.key, 'up'); break;
    }
  } catch (e) { console.error('控制执行出错:', e); }
}

// 发送信令到指定对端（通过TCP）
ipcMain.on('send-signal', (event, { peerId, msg }) => {
  const device = onlineDevices.get(peerId);
  if (!device) return;
  const client = net.connect({ port: device.port, host: device.ip }, () => {
    client.write(JSON.stringify(msg));
    client.end();
  });
});

app.whenReady().then(async () => {
  mainWindow = new BrowserWindow({
    width: 800,
    height: 600,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  await mainWindow.loadFile('renderer/index.html');
  requestPermission();
  const port = await startSignalingServer();
  startMDNS(port);
  console.log(`信令服务运行在端口 ${port}`);
});
