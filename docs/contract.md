# STEAL BATTLE — Shared Build Contract v1.0

**Status: FROZEN.** No builder changes this file. Ambiguities go to the
coordinator, who amends the contract and re-broadcasts. Code that contradicts
this file fails conformance review, no matter how good it is.

**Game:** Steal Battle (Brainrot Buddies franchise, game 01). Kids 6-12.
**Repo:** `rootforsree/brainrot-buddies-steal-battle` (public).
**Package:** `com.sreecharan.brainrot_buddies_steal_battle`
**Stack:** Kotlin 2.4.20, AGP 9.4.1, Gradle 9.8.0, Compose BOM 2026.06.00
(template-pinned), targetSdk 36, minSdk 26, Hilt 2.60.1 (KSP), Room 2.8.5,
Preferences/Proto DataStore, Navigation Compose type-safe routes.
Single app module, feature packages (no module split).

**New dependencies (already in `gradle/libs.versions.toml`):**
- `play-services-nearby:19.3.0` → `libs.play.services.nearby`
- `play-services-ads:23.6.0` → `libs.play.services.ads`
- `billing-ktx:8.0.0` → `libs.billing.ktx`

## 0. Non-negotiable rules

1. **NO SERVER. NO BACKEND. NO MATCHMAKING SERVICE. ZERO RUNNING COSTS.**
   Everything single-player is fully offline. Multiplayer is nearby-friends
   P2P only via Google Play Services Nearby Connections (Bluetooth/WiFi, no
   internet). There is no Firebase, no Retrofit/Ktor usage, no WebSocket, no
   account system. If a feature needs a server, it is CUT (see §3).
2. **NEVER run `./gradlew` on this machine.** Every local Gradle invocation
   pops approval cards on the client's phone. GitHub Actions is the ONLY
   build runner. Write code, coordinator pushes, CI compiles.
3. **KSP incremental is OFF** (`ksp.incremental=false` in gradle.properties).
   The template's CI hit the known KSP incremental-cache bug
   (`JsonDecodingException: EOF` in `IncrementalContextBase` on
   `:app:kspReleaseKotlin`). Full KSP runs are deterministic. Do not
   re-enable without a KSP upgrade that documents the fix.
4. **Skill bar is law:** `android-app-architect` (Kotlin ban list, Compose
   discipline, MVVM/UDF, Hilt, Room offline-first, quality gates on
   `./gradlew check`) and `github-workflow` (CI/build/release, SHA-pinned
   actions, secrets already installed).
5. **Families policy + COPPA are designed in, not bolted on.** No AAID
   (stripped at manifest merge), no IMEI/MAC/location, no persistent device
   identifiers anywhere, no analytics SDK in v1, parent gate on all
   purchases and social surfaces, For Parents screen, data-deletion button.
6. **Research bibles are the v1 bar.** `audio_bible.md`, `animation_bible.md`,
   `virality_playbook.md`, `kids_ux_bible.md` (in
   `~/workspace/brainrot-app/research/`) are normative. Their specs appear
   below as §§8-11. Where a bible conflicts with the design doc's pacing,
   the bible wins; the superseded design-doc section is marked below.

## 1. V1 scope (frozen — build exactly this)

### Core loop (fully offline)
- Conveyor spawner: one buddy every 8s, rides ~15s, tap-to-claim.
  Pull weights: **Common 70% / Rare 22% / Epic 8%**. No Legendary/Mythic pulls.
- Tap-to-claim with confetti + catchphrase (§8, §9 timings).
- Base grid: 24 slots max, start with 4. Waddle-in animation, idle-life
  system on every buddy (§9.3).
- Vault: coins pool, **4-hour cap**, fill meter, collect button with coin
  cascade (§8).
- Shop: base slots, Lucky/Mega tickets, shields, buddy upgrades.
- Upgrades L1→L3 only. Multipliers: L1 1x, L2 1.5x, L3 2.2x.
  Cost: coins = 100 × baseCps × (targetLevel−1); bites = 5 × (targetLevel−1).
  (Common→L2: 100 coins + 5 bites. Rare→L3: 1600 + 10. Epic→L3: 8000 + 10.)
- Album/sticker book: 34 buddies + 10 silhouettes (6 Legendary + 4 Mythic,
  "coming soon"). Tier completion: Commons 500 / Rares 2,500 / Epics 10,000.
- Duplicates → **5 Buddy Bites** each. **20 Bites = re-roll** one conveyor
  pull's rarity up one tier.
- Pity (Luck Meter, visible): 25 Commons without Rare+ → guaranteed Rare;
  60 pulls without Epic → guaranteed Epic.
- Lucky Ticket (earn 1/day from daily gift; never sold): next pull min Rare.
  Mega Ticket (2,500 coins): next pull min Epic. **No Super Ticket in v1**
  (no Legendaries exist). Tickets bought with coins or earned. NEVER for
  real money.

### Onboarding (SUPERSEDES design doc §"First 10 minutes")
The complexity ladder (kids_ux_bible) replaces the doc's front-loaded
tutorial. Systems unlock one per day, each via a gift moment, only when
the need is felt.
- **First launch:** splash (no stall) → "Play" → guest ID created on device →
  base naming (12 presets, §6) → Benny arrow: "Tap him! Quick!" → claim
  Frogboot → claim Pip McSnacks → buy slot (50 coins) → claim SPLORCH! →
  vault collect → daily gift day 1 → raid countdown widget visible. Hour 1 =
  claim, collect, buy slots. NOTHING else is visible.
