#include "dsp_engine.h"
#include "snapins/gain.h"
#include "snapins/stereo.h"
#include "snapins/filter.h"
#include "snapins/eq_3band.h"
#include "snapins/compressor.h"
#include "snapins/limiter.h"
#include "snapins/gate.h"
#include "snapins/dynamics.h"
#include "snapins/compactor.h"
#include "snapins/transient_shaper.h"
#include "snapins/distortion.h"
#include "snapins/shaper.h"
#include "snapins/chorus.h"
#include "snapins/ensemble.h"
#include "snapins/flanger.h"
#include "snapins/phaser.h"
#include "snapins/delay.h"
#include "snapins/reverb.h"
#include "snapins/bitcrush.h"
#include "snapins/comb_filter.h"
#include "snapins/channel_mixer.h"
#include "snapins/formant_filter.h"
#include "snapins/frequency_shifter.h"
#include "snapins/haas.h"
#include "snapins/ladder_filter.h"
#include "snapins/nonlinear_filter.h"
#include "snapins/phase_distortion.h"
#include "snapins/pitch_shifter.h"
#include "snapins/resonator.h"
#include "snapins/reverser.h"
#include "snapins/ring_mod.h"
#include "snapins/tape_stop.h"
#include "snapins/trance_gate.h"
#include <algorithm>
#include <cstring>
#include <sstream>
#include <android/log.h>

#define LOG_TAG "MonochromeDSP"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── Factory ─────────────────────────────────────────────────────────────

SnapinProcessor* createSnapin(SnapinType type) {
    switch (type) {
        case SnapinType::GAIN:       return new GainProcessor();
        case SnapinType::STEREO:     return new StereoProcessor();
        case SnapinType::FILTER:     return new FilterProcessor();
        case SnapinType::EQ_3BAND:   return new Eq3BandProcessor();
        case SnapinType::COMPRESSOR: return new CompressorProcessor();
        case SnapinType::LIMITER:    return new LimiterProcessor();
        case SnapinType::GATE:       return new GateProcessor();
        case SnapinType::DYNAMICS:   return new DynamicsProcessor();
        case SnapinType::COMPACTOR:  return new CompactorProcessor();
        case SnapinType::TRANSIENT_SHAPER: return new TransientShaperProcessor();
        case SnapinType::DISTORTION: return new DistortionProcessor();
        case SnapinType::SHAPER:     return new ShaperProcessor();
        case SnapinType::CHORUS:     return new ChorusProcessor();
        case SnapinType::ENSEMBLE:   return new EnsembleProcessor();
        case SnapinType::FLANGER:    return new FlangerProcessor();
        case SnapinType::PHASER:     return new PhaserProcessor();
        case SnapinType::DELAY:      return new DelayProcessor();
        case SnapinType::REVERB:     return new ReverbProcessor();
        case SnapinType::BITCRUSH:   return new BitcrushProcessor();
        case SnapinType::COMB_FILTER: return new CombFilterProcessor();
        case SnapinType::CHANNEL_MIXER: return new ChannelMixerProcessor();
        case SnapinType::FORMANT_FILTER: return new FormantFilterProcessor();
        case SnapinType::FREQUENCY_SHIFTER: return new FrequencyShifterProcessor();
        case SnapinType::HAAS:       return new HaasProcessor();
        case SnapinType::LADDER_FILTER: return new LadderFilterProcessor();
        case SnapinType::NONLINEAR_FILTER: return new NonlinearFilterProcessor();
        case SnapinType::PHASE_DISTORTION: return new PhaseDistortionProcessor();
        case SnapinType::PITCH_SHIFTER: return new PitchShifterProcessor();
        case SnapinType::RESONATOR:  return new ResonatorProcessor();
        case SnapinType::REVERSER:   return new ReverserProcessor();
        case SnapinType::RING_MOD:   return new RingModProcessor();
        case SnapinType::TAPE_STOP:  return new TapeStopProcessor();
        case SnapinType::TRANCE_GATE: return new TranceGateProcessor();
        default:
            LOGE("Unsupported snapin type: %d", static_cast<int>(type));
            return nullptr;
    }
}

// ── Engine lifecycle ────────────────────────────────────────────────────

