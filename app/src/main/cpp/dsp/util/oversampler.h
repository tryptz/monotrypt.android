#pragma once
#include <vector>
#include <cstring>
#include <cmath>

// Simple 2x oversampler with half-band anti-alias filter.
class Oversampler {
public:
    void prepare(int maxBlockSize) {
        upBuf_.resize(maxBlockSize * 2, 0.0f);
        downBuf_.resize(maxBlockSize, 0.0f);
        std::memset(h_, 0, sizeof(h_));
    }

    void reset() {
        std::memset(h_, 0, sizeof(h_));
    }

    // Upsample: insert zeros between samples, then filter
    float* upsample(const float* input, int numFrames) {
        for (int i = 0; i < numFrames; i++) {
            upBuf_[i * 2] = input[i] * 2.0f;
            upBuf_[i * 2 + 1] = 0.0f;
        }
        // Simple 5-tap half-band FIR: [0.0625, 0.25, 0.375, 0.25, 0.0625]
        for (int i = 0; i < numFrames * 2; i++) {
            float x = upBuf_[i];
            float y = 0.0625f * h_[3] + 0.25f * h_[2] + 0.375f * h_[1] + 0.25f * h_[0] + 0.0625f * x;
            h_[3] = h_[2]; h_[2] = h_[1]; h_[1] = h_[0]; h_[0] = x;
            upBuf_[i] = y;
        }
        return upBuf_.data();
    }

    // Downsample: filter then decimate by 2
    float* downsample(const float* input, int numFrames2x) {
        int outFrames = numFrames2x / 2;
        // Apply same filter
        for (int i = 0; i < numFrames2x; i++) {
            float x = input[i];
            float y = 0.0625f * dh_[3] + 0.25f * dh_[2] + 0.375f * dh_[1] + 0.25f * dh_[0] + 0.0625f * x;
            dh_[3] = dh_[2]; dh_[2] = dh_[1]; dh_[1] = dh_[0]; dh_[0] = x;
            if (i % 2 == 0) {
                downBuf_[i / 2] = y;
            }
        }
        return downBuf_.data();
    }

    int getOversampleFactor() const { return 2; }

private:
    std::vector<float> upBuf_;
    std::vector<float> downBuf_;
    float h_[4] = {};
    float dh_[4] = {};
};
