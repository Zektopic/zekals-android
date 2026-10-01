# Language and voice expansion

Packs live in `app/src/main/assets/languages/`. English, French, Simplified Chinese,
Italian, Sinhala and Greek use the desktop schema version 1. The first five contain
complete desktop UI keys; Greek retains some English fallback. Android adds
`cameraControl`, `inference` and `cancelCalibration`. Translations require native
speaker/user review before being marked reviewed.

Copy the example in `examples/language-pack/es.json`, change its identity/locale,
translate UI strings and phrases, and define keyboard rows and extra pages. The
example is incomplete and is not shipped as Spanish support. Set `direction` for
right-to-left packs. Keep files below 64 KiB and bounded keys/phrases as documented
by `scripts/validate_packs.py`. Run that script and rebuild; asset discovery does
not require Java edits. Do not place markup or executable actions inside keys.

Use the system IME for unrestricted Chinese entry. The virtual board deliberately
contains a small set of common Hanzi. Sinhala combining marks show a dotted-circle
label while inserting only the actual mark; Join letters inserts a zero-width
joiner. Deletion uses Android ICU grapheme boundaries, which must be checked on the
minimum supported OS and with native conjunct examples.

The pack's locale selects matching installed Android voices. Piper metadata is
shared documentation for the desktop and is not a mobile neural model. Natural
voice quality depends on the installed Android engine/voice. Sinhala requires a
Sinhala-capable installed offline voice; none is bundled. Do not silently choose
English or send text to a network voice when a requested voice is absent.