- **Day 2:** Lucky Ticket arrives as a gift → ticket system learned by using.
- **Day 3:** Benny points at a buddy: "Feed him Bites, he makes MORE coins!"
  → upgrades L1-L3.
- **Day 4:** practice raid on a dummy base (steal a buddy, zero stakes) +
  shield tutorial → free 8-hour shield granted.
- **Day 5:** Album appears: "Look what you caught!"
- **Day 7:** first real raid window opens (friends only, P2P). Rookie Shield:
  steal-immune for the first 7 days (except practice mode).
- **Benny rules:** ≤8 words per tip, 1 tip per screen, every tip ends in an
  immediate action. After week 1 Benny goes silent unless tapped. He NEVER
  interrupts a raid, claim streak, or purchase.

### Raids (P2P-adapted, no server)
- Raid windows: local 6-hour cycle (8 AM / 2 PM / 8 PM / 2 AM device time).
  During a window: up to **3 raids** against friends.
- Targets: (a) LIVE friend base over Nearby (both nearby, connected) →
  **true steal**: buddy moves devices via RaidRequest/RaidResult (§7);
  (b) saved base **snapshot** (exchanged over Nearby when together) →
  **Shadow Heist**: steal succeeds, buddy joins YOUR base; victim's device
  learns on next P2P sync and gets revenge option + consolation. The victim
  never silently loses a buddy. One successful shadow heist per snapshot;
  snapshots refresh only on new P2P exchange.
- Steal ONE buddy per raid. Cannot steal shielded buddies or buddies on
  30-minute steal cooldown. Cannot raid the same friend twice per window.
  Revenge raids (24h window, free) do not count against the 3.
- Victim (live): "Sneaky Snout" replay card (comedy framing) + revenge
  button + consolation gift = 50% of stolen buddy's hourly earnings.
- Shields: base 8h = 500 coins; per-slot = 100 coins. Visible dome + countdown.
- Failed raid (all shielded): visit is REFUNDED (not consumed) + "nice try"
  coin gift. Comedy "bonk", never punishment.
- **CUT (need a server):** "3 random players" global matchmaking, weekly
  global Top-Bases leaderboards.

### Retention (v1)
- 30-day login calendar (Day 1: 200 coins; Day 7: Mega Ticket; Day 14:
  guaranteed Rare; Day 21: 5,000 coins; Day 30: guaranteed Epic). Missed day
  PAUSES, never resets. Catch-up day: 500 coins, optional.
- Streak badges (7/14/30/60/100) with kind break copy + consolation gift.
- Weekly surprise gift (Flippy Flappy Flip face-plant, up to Epic).
- Offline progress: 4h cap + return screen ("While you were away: +N coins!").
- Comeback: 3-day (1,000 coins + Lucky Ticket), 7-day (Benny recap +
  guaranteed Rare). 30-day tier is V2.
- Redeem codes (Settings): `STEALFUN26` = 500 coins + party emote;
  `BUDDYUP26` = 1 Lucky Ticket. One use per guest ID.
- Season 1: free track only, 6-week local-clock cycle (Monday-anchored
  epochs from 2026-01-05). NO new seasonal buddies in v1 (V2).

### Social (v1)
- Friends: 6-letter codes exchanged in person, mutual-confirm pairing
  (§7), behind parent gate. Practice visits anytime (no stealing).
- Pairing ritual: both sides get a gift + "first heist" ceremony on first
  mutual raid (virality).
- Kid-safe comms only: **12 preset phrases** (§6), **10 emotes** (§6).
  No free text, no voice, no stranger contact. Ever.
- Base tour mode: one-tap auto-camera pan across your base, buddies do idle
  acts + catchphrases. Built for holding the phone up to friends.
- Raid viewer mode: oversized visuals readable from 3 feet (virality).

### Monetization (v1)
- Rewarded ads: EXACTLY ONE placement (Shop "Free coins" button). Max 1 per
  30 min. Families self-certified SDK (AdMob 23.6.0) ONLY. G-rated,
  non-personalized (`npa=1`), `tagForChildDirectedTreatment(true)`,
  `max_ad_content_rating=G`. Closeable ≤5s. NEVER on launch. Starts muted
  with visible unmute. Skippable with zero penalty.
- IAP: Remove-ads $2.99 (one-time); 3 cosmetic hats $0.99 each (Party Cone,
  Tiny Crown, Propeller Cap — equip to any buddy, visual only). Parent gate
  (hold 3s + math question) on EVERY purchase. Paid IAP is deterministic;
  NOTHING random is purchasable. No loot boxes, no paid gacha, ever.
- Season 1 paid track: NOT in v1 (free track only).

### Settings / parental (v1)
- Parent gate (hold-to-confirm + math) on: IAP, ads toggle, friend codes,
  P2P pairing.
- Data controls: delete-all-data button (Room + DataStore + files), no
  account required to play.
- Audio: 3 separate sliders (Music / Sounds / Voices) + haptics toggle.
  Reachable in 2 taps, zero judgment copy.
