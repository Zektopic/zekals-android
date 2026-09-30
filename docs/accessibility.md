# Access and speech

Use touch, a physical keyboard, TalkBack, Android Switch Access, pointer hover
dwell, or the built-in scanning mode. Settings cycles access mode; Space/Enter
select the scanned button and Escape pauses. Pause, Scroll up and Scroll down stay
outside the scroll area. Text and settings use system font scaling; large-text
and contrast preferences are also available. Buttons have at least 64 dp height.

Compose messages with the device IME or character boards. Clear has a 30-edit Undo
history. Grapheme deletion uses Android ICU. Chinese has common characters and
phrases; use a full system IME for unrestricted Pinyin input. Sinhala provides
letters and combining signs, but speech depends on an installed Sinhala engine.

Speech uses Android TextToSpeech and only selects voices marked offline, matching
the chosen language. Available voices are ordered by reported quality, then name.
Use Voice to choose another. No fallback speaks a different language. Install
voices through the device's TTS settings; engine packages have their own privacy,
licenses and network behavior. No voice model is bundled with the basic app.

Speech stops and automatic input pauses when the activity leaves the foreground.
Messages and undo are held in memory. Rotation retains only the current message
through Android's in-process non-configuration instance; process death clears it.
Preferences persist; messages are excluded from Android view-state saving and app
backup. Test this behavior on the target OS and with the user's accessibility tools.
