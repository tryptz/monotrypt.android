#pragma once
#include "snapin_processor.h"
#include "transfer_curve.h"
#include "dc_blocker.h"
#include <cmath>
#include <algorithm>

// Waveshaping via 256-point transfer curve with cubic interpolation.
// Overflow modes: Hold (clamp), Repeat (wrap), Mirror (reflect).
class ShaperProcessor : public SnapinProcessor {
public:
    enum Params {
        DRIVE = 0, MIX, OVERFLOW, DC_FILTER, NUM_PARAMS
    };
    // Overflow: 0=Hold, 1=Repeat, 2=Mirror

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        dcL_.prepare(sampleRate);
        dcR_.prepare(sampleRate);
        // Default to soft clip curve
        curve_.setSoftClip();
    }

    void process(float* left, float* right, int numFrames) override {
        float driveLin = std::pow(10.0f, driveDb_ / 20.0f);

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];

            // Apply drive
            float wetL = left[i] * driveLin;
            float wetR = right[i] * driveLin;

            // Handle overflow before lookup
            wetL = handleOverflow(wetL);
            wetR = handleOverflow(wetR);

            // Transfer curve lookup
            wetL = curve_.process(wetL);
            wetR = curve_.process(wetR);

            // DC filter
            if (dcFilter_) {
                wetL = dcL_.process(wetL);
                wetR = dcR_.process(wetR);
            }

            // Mix
            left[i]  = dryL * (1.0f - mix_) + wetL * mix_;
            right[i] = dryR * (1.0f - mix_) + wetR * mix_;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case DRIVE:     driveDb_ = std::max(0.0f, std::min(48.0f, value)); break;
            case MIX:       mix_ = std::max(0.0f, std::min(1.0f, value / 100.0f)); break;
            case OVERFLOW:  overflow_ = static_cast<int>(std::max(0.0f, std::min(2.0f, value))); break;
            case DC_FILTER: dcFilter_ = value > 0.5f; break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case DRIVE:     return driveDb_;
            case MIX:       return mix_ * 100.0f;
            case OVERFLOW:  return static_cast<float>(overflow_);
            case DC_FILTER: return dcFilter_ ? 1.0f : 0.0f;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Shaper"; }
    SnapinType getType() const override { return SnapinType::SHAPER; }

    // Access curve data for preset save/load
    TransferCurve& getCurve() { return curve_; }
    const TransferCurve& getCurve() const { return curve_; }

private:
    float handleOverflow(float x) const {
        switch (overflow_) {
            case 0:  // Hold (clamp)
                return std::max(-1.0f, std::min(1.0f, x));
            case 1:  // Repeat (wrap)
                x = std::fmod(x + 1.0f, 2.0f);
                if (x < 0.0f) x += 2.0f;
                return x - 1.0f;
            case 2:  // Mirror (reflect)
                while (x > 1.0f || x < -1.0f) {
                    if (x > 1.0f) x = 2.0f - x;
                    if (x < -1.0f) x = -2.0f - x;
                }
                return x;
            default:
                return std::max(-1.0f, std::min(1.0f, x));
        }
    }

    float driveDb_ = 0.0f;
    float mix_ = 1.0f;
    int overflow_ = 0;  // Hold
    bool dcFilter_ = true;

    TransferCurve curve_;
    DcBlocker dcL_, dcR_;
};
