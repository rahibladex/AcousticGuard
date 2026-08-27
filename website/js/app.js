/**
 * NariShakti SOS - Interactive Web Application Logic
 * Implements real-time audio visualization, phone simulator,
 * Web Audio API siren synthesis, Fake Call & Safe Walk simulators.
 */

// ==========================================================================
// 1. Web Audio API Synthesis Engine (Alarm Siren, Beeps, Audio Context)
// ==========================================================================

class AudioSynthEngine {
  constructor() {
    this.ctx = null;
    this.isMuted = false;
    this.alarmOsc = null;
    this.alarmGain = null;
    this.alarmInterval = null;
  }

  init() {
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (AudioCtx) {
        this.ctx = new AudioCtx();
      }
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume();
    }
  }

  playCountdownBeep(freq = 880) {
    if (this.isMuted) return;
    this.init();
    if (!this.ctx) return;

    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(freq, this.ctx.currentTime);

    gain.gain.setValueAtTime(0.25, this.ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.18);

    osc.connect(gain);
    gain.connect(this.ctx.destination);

    osc.start();
    osc.stop(this.ctx.currentTime + 0.2);
  }

  startAlarmSiren() {
    if (this.isMuted) return;
    this.init();
    if (!this.ctx) return;

    this.stopAlarmSiren();

    let high = true;
    const playTone = () => {
      if (!this.ctx) return;
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();

      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(high ? 960 : 700, this.ctx.currentTime);
      gain.gain.setValueAtTime(0.3, this.ctx.currentTime);
      gain.gain.linearRampToValueAtTime(0.01, this.ctx.currentTime + 0.28);

      osc.connect(gain);
      gain.connect(this.ctx.destination);

      osc.start();
      osc.stop(this.ctx.currentTime + 0.3);
      high = !high;
    };

    playTone();
    this.alarmInterval = setInterval(playTone, 320);
  }

  stopAlarmSiren() {
    if (this.alarmInterval) {
      clearInterval(this.alarmInterval);
      this.alarmInterval = null;
    }
  }

  playPhoneRing() {
    if (this.isMuted) return;
    this.init();
    if (!this.ctx) return;

    const osc1 = this.ctx.createOscillator();
    const osc2 = this.ctx.createOscillator();
    const gain = this.ctx.createGain();

    osc1.type = 'sine';
    osc2.type = 'sine';
    osc1.frequency.setValueAtTime(440, this.ctx.currentTime);
    osc2.frequency.setValueAtTime(480, this.ctx.currentTime);

    gain.gain.setValueAtTime(0.2, this.ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.8);

    osc1.connect(gain);
    osc2.connect(gain);
    gain.connect(this.ctx.destination);

    osc1.start();
    osc2.start();
    osc1.stop(this.ctx.currentTime + 0.85);
    osc2.stop(this.ctx.currentTime + 0.85);
  }
}

const synth = new AudioSynthEngine();

// ==========================================================================
// 2. Interactive Phone Simulator State Machine
// ==========================================================================