DspEngine::DspEngine(int sampleRate, int maxBlockSize)
    : sampleRate_(sampleRate), maxBlockSize_(maxBlockSize) {
    sumL_.resize(maxBlockSize, 0.0f);
    sumR_.resize(maxBlockSize, 0.0f);
    busL_.resize(maxBlockSize, 0.0f);
    busR_.resize(maxBlockSize, 0.0f);

    // Smoothing coeff: ~5ms time constant
    gainSmoothCoeff_ = 1.0f - std::exp(-1.0f / (0.005f * sampleRate));

    LOGD("DspEngine created: sr=%d, maxBlock=%d", sampleRate, maxBlockSize);
}

DspEngine::~DspEngine() {
    LOGD("DspEngine destroyed");
}

// ── Audio processing ────────────────────────────────────────────────────

void DspEngine::process(float* left, float* right, int numFrames) {
    // Clear sum buffers
    std::fill(sumL_.begin(), sumL_.begin() + numFrames, 0.0f);
    std::fill(sumR_.begin(), sumR_.begin() + numFrames, 0.0f);

    bool hasSolo = anySoloed();

    // Lock for reading plugin chains (brief lock — plugins don't allocate during process)
    std::lock_guard<std::mutex> lock(chainMutex_);

    // Process mix buses 0-3
    for (int b = 0; b < NUM_MIX_BUSES; b++) {
        Bus& bus = buses_[b];

        // Skip if muted or (solo mode active and this bus not soloed)
        if (bus.muted || (hasSolo && !bus.soloed)) continue;

        // Copy input to bus scratch buffer
        std::copy(left, left + numFrames, busL_.data());
        std::copy(right, right + numFrames, busR_.data());

        // Run plugin chain
        for (auto& plugin : bus.plugins) {
            if (plugin && !plugin->isBypassed()) {
                plugin->process(busL_.data(), busR_.data(), numFrames);
            }
        }

        // Recalculate target gains from dB + pan
        recalcBusGains(bus);

        // Apply bus gain + pan with smoothing, sum into master input
        for (int i = 0; i < numFrames; i++) {
            bus.smoothGainL += gainSmoothCoeff_ * (bus.targetGainL - bus.smoothGainL);
            bus.smoothGainR += gainSmoothCoeff_ * (bus.targetGainR - bus.smoothGainR);
            sumL_[i] += busL_[i] * bus.smoothGainL;
            sumR_[i] += busR_[i] * bus.smoothGainR;
        }
    }

    // Run master bus chain
    Bus& master = buses_[MASTER_BUS];
    for (auto& plugin : master.plugins) {
        if (plugin && !plugin->isBypassed()) {
            plugin->process(sumL_.data(), sumR_.data(), numFrames);
        }
    }

    // Apply master gain and write to output
    recalcBusGains(master);
    for (int i = 0; i < numFrames; i++) {
        master.smoothGainL += gainSmoothCoeff_ * (master.targetGainL - master.smoothGainL);
        master.smoothGainR += gainSmoothCoeff_ * (master.targetGainR - master.smoothGainR);
        left[i]  = sumL_[i] * master.smoothGainL;
        right[i] = sumR_[i] * master.smoothGainR;
    }
}

// ── Bus control ─────────────────────────────────────────────────────────

void DspEngine::setBusGain(int busIndex, float gainDb) {
    if (busIndex < 0 || busIndex >= TOTAL_BUSES) return;
    buses_[busIndex].gainDb = gainDb;
}

void DspEngine::setBusPan(int busIndex, float pan) {
    if (busIndex < 0 || busIndex >= TOTAL_BUSES) return;
    buses_[busIndex].pan = std::max(-1.0f, std::min(1.0f, pan));
}

void DspEngine::setBusMute(int busIndex, bool muted) {
    if (busIndex < 0 || busIndex >= TOTAL_BUSES) return;
    buses_[busIndex].muted = muted;
}

void DspEngine::setBusSolo(int busIndex, bool soloed) {
    if (busIndex < 0 || busIndex >= TOTAL_BUSES) return;
    buses_[busIndex].soloed = soloed;
}

// ── Plugin chain management ─────────────────────────────────────────────

