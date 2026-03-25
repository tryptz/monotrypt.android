#pragma once
#include <vector>
#include <cstring>
#include <algorithm>

// Pre-delay ring buffer for lookahead limiters/gates.
class LookaheadBuffer {
public:
    void prepare(int delaySamples) {
        delay_ = delaySamples;
        buffer_.resize(delaySamples, 0.0f);
        writePos_ = 0;
    }

    void reset() {
        std::fill(buffer_.begin(), buffer_.end(), 0.0f);
        writePos_ = 0;
    }

    float process(float in) {
        if (delay_ == 0) return in;
        float out = buffer_[writePos_];
        buffer_[writePos_] = in;
        writePos_ = (writePos_ + 1) % delay_;
        return out;
    }

    int getDelay() const { return delay_; }

private:
    std::vector<float> buffer_;
    int writePos_ = 0;
    int delay_ = 0;
};
