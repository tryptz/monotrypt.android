#include "audio_ring_buffer.h"

void AudioRingBuffer::Push(const float* data, size_t count, int channel_count, int sample_rate) {
    std::lock_guard<std::mutex> guard(mutex_);
    samples_.assign(data, data + count);
    channel_count_ = channel_count;
    sample_rate_ = sample_rate;
}

bool AudioRingBuffer::Pop(std::vector<float>& out, int& channel_count, int& sample_rate) {
    std::lock_guard<std::mutex> guard(mutex_);
    if (samples_.empty()) {
        return false;
    }
    out = samples_;
    samples_.clear();
    channel_count = channel_count_;
    sample_rate = sample_rate_;
    return true;
}

void AudioRingBuffer::Clear() {
    std::lock_guard<std::mutex> guard(mutex_);
    samples_.clear();
}