const PhoneSimulator = {
  isSafetyActive: false,
  isEmergency: false,
  countdownVal: 5,
  countdownTimer: null,
  contacts: ['Mom (+91 98765 43210)', 'Sister (+91 91234 56789)'],

  elements: {
    trackingBtn: document.getElementById('phoneTrackingBtn'),
    trackingBtnText: document.getElementById('phoneTrackingBtnText'),
    aiStatus: document.getElementById('phoneAiStatus'),
    motionStatus: document.getElementById('phoneMotionStatus'),
    gpsStatus: document.getElementById('phoneGpsStatus'),
    testAlarmBtn: document.getElementById('phoneTestAlarmBtn'),
    countdownModal: document.getElementById('phoneEmergencyModal'),
    countdownText: document.getElementById('phoneCountdownNum'),
    safeBtn: document.getElementById('phoneSafeBtn'),
    contactsList: document.getElementById('phoneContactsList'),
    addContactBtn: document.getElementById('phoneAddContactBtn')
  },

  init() {
    if (!this.elements.trackingBtn) return;

    this.elements.trackingBtn.addEventListener('click', () => this.toggleSafetyMode());
    this.elements.testAlarmBtn.addEventListener('click', () => this.triggerEmergencyCountdown());
    this.elements.safeBtn.addEventListener('click', () => this.cancelEmergency());
    
    if (this.elements.addContactBtn) {
      this.elements.addContactBtn.addEventListener('click', () => this.promptAddContact());
    }

    this.renderContacts();
  },

  toggleSafetyMode() {
    synth.init();
    if (this.isEmergency) {
      this.cancelEmergency();
      return;
    }

    this.isSafetyActive = !this.isSafetyActive;

    if (this.isSafetyActive) {
      this.elements.trackingBtn.classList.add('is-active');
      this.elements.trackingBtnText.textContent = 'STOP TRACKING';
      this.elements.aiStatus.textContent = 'AI Detection:\nON';
      this.elements.aiStatus.style.color = '#34C759';
      this.elements.motionStatus.textContent = 'Motion SOS:\nActive';
      this.elements.motionStatus.style.color = '#34C759';
      this.elements.gpsStatus.textContent = 'GPS : ON (High Acc)';
      this.elements.gpsStatus.style.color = '#34C759';

      showToast('🛡️ Safety Mode Started: Monitoring Audio & Motion');
    } else {
      this.elements.trackingBtn.classList.remove('is-active', 'is-emergency');
      this.elements.trackingBtnText.textContent = 'START TRACKING';
      this.elements.aiStatus.textContent = 'AI Detection:\nOFF';
      this.elements.aiStatus.style.color = '#9A9AAB';
      this.elements.motionStatus.textContent = 'Motion SOS:\nInactive';
      this.elements.motionStatus.style.color = '#9A9AAB';
      this.elements.gpsStatus.textContent = 'GPS : OFF';
      this.elements.gpsStatus.style.color = '#9A9AAB';

      showToast('Safety Mode Stopped');
    }
  },

  triggerEmergencyCountdown() {
    synth.init();
    if (this.isEmergency) return;
    this.isEmergency = true;
    this.countdownVal = 5;
    this.elements.countdownText.textContent = this.countdownVal;
    this.elements.countdownModal.classList.remove('hidden');

    synth.playCountdownBeep(650);

    if (this.countdownTimer) clearInterval(this.countdownTimer);

    this.countdownTimer = setInterval(() => {
      this.countdownVal--;
      if (this.countdownVal > 0) {
        this.elements.countdownText.textContent = this.countdownVal;
        synth.playCountdownBeep(750 + (5 - this.countdownVal) * 80);
      } else {
        clearInterval(this.countdownTimer);
        this.activateFullEmergency();
      }
    }, 1000);
  },

  activateFullEmergency() {
    this.elements.countdownModal.classList.add('hidden');
    this.elements.trackingBtn.classList.remove('is-active');
    this.elements.trackingBtn.classList.add('is-emergency');
    this.elements.trackingBtnText.textContent = 'STOP EMERGENCY';
    
    synth.startAlarmSiren();
    showToast('🚨 EMERGENCY PROTOCOL ACTIVE! SMS & GPS Dispatched.', 5000);
  },

  cancelEmergency() {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
    synth.stopAlarmSiren();
    this.isEmergency = false;
    this.elements.countdownModal.classList.add('hidden');
    
    if (this.isSafetyActive) {
      this.elements.trackingBtn.classList.remove('is-emergency');
      this.elements.trackingBtn.classList.add('is-active');
      this.elements.trackingBtnText.textContent = 'STOP TRACKING';
    } else {
      this.elements.trackingBtn.classList.remove('is-emergency', 'is-active');
      this.elements.trackingBtnText.textContent = 'START TRACKING';
    }

    showToast('✅ Emergency Cancelled: You are marked SAFE');
  },

  renderContacts() {
    if (!this.elements.contactsList) return;
    this.elements.contactsList.innerHTML = '';
    this.contacts.forEach((contact, idx) => {
      const item = document.createElement('div');
      item.className = 'app-contact-item';
      item.innerHTML = `
        <span>${contact}</span>
        <button class="app-contact-remove" data-idx="${idx}" title="Remove Contact">✕</button>
      `;
      item.querySelector('.app-contact-remove').addEventListener('click', (e) => {
        const index = parseInt(e.target.getAttribute('data-idx'), 10);
        this.contacts.splice(index, 1);
        this.renderContacts();
        showToast('Contact removed from safety circle');
      });
      this.elements.contactsList.appendChild(item);
    });
  },

  promptAddContact() {
    const name = prompt('Enter Trusted Contact Name & Phone Number:', 'Friend (+91 99887 76655)');
    if (name && name.trim().length > 2) {
      this.contacts.push(name.trim());
      this.renderContacts();
      showToast('Added ' + name + ' to Trusted Contacts');
    }
  }
};