- "For Parents" screen: plain-language data practices, ad policy, contact.
- Notifications: NOT in v1 (V2, with opt-in / max 1 per day / never
  sleep-or-school-hours per the playbook).

### UGC poster import "My Poster" (v1, local-only)
- Player picks a photo; app crops into a chunky frame hung in their base.
- Image lives in app-private internal storage ONLY. EXIF/GPS stripped on
  import. NEVER uploaded, synced, shared, traded, or shown to other players.
  Deleted with app/data-clear.
- Terms line: "Import only images you own." Never marketed or tutorialized
  as meme importing. COPPA: photo never leaves the device → no collection.

## 2. Virality v1 additions (serverless-compatible, from the playbook)
These are IN v1 scope (frozen):
- **V1.1 Staged steal/revenge auto-replays:** 5s cinematic after every steal
  and revenge (slow-mo grab, comedy caption, victim reaction, catchphrase
  sting). Staged for a parent to film. NO in-app recording, NO upload,
  NO share intent.
- **V1.2 Catchphrase hooks on every buddy:** claim + steal + idle triggers
  (§8). The "Roblox oof" effect.
- **V1.3 Saturday 11 AM local event** ("Zara's Zoom Race", simplified):
  20-minute conveyor sprint, pulls every 4s, double Epic chance, 128 BPM
  funk variation of the base loop. 24h countdown banner. Driven purely by
  local clock — each device runs its own instance; the shared schedule (not
  synced state) creates the playground conversation. Midnight Munchies is V2.
- **V1.4 Rarity flex visuals:** rainbow trails / golden glow / oversized
  slots for top buddies, readable across a room (Builder B).
- **V1.5 Villain cards + revenge ceremony:** named villain card
  ("Sneaky Snout took Sassy Soda!"), 24h countdown, versus screen.
- **V1.6 Mystery-pull ceremony:** Lucky/Mega Ticket pulls get drumroll,
  screen darken, slow reveal, escalating music, confetti physics.
- **V1.7 "Show a grown-up!" nudge:** after big moments (Epic pull, steal,
  revenge), a gentle parent-facing card. Never prompts the kid to post.
