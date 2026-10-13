# Eden TH

A personal build of [Eden](https://git.eden-emu.dev/eden-emu/eden), the open-source Nintendo Switch
emulator, made from its official source by GitHub Actions.

- `patches/apply.py` - the only changes: its own package (`dev.eden.eden_emulator.th`, installs beside
  the official app), the name "Eden TH", and the GPU driver manager on Mali phones as well as Adreno.
- `.github/workflows/build.yml` - clones the official source, applies the patches, builds the
  Android APK and publishes it as the `latest` release here.

Eden needs `prod.keys`, firmware and games dumped from a Switch you own. None are included or
downloaded by this repository.

Eden is GPL-3.0-or-later; so is everything here.
