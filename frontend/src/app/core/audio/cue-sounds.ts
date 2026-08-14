/**
 * Web Audio cues for the monitoring board (no asset files).
 * Browsers may suspend AudioContext until the first user gesture —
 * call {@link unlockAudio} early and on interaction so TV displays can play.
 */

let sharedContext: AudioContext | null = null;

function getContext(): AudioContext | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const AudioCtx =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AudioCtx) {
    return null;
  }
  sharedContext ??= new AudioCtx();
  if (sharedContext.state === 'suspended') {
    void sharedContext.resume();
  }
  return sharedContext;
}

function tone(
  frequency: number,
  startOffsetSec: number,
  durationSec: number,
  gain = 0.18,
  type: OscillatorType = 'sine',
): void {
  const ctx = getContext();
  if (!ctx) {
    return;
  }
  const oscillator = ctx.createOscillator();
  const amp = ctx.createGain();
  oscillator.type = type;
  oscillator.frequency.value = frequency;
  amp.gain.value = 0;
  oscillator.connect(amp);
  amp.connect(ctx.destination);

  const start = ctx.currentTime + startOffsetSec;
  amp.gain.setValueAtTime(0, start);
  amp.gain.linearRampToValueAtTime(gain, start + 0.025);
  amp.gain.exponentialRampToValueAtTime(0.001, start + durationSec);
  oscillator.start(start);
  oscillator.stop(start + durationSec + 0.03);
}

/**
 * Online ticket submitted — short ascending alert, distinct from the counter call.
 * (Previously a soft F5→C6 bell; now a brighter C–E–G chirp.)
 */
export function playNewTicketCue(): void {
  tone(523.25, 0, 0.11, 0.16, 'triangle'); // C5
  tone(659.25, 0.09, 0.11, 0.15, 'triangle'); // E5
  tone(783.99, 0.18, 0.28, 0.14, 'sine'); // G5
}

/**
 * Professional queue call cue — short two-tone “ding-dong”, no long trail.
 */
export function playNowServingCue(): void {
  // Classic counter-call chime (≈0.55s total)
  tone(659.25, 0, 0.2, 0.2, 'sine'); // E5
  tone(987.77, 0.2, 0.28, 0.16, 'sine'); // B5
}

/** Optional success tap on the kiosk. */
export function playSuccessCue(): void {
  tone(659.25, 0, 0.1, 0.14, 'sine');
  tone(880, 0.1, 0.16, 0.12, 'sine');
}

/** Soft chime when a new chat message arrives. */
export function playMessageCue(): void {
  tone(784, 0, 0.1, 0.14, 'sine');
  tone(988, 0.1, 0.16, 0.12, 'triangle');
}

/** Unlock / resume audio (safe to call repeatedly). */
export function unlockAudio(): void {
  getContext();
}

/**
 * Attach one-shot listeners so the first click/key/touch resumes AudioContext
 * when autoplay policies block sound on page load.
 */
export function armAutoUnlock(): () => void {
  if (typeof window === 'undefined') {
    return () => undefined;
  }
  const unlock = () => unlockAudio();
  const opts: AddEventListenerOptions = { capture: true, passive: true };
  window.addEventListener('pointerdown', unlock, opts);
  window.addEventListener('keydown', unlock, opts);
  window.addEventListener('touchstart', unlock, opts);
  document.addEventListener('visibilitychange', unlock);
  unlock();
  return () => {
    window.removeEventListener('pointerdown', unlock, opts);
    window.removeEventListener('keydown', unlock, opts);
    window.removeEventListener('touchstart', unlock, opts);
    document.removeEventListener('visibilitychange', unlock);
  };
}