- **V1.8 Base tour mode** (see §1 Social).
- Ops/marketing items are OUT of build scope (official TikTok/Shorts
  channel, meme-template packs, YouTuber seeding kit — client's team).

## 3. CUT / deferred (do not build)
- V2: Legendaries + Mythics in pulls, upgrades L4-L5, trait mutations,
  rebirth prestige, paid season pass, full cosmetic catalog, Midnight
  Munchies, comeback 30-day tier, push notifications, friend practice-visit
  buddy bonus, seasonal buddies (4/season).
- V3+: Buddy Clubs, colorblind-safe rarity icons, one-tap motor-access mode.
- Never: global matchmaking, global leaderboards, free chat, share-to-social,
  contact invites, personalized ads, purchasable randomness, analytics SDK.

## 4. Frozen economy numbers

| Item | Value |
|---|---|
| Conveyor cadence / claim window | 8s spawn, 15s window |
| Pull weights (v1) | Common 70% / Rare 22% / Epic 8% |
| Coin rates L1 (coins/sec) | Common 1, Rare 8, Epic 40 |
| Upgrade multipliers | L1 1x, L2 1.5x, L3 2.2x |
| Upgrade cost | coins = 100 × baseCps × (targetLevel−1); bites = 5 × (targetLevel−1). (Amended 2026-10-16: the three worked examples in §1 are authoritative; the formula text was off by one level.) |
| Slot costs (20 unlocks) | 50, 100, 250, 500, 1000, 2500, 5000, 10000, 25000, 50000, 100000, then 150000 ×9 |
| Lucky Ticket | 1/day from daily gift; next pull min Rare |
| Mega Ticket | 2,500 coins; next pull min Epic |
| Duplicate → Bites | 5 bites per duplicate |
| Bites re-roll | 20 bites = +1 rarity tier on next pull |
| Pity | 25 Commons w/o Rare+ → Rare; 60 pulls w/o Epic → Epic |
| Shield: base 8h | 500 coins; per-slot: 100 coins |
| Steal cooldown per buddy | 30 min |
| Revenge window | 24h, free, 1 per theft |
| Consolation gift | 50% of stolen buddy's hourly earnings |
| Vault cap | 4h of current income |
| Offline cap | 4h |
| Raid windows | 8 AM / 2 PM / 8 PM / 2 AM local; 3 raids per window |
| Album completion | Commons 500 / Rares 2,500 / Epics 10,000 coins |
| Rewarded ad | 100 coins OR 5 bites; max 1 per 30 min |
| Remove-ads | $2.99; hats $0.99 ×3 (Party Cone, Tiny Crown, Propeller Cap) |
| Redeem codes | STEALFUN26 (500 coins + party emote), BUDDYUP26 (1 Lucky Ticket) |

## 5. Frozen data schemas

All Room entities live in `data/local/`. IDs are `@JvmInline value class`
wrappers (no type aliases). Room is the single source of truth; UI observes
DAO flows only. Schema version 1, `exportSchema = true`.

```kotlin
// Static catalog (code, NOT Room): BuddyCatalog.kt (Builder A)
enum class Rarity { COMMON, RARE, EPIC }            // v1 only
@JvmInline value class BuddyId(val value: String)   // e.g. "frogboot"
data class BuddyDef(
    val id: BuddyId,
    val name: String,            // display name, e.g. "Frogboot"
    val rarity: Rarity,
    val baseCoinsPerSec: Int,    // 1 / 8 / 40
    val imageRes: Int,           // R.drawable.buddy_frogboot
    val catchphrase: String,     // §6 list
    val voiceFile: String,       // res/raw/voice_frogboot (ogg/mp3)
    val blurb: String,           // one-line album text
)

@Entity(tableName = "owned_buddies")
data class OwnedBuddyEntity(
    @PrimaryKey val uid: String,            // UUID
    val buddyId: String,                    // BuddyId.value
    val level: Int = 1,                     // 1..3
    val hatId: String? = null,              // "party_cone"|"tiny_crown"|"propeller_cap"
    val acquiredAt: Long = 0L,              // epoch ms
    val stealCooldownUntil: Long = 0L,      // epoch ms
)

@Entity(tableName = "base_slots")
data class BaseSlotEntity(
    @PrimaryKey val slotIndex: Int,         // 0..23
    val unlocked: Boolean = false,          // 0..3 true at start
    val ownedBuddyUid: String? = null,
    val shieldUntil: Long = 0L,             // per-slot shield, epoch ms
)

@Entity(tableName = "vault")
data class VaultEntity(
    @PrimaryKey val id: Int = 1,            // singleton row
    val coins: Long = 0L,                   // pooled, uncollected
    val lifetimeCoins: Long = 0L,           // all-time earned (pity/stats)
    val lastTickAt: Long = 0L,             // epoch ms of last income calc
)

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey val type: String,           // "lucky"|"mega"
    val count: Int = 0,
)

@Entity(tableName = "shields")
data class ShieldEntity(
    @PrimaryKey val id: Int = 1,            // singleton row
    val baseShieldUntil: Long = 0L,        // epoch ms
)

@Entity(tableName = "raid_records")
data class RaidRecordEntity(
    @PrimaryKey val raidId: String,         // UUID
    val direction: String,                 // "out"|"in"
    val otherGuestId: String,
    val otherBaseName: String,
    val buddyId: String,                   // stolen buddy
    val buddyLevel: Int,
    val createdAt: Long,
    val revengeUsed: Boolean = false,
    val revengeDeadline: Long = 0L,        // createdAt + 24h
    val isShadow: Boolean = false,         // snapshot raid vs live raid
)

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val guestId: String,
    val baseName: String,
    val code: String,                       // 6-letter code
    val pairedAt: Long,
    val lastSeenAt: Long = 0L,
    val snapshotJson: String? = null,      // last base snapshot (§7)
    val snapshotAt: Long = 0L,
    val snapshotRaided: Boolean = false,   // one shadow heist per snapshot
)

@Entity(tableName = "calendar")
data class CalendarEntity(
    @PrimaryKey val id: Int = 1,            // singleton row
    val currentDay: Int = 1,               // 1..30
    val lastClaimDate: String = "",        // yyyy-MM-dd (device zone)
    val streakDays: Int = 0,
)
```

DataStore (Preferences) keys — `data/datastore/GamePrefs.kt` (Builder A):
`guest_id` (UUID, created once), `base_name`, `music_vol` (0-100),
`sfx_vol`, `voice_vol`, `haptics` (bool), `onboarding_day` (int: 1..7+),
`tutorial_done` (bool), `ads_removed` (bool), `poster_path` (string, internal
file name or ""), `last_raid_window` (yyyy-MM-dd-HH bucket), `raids_used`,
`benny_hints` (bool), `used_codes` (string set).

## 6. Frozen content lists

**34 v1 buddies** (`BuddyId` → name → catchphrase). Catchphrases follow the
audio-bible formula: [CHANT] + [PAYOFF with ONE stressed word], ≤8 words.
- COMMONS (1 coin/s): `frogboot` Frogboot "Plip plop! RAIN'S HERE!" ·
  `sir_munchalot` Sir Munchalot "Huzzah! SOGGY BEWARE!" ·
  `splorch` SPLORCH! "Splorch splorch! JUICE LOOSE!" ·
  `pip_mcsnacks` Pip McSnacks "Count count... FORTY-SEVEN!" ·
  `honk_then_run` Honk Then Run "Honk honk! TIME TO RUN!" ·
  `tess_two_step` Tess Two-Step "Tap tap... GROOVE ON!" ·
  `mellow_marvin` Mellow Marvin "Bubbles up... PEACE OUT." ·
  `her_fluffiness` Her Fluffiness "Puff puff... SO FLUFFY!" ·
  `rosie_the_roller` Rosie the Roller "Roll roll! PAINT SPLAT!" ·
  `wobbles` Wobbles "Wobble wobble... WHOA!" ·
  `doodles_daily` Doodles Daily "Doodle doodle! ART ATTACK!" ·
  `chief_crumb_officer` Chief Crumb Officer "Sniff sniff! CRUMB SECURED!"
- RARES (8 coins/s): `lava_lulu` Lava Lulu "Bubble bubble... MOOD SWING!" ·
  `hot_toast_incoming` Hot Toast Incoming "Ding! TOAST'S UP!" ·
  `i_say` I Say! "I say! PIP PIP!" ·
  `gulp` Gulp "Slurp slurp... AH-HO!" ·
  `purseonality` Purseonality "Clasp snap! SHINY MINE!" ·
  `pop_goes_pete` Pop Goes Pete "Pop pop pop! BEAT DROP!" ·
  `naps_fix_everything` Naps Fix Everything "Mumble... NAP FIRST." ·
  `spin_cycle` Spin Cycle "Spin spin! WHEEE... whoa." ·
  `sassy_soda` Sassy Soda "Fizz fizz! PET ME!" ·
  `wiggle_for_light` Wiggle for Light "Wiggle wiggle! LIGHTS ON!" ·
  `accordion_to_pug` Accordion to Pug "Squeeze squeeze! HUG SONG!" ·
  `ding_dong_dash` Ding-Dong Dash "Ding dong! PACKAGE... MAYBE!"
- EPICS (40 coins/s): `extra_butter` Extra Butter "Pop pop! BUTTER UP!" ·
  `drumroll_please` Drumroll Please "Brrrum! MAKE WAY!" ·
  `mirror_mirror` Mirror Mirror "Twinkle twinkle! POSE TIME!" ·
  `instant_replay` Instant Replay "AND SHE... HOVERS!" ·
  `splash_zone` Splash Zone "Stomp stomp! SPLASH ATTACK!" ·
  `pudding_and_proud` Pudding and Proud "Wobble wobble! PUDDING POWER!" ·
  `brown_sugar` Brown Sugar "Blub blub... BOBA RAIN!" ·
  `crash_course` Crash Course "Vroom vroom! NO BRAKES!" ·
  `soup_for_thought` Soup for Thought "Slurp... THINKING NOODLES!" ·
  `motion_carried` Motion Carried "Jingle jangle! MOTION PASSED!"
- Heroes (roles, catchphrases from the bible): Benny "Bloop bloop, cake for
  YOU!" (guide) · Wally "Wiggle wiggle, can't stop the JIGGLE!" (raid
  announcer) · Sammy "Zzz... five more minutes... zzz..." (shield napper) ·
  Flip "Flip flap, SYRUP SLAP!" (weekly gift) · Milo "Munch munch...
  crunch crunch... (yours now!)" (shopkeeper) · Zara "Zoom zoom, VROOM
  VROOM, outta my way-woah!" (Saturday event host).