// ==========================================================================
// 3. Acoustic Decibel & Sensitivity Lab (Canvas Waveform & Audio Meter)
// ==========================================================================

const AcousticLab = {
  canvas: document.getElementById('acousticCanvas'),
  ctx: null,
  slider: document.getElementById('sensitivitySlider'),
  sliderValDisplay: document.getElementById('sliderValDisplay'),
  liveDbDisplay: document.getElementById('liveDbVal'),
  dbBadge: document.getElementById('dbBadge'),
  thresholdAdvice: document.getElementById('thresholdAdvice'),
  micBtn: document.getElementById('enableMicBtn'),
  presetChips: document.querySelectorAll('.preset-chip'),

  currentDb: 42,
  thresholdDb: 80,
  isLiveMic: false,
  micStream: null,
  analyser: null,
  animationId: null,

  init() {
    if (!this.canvas) return;
    this.ctx = this.canvas.getContext('2d');
    this.resizeCanvas();
    window.addEventListener('resize', () => this.resizeCanvas());

    if (this.slider) {
      this.slider.addEventListener('input', (e) => {
        this.thresholdDb = parseInt(e.target.value, 10);
        this.sliderValDisplay.textContent = `${this.thresholdDb} dB`;
        this.updateAdvice();
      });
    }

    this.presetChips.forEach(chip => {
      chip.addEventListener('click', () => {
        this.presetChips.forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        const db = parseInt(chip.dataset.db, 10);
        this.setSimulatedDb(db);
      });
    });

    if (this.micBtn) {
      this.micBtn.addEventListener('click', () => this.toggleLiveMic());
    }

    this.startCanvasLoop();
    this.updateAdvice();
  },

  resizeCanvas() {
    if (!this.canvas) return;
    this.canvas.width = this.canvas.offsetWidth * window.devicePixelRatio || 600;
    this.canvas.height = this.canvas.offsetHeight * window.devicePixelRatio || 200;
  },

  setSimulatedDb(db) {
    this.isLiveMic = false;
    if (this.micBtn) this.micBtn.textContent = '🎙️ Use Live Microphone';
    this.currentDb = db;
    this.updateUi();

    if (db >= this.thresholdDb) {
      showToast(`⚠️ Acoustic Trigger! Sound (${db}dB) >= Threshold (${this.thresholdDb}dB)`);
      if (PhoneSimulator.isSafetyActive && !PhoneSimulator.isEmergency) {
        PhoneSimulator.triggerEmergencyCountdown();
      }
    }
  },

  updateAdvice() {
    if (!this.thresholdAdvice) return;
    if (this.thresholdDb <= 55) {
      this.thresholdAdvice.textContent = 'Ultra-Sensitive: Best for quiet libraries or sleeping areas.';
      this.thresholdAdvice.style.color = '#34C759';
    } else if (this.thresholdDb <= 75) {
      this.thresholdAdvice.textContent = 'Recommended: Balanced for standard urban commutes and campus walks.';
      this.thresholdAdvice.style.color = '#A828FF';
    } else if (this.thresholdDb <= 90) {
      this.thresholdAdvice.textContent = 'Loud Environment: Recommended for crowded transit, concerts, or heavy traffic.';
      this.thresholdAdvice.style.color = '#FF9F0A';
    } else {
      this.thresholdAdvice.textContent = 'Distress Only: Only triggers on high-intensity screaming & impact noise (90dB+).';
      this.thresholdAdvice.style.color = '#FF3B30';
    }
  },

  updateUi() {
    if (this.liveDbDisplay) {
      this.liveDbDisplay.textContent = Math.round(this.currentDb);
    }
    if (this.dbBadge) {
      if (this.currentDb >= this.thresholdDb) {
        this.dbBadge.textContent = 'CRITICAL / TRIGGER';
        this.dbBadge.className = 'badge badge-red db-level-badge';
      } else if (this.currentDb >= 70) {
        this.dbBadge.textContent = 'ELEVATED NOISE';
        this.dbBadge.className = 'badge badge-blue db-level-badge';
      } else {
        this.dbBadge.textContent = 'SAFE AMBIENT';
        this.dbBadge.className = 'badge badge-green db-level-badge';
      }
    }
  },

  async toggleLiveMic() {
    if (this.isLiveMic) {
      this.isLiveMic = false;
      this.micBtn.textContent = '🎙️ Use Live Microphone';
      if (this.micStream) {
        this.micStream.getTracks().forEach(t => t.stop());
      }
      showToast('Live microphone disconnected');
      return;
    }

    try {
      synth.init();
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
      this.micStream = stream;
      this.isLiveMic = true;
      this.micBtn.textContent = '🔴 Stop Live Mic';

      const audioCtx = synth.ctx;
      const source = audioCtx.createMediaStreamSource(stream);
      this.analyser = audioCtx.createAnalyser();
      this.analyser.fftSize = 256;
      source.connect(this.analyser);

      showToast('🎙️ Live Microphone Active: Calibrating ambient decibels...');
    } catch (err) {
      console.warn('Microphone access not granted:', err);
      showToast('Microphone access denied. Using simulation mode.');
    }
  },

  startCanvasLoop() {
    let tick = 0;
    const render = () => {
      this.animationId = requestAnimationFrame(render);
      if (!this.ctx || !this.canvas) return;

      const width = this.canvas.width;
      const height = this.canvas.height;
      const c = this.ctx;

      c.clearRect(0, 0, width, height);

      if (this.isLiveMic && this.analyser) {
        const bufferLength = this.analyser.frequencyBinCount;
        const dataArray = new Uint8Array(bufferLength);
        this.analyser.getByteFrequencyData(dataArray);

        let sum = 0;
        for (let i = 0; i < bufferLength; i++) {
          sum += dataArray[i];
        }
        const avg = sum / bufferLength;
        // Approximate dB mapping from audio buffer
        this.currentDb = Math.min(100, Math.max(30, 30 + (avg / 255) * 70));
        this.updateUi();

        if (this.currentDb >= this.thresholdDb && PhoneSimulator.isSafetyActive && !PhoneSimulator.isEmergency) {
          PhoneSimulator.triggerEmergencyCountdown();
        }

        // Draw frequency bars
        const barWidth = (width / bufferLength) * 2.5;
        let x = 0;
        for (let i = 0; i < bufferLength; i++) {
          const barHeight = (dataArray[i] / 255) * height * 0.9;
          const grad = c.createLinearGradient(0, height, 0, 0);
          grad.addColorStop(0, '#8C00E3');
          grad.addColorStop(1, '#A828FF');

          c.fillStyle = grad;
          c.fillRect(x, height - barHeight, barWidth - 1, barHeight);
          x += barWidth;
        }
      } else {
        // Simulated sine and audio wave
        tick += 0.04;
        const noise = Math.sin(tick * 2) * 4;
        const targetDb = this.currentDb + noise;

        c.beginPath();
        c.lineWidth = 3;
        const grad = c.createLinearGradient(0, 0, width, 0);
        grad.addColorStop(0, '#8C00E3');
        grad.addColorStop(0.5, '#D0BCFF');
        grad.addColorStop(1, targetDb >= this.thresholdDb ? '#FF3B30' : '#8C00E3');
        c.strokeStyle = grad;

        const points = 60;
        const slice = width / points;
        const amplitude = (targetDb / 100) * (height * 0.4);

        for (let i = 0; i <= points; i++) {
          const x = i * slice;
          const y = (height / 2) + Math.sin(tick * 3 + (i * 0.2)) * amplitude * Math.cos(tick + i * 0.1);
          if (i === 0) c.moveTo(x, y);
          else c.lineTo(x, y);
        }
        c.stroke();

        // Threshold Reference Line
        const threshY = height - (this.thresholdDb / 100) * height;
        c.beginPath();
        c.setLineDash([6, 6]);
        c.strokeStyle = '#FF3B30';
        c.lineWidth = 1.5;
        c.moveTo(0, threshY);
        c.lineTo(width, threshY);
        c.stroke();
        c.setLineDash([]);
      }
    };
    render();
  }
};

