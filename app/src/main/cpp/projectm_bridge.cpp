#include "projectm_bridge.h"

#include <algorithm>
#include <cstring>

namespace {
constexpr int kDefaultFps = 60;
}

ProjectMBridge::ProjectMBridge(
        std::string asset_root,
        std::string preset_root,
        std::string texture_root,
        int width,
        int height,
        int mesh_width,
        int mesh_height)
        : asset_root_(std::move(asset_root)),
          preset_root_(std::move(preset_root)),
          texture_root_(std::move(texture_root)) {
    projectm_ = projectm_create();
    if (projectm_ == nullptr) {
        return;
    }

    playlist_ = projectm_playlist_create(projectm_);
    if (playlist_ == nullptr) {
        projectm_destroy(projectm_);
        projectm_ = nullptr;
        return;
    }

    projectm_set_window_size(projectm_, width, height);
    projectm_set_mesh_size(projectm_, mesh_width, mesh_height);
    projectm_set_fps(projectm_, kDefaultFps);
    projectm_set_aspect_correction(projectm_, true);
    projectm_set_beat_sensitivity(projectm_, 1.0f);
    projectm_set_preset_duration(projectm_, 20.0);
    projectm_set_soft_cut_duration(projectm_, 2.0);
    projectm_set_hard_cut_enabled(projectm_, true);
    projectm_set_hard_cut_duration(projectm_, 10.0);

    const char* texture_paths[] = {texture_root_.c_str()};
    projectm_set_texture_search_paths(projectm_, texture_paths, 1);
    projectm_playlist_add_path(playlist_, preset_root_.c_str(), true, false);
    projectm_playlist_set_shuffle(playlist_, true);

    if (projectm_playlist_size(playlist_) > 0) {
        projectm_playlist_set_position(playlist_, 0, true);
        current_preset_ = ReadCurrentPreset();
    }
}

ProjectMBridge::~ProjectMBridge() {
    if (playlist_ != nullptr) {
        projectm_playlist_destroy(playlist_);
        playlist_ = nullptr;
    }
    if (projectm_ != nullptr) {
        projectm_destroy(projectm_);
        projectm_ = nullptr;
    }
}

bool ProjectMBridge::IsReady() const {
    return projectm_ != nullptr && playlist_ != nullptr;
}

void ProjectMBridge::Resize(int width, int height) {
    if (projectm_ != nullptr) {
        projectm_set_window_size(projectm_, width, height);
    }
}

void ProjectMBridge::RenderFrame(int64_t /*frame_time_nanos*/) {
    if (!IsReady() || paused_) {
        return;
    }
    PushBufferedAudioToProjectM();
    projectm_opengl_render_frame(projectm_);
}

void ProjectMBridge::PushPcm(const float* data, size_t count, int channel_count, int sample_rate) {
    if (!IsReady() || data == nullptr || count == 0) {
        return;
    }
    audio_buffer_.Push(data, count, channel_count, sample_rate);
}

bool ProjectMBridge::SetPreset(const std::string& preset_path) {
    if (!IsReady()) {
        return false;
    }
    const auto resolved = FindPresetPath(preset_path);
    if (resolved.empty()) {
        return false;
    }

    const auto playlist_size = projectm_playlist_size(playlist_);
    for (uint32_t index = 0; index < playlist_size; ++index) {
        char* item = projectm_playlist_item(playlist_, index);
        if (item == nullptr) {
            continue;
        }
        std::string item_path(item);
        projectm_playlist_free_string(item);
        if (item_path == resolved) {
            projectm_playlist_set_position(playlist_, index, true);
            current_preset_ = item_path;
            return true;
        }
    }

    projectm_load_preset_file(projectm_, resolved.c_str(), true);
    current_preset_ = resolved;
    return true;
}

std::string ProjectMBridge::NextPreset() {
    if (!IsReady()) {
        return {};
    }
    projectm_playlist_play_next(playlist_, true);
    current_preset_ = ReadCurrentPreset();
    return current_preset_;
}

void ProjectMBridge::SetShuffle(bool enabled) {
    if (playlist_ != nullptr) {
        projectm_playlist_set_shuffle(playlist_, enabled);
    }
}

void ProjectMBridge::SetBeatSensitivity(int value) {
    if (projectm_ != nullptr) {
        const auto scaled = 0.25f + (static_cast<float>(std::clamp(value, 0, 100)) / 100.0f) * 1.75f;
        projectm_set_beat_sensitivity(projectm_, scaled);
    }
}

void ProjectMBridge::SetBrightness(int value) {
    brightness_ = std::clamp(value, 0, 100);
}

void ProjectMBridge::SetPaused(bool paused) {
    paused_ = paused;
}

void ProjectMBridge::SetQuality(int mesh_width, int mesh_height) {
    if (projectm_ != nullptr) {
        projectm_set_mesh_size(projectm_, mesh_width, mesh_height);
    }
}

void ProjectMBridge::SetFps(int fps) {
    if (projectm_ != nullptr) {
        projectm_set_fps(projectm_, fps);
    }
}

void ProjectMBridge::SetPresetDuration(int seconds) {
    if (projectm_ == nullptr) {
        return;
    }
    const auto clamped = std::clamp(seconds, 5, 120);
    projectm_set_preset_duration(projectm_, static_cast<double>(clamped));
    projectm_set_soft_cut_duration(projectm_, std::min(3.0, clamped / 4.0));
    projectm_set_hard_cut_enabled(projectm_, true);
    projectm_set_hard_cut_duration(projectm_, std::max(5.0, clamped * 0.75));
}

std::string ProjectMBridge::CurrentPreset() const {
    return current_preset_;
}

std::string ProjectMBridge::FindPresetPath(const std::string& preset_path) const {
    if (preset_path.empty() || playlist_ == nullptr) {
        return {};
    }
    const auto playlist_size = projectm_playlist_size(playlist_);
    for (uint32_t index = 0; index < playlist_size; ++index) {
        char* item = projectm_playlist_item(playlist_, index);
        if (item == nullptr) {
            continue;
        }
        std::string item_path(item);
        projectm_playlist_free_string(item);
        if (item_path == preset_path) {
            return item_path;
        }
    }
    return {};
}

std::string ProjectMBridge::ReadCurrentPreset() const {
    if (playlist_ == nullptr) {
        return {};
    }
    const auto index = projectm_playlist_get_position(playlist_);
    char* item = projectm_playlist_item(playlist_, index);
    if (item == nullptr) {
        return {};
    }
    std::string result(item);
    projectm_playlist_free_string(item);
    return result;
}

void ProjectMBridge::PushBufferedAudioToProjectM() {
    if (projectm_ == nullptr) {
        return;
    }

    std::vector<float> samples;
    int channel_count = 2;
    int sample_rate = 44100;
    if (!audio_buffer_.Pop(samples, channel_count, sample_rate) || samples.empty()) {
        return;
    }

    (void) sample_rate;
    const unsigned int per_channel_count = static_cast<unsigned int>(samples.size() / std::max(channel_count, 1));
    projectm_pcm_add_float(
            projectm_,
            samples.data(),
            per_channel_count,
            channel_count == 1 ? PROJECTM_MONO : PROJECTM_STEREO
    );
}