**Drawable names (frozen):** `buddy_<buddyId>` for the 34 (e.g.
`R.drawable.buddy_frogboot`), `hero_benny|wally|sammy|flip|milo|zara` for
portraits. All WebP in `drawable-nodpi`.

**12 preset phrases:** "Nice grab!" · "My buddy! Give it back!" ·
"Good game!" · "That was sneaky!" · "Wow, nice base!" · "Rematch?" ·
"You got lucky!" · "I'll get you back!" · "Cool buddy!" ·
"Thanks for the visit!" · "That was fun!" · "Sneaky snout!"

**10 emotes** (`emote_happy|sad|giggle|wow|angry_fun|heart|star|thumbsup|sleepy|party`
vector drawables): Happy, Sad-funny, Giggle, Wow, Play-angry, Heart, Star,
Thumbs-up, Sleepy, Party popper.

**12 base names:** "Wobble Tower" · "Snack Shack" · "Giggle Grove" ·
"Bouncy Bay" · "Plip Plop Place" · "Jelly Junction" · "Noodle Nest" ·
"Pudding Palace" · "Bubble Barn" · "Squeak Street" · "Tumble Town" ·
"Fizz Field".

## 7. Navigation routes (frozen, type-safe, @Serializable)

```kotlin
@Serializable object Base          // game board (default start)
@Serializable object Shop
@Serializable object Album
@Serializable object Friends
@Serializable object Settings
@Serializable data class RaidVisit(val friendGuestId: String)  // raid a friend
@Serializable object ForParents    // from Settings
@Serializable object PosterImport  // from Settings or base decoration
```
Bottom tabs: Base · Shop · Album · Friends · More(Settings). Raid bell lives
on Base. One activity, one root NavHost (Builder C implements AppNavHost;
routes above are the complete set — no new routes without contract amendment).

## 8. Nearby P2P protocol (frozen)

- **Service ID:** `com.sreecharan.brainrot_buddies_steal_battle.NEARBY_V1`
- **Strategy:** `P2P_STAR`. Endpoint name = player's base name.
- **API gate:** P2P features require API 31+. Below 31 the Friends screen
  shows: "Nearby raids need Android 12+. Ask a grown-up!" — no location
  permission is EVER requested (kids app; manifest has none).
- **Permissions** (manifest already declares): `BLUETOOTH_ADVERTISE`,
  `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (`neverForLocation`). Requested
  in-context on the Friends screen with a kid-friendly explanation, never
  at launch.
- **Pairing flow:** 6-letter code shown behind parent gate (charset
  `ABCDEFGHJKMNPQRSTUVWXYZ23456789`, no 0/O/1/I/L). Both kids open Friends
  → "Find nearby friend" → each sees the other's base name → tap →
  Nearby auth token shown on BOTH screens → both tap "Match!" → exchange
  `Hello` → codes verified → saved as Friend. Unpair anytime.
- **Payloads** — JSON via kotlinx.serialization, every payload carries
  `"v": 1`. All payloads are tiny (<4 KB); snapshot <2 KB.
```kotlin
@Serializable data class Hello(val v: Int = 1, val guestId: String,
    val baseName: String, val code: String)