// ==========================================================================
// 4. Safety Tools Sandbox (Fake Call, Safe Walk, Shake-to-SOS)
// ==========================================================================

const SafetyTools = {
  // Tabs
  tabBtns: document.querySelectorAll('.tool-tab-btn'),
  tabPanes: document.querySelectorAll('.tool-tab-pane'),

  // Fake Call
  btnAcceptCall: document.getElementById('btnAcceptFakeCall'),
  btnDeclineCall: document.getElementById('btnDeclineFakeCall'),
  fakeCallStatus: document.getElementById('fakeCallStatus'),
  fakeCallTimerId: null,
  callSeconds: 0,

  // Safe Walk
  safeWalkTimerDisplay: document.getElementById('safeWalkTimerDisplay'),
  safeWalkChips: document.querySelectorAll('.safewalk-chip'),
  btnStartSafeWalk: document.getElementById('btnStartSafeWalk'),
  btnCheckinSafeWalk: document.getElementById('btnCheckinSafeWalk'),
  safeWalkDurationMins: 15,
  safeWalkSecondsRemaining: 900,
  safeWalkInterval: null,
  isSafeWalkActive: false,

  // Shake SOS
  btnSimulateShake: document.getElementById('btnSimulateShake'),
  shakeIconBox: document.getElementById('shakeIconBox'),

  init() {
    this.initTabs();
    this.initFakeCall();
    this.initSafeWalk();
    this.initShakeDetector();
  },

  initTabs() {
    this.tabBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        const target = btn.dataset.tab;
        this.tabBtns.forEach(b => b.classList.remove('active'));
        this.tabPanes.forEach(p => p.classList.remove('active'));

        btn.classList.add('active');
        const activePane = document.getElementById(target);
        if (activePane) activePane.classList.add('active');
      });
    });
  },

  initFakeCall() {
    if (!this.btnAcceptCall) return;

    this.btnAcceptCall.addEventListener('click', () => {
      synth.init();
      if (this.fakeCallStatus) {
        this.fakeCallStatus.textContent = 'Connected (00:00)';
        this.fakeCallStatus.style.color = '#34C759';
      }
      this.callSeconds = 0;
      if (this.fakeCallTimerId) clearInterval(this.fakeCallTimerId);
      this.fakeCallTimerId = setInterval(() => {
        this.callSeconds++;
        const mins = String(Math.floor(this.callSeconds / 60)).padStart(2, '0');
        const secs = String(this.callSeconds % 60).padStart(2, '0');
        this.fakeCallStatus.textContent = `Connected (${mins}:${secs})`;
      }, 1000);
      showToast('📞 Fake Call Connected. Use this to gracefully excuse yourself.');
    });

    this.btnDeclineCall.addEventListener('click', () => {
      if (this.fakeCallTimerId) clearInterval(this.fakeCallTimerId);
      if (this.fakeCallStatus) {
        this.fakeCallStatus.textContent = 'Call Ended';
        this.fakeCallStatus.style.color = '#8E8E93';
      }
      setTimeout(() => {
        if (this.fakeCallStatus) {
          this.fakeCallStatus.textContent = 'Incoming Call...';
          this.fakeCallStatus.style.color = '#8E8E93';
        }
      }, 2000);
      showToast('Call dismissed.');
    });
  },

  initSafeWalk() {
    if (!this.btnStartSafeWalk) return;

    this.safeWalkChips.forEach(chip => {
      chip.addEventListener('click', () => {
        this.safeWalkChips.forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        this.safeWalkDurationMins = parseInt(chip.dataset.mins, 10);
        this.safeWalkSecondsRemaining = this.safeWalkDurationMins * 60;
        this.updateSafeWalkDisplay();
      });
    });

    this.btnStartSafeWalk.addEventListener('click', () => {
      if (this.isSafeWalkActive) {
        // Pause/Cancel
        clearInterval(this.safeWalkInterval);
        this.isSafeWalkActive = false;
        this.btnStartSafeWalk.textContent = 'Start Safe Walk Timer';
        showToast('Safe Walk Timer Paused');
      } else {
        // Start
        synth.init();
        this.isSafeWalkActive = true;
        this.btnStartSafeWalk.textContent = 'Pause Timer';
        showToast(`🚶 Safe Walk Started: ${this.safeWalkDurationMins} minutes window`);

        if (this.safeWalkInterval) clearInterval(this.safeWalkInterval);
        this.safeWalkInterval = setInterval(() => {
          this.safeWalkSecondsRemaining--;
          this.updateSafeWalkDisplay();

          if (this.safeWalkSecondsRemaining <= 0) {
            clearInterval(this.safeWalkInterval);
            this.isSafeWalkActive = false;
            this.btnStartSafeWalk.textContent = 'Start Safe Walk Timer';
            showToast('⚠️ Safe Walk Expired! Triggering Emergency Protocol.');
            PhoneSimulator.triggerEmergencyCountdown();
          }
        }, 1000);
      }
    });

    if (this.btnCheckinSafeWalk) {
      this.btnCheckinSafeWalk.addEventListener('click', () => {
        clearInterval(this.safeWalkInterval);
        this.isSafeWalkActive = false;
        this.safeWalkSecondsRemaining = this.safeWalkDurationMins * 60;
        this.updateSafeWalkDisplay();
        this.btnStartSafeWalk.textContent = 'Start Safe Walk Timer';
        showToast('🎉 Check-in Confirmed: You have safely arrived!');
      });
    }
  },

  updateSafeWalkDisplay() {
    if (!this.safeWalkTimerDisplay) return;
    const mins = String(Math.floor(this.safeWalkSecondsRemaining / 60)).padStart(2, '0');
    const secs = String(this.safeWalkSecondsRemaining % 60).padStart(2, '0');
    this.safeWalkTimerDisplay.textContent = `${mins}:${secs}`;
  },

  initShakeDetector() {
    if (!this.btnSimulateShake) return;

    this.btnSimulateShake.addEventListener('click', () => {
      synth.init();
      if (this.shakeIconBox) {
        this.shakeIconBox.classList.add('shake-active-animation');
        setTimeout(() => {
          this.shakeIconBox.classList.remove('shake-active-animation');
          showToast('⚡ Shake-to-SOS Triggered via Accelerometer!');
          PhoneSimulator.triggerEmergencyCountdown();
        }, 800);
      }
    });

    // Real Mobile Device Accelerometer Shake Support
    if (window.DeviceMotionEvent) {
      let lastX, lastY, lastZ;
      let lastTime = 0;
      const SHAKE_THRESHOLD = 25;

      window.addEventListener('devicemotion', (e) => {
        const current = e.accelerationIncludingGravity;
        if (!current) return;
        const now = Date.now();

        if ((now - lastTime) > 100) {
          const diffTime = now - lastTime;
          lastTime = now;

          const speed = Math.abs(current.x + current.y + current.z - lastX - lastY - lastZ) / diffTime * 10000;

          if (speed > SHAKE_THRESHOLD) {
            if (PhoneSimulator.isSafetyActive && !PhoneSimulator.isEmergency) {
              PhoneSimulator.triggerEmergencyCountdown();
            }
          }

          lastX = current.x;
          lastY = current.y;
          lastZ = current.z;
        }
      });
    }
  }
};

