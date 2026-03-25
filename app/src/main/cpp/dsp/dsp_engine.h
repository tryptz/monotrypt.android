#pragma once
#include "snapin_processor.h"
#include <vector>
#include <memory>
#include <mutex>
#include <cmath>
#include <string>

static constexpr int NUM_MIX_BUSES = 4;
static constexpr int MASTER_BUS = 4;
static constexpr int TOTAL_BUSES = 5;  // 4 mix + 1 master
static constexpr int MAX_PLUGINS_PER_BUS = 16;

struct Bus {
    std::vector<std::unique_ptr<SnapinProcessor>> plugins;
    float gainDb = 0.0f;
    float pan = 0.0f;       // -1 left, 0 center, +1 right
    bool muted = false;
    bool soloed = false;

    // Smoothed gain values (updated per block)
    float smoothGainL = 1.0f;
    float smoothGainR = 1.0f;
    float targetGainL = 1.0f;
    float targetGainR = 1.0f;
};

class DspEngine {
public:
    DspEngine(int sampleRate, int maxBlockSize);
    ~DspEngine();

    // Audio processing — called from audio thread
    void process(float* left, float* right, int numFrames);

    // Bus control — simple writes, safe for cross-thread
    void setBusGain(int busIndex, float gainDb);
    void setBusPan(int busIndex, float pan);
    void setBusMute(int busIndex, bool muted);
    void setBusSolo(int busIndex, bool soloed);

    // Plugin chain — uses mutex since changes are infrequent
    int addPlugin(int busIndex, int slotIndex, int pluginType);
    void removePlugin(int busIndex, int slotIndex);
    void movePlugin(int busIndex, int fromSlot, int toSlot);
    void setParameter(int busIndex, int slotIndex, int paramIndex, float value);
    void setPluginBypassed(int busIndex, int slotIndex, bool bypassed);

    // State serialization
    std::string getStateJson() const;
    void loadStateJson(const std::string& json);

private:
    Bus buses_[TOTAL_BUSES];
    int sampleRate_;
    int maxBlockSize_;
    std::mutex chainMutex_;

    // Scratch buffers
    std::vector<float> sumL_, sumR_;
    std::vector<float> busL_, busR_;

    bool anySoloed() const;
    void recalcBusGains(Bus& bus);

    // Smoothing coefficient for gain changes
    float gainSmoothCoeff_ = 0.005f;
};
