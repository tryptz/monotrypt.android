#pragma once
#include <vector>
#include <cstring>
#include <cmath>
#include <algorithm>

// Fractional delay line with cubic interpolation.
class DelayLine {
public:
    void prepare(int maxDelaySamples) {
        buffer_.resize(maxDelaySamples + 4, 0.0f);
        maxDelay_ = maxDelaySamples;
        writePos_ = 0;
    }

    void reset() {
        std::fill(buffer_.begin(), buffer_.end(), 0.0f);
        writePos_ = 0;
    }

    void write(float sample) {
        buffer_[writePos_] = sample;
        writePos_ = (writePos_ + 1) % static_cast<int>(buffer_.size());
    }

    // Read at integer delay
    float read(int delaySamples) const {
        int idx = writePos_ - 1 - delaySamples;
        int sz = static_cast<int>(buffer_.size());
        while (idx < 0) idx += sz;
        return buffer_[idx % sz];
    }

    // Read at fractional delay with cubic interpolation
    float readCubic(float delaySamples) const {
        int sz = static_cast<int>(buffer_.size());
        int intDelay = static_cast<int>(delaySamples);
        float frac = delaySamples - static_cast<float>(intDelay);

        auto at = [&](int d) -> float {
            int idx = writePos_ - 1 - d;
            while (idx < 0) idx += sz;
            return buffer_[idx % sz];
        };

        float y0 = at(intDelay + 1);
        float y1 = at(intDelay);
        float y2 = at(intDelay - 1);
        float y3 = at(intDelay - 2);

        // Catmull-Rom cubic interpolation
        float c0 = y1;
        float c1 = 0.5f * (y2 - y0);
        float c2 = y0 - 2.5f * y1 + 2.0f * y2 - 0.5f * y3;
        float c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);

        return ((c3 * frac + c2) * frac + c1) * frac + c0;
    }

    // Read with linear interpolation (cheaper)
    float readLinear(float delaySamples) const {
        int sz = static_cast<int>(buffer_.size());
        int intDelay = static_cast<int>(delaySamples);
        float frac = delaySamples - static_cast<float>(intDelay);

        int idx1 = writePos_ - 1 - intDelay;
        int idx2 = idx1 - 1;
        while (idx1 < 0) idx1 += sz;
        while (idx2 < 0) idx2 += sz;

        return buffer_[idx1 % sz] * (1.0f - frac) + buffer_[idx2 % sz] * frac;
    }

    int getMaxDelay() const { return maxDelay_; }

private:
    std::vector<float> buffer_;
    int writePos_ = 0;
    int maxDelay_ = 0;
};
