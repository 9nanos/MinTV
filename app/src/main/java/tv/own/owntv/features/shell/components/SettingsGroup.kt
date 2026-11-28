package tv.own.owntv.features.shell.components

import tv.own.owntv.R
import tv.own.owntv.features.settings.VideoGroup

/**
 * The twelve Settings groups, in the order of More › Settings' cards — a card's index is the
 * group it opens. [video] marks the five groups whose rows are the Video player's.
 */
internal enum class SettingsGroup(val titleRes: Int, val video: VideoGroup? = null) {
    QUICK(R.string.settings_group_quick),
    PROFILE(R.string.settings_group_profile),
    SOURCES(R.string.settings_group_sources),
    APPEARANCE(R.string.settings_group_appearance),
    LAYOUT(R.string.settings_group_layout),
    CONTENT(R.string.settings_group_content_metadata),
    PLAYER(R.string.settings_vp_cat_player, VideoGroup.PLAYER),
    PICTURE(R.string.settings_vp_cat_picture, VideoGroup.PICTURE),
    SOUND(R.string.settings_group_sound_subtitles, VideoGroup.SOUND),
    LIVE(R.string.settings_live_tv, VideoGroup.LIVE),
    WATCHING(R.string.settings_group_watching_recording, VideoGroup.WATCHING),
    APP(R.string.settings_group_app),
    SPORT(R.string.mintv_my_hockey),
    ;

    companion object {
        /** The group a Video player row lives in; Diagnostics sits at the end of App. */
        fun of(group: VideoGroup): SettingsGroup = entries.firstOrNull { it.video == group } ?: APP
    }
}

/**
 * Min TV 0.21: the five sections the category column shows. Each is one page listing its groups' rows
 * one after another under the group's heading — no extra level. [SettingsGroup] ordinals stay the
 * stable deep-link and focus-restore identity; a section is only how they are presented.
 */
internal enum class SettingsSection(val titleRes: Int, val groups: List<SettingsGroup>) {
    PROFILE(R.string.mintv_v021_section_profile, listOf(SettingsGroup.PROFILE, SettingsGroup.QUICK)),
    SOURCES(R.string.mintv_v021_section_sources, listOf(SettingsGroup.SOURCES, SettingsGroup.CONTENT)),
    LOOK(R.string.mintv_v021_section_look, listOf(SettingsGroup.APPEARANCE, SettingsGroup.LAYOUT)),
    PLAYBACK(R.string.mintv_v021_section_playback, listOf(SettingsGroup.PLAYER, SettingsGroup.PICTURE,
        SettingsGroup.SOUND, SettingsGroup.LIVE, SettingsGroup.WATCHING)),
    SYSTEM(R.string.mintv_v021_section_system, listOf(SettingsGroup.SPORT, SettingsGroup.APP)),
    ;

    companion object {
        fun of(group: SettingsGroup): SettingsSection = entries.first { group in it.groups }
    }
}

