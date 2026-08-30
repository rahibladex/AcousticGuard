/**
 * TEJASHWINI (AcousticGuard) — Interactive Website & Presentation Engine
 * Handles Presentation Slide Deck, Live SOS Protocol Simulator,
 * Web Audio Siren Synthesizer, Scenario Explorer & Fake Call Decoy.
 */

document.addEventListener('DOMContentLoaded', () => {
  initPresentationEngine();
  initScenarioExplorer();
  initFakeCallDemo();
});

/* ==========================================================================
   1. Presentation Slide Deck Engine
   ========================================================================== */
function initPresentationEngine() {
  const presentationContainer = document.getElementById('presentationContainer');
  const toggleBtn = document.getElementById('presentationToggleBtn');
  const closeBtn = document.getElementById('closePresentationBtn');
  const prevBtn = document.getElementById('prevSlideBtn');
  const nextBtn = document.getElementById('nextSlideBtn');
  const slides = document.querySelectorAll('.slide');
  const dotsContainer = document.getElementById('slideDots');
  const slideCounter = document.getElementById('slideCounter');
  
  if (!presentationContainer || slides.length === 0) return;

  let currentSlide = 0;
  const totalSlides = slides.length;

  // Create dot indicators
  dotsContainer.innerHTML = '';
  slides.forEach((_, idx) => {
    const dot = document.createElement('div');
    dot.classList.add('slide-dot');
    if (idx === 0) dot.classList.add('active');
    dot.addEventListener('click', () => goToSlide(idx));
    dotsContainer.appendChild(dot);
  });

  const dots = document.querySelectorAll('.slide-dot');

  function updateSlideView() {
    slides.forEach((slide, idx) => {
      slide.classList.remove('active', 'prev');
      if (idx === currentSlide) {
        slide.classList.add('active');
      } else if (idx < currentSlide) {
        slide.classList.add('prev');
      }
    });

    dots.forEach((dot, idx) => {
      dot.classList.toggle('active', idx === currentSlide);
    });

    if (slideCounter) {
      slideCounter.textContent = `Slide ${currentSlide + 1} of ${totalSlides}`;
    }

    // Auto-play and request fullscreen on Slide 7 (index 6)
    const slide7Iframe = document.getElementById('slide7Video');
    const slide7Container = document.getElementById('slide7VideoContainer');
    
    if (currentSlide === 6 && slide7Iframe) {
      slide7Iframe.src = "https://www.youtube.com/embed/GI85h-b8QOw?autoplay=1&enablejsapi=1&rel=0";
      setTimeout(() => {
        try {
          if (slide7Container && slide7Container.requestFullscreen) {
            slide7Container.requestFullscreen().catch(() => {});
          } else if (slide7Iframe && slide7Iframe.requestFullscreen) {
            slide7Iframe.requestFullscreen().catch(() => {});
          }
        } catch (e) {}
      }, 300);
    } else if (slide7Iframe) {
      slide7Iframe.contentWindow.postMessage('{"event":"command","func":"pauseVideo","args":""}', '*');
      if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => {});
      }
    }

    if (prevBtn) prevBtn.disabled = currentSlide === 0;
    if (nextBtn) {
      if (currentSlide === totalSlides - 1) {
        nextBtn.textContent = 'Finish Presentation';
      } else {
        nextBtn.textContent = 'Next Slide ➔';
      }
    }
  }

  function launchSlide7Fullscreen() {
    const slide7Iframe = document.getElementById('slide7Video');
    const slide7Container = document.getElementById('slide7VideoContainer');
    if (slide7Iframe) {
      slide7Iframe.src = "https://www.youtube.com/embed/GI85h-b8QOw?autoplay=1&enablejsapi=1&rel=0";
      try {
        if (slide7Container && slide7Container.requestFullscreen) {
          slide7Container.requestFullscreen().catch(() => {});
        } else if (slide7Iframe && slide7Iframe.requestFullscreen) {
          slide7Iframe.requestFullscreen().catch(() => {});
        }
      } catch (e) {}
    }
  }

  const playFsBtn = document.getElementById('playSlide7FullscreenBtn');
  if (playFsBtn) {
    playFsBtn.addEventListener('click', launchSlide7Fullscreen);
  }

  function goToSlide(idx) {
    if (idx >= 0 && idx < totalSlides) {
      currentSlide = idx;
      updateSlideView();
    }
  }

  function nextSlide() {
    if (currentSlide < totalSlides - 1) {
      currentSlide++;
      updateSlideView();
    } else {
      closePresentation();
    }
  }

  function prevSlide() {
    if (currentSlide > 0) {
      currentSlide--;
      updateSlideView();
    }
  }

  function openPresentation() {
    presentationContainer.classList.add('active');
    document.body.style.overflow = 'hidden';
    updateSlideView();
  }

  function closePresentation() {
    presentationContainer.classList.remove('active');
    document.body.style.overflow = 'auto';
  }

  // Event Listeners
  if (toggleBtn) toggleBtn.addEventListener('click', openPresentation);
  if (closeBtn) closeBtn.addEventListener('click', closePresentation);
  if (nextBtn) nextBtn.addEventListener('click', nextSlide);
  if (prevBtn) prevBtn.addEventListener('click', prevSlide);

  // Keyboard navigation
  document.addEventListener('keydown', (e) => {
    if (!presentationContainer.classList.contains('active')) return;

    if (e.key === 'ArrowRight' || e.key === 'Space') {
      e.preventDefault();
      nextSlide();
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      prevSlide();
    } else if (e.key === 'Escape') {
      closePresentation();
    }
  });

  // Touch Swipe for mobile slides
  let touchStartX = 0;
  presentationContainer.addEventListener('touchstart', (e) => {
    touchStartX = e.changedTouches[0].screenX;
  }, { passive: true });

  presentationContainer.addEventListener('touchend', (e) => {
    const touchEndX = e.changedTouches[0].screenX;
    if (touchStartX - touchEndX > 50) nextSlide();
    if (touchEndX - touchStartX > 50) prevSlide();
  }, { passive: true });
}