// ==========================================================================
// 5. Changelog & Timeline Filter
// ==========================================================================

const ChangelogFilter = {
  filterBtns: document.querySelectorAll('.changelog-filter-btn'),
  cards: document.querySelectorAll('.changelog-card'),

  init() {
    this.filterBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        const filter = btn.dataset.filter;
        this.filterBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');

        this.cards.forEach(card => {
          if (filter === 'all' || card.dataset.category.includes(filter)) {
            card.style.display = 'flex';
          } else {
            card.style.display = 'none';
          }
        });
      });
    });
  }
};

// ==========================================================================
// 6. FAQ Accordion
// ==========================================================================

const FaqAccordion = {
  items: document.querySelectorAll('.faq-item'),

  init() {
    this.items.forEach(item => {
      const btn = item.querySelector('.faq-question');
      const answer = item.querySelector('.faq-answer');

      btn.addEventListener('click', () => {
        const isActive = item.classList.contains('active');
        this.items.forEach(i => {
          i.classList.remove('active');
          i.querySelector('.faq-answer').style.maxHeight = null;
        });

        if (!isActive) {
          item.classList.add('active');
          answer.style.maxHeight = answer.scrollHeight + 'px';
        }
      });
    });
  }
};

// ==========================================================================
// 7. Theme Engine (Dark / Light Mode)
// ==========================================================================

