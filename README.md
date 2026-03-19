# Monochrome Android - Precision AutoEQ (Alpha)

Monochrome is a high-performance audio engine for Android, featuring an industrial-grade **AutoEQ** correction system designed for surgical headphone matching and neutral-reference listening.

> [!IMPORTANT]
> **ALPHA RELEASE**: This version contains the high-precision **Global Greedy** AutoEQ engine. Please be aware that this is a development build and represents the cutting edge of our audio correction research.

## 🚀 Download High-Precision Alpha APK
You can directly download the clinical-precision build here:
[**Download monochrome-autoeq-alpha.apk**](monochrome-autoeq-alpha.apk) (Alpha Build)

---

## 🎧 The AutoEQ Engine - How It Works

The Monochrome AutoEQ engine uses a proprietary **Global Greedy Iterative Peaking** strategy. Unlike traditional equalizers that use fixed sectors or simple smoothing, our engine evaluates the entire 20Hz-20kHz spectrum as a holistic system.

### 1. Global Greedy Iterative Correction
The core algorithm follows a recursive refinement process:
- **Scan**: The engine identifies the absolute largest deviation from the target curve across the entire frequency range.
- **Isolate**: It calculates the optimal resonance (Q) and Gain required to surgically flatten that specific peak.
- **Refinement**: After applying the filter, the engine re-evaluates the *entire* remaining error curve, ensuring that every subsequent filter band accounts for the phase-coherent shifts of the previous ones.
- **Result**: A tightly fitted correction curve that "hugs" the Seap or Harman target with clinical accuracy.

### 2. High-Precision Double Math
Audio stability at subsonic frequencies (20Hz-50Hz) is notoriously difficult. Monochrome uses **64-bit Double Precision** math and a **Direct Complex Frequency Response** formula. This eliminates the "math singularities" and vertical spikes common in lower-precision mobile EQ implementations, providing a rock-solid, artifact-free baseline.

### 3. Surgical Q Control
The engine allows for an ultra-narrow **Q-factor of up to 6.0**, enabling the Correction of extremely narrow resonance peaks in high-end IEMs and planar headphones that would otherwise be missed by broader equalizers.

### 4. Professional Pro-UX Interface
- **Spectral Color Coding**: Bands are automatically colored based on their frequency (Sub-bass, Mids, Highs).
- **Interactive Tooltips**: Drag any dot to see real-time Frequency (Hz) and Gain (dB) labels.
- **120Hz Fluidity**: The interface is optimized for high-refresh-rate displays for instantaneous visual feedback.

---

## ⚙️ Requirements
- Android 10+
- Support for modern Audio Effects API
- High-resolution target files (Seap/AutoEq standard)

## 🛠 Building from Source
Run the following command to build the high-precision release yourself:
```powershell
.\gradlew assembleRelease
```

---
*Built with passion for audio fidelity and mathematical precision.*
