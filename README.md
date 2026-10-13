# Eden TH

A personal build of [Eden](https://git.eden-emu.dev/eden-emu/eden), the open-source Nintendo Switch
emulator, made from its official source by GitHub Actions.

- `patches/apply.py` - the only changes: its own package (`dev.eden.eden_emulator.th`, installs beside
  the official app), the name "Eden TH", the GPU driver manager on Mali phones as well as Adreno,
  and motion effects in the game list: cards lean with the phone, swing with the scroll and dip
  under the finger through a spring; the carousel turns its side cards (cover flow); a glow behind
  the list drifts with the tilt; one switch for all of it in the view menu. New files are in
  `patches/files`.
- The Eden version is pinned in the workflow, so the patches always meet the code they were written
  for.
- `.github/workflows/build.yml` - clones the official source, applies the patches, builds the
  Android APK and publishes it as the `latest` release here.

Eden needs `prod.keys`, firmware and games dumped from a Switch you own. None are included or
downloaded by this repository.

Eden is GPL-3.0-or-later; so is everything here.