@Serializable data class SnapshotSlot(val slot: Int, val buddyId: String,
    val level: Int)
@Serializable data class BaseSnapshot(val v: Int = 1, val guestId: String,
    val baseName: String, val exportedAt: Long, val shieldUntil: Long,
    val coinsPerSec: Int, val slots: List<SnapshotSlot>)
@Serializable data class RaidRequest(val v: Int = 1, val raidId: String,
    val attackerGuestId: String, val attackerBaseName: String, val slot: Int)
@Serializable data class RaidResult(val v: Int = 1, val raidId: String,
    val accepted: Boolean, val buddyId: String? = null,
    val buddyLevel: Int = 1, val reason: String? = null)
// reason: "shielded" | "cooldown" | "empty" | "already_raided"
@Serializable data class P2PChat(val v: Int = 1, val kind: String,
    val refId: String)   // kind: "phrase"|"emote"; refId: phrase index or emote id
// Shadow-heist sync (added 2026-10-16): when friends reconnect, the attacker
// sends each pending shadow raid so the victim's device can show the replay
// card + grant revenge + consolation. Watermarked for exactly-once delivery.
@Serializable data class ShadowNotice(val v: Int = 1, val raidId: String,
    val attackerGuestId: String, val attackerBaseName: String,
    val buddyId: String, val buddyLevel: Int, val createdAt: Long)
