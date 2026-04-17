#pragma once

// 1st-order DC blocking filter (~5Hz HPF).
// y[n] = x[n] - x[n-1] + R * y[n-1], R ≈ 0.995
class DcBlocker {
public:
    void prepare(double sampleRate) {
        // R controls the cutoff: higher R = lower cutoff
        // ~5Hz at any sample rate
        R_ = 1.0f - (10.0f * 3.14159265f / static_cast<float>(sampleRate));
        if (R_ < 0.9f) R_ = 0.9f;
        if (R_ > 0.9999f) R_ = 0.9999f;
    }

    void reset() { x1_ = y1_ = 0.0f; }

    float process(float in) {
        float out = in - x1_ + R_ * y1_;
        x1_ = in;
        y1_ = out;
        return out;
    }

    void processBlock(float* data, int numFrames) {
        for (int i = 0; i < numFrames; i++) {
            data[i] = process(data[i]);
        }
    }

private:
    float R_ = 0.995f;
    float x1_ = 0.0f;
    float y1_ = 0.0f;
};
