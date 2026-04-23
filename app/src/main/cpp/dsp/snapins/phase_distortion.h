#pragma once
#include "snapin_processor.h"
#include "hilbert.h"
#include "dc_blocker.h"
#include "biquad.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Phase modulation distortion — signal modulates its own phase.
class PhaseDistortionProcessor : public SnapinProcessor {
public:
    enum Params {
        DRIVE = 0, NORMALIZE, TONE, BIAS, SPREAD, MIX, NUM_PARAMS
    };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        hilbertL_.prepare(sampleRate);
        hilbertR_.prepare(sampleRate);
        dcL_.prepare(sampleRate);
        dcR_.prepare(sampleRate);
        toneLpL_.reset();
        toneLpR_.reset();
        updateTone();
    }

    void process(float* left, float* right, int numFrames) override {
        float driveAmt = drive_ / 100.0f * static_cast<float>(M_PI);
        float biasL = bias_ + spread_ * 0.5f;
        float biasR = bias_ - spread_ * 0.5f;
        float m = mix_ / 100.0f;

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];

            // Hilbert transform for analytic signal
            float realL, imagL, realR, imagR;
            hilbertL_.process(dryL, realL, imagL);
            hilbertR_.process(dryR, realR, imagR);

            // Extract envelope for normalization
            float envL = std::sqrt(realL * realL + imagL * imagL);
            float envR = std::sqrt(realR * realR + imagR * imagR);

            // Phase modulation: rotate phase by input * drive
            float phaseModL = dryL * driveAmt + biasL;
            float phaseModR = dryR * driveAmt + biasR;

            float cosL = std::cos(phaseModL);
            float sinL = std::sin(phaseModL);
            float cosR = std::cos(phaseModR);
            float sinR = std::sin(phaseModR);

            float wetL = realL * cosL - imagL * sinL;
            float wetR = realR * cosR - imagR * sinR;

            // Normalize to preserve envelope
            if (normalize_ && envL > 1e-6f) {
                float wetEnv = std::sqrt(wetL * wetL);
                if (wetEnv > 1e-6f) wetL *= envL / wetEnv;
            }
            if (normalize_ && envR > 1e-6f) {
                float wetEnv = std::sqrt(wetR * wetR);
                if (wetEnv > 1e-6f) wetR *= envR / wetEnv;
            }

            // Tone filter
            wetL = toneLpL_.process(wetL);
            wetR = toneLpR_.process(wetR);

            // DC block
            wetL = dcL_.process(wetL);
            wetR = dcR_.process(wetR);

            left[i]  = dryL * (1.0f - m) + wetL * m;
            right[i] = dryR * (1.0f - m) + wetR * m;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case DRIVE:     drive_ = std::max(0.0f, std::min(100.0f, value)); break;
            case NORMALIZE: normalize_ = value > 0.5f; break;
            case TONE:
                tone_ = std::max(0.0f, std::min(100.0f, value));
                updateTone();
                break;
            case BIAS:   bias_ = std::max(-3.14159f, std::min(3.14159f, value)); break;
            case SPREAD: spread_ = std::max(0.0f, std::min(1.0f, value / 100.0f)); break;
            case MIX:    mix_ = std::max(0.0f, std::min(100.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case DRIVE:     return drive_;
            case NORMALIZE: return normalize_ ? 1.0f : 0.0f;
            case TONE:      return tone_;
            case BIAS:      return bias_;
            case SPREAD:    return spread_ * 100.0f;
            case MIX:       return mix_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Phase Distortion"; }
    SnapinType getType() const override { return SnapinType::PHASE_DISTORTION; }

private:
    void updateTone() {
        // Tone 0% = 200Hz LPF, 100% = 20kHz (wide open)
        float freq = 200.0f * std::pow(100.0f, tone_ / 100.0f);
        freq = std::min(freq, static_cast<float>(sampleRate_) * 0.45f);
        toneLpL_.configure(BiquadType::LowPass, sampleRate_, freq, 0.707, 0.0);
        toneLpR_.configure(BiquadType::LowPass, sampleRate_, freq, 0.707, 0.0);
    }

    float drive_ = 30.0f;
    bool normalize_ = false;
    float tone_ = 100.0f;
    float bias_ = 0.0f;
    float spread_ = 0.0f;
    float mix_ = 100.0f;

    HilbertTransform hilbertL_, hilbertR_;
    DcBlocker dcL_, dcR_;
    Biquad toneLpL_, toneLpR_;
};
