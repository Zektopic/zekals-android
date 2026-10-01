# Access and speech

Use touch, a physical keyboard, TalkBack, Android Switch Access, pointer hover
dwell, the built-in scanning mode or eye tracking (Vision). Settings cycles access
mode and the choice persists. Pause, Scroll up and Scroll down stay outside the
scroll area. Text and settings use system font scaling; large-text and contrast
preferences are also available. Buttons have at least 64 dp height, and buttons in
a row share one height so wrapped labels do not misalign them.

The app runs in landscape only (either way up). On wide screens (900 dp and up)
the message, speech controls and phrases sit beside the keyboard so the whole board
fits without scrolling. Narrower screens stack them and pin a two-line copy of the
message above the scroll area whenever the message box is scrolled out of view.

Single-switch scanning is row/column: rows are highlighted in turn, Space or Enter
opens the highlighted row, then its buttons are scanned and the next press chooses
one. A silent pass through a row returns to row scanning on the same row; after a
choice scanning stays on that row so repeated letters are quick. Escape pauses in
every mode and the next switch press resumes without choosing anything. Switch keys
are read before Android's focus handling, so they work even while a button has
keyboard focus or the screen was just touched.

Compose messages with the device IME or character boards. Clear has a 30-edit Undo
history. Delete removes a combining sign or joiner on its own, because each is a
separate key, and otherwise removes a whole grapheme (Android ICU), so emoji and
Hanzi delete as one character. Chinese has common characters and phrases; use a
full system IME for unrestricted Pinyin input. Sinhala provides letters and
combining signs, but speech depends on an installed Sinhala engine.

Settings shows the current dwell time, scan interval, speech speed and voice next
to their controls. Speech uses Android TextToSpeech and only selects voices marked
offline and installed, matching the chosen language. Voices are listed by accent
(for example "English (United States) · 2/6") and the language name is spoken as a
preview when you switch; a phrase such as "Yes" is never used as a preview because
it could be mistaken for an answer. Install voices opens the engine's voice data
screen. No fallback speaks a different language. Speech plays on the media stream,
so the status line warns when media volume is muted.

In Eye tracking a pointer follows the calibrated gaze and fills a ring as dwell
progresses. Without a calibration there is nothing to draw, and the app says so.
The camera keeps the screen on while it runs, restarts when the app returns to the
foreground, and the calibration is kept until the camera is stopped from Settings
or the inference provider changes. Calibration is tied to the screen size and the
way up the device is held; flipping it asks for a new calibration.

Speech stops and automatic selection pauses when the app leaves the foreground;
rotation and the camera permission prompt do not pause it. Messages and undo are
held in memory. Rotation retains the message and pause state through Android's
in-process non-configuration instance; process death clears the message.
Preferences persist; messages are excluded from Android view-state saving and app
backup. Test this behavior on the target OS and with the user's accessibility tools.