int DspEngine::addPlugin(int busIndex, int slotIndex, int pluginType) {
    if (busIndex < 0 || busIndex >= TOTAL_BUSES) return -1;
    Bus& bus = buses_[busIndex];
    if (static_cast<int>(bus.plugins.size()) >= MAX_PLUGINS_PER_BUS) return -1;

    auto* proc = createSnapin(static_cast<SnapinType>(pluginType));
    if (!proc) return -1;

    proc->prepare(static_cast<double>(sampleRate_), maxBlockSize_);

    std::lock_guard<std::mutex> lock(chainMutex_);

    int idx = std::min(slotIndex, static_cast<int>(bus.plugins.size()));
    bus.plugins.insert(bus.plugins.begin() + idx, std::unique_ptr<SnapinProcessor>(proc));

    LOGD("Added plugin type %d to bus %d slot %d", pluginType, busIndex, idx);
    return idx;
}

void DspEngine::removePlugin(int busIndex, int slotIndex) {
    if (busIndex < 0 || busIndex >= TOTAL_BUSES) return;
    Bus& bus = buses_[busIndex];
    if (slotIndex < 0 || slotIndex >= static_cast<int>(bus.plugins.size())) return;

    std::lock_guard<std::mutex> lock(chainMutex_);
    bus.plugins.erase(bus.plugins.begin() + slotIndex);
    LOGD("Removed plugin from bus %d slot %d", busIndex, slotIndex);
}

void DspEngine::movePlugin(int busIndex, int fromSlot, int toSlot) {
    if (busIndex < 0 || busIndex >= TOTAL_BUSES) return;
    Bus& bus = buses_[busIndex];
    int sz = static_cast<int>(bus.plugins.size());
    if (fromSlot < 0 || fromSlot >= sz || toSlot < 0 || toSlot >= sz) return;
    if (fromSlot == toSlot) return;

    std::lock_guard<std::mutex> lock(chainMutex_);
    auto plugin = std::move(bus.plugins[fromSlot]);
    bus.plugins.erase(bus.plugins.begin() + fromSlot);
    bus.plugins.insert(bus.plugins.begin() + toSlot, std::move(plugin));
}

void DspEngine::setParameter(int busIndex, int slotIndex, int paramIndex, float value) {
    if (busIndex < 0 || busIndex >= TOTAL_BUSES) return;
    Bus& bus = buses_[busIndex];
    if (slotIndex < 0 || slotIndex >= static_cast<int>(bus.plugins.size())) return;
    if (bus.plugins[slotIndex]) {
        bus.plugins[slotIndex]->setParameter(paramIndex, value);
    }
}

