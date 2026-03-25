#pragma once
#include <vector>
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Overlap-add crossfade buffer for reverser / pitch shifter.
class CrossfadeBuffer {
public:
    void prepare(int maxSegmentSamples) {
        maxSegment_ = maxSegmentSamples;
        buffer_.resize(maxSegmentSamples * 2, 0.0f);
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

    // Read backwards from current position
    float readReverse(int offset) const {
        int sz = static_cast<int>(buffer_.size());
        int idx = writePos_ - 1 - offset;
        while (idx < 0) idx += sz;
        return buffer_[idx % sz];
    }

    // Hann window for crossfading
    static float hannWindow(float phase) {
        return 0.5f * (1.0f - std::cos(2.0f * static_cast<float>(M_PI) * phase));
    }

    int getMaxSegment() const { return maxSegment_; }

private:
    std::vector<float> buffer_;
    int writePos_ = 0;
    int maxSegment_ = 0;
};