const ThemeEngine = {
  toggleBtn: document.getElementById('themeToggleBtn'),

  init() {
    const saved = localStorage.getItem('narishakti_theme') || 'dark';
    document.documentElement.setAttribute('data-theme', saved);
    this.updateIcon(saved);

    if (this.toggleBtn) {
      this.toggleBtn.addEventListener('click', () => {
        const current = document.documentElement.getAttribute('data-theme') || 'dark';
        const next = current === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', next);
        localStorage.setItem('narishakti_theme', next);
        this.updateIcon(next);
      });
    }
  },

  updateIcon(theme) {
    if (!this.toggleBtn) return;
    this.toggleBtn.innerHTML = theme === 'dark' ? '🌙' : '☀️';
  }
};

// ==========================================================================
// 8. Toast Helper
// ==========================================================================

function showToast(msg, duration = 3000) {
  let container = document.querySelector('.toast-container');
  if (!container) {
    container = document.createElement('div');
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = 'toast';
  toast.innerHTML = `<span>${msg}</span>`;

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

// ==========================================================================
// 9. App Bootstrapper
// ==========================================================================

document.addEventListener('DOMContentLoaded', () => {
  PhoneSimulator.init();
  AcousticLab.init();
  SafetyTools.init();
  ChangelogFilter.init();
  FaqAccordion.init();
  ThemeEngine.init();

  // Smooth scroll spy for active nav link
  const sections = document.querySelectorAll('section[id]');
  const navLinks = document.querySelectorAll('.nav-link');

  window.addEventListener('scroll', () => {
    let current = '';
    sections.forEach(section => {
      const sectionTop = section.offsetTop - 120;
      if (window.scrollY >= sectionTop) {
        current = section.getAttribute('id');
      }
    });

    navLinks.forEach(link => {
      link.classList.remove('active');
      if (link.getAttribute('href') === `#${current}`) {
        link.classList.add('active');
      }
    });
  });
});
