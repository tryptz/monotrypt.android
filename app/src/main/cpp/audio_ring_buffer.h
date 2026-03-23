#pragma once

#include <mutex>
#include <vector>

class AudioRingBuffer {
public:
    void Push(const float* data, size_t count, int channel_count, int sample_rate);
    bool Pop(std::vector<float>& out, int& channel_count, int& sample_rate);
    void Clear();

private:
    std::mutex mutex_;
    std::vector<float> samples_;
    int channel_count_ = 2;
    int sample_rate_ = 44100;
};