void DspEngine::setPluginBypassed(int busIndex, int slotIndex, bool bypassed) {
    if (busIndex < 0 || busIndex >= TOTAL_BUSES) return;
    Bus& bus = buses_[busIndex];
    if (slotIndex < 0 || slotIndex >= static_cast<int>(bus.plugins.size())) return;
    if (bus.plugins[slotIndex]) {
        bus.plugins[slotIndex]->setBypassed(bypassed);
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────

bool DspEngine::anySoloed() const {
    for (int i = 0; i < NUM_MIX_BUSES; i++) {
        if (buses_[i].soloed) return true;
    }
    return false;
}

void DspEngine::recalcBusGains(Bus& bus) {
    float linear = (bus.gainDb <= -100.0f) ? 0.0f
        : std::pow(10.0f, bus.gainDb / 20.0f);

    // Equal-power pan law
    float panNorm = (bus.pan + 1.0f) * 0.5f;  // 0..1
    bus.targetGainL = linear * std::cos(panNorm * 1.5707963f);  // pi/2
    bus.targetGainR = linear * std::sin(panNorm * 1.5707963f);
}

// ── State serialization (simple JSON) ───────────────────────────────────

std::string DspEngine::getStateJson() const {
    std::lock_guard<std::mutex> lock(const_cast<std::mutex&>(chainMutex_));
    std::ostringstream ss;
    ss << "{\"buses\":[";
    for (int b = 0; b < TOTAL_BUSES; b++) {
        const Bus& bus = buses_[b];
        if (b > 0) ss << ",";
        ss << "{\"gain\":" << bus.gainDb
           << ",\"pan\":" << bus.pan
           << ",\"muted\":" << (bus.muted ? "true" : "false")
           << ",\"soloed\":" << (bus.soloed ? "true" : "false")
           << ",\"plugins\":[";
        for (int p = 0; p < static_cast<int>(bus.plugins.size()); p++) {
            if (p > 0) ss << ",";
            auto& plug = bus.plugins[p];
            ss << "{\"type\":" << static_cast<int>(plug->getType())
               << ",\"bypassed\":" << (plug->isBypassed() ? "true" : "false")
               << ",\"params\":[";
            for (int i = 0; i < plug->getNumParameters(); i++) {
                if (i > 0) ss << ",";
                ss << plug->getParameter(i);
            }
            ss << "]}";
        }
        ss << "]}";
    }
    ss << "]}";
    return ss.str();
}

void DspEngine::loadStateJson(const std::string& json) {
    // Simple parser — handles the format produced by getStateJson()
    // For robustness, a proper JSON library could be used, but we keep
    // dependencies minimal in the native DSP module.

    std::lock_guard<std::mutex> lock(chainMutex_);

    // Clear all buses
    for (int b = 0; b < TOTAL_BUSES; b++) {
        buses_[b].plugins.clear();
        buses_[b].gainDb = 0.0f;
        buses_[b].pan = 0.0f;
        buses_[b].muted = false;
        buses_[b].soloed = false;
    }

    // Minimal JSON parsing
    size_t pos = 0;
    auto findNext = [&](const std::string& key) -> size_t {
        size_t found = json.find(key, pos);
        return found;
    };

    auto readFloat = [&](size_t start) -> float {
        size_t end = json.find_first_of(",]}", start);
        if (end == std::string::npos) return 0.0f;
        return std::stof(json.substr(start, end - start));
    };

    auto readBool = [&](size_t start) -> bool {
        return json.substr(start, 4) == "true";
    };

    int busIdx = 0;
    pos = 0;

    while (pos < json.size() && busIdx < TOTAL_BUSES) {
        size_t busStart = json.find("{\"gain\":", pos);
        if (busStart == std::string::npos) break;

        pos = busStart + 8;
        buses_[busIdx].gainDb = readFloat(pos);

        size_t panPos = json.find("\"pan\":", pos);
        if (panPos != std::string::npos) {
            buses_[busIdx].pan = readFloat(panPos + 6);
            pos = panPos + 6;
        }

        size_t mutedPos = json.find("\"muted\":", pos);
        if (mutedPos != std::string::npos) {
            buses_[busIdx].muted = readBool(mutedPos + 8);
            pos = mutedPos + 8;
        }

        size_t soloedPos = json.find("\"soloed\":", pos);
        if (soloedPos != std::string::npos) {
            buses_[busIdx].soloed = readBool(soloedPos + 9);
            pos = soloedPos + 9;
        }

        // Parse plugins array
        size_t pluginsPos = json.find("\"plugins\":[", pos);
        if (pluginsPos != std::string::npos) {
            pos = pluginsPos + 11;

            while (pos < json.size()) {
                size_t typePos = json.find("\"type\":", pos);
                if (typePos == std::string::npos || typePos > json.find("]}", pos)) break;

                pos = typePos + 7;
                int plugType = static_cast<int>(readFloat(pos));

                auto* proc = createSnapin(static_cast<SnapinType>(plugType));
                if (proc) {
                    proc->prepare(static_cast<double>(sampleRate_), maxBlockSize_);

                    size_t bypPos = json.find("\"bypassed\":", pos);
                    if (bypPos != std::string::npos) {
                        proc->setBypassed(readBool(bypPos + 11));
                        pos = bypPos + 11;
                    }

                    size_t paramsPos = json.find("\"params\":[", pos);
                    if (paramsPos != std::string::npos) {
                        pos = paramsPos + 10;
                        int paramIdx = 0;
                        while (pos < json.size() && json[pos] != ']') {
                            if (json[pos] == ',' || json[pos] == ' ') { pos++; continue; }
                            float val = readFloat(pos);
                            proc->setParameter(paramIdx++, val);
                            size_t next = json.find_first_of(",]", pos);
                            if (next == std::string::npos) break;
                            pos = next;
                            if (json[pos] == ',') pos++;
                        }
                    }

                    buses_[busIdx].plugins.push_back(std::unique_ptr<SnapinProcessor>(proc));
                }

                // Advance past this plugin object
                size_t endBrace = json.find("}", pos);
                if (endBrace == std::string::npos) break;
                pos = endBrace + 1;
            }
        }

        busIdx++;
    }

    LOGD("Loaded state JSON, %d buses parsed", busIdx);
}
