const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  onDevicesUpdated: (callback) => ipcRenderer.on('devices-updated', (event, devices) => callback(devices)),
  sendSignal: (msg, peerIP, peerPort) => {
    // 通过IPC让主进程建立TCP连接并发信令
    ipcRenderer.send('send-signal', { msg, peerIP, peerPort });
  },
  onSignal: (callback) => ipcRenderer.on('webrtc-signal', (event, msg) => callback(msg)),
});