/* ==========================================================================
   3. Interactive Situation & Scenario Explorer
   ========================================================================== */
function initScenarioExplorer() {
  const scenarioBtns = document.querySelectorAll('.scenario-tab-btn');
  const scenarioDisplays = document.querySelectorAll('.scenario-detail-card');

  if (scenarioBtns.length === 0) return;

  scenarioBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      const target = btn.getAttribute('data-target');

      scenarioBtns.forEach(b => b.classList.remove('active'));
      scenarioDisplays.forEach(d => d.classList.remove('active'));

      btn.classList.add('active');
      const targetCard = document.getElementById(target);
      if (targetCard) targetCard.classList.add('active');
    });
  });
}

/* ==========================================================================
   4. Decoy Fake Call Demo
   ========================================================================== */
function initFakeCallDemo() {
  const launchFakeCallBtn = document.getElementById('launchFakeCallBtn');
  const fakeCallOverlay = document.getElementById('fakeCallOverlay');
  const acceptCallBtn = document.getElementById('acceptFakeCallBtn');
  const declineCallBtn = document.getElementById('declineFakeCallBtn');
  const callDurationEl = document.getElementById('fakeCallDuration');

  if (!launchFakeCallBtn || !fakeCallOverlay) return;

  let callTimer = null;
  let seconds = 0;

  launchFakeCallBtn.addEventListener('click', () => {
    fakeCallOverlay.style.display = 'flex';
    seconds = 0;
    if (callDurationEl) callDurationEl.textContent = 'Incoming Call...';
  });

  if (acceptCallBtn) {
    acceptCallBtn.addEventListener('click', () => {
      if (callDurationEl) {
        callDurationEl.textContent = '00:00';
        callTimer = setInterval(() => {
          seconds++;
          const mins = String(Math.floor(seconds / 60)).padStart(2, '0');
          const secs = String(seconds % 60).padStart(2, '0');
          callDurationEl.textContent = `${mins}:${secs}`;
        }, 1000);
      }
    });
  }

  if (declineCallBtn) {
    declineCallBtn.addEventListener('click', () => {
      clearInterval(callTimer);
      fakeCallOverlay.style.display = 'none';
    });
  }
}
