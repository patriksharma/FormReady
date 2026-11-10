# FormReady

Resize photos, signatures and PDFs to an exact KB size for online application forms.
Kotlin · Jetpack Compose · Material 3 · MVVM + use cases · Hilt · fully offline core.

---

## 1. Open and run

1. Android Studio → **Open** → select this folder. Let Gradle sync (it generates the wrapper jar).
2. Run on a device or emulator, **API 29+** (`minSdk 29` — deliberate: MediaStore saves need no storage permission at all above API 28).
3. It builds and runs as-is. Ads use Google's test IDs, billing shows fallback prices until you wire Play Console, and Google sign-in stays dormant until you add `google-services.json`.

---

## 2. What is implemented

| Area | Status |
|---|---|
| Photo resize + exact KB targeting | Complete — pick, crop, px/mm/cm/in, presets, binary-search compression, preview, save, share |
| Signature resize | Complete — same engine plus local trim + background-whitening passes |
| Image → PDF | Complete — multi-select, reorder, A4/Letter/original, orientation, quality, optional KB ceiling |
| PDF compression | Complete — original vs compressed size, % saved, optional KB ceiling |
| Themes | 6 free + 3 premium palettes, light/dark/system, Material You — all switchable in Settings |
| Monetization | Play Billing 7 (one-time products only), 3 free exports/day, banner ads, restore purchases |
| Analytics | 8 events, no file contents or personal data, no-op when Firebase is absent |
| Optional Google sign-in | Credential Manager + Firebase Auth, in Settings, gates nothing |

The core (resize, compress, PDF, KB targeting) works with the device in airplane mode. No login, no account, no backend, no cloud, no AI API.

---

## 3. Architecture

```
core/     image/  ImageLoader · SizeTargeter · SignatureCleaner · Units
          pdf/    ImagesToPdfBuilder · PdfCompressor · PageSize/PdfQuality
          storage/FileExporter (MediaStore save + FileProvider share)
data/     prefs/ SettingsRepository (DataStore: theme, DPI, quota, entitlements, custom presets)
          billing/ BillingManager + Entitlements + Products
          auth/ AuthRepository      analytics/ Analytics      presets/ PresetRepository
domain/   model/ PhotoJob · SignatureJob · ExportTool
          usecase/ ResizeImageUseCase · BuildPdfUseCase · CompressPdfUseCase · ExportGate
ui/       theme · navigation · home · imagetool (shared photo/signature body) · photo ·
          signature · imagetopdf · pdfcompress · paywall · settings · components · ads
```

Every screen: `@HiltViewModel` → `StateFlow<UiState>` → stateless composable. All image and PDF work runs on `Dispatchers.Default`; nothing touches the main thread.

**The core feature — `core/image/SizeTargeter.kt`:**

1. Decode with `inSampleSize` against a 12 MP budget, EXIF rotation applied (`ImageLoader`).
2. Scale to the requested W×H.
3. Binary-search JPEG quality between 20 and 95 — about 7 encodes into memory, never to disk.
4. If quality 1 still overshoots, step dimensions down 10% and search again, up to 12 passes.
5. Return `withinLimit` so the UI can say *Target: 50 KB · Output: 48.7 KB*, or admit honestly when the limit is impossible.

`ExportGate` is the single place that decides whether an export may happen (free = 3/day) and records success/failure.

---

## 4. Before you publish

### AdMob
- `AndroidManifest.xml` → replace the `com.google.android.gms.ads.APPLICATION_ID` test value.
- `ui/ads/AdBanner.kt` → replace `TEST_BANNER_UNIT_ID`.
- Leave the test IDs in debug builds. Live ads on a debug build risks a policy strike.

### Play Billing
Create three **one-time (in-app) products** in Play Console with exactly these IDs:

| Product ID | Price | Unlocks |
|---|---|---|
| `remove_ads_30_days` | ₹79 | Ads off for 30 days |
| `lifetime_unlimited` | ₹199 | Unlimited exports + no ads, forever |
| `lifetime_premium_templates` | ₹299 | The above + premium presets and themes |

Prices in the paywall come from Play once the products are live; `Products.fallbackPrice()` is only a placeholder. Billing needs a build uploaded to at least internal testing and a licence-tester account — it does nothing on a plain debug build.

### Firebase (optional — only for Google sign-in and analytics)
1. Firebase console → add an Android app with package `com.formready.resizer`.
2. Add your debug and release SHA-1 fingerprints (required for Google sign-in).
3. Download `google-services.json` into `app/`.

The Gradle script detects the file and switches the plugin on by itself; `BuildConfig.FIREBASE_CONFIGURED` follows. Without it, `Analytics` no-ops and Settings shows that sign-in is off. Nothing else changes.

### Play Data Safety
Declare: no data collected if you ship without Firebase. With Firebase: analytics identifiers and, if the user signs in, email/name — both optional and not linked to file contents. Photos, signatures and PDFs never leave the device; say so on the listing, it converts well.

---

## 5. Test pass

**Core KB targeting**
- Photo → Select photo → preset *Application photo 200x230* → Resize & compress. Result must read `Target: 50 KB` / `Output: ~48 KB`.
- Set 10 KB on a 12 MP photo — the downscale fallback kicks in and the output still lands under 10 KB.
- Set 2000×2000 at 5 KB — expect the red "could not reach that limit" line, not a silent overshoot.
- Rotate a portrait photo shot on a phone; EXIF orientation must survive.

**Signature** — photograph a signature on white paper, turn on trim + whiten, target 20 KB.

**Image → PDF** — add 5 images, reorder with the arrows, A4 portrait, 200 KB ceiling.

**PDF compress** — feed it a scanned PDF; check the "% saved" row. On an already-optimised PDF the output can be *larger*; the app says so rather than pretending.

**Monetization** — export 3 times, then export again: the daily-limit dialog should appear and route to the paywall.

**Themes** — Settings → change palette and dark mode; it applies instantly and survives a restart. Locked palettes show a padlock.

---

## 6. Known trade-offs

- **PDF compression rasterises pages**, so text stops being selectable. That is the price of avoiding an AGPL PDF library; fine for form uploads, and the UI warns the user to keep the original.
- **The 30-day ad removal is tracked locally** (Play consumes the purchase). Reinstalling loses the remaining days. The two lifetime products restore properly via `queryPurchasesAsync`.
- **Entitlements are stored on-device.** Adequate for these price points; add a server check only if piracy actually shows up in the numbers.
- **UI strings are hardcoded in the composables.** Extract to `strings.xml` when you localise — worth doing for a worldwide listing.

---

## 7. Sensible next steps

Onboarding on first launch · an interstitial after every N exports (banner-only is currently the safer choice) · in-app rating prompt after a successful export · drag-to-reorder in the PDF list · batch mode for multiple photos at once.
