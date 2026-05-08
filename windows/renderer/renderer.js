const deviceListEl = document.getElementById('device-list');
const pushBtn = document.getElementById('push-btn');
const remoteContainer = document.getElementById('remote-container');
const remoteVideo = document.getElementById('remote-video');
let localStream, peerConnection, selectedDevice;
const signalQueue = new Map(); // 简单信令队列

window.electronAPI.onDevicesUpdated((devices) => {
  deviceListEl.innerHTML = '';
  devices.forEach(d => {
    const li = document.createElement('li');
    li.textContent = d.name + ' (' + d.ip + ')';
    li.onclick = () => {
      selectedDevice = d;
      pushBtn.disabled = false;
    };
    deviceListEl.appendChild(li);
  });
});

window.electronAPI.onSignal(async (msg) => {
  if (msg.type === 'peer-joined') {
    console.log('peer joined:', msg.name);
  } else if (msg.type === 'offer') {
    await handleOffer(msg);
  } else if (msg.type === 'answer') {
    await peerConnection.setRemoteDescription(new RTCSessionDescription(msg.sdp));
  } else if (msg.type === 'candidate') {
    await peerConnection.addIceCandidate(new RTCIceCandidate(msg.candidate));
  }
});

pushBtn.onclick = async () => {
  if (!selectedDevice) return;
  // 开始拉流（查看对方屏幕）
  startView(selectedDevice);
};

async function startView(device) {
  remoteContainer.style.display = 'block';
  peerConnection = new RTCPeerConnection({ iceServers: [] });

  peerConnection.ontrack = (event) => {
    remoteVideo.srcObject = event.streams[0];
  };

  // 控制通道（数据通道）
  const controlChannel = peerConnection.createDataChannel('control');
  controlChannel.onopen = () => console.log('控制通道已打开');
  setupControlEvents(controlChannel, device.name);

  // 创建Offer并发给对方
  const offer = await peerConnection.createOffer();
  await peerConnection.setLocalDescription(offer);
  sendSignal(device.name, { type: 'offer', sdp: peerConnection.localDescription });

  peerConnection.onicecandidate = (e) => {
    if (e.candidate) sendSignal(device.name, { type: 'candidate', candidate: e.candidate });
  };
}

async function handleOffer(msg) {
  // 被查看端逻辑
  remoteContainer.style.display = 'block';
  peerConnection = new RTCPeerConnection({ iceServers: [] });
  peerConnection.ontrack = (e) => { remoteVideo.srcObject = e.streams[0]; };

  // 被控制端的数据通道监听
  peerConnection.ondatachannel = (event) => {
    const channel = event.channel;
    channel.onmessage = (e) => {
      const cmd = JSON.parse(e.data);
      // 直接通过信令发送给主进程执行控制（实际项目中应通过IPC）
      window.electronAPI.sendSignal(selectedDevice?.name, { type: 'control', command: cmd });
    };
  };

  // 启动屏幕捕获
  localStream = await navigator.mediaDevices.getDisplayMedia({ video: true });
  localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream));

  await peerConnection.setRemoteDescription(new RTCSessionDescription(msg.sdp));
  const answer = await peerConnection.createAnswer();
  await peerConnection.setLocalDescription(answer);
  sendSignal(msg.peerId, { type: 'answer', sdp: peerConnection.localDescription });

  peerConnection.onicecandidate = (e) => {
    if (e.candidate) sendSignal(msg.peerId, { type: 'candidate', candidate: e.candidate });
  };
}

function sendSignal(peerId, msg) {
  window.electronAPI.sendSignal(peerId, { ...msg, peerId: peerId });
}

function setupControlEvents(channel, peerId) {
  remoteVideo.addEventListener('mousemove', (e) => {
    const rect = remoteVideo.getBoundingClientRect();
    const scaleX = remoteVideo.videoWidth / rect.width;
    const scaleY = remoteVideo.videoHeight / rect.height;
    channel.send(JSON.stringify({
      type: 'mousemove',
      x: Math.round((e.clientX - rect.left) * scaleX),
      y: Math.round((e.clientY - rect.top) * scaleY)
    }));
  });
  remoteVideo.addEventListener('mousedown', (e) => {
    channel.send(JSON.stringify({ type: 'mousedown', button: 'left' }));
  });
  remoteVideo.addEventListener('mouseup', (e) => {
    channel.send(JSON.stringify({ type: 'mouseup', button: 'left' }));
  });
  // 键盘事件需焦点处理，此处略，可扩展
}