```
- **Live raid:** attacker sends `RaidRequest`; victim's device validates
  (shield? cooldown? already raided this window?) → removes buddy from its
  base → replies `RaidResult(accepted=true, buddyId, level)` → shows replay
  card + revenge button + consolation. Attacker's buddy appears with a
  "stolen" sparkle. `P2PChat` carries preset phrases/emotes during the visit.
- **Shadow heist (snapshot):** all validation runs against the saved
  `BaseSnapshot` on the attacker's device. Success → buddy joins attacker's
  base, `FriendEntity.snapshotRaided = true`. On next P2P connection with
  that friend, attacker sends the pending `RaidRecord`; victim's device
  shows the replay card + revenge + consolation then.
- **Anti-grief:** one raid per friend per window; one shadow heist per
  snapshot; 30-min steal cooldown per buddy; rookie shield 7 days.

## 9. Audio spec (from audio_bible — build requirements)

**Voices:** warm goofy cartoon, NEVER harsh TTS. Per-personality tempering:
sleepy = slow/low/trailing; speedy = fast/bright/breathless; prankster =
mischievous lilt; gentle = soft/warm; diva = dramatic. Catchphrase formula
is frozen in §6. Files: OGG Vorbis, 44.1 kHz, mono, normalized −16 LUFS.
`res/raw/voice_<buddyId>` (34) + `voice_benny|wally|sammy|flip|milo|zara`.

**Music:** 104–112 BPM toy-pop loop (ukulele lead, xylophone/marimba double,
soft claps on 2+4, round sub-bass, glockenspiel sparkle). 16–24s seamless.
STEMMED: `music_drums`, `music_bass`, `music_melody`, `music_sparkle` —
game layers full band during raid windows/events, strips to ukulele+shaker
when calm. Ducks 6 dB under any voice/SFX, eases back over 0.8s. Zara's
Saturday event gets the 128 BPM funk variation (same stems, event-only).
After 20 min continuous play, strip to the calm layer (wind-down cue).

**SFX hooks** (each fires on the exact visual frame; 5–8% pitch/volume
jitter per trigger on utility sounds ONLY, never voices/jingles):
` sfx_claim_chime` (ascending do-mi-sol 3-note, 0.6s) ·
`sfx_coin_tick` (soft cha-ching, pitch rises with income rate) ·
`sfx_steal_success` (slide-whistle up + pop) ·
`sfx_steal_fail` (gentle boing + soft descending two-note, never harsh) ·
`sfx_upgrade` (layered ascending major chord, brighter per level) ·
`sfx_shield_break` (glassy shimmer + soft thud) ·
`sfx_button` (tiny warm blip) ·
`sfx_vault_collect` (coin cascade + final ding) ·
`sfx_gift_open` (music-box wind-up + lid pop + sparkle) ·
`sfx_raid_bell` (Wally "Wiggle wiggle, RAID TIME!" + bell arpeggio, ≤3s,
audible from another room).

**SoundManager** (Builder B, `core/audio/`): 3 channels
(Music/Sounds/Voices) with independent 0–100 volumes persisted in DataStore.
Priority: voice > event stings > reward SFX > ambient ticks > music bed.
Cap 8 simultaneous sounds. Every sting ramps in ≥100 ms (no startles).
**No audio before the first tap.** Honor hardware mute + Do Not Disturb.
Rewarded ads start muted with visible unmute.

## 10. Motion spec (from animation_bible — builders implement to the ms)

- **Every tap answers in <100 ms** (sound + visual in the same frame batch).
  Full animation may follow; the ack never waits.
- **Claim sequence:** 0–60 ms squash ack (0.85y/1.15x + star pop + pop
  sound); 60–250 ms spring arc to slot (stiffness 300, damping 15–20, one
  overshoot, motion trail, whoosh); 250–600 ms landing (overshoot 1.15→1.0,
  dust poof, catchphrase, "+N coins/sec" float-up fade). Never blocks the
  next tap; conveyor keeps running.
- **Rarity scaling:** Rare = blue ring burst on land. Epic = 100 ms slow-mo
  (0.3x) on grab + gold ray burst. (Legendary/Mythic full ritual is V2.)
- **Steal success:** 80 ms hit-stop, buddy stretches 1.2y, zoom punch 1.08x,
  "Yoink!" + red-tinted poof, arc into sack with trail. ~700 ms.
- **Steal fail:** shield dome spring-wobble ripple, metallic bonk + light
  haptic, buddy taunt wiggle, "Shielded!" easeOutBack pop. ~500 ms.
- **Upgrade:** squash-stretch pulse, slight growth, 12 star sprites spiral
  up, cha-ching rises with level, "LV n" badge pop. ~600 ms.
- **Raid bell:** ONE gentle screen-edge pulse (no sustained shake); Wally
  slides in with spring bounce; caption pops word-by-word with the voice.
  ~1.5 s.
- **Daily gift:** box shakes ~1.5 s (anticipation), tap to open, burst +
  easeOutBack reveal, calendar stamp bounce. ~2 s.
- **Idle life (every buddy, 3 layers):** breathing scale 1.0↔1.03 on
  2.5–4 s sine; blink every 2–7 s (lid closes 70 ms, opens 100 ms;
  ~18% double blinks); gaze saccades every 0.25–0.9 s; follow-through sway
  ~400 ms after movement; silly personality act every 30–60 s randomized.
  Conveyor buddies wobble-walk (±8° rotation, vertical bob); last 3 s of
  claim window: 100 ms anticipation crouch.
- **Juice rules:** squash & stretch 10–20% on state changes; easeOutBack on
  all UI pops; particle bursts 5–20 pooled sprites (sim <2 ms, ≤3 overdraw
  layers, ≤1000 total); hit-stop 60–100 ms on steal grabs; floating "+N"
  rises ~24 px over 0.6–1.2 s; haptics paired with visual peaks + settings
  toggle; beat-synced celebration visuals; trauma-based screen shake
  (Eiserloh: offset = trauma² × noise, decays ~1/s) — NEVER sustained.
- **Photosensitivity HARD LIMITS (WCAG 2.3.1):** ≤3 flashes per second; NO
  red flashes; flashing area <25% of screen; no flashing on launch.
  Reduce-motion toggle + respect system reduce-motion/transparency.
- **Celebrations:** routine events skippable freely; rare+ skippable after
  1 s; never force an unskippable reveal.
- **Pacing:** base game bouncy but readable; frenzy reserved for short
  celebrations. If a screen has >3 simultaneous effects, cut the weakest.

## 11. UX acceptance criteria (from kids_ux_bible — every screen must pass)

1. **Teach by doing.** Zero reading-required tutorials. Glowing pulsing
   arrow + live moment teaches each mechanic. (Intrusive popups are banned.)
2. **One screen, one job.** Base = watch + collect. Shop = buy. Album =
   browse. If a screen does two jobs, split it.
3. **56 dp minimum tap targets** for gameplay (creatures get an invisible
   1.5× forgiving hit zone — moving targets). 48 dp elsewhere. 8 dp+
   spacing between tappables.
4. **Single-tap only.** No pinch, no two-finger, no long-press-to-discover.
   Drag allowed only for moving buddies between slots.
5. **Every tap answers** (§10: <100 ms ack). Tapping a locked slot wiggles
   it — never silence.
6. **Color + shape are the language.** Rarity = stars + color
   (gray/blue/purple). Locked = silhouette + padlock. Pulsing glow = "tap
   me now." Never explain in a text legend what visuals already say.
7. **Never punish curiosity.** Mis-taps cost nothing. Shop purchases get
   hold-to-confirm. Looking is always free.
8. **Guide speaks in headlines.** Benny ≤8 words per tip, 1 tip per screen,
   every tip ends in an immediate action.
9. **Show the next fun thing, always.** Next spawn visible, raid countdown
   visible, tomorrow's gift grayed out behind today's.
10. **Depth hides behind play.** New systems arrive via gift moments on the
    ladder (§1), never via menu tabs.
11. **Fail states = comedy, never punishment:** missed claim → buddy waves
    "bye!", Luck Meter fills; stolen buddy → replay card + revenge +
    consolation; shielded raid → visit refunded + "nice try" gift; vault
    cap → coins pause (never lost), big collect moment; missed event →
    "Next race Saturday!" countdown, rewards return later. NO red X, NO
    "FAILED" screen, NO error sounds, redo ≤30 s.

## 12. Builder file ownership (DISJOINT — no two builders touch one file)

```
app/src/main/java/com/sreecharan/brainrot_buddies_steal_battle/
├── data/local/            → BUILDER A (7 entities, 7 DAOs, AppDatabase)
├── data/datastore/        → BUILDER A (GamePrefs keys §5; keep template SettingsRepository)
├── data/repository/       → BUILDER A (GameRepository interface + impl)
├── data/model/            → BUILDER A (BuddyDef, Rarity, value classes; keep ThemeMode)
├── game/engine/           → BUILDER A (GameEngine: pulls, pity, economy,
│                             offline calc, calendar, raid windows, redeem codes)
├── di/DatabaseModule.kt   → BUILDER A (DAO providers)
├── di/RepositoryModule.kt → BUILDER A (+ GameRepository @Binds)
├── ui/home/               → BUILDER B (BaseScreen game board: Canvas conveyor,
│                             base grid, vault HUD, raid bell, tour mode)
├── ui/components/         → BUILDER B: game-canvas components only.
│                             BUILDER C: ParentGate + shared UI components.
│                             (Split by file: B owns *Canvas*, *Board*, *Juice*;
│                              C owns ParentGate.kt, dialog/sheet components.)
├── core/audio/            → BUILDER B (SoundManager, 3 channels, ducking,
│                             priority, jitter rules §9)
├── ui/shop/               → BUILDER C
├── ui/album/              → BUILDER C
├── ui/gifts/              → BUILDER C (calendar + weekly gift + redeem codes UI)
├── ui/settings/           → BUILDER C (settings, For Parents, data controls,
│                             audio sliders, haptics, Benny hints toggle)
├── ui/onboarding/         → BUILDER C (tutorial flow, base naming, Benny tips)
├── ui/poster/             → BUILDER C (local-only import, EXIF strip)
├── navigation/AppNavHost.kt → BUILDER C (routes frozen §7)
├── MainActivity.kt        → BUILDER C (wire nav; keep template shell)
├── p2p/                   → BUILDER D (NearbyManager, protocol §8, snapshot
│                             codec, pairing flow, API-31 gate)
├── ui/friends/            → BUILDER D (friend codes, pairing UI, snapshots)
├── ui/raids/              → BUILDER D (raid visit flow, revenge, villain
│                             cards, staged replays calling Builder B's juice)
├── billing/               → BUILDER D (BillingManager; billing/di/BillingModule.kt)
└── ads/                   → BUILDER D (RewardedAdManager; ads/di/AdsModule.kt)
```
- Tests mirror sources: `src/test/...` per builder, same ownership.
- `res/drawable-nodpi/buddy_*`, `hero_*`, `emote_*`: COORDINATOR (art agent).
- `res/raw/`: COORDINATOR (audio). `res/mipmap-*` icon: COORDINATOR.
- Shared strings/dimens: builders add to `res/values/strings.xml` with
  feature prefixes (`shop_`, `album_`, `raid_`, `friend_`, `settings_`).
  No hardcoded user-visible strings (Lint HardcodedText is an error).

## 13. DI wiring (frozen)

- `@Singleton`: `GameRepository`, `GameEngine`, `NearbyManager`,
  `SoundManager`, `BillingManager`, `RewardedAdManager`.
- Dispatchers: template `DispatchersModule` (`@IoDispatcher`,
  `@DefaultDispatcher`); never hardcode dispatchers.
- `@Binds` over `@Provides`; unscoped by default; `@Singleton` only for
  the six above.
- ViewModels: one immutable `UiState` each, `StateFlow` + `update{copy}`,
  `collectAsStateWithLifecycle()` in routes, events via
  `Channel(BUFFERED).receiveAsFlow()`. Public intent functions
  (`fun onClaimTapped(id: BuddyId)`), no sealed Intent unless the
  three-condition rule holds.

## 14. Definition of done (all must hold)

1. `./gradlew check` green on CI: ktlint/Spotless, detekt, Android Lint
   (`warningsAsErrors`), all unit tests (JUnit5 + MockK + Turbine).
2. Every ViewModel has a test class (state transitions, not internals).
   GameEngine has full unit tests (pull weights, pity, offline calc,
   economy math). Repository error paths tested.
3. Conformance review passes: no duplicate entities, no orphaned code, no
   unlinked routes, one source of truth (Room), all §5/§6/§7/§8 frozen
   values match the contract byte-for-byte.
4. All 34 `buddy_*` + 6 `hero_*` drawables present; app icon = buddy on a
   conveyor (client validates on install before it ships as default).
5. All §9 audio assets present in `res/raw/`; 3-channel volumes work;
   no audio before first tap.
6. §10 motion spec implemented on claim/steal/upgrade/shield/gift/bell;
   idle-life on all buddies; photosensitivity limits verified by review.
7. §11 UX checklist passes on every screen (tap targets measured,
   no dead taps, Benny ≤8 words).
8. Parent gate on IAP + ads toggle + friend codes + pairing. For Parents
   screen present. Data-deletion button works (Room + DataStore + files).
9. Release build: R8 full + shrinkResources, signed with the series key
   (CI secrets), versionCode from run number, versionName from tag.
10. No `!!` in prod, no `GlobalScope`, no hardcoded dispatchers, no
    secrets in code, no `ACCESS_FINE_LOCATION` anywhere.

**Conformance review order:** data layer merges first (Builder A), then
engine tests green, then UI (B, C, D). The coordinator runs the contract
checklist against every file before any push to main.
