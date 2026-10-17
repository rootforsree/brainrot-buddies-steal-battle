package com.sreecharan.brainrot_buddies_steal_battle.data.model

import com.sreecharan.brainrot_buddies_steal_battle.R

private const val COMMON_COINS_PER_SEC = 1
private const val RARE_COINS_PER_SEC = 8
private const val EPIC_COINS_PER_SEC = 40

/** Rarity tiers available in v1. No Legendary/Mythic pulls. */
enum class Rarity {
    COMMON,
    RARE,
    EPIC,
}

/** Typed buddy id, e.g. "frogboot". */
@JvmInline
value class BuddyId(
    val value: String,
)

/** Typed hero id, e.g. "benny". */
@JvmInline
value class HeroId(
    val value: String,
)

/**
 * Static definition of one catchable buddy. The catalog is code, not Room,
 * per docs/contract.md §5. All values frozen by §6.
 */
data class BuddyDef(
    val id: BuddyId,
    val name: String,
    val rarity: Rarity,
    val baseCoinsPerSec: Int,
    val imageRes: Int,
    val catchphrase: String,
    val voiceFile: String,
    val blurb: String,
)

/** Static definition of one of the six guide heroes (§6). */
data class HeroDef(
    val id: HeroId,
    val name: String,
    val role: String,
    val catchphrase: String,
    val voiceFile: String,
    val imageRes: Int,
)

/** The 34 v1 buddies, frozen per contract §6. */
object BuddyCatalog {
    val all: List<BuddyDef> =
        listOf(
            BuddyDef(
                id = BuddyId("frogboot"),
                name = "Frogboot",
                rarity = Rarity.COMMON,
                baseCoinsPerSec = COMMON_COINS_PER_SEC,
                imageRes = R.drawable.buddy_frogboot,
                catchphrase = "Plip plop! RAIN'S HERE!",
                voiceFile = "voice_frogboot",
                blurb = "Hops in rain boots three sizes too big.",
            ),
            BuddyDef(
                id = BuddyId("sir_munchalot"),
                name = "Sir Munchalot",
                rarity = Rarity.COMMON,
                baseCoinsPerSec = COMMON_COINS_PER_SEC,
                imageRes = R.drawable.buddy_sir_munchalot,
                catchphrase = "Huzzah! SOGGY BEWARE!",
                voiceFile = "voice_sir_munchalot",
                blurb = "Knight of the soggy sandwich.",
            ),
            BuddyDef(
                id = BuddyId("splorch"),
                name = "SPLORCH!",
                rarity = Rarity.COMMON,
                baseCoinsPerSec = COMMON_COINS_PER_SEC,
                imageRes = R.drawable.buddy_splorch,
                catchphrase = "Splorch splorch! JUICE LOOSE!",
                voiceFile = "voice_splorch",
                blurb = "Ninety percent juice, ten percent mystery.",
            ),
            BuddyDef(
                id = BuddyId("pip_mcsnacks"),
                name = "Pip McSnacks",
                rarity = Rarity.COMMON,
                baseCoinsPerSec = COMMON_COINS_PER_SEC,
                imageRes = R.drawable.buddy_pip_mcsnacks,
                catchphrase = "Count count... FORTY-SEVEN!",
                voiceFile = "voice_pip_mcsnacks",
                blurb = "Counts every crumb. Twice.",
            ),
            BuddyDef(
                id = BuddyId("honk_then_run"),
                name = "Honk Then Run",
                rarity = Rarity.COMMON,
                baseCoinsPerSec = COMMON_COINS_PER_SEC,
                imageRes = R.drawable.buddy_honk_then_run,
                catchphrase = "Honk honk! RUN RUN!",
                voiceFile = "voice_honk_then_run",
                blurb = "Goose-adjacent. Extremely fast.",
            ),
            BuddyDef(
                id = BuddyId("tess_two_step"),
                name = "Tess Two-Step",
                rarity = Rarity.COMMON,
                baseCoinsPerSec = COMMON_COINS_PER_SEC,
                imageRes = R.drawable.buddy_tess_two_step,
                catchphrase = "Tap tap... GROOVE ON!",
                voiceFile = "voice_tess_two_step",
                blurb = "Tap dances everywhere, even in lines.",
            ),
            BuddyDef(
                id = BuddyId("mellow_marvin"),
                name = "Mellow Marvin",
                rarity = Rarity.COMMON,
                baseCoinsPerSec = COMMON_COINS_PER_SEC,
                imageRes = R.drawable.buddy_mellow_marvin,
                catchphrase = "Bubbles up... PEACE OUT.",
                voiceFile = "voice_mellow_marvin",
                blurb = "Professional bubble watcher.",
            ),
            BuddyDef(
                id = BuddyId("her_fluffiness"),
                name = "Her Fluffiness",
                rarity = Rarity.COMMON,
                baseCoinsPerSec = COMMON_COINS_PER_SEC,
                imageRes = R.drawable.buddy_her_fluffiness,
                catchphrase = "Puff puff... SO FLUFFY!",
                voiceFile = "voice_her_fluffiness",
                blurb = "Fluffiest buddy in the whole base.",
            ),
            BuddyDef(
                id = BuddyId("rosie_the_roller"),
                name = "Rosie the Roller",
                rarity = Rarity.COMMON,
                baseCoinsPerSec = COMMON_COINS_PER_SEC,
                imageRes = R.drawable.buddy_rosie_the_roller,
                catchphrase = "Roll roll! PAINT SPLAT!",
                voiceFile = "voice_rosie_the_roller",
                blurb = "Paints rainbows, mostly on herself.",
            ),
            BuddyDef(
                id = BuddyId("wobbles"),
                name = "Wobbles",
                rarity = Rarity.COMMON,
                baseCoinsPerSec = COMMON_COINS_PER_SEC,
                imageRes = R.drawable.buddy_wobbles,
                catchphrase = "Wobble wobble... WHOA!",
                voiceFile = "voice_wobbles",
                blurb = "Wobbles but never falls down. Usually.",
            ),
            BuddyDef(
                id = BuddyId("doodles_daily"),
                name = "Doodles Daily",
                rarity = Rarity.COMMON,
                baseCoinsPerSec = COMMON_COINS_PER_SEC,
                imageRes = R.drawable.buddy_doodles_daily,
                catchphrase = "Doodle doodle! ART ATTACK!",
                voiceFile = "voice_doodles_daily",
                blurb = "Draws a masterpiece before breakfast.",
            ),
            BuddyDef(
                id = BuddyId("chief_crumb_officer"),
                name = "Chief Crumb Officer",
                rarity = Rarity.COMMON,
                baseCoinsPerSec = COMMON_COINS_PER_SEC,
                imageRes = R.drawable.buddy_chief_crumb_officer,
                catchphrase = "Sniff sniff! CRUMB SECURED!",
                voiceFile = "voice_chief_crumb_officer",
                blurb = "In charge of all crumbs, everywhere.",
            ),
            BuddyDef(
                id = BuddyId("lava_lulu"),
                name = "Lava Lulu",
                rarity = Rarity.RARE,
                baseCoinsPerSec = RARE_COINS_PER_SEC,
                imageRes = R.drawable.buddy_lava_lulu,
                catchphrase = "Bubble bubble... MOOD SWING!",
                voiceFile = "voice_lava_lulu",
                blurb = "Sweet until she is spicy.",
            ),
            BuddyDef(
                id = BuddyId("hot_toast_incoming"),
                name = "Hot Toast Incoming",
                rarity = Rarity.RARE,
                baseCoinsPerSec = RARE_COINS_PER_SEC,
                imageRes = R.drawable.buddy_hot_toast_incoming,
                catchphrase = "Ding! TOAST'S UP!",
                voiceFile = "voice_hot_toast_incoming",
                blurb = "Pops up when you least expect it.",
            ),
            BuddyDef(
                id = BuddyId("i_say"),
                name = "I Say!",
                rarity = Rarity.RARE,
                baseCoinsPerSec = RARE_COINS_PER_SEC,
                imageRes = R.drawable.buddy_i_say,
                catchphrase = "I say! PIP PIP!",
                voiceFile = "voice_i_say",
                blurb = "Very proper. Very loud.",
            ),
            BuddyDef(
                id = BuddyId("gulp"),
                name = "Gulp",
                rarity = Rarity.RARE,
                baseCoinsPerSec = RARE_COINS_PER_SEC,
                imageRes = R.drawable.buddy_gulp,
                catchphrase = "Slurp slurp... AH-HO!",
                voiceFile = "voice_gulp",
                blurb = "Drinks the ocean, one slurp at a time.",
            ),
            BuddyDef(
                id = BuddyId("purseonality"),
                name = "Purseonality",
                rarity = Rarity.RARE,
                baseCoinsPerSec = RARE_COINS_PER_SEC,
                imageRes = R.drawable.buddy_purseonality,
                catchphrase = "Clasp snap! SHINY MINE!",
                voiceFile = "voice_purseonality",
                blurb = "Carries snacks for the whole squad.",
            ),
            BuddyDef(
                id = BuddyId("pop_goes_pete"),
                name = "Pop Goes Pete",
                rarity = Rarity.RARE,
                baseCoinsPerSec = RARE_COINS_PER_SEC,
                imageRes = R.drawable.buddy_pop_goes_pete,
                catchphrase = "Pop pop pop! BEAT DROP!",
                voiceFile = "voice_pop_goes_pete",
                blurb = "The beat drops wherever he goes.",
            ),
            BuddyDef(
                id = BuddyId("naps_fix_everything"),
                name = "Naps Fix Everything",
                rarity = Rarity.RARE,
                baseCoinsPerSec = RARE_COINS_PER_SEC,
                imageRes = R.drawable.buddy_naps_fix_everything,
                catchphrase = "Mumble... NAP FIRST.",
                voiceFile = "voice_naps_fix_everything",
                blurb = "Asleep right now, probably.",
            ),
            BuddyDef(
                id = BuddyId("spin_cycle"),
                name = "Spin Cycle",
                rarity = Rarity.RARE,
                baseCoinsPerSec = RARE_COINS_PER_SEC,
                imageRes = R.drawable.buddy_spin_cycle,
                catchphrase = "Spin spin! WHEEE... whoa.",
                voiceFile = "voice_spin_cycle",
                blurb = "Dizzy? Never heard of her.",
            ),
            BuddyDef(
                id = BuddyId("sassy_soda"),
                name = "Sassy Soda",
                rarity = Rarity.RARE,
                baseCoinsPerSec = RARE_COINS_PER_SEC,
                imageRes = R.drawable.buddy_sassy_soda,
                catchphrase = "Fizz fizz! PET ME!",
                voiceFile = "voice_sassy_soda",
                blurb = "Fizzy, sassy, and a little bit sticky.",
            ),
            BuddyDef(
                id = BuddyId("wiggle_for_light"),
                name = "Wiggle for Light",
                rarity = Rarity.RARE,
                baseCoinsPerSec = RARE_COINS_PER_SEC,
                imageRes = R.drawable.buddy_wiggle_for_light,
                catchphrase = "Wiggle wiggle! LIGHTS ON!",
                voiceFile = "voice_wiggle_for_light",
                blurb = "Dances until the lights come on.",
            ),
            BuddyDef(
                id = BuddyId("accordion_to_pug"),
                name = "Accordion to Pug",
                rarity = Rarity.RARE,
                baseCoinsPerSec = RARE_COINS_PER_SEC,
                imageRes = R.drawable.buddy_accordion_to_pug,
                catchphrase = "Squeeze squeeze! HUG SONG!",
                voiceFile = "voice_accordion_to_pug",
                blurb = "Squeezes out a hug song on demand.",
            ),
            BuddyDef(
                id = BuddyId("ding_dong_dash"),
                name = "Ding-Dong Dash",
                rarity = Rarity.RARE,
                baseCoinsPerSec = RARE_COINS_PER_SEC,
                imageRes = R.drawable.buddy_ding_dong_dash,
                catchphrase = "Ding dong! PACKAGE... MAYBE!",
                voiceFile = "voice_ding_dong_dash",
                blurb = "Delivers packages. Maybe. Probably snacks.",
            ),
            BuddyDef(
                id = BuddyId("extra_butter"),
                name = "Extra Butter",
                rarity = Rarity.EPIC,
                baseCoinsPerSec = EPIC_COINS_PER_SEC,
                imageRes = R.drawable.buddy_extra_butter,
                catchphrase = "Pop pop! BUTTER UP!",
                voiceFile = "voice_extra_butter",
                blurb = "Everything is better with butter.",
            ),
            BuddyDef(
                id = BuddyId("drumroll_please"),
                name = "Drumroll Please",
                rarity = Rarity.EPIC,
                baseCoinsPerSec = EPIC_COINS_PER_SEC,
                imageRes = R.drawable.buddy_drumroll_please,
                catchphrase = "Brrrum! MAKE WAY!",
                voiceFile = "voice_drumroll_please",
                blurb = "Makes every entrance a big one.",
            ),
            BuddyDef(
                id = BuddyId("mirror_mirror"),
                name = "Mirror Mirror",
                rarity = Rarity.EPIC,
                baseCoinsPerSec = EPIC_COINS_PER_SEC,
                imageRes = R.drawable.buddy_mirror_mirror,
                catchphrase = "Twinkle twinkle! POSE TIME!",
                voiceFile = "voice_mirror_mirror",
                blurb = "Practicing poses since forever.",
            ),
            BuddyDef(
                id = BuddyId("instant_replay"),
                name = "Instant Replay",
                rarity = Rarity.EPIC,
                baseCoinsPerSec = EPIC_COINS_PER_SEC,
                imageRes = R.drawable.buddy_instant_replay,
                catchphrase = "AND SHE... HOVERS!",
                voiceFile = "voice_instant_replay",
                blurb = "Replays her best moments, in slow motion.",
            ),
            BuddyDef(
                id = BuddyId("splash_zone"),
                name = "Splash Zone",
                rarity = Rarity.EPIC,
                baseCoinsPerSec = EPIC_COINS_PER_SEC,
                imageRes = R.drawable.buddy_splash_zone,
                catchphrase = "Stomp stomp! SPLASH ATTACK!",
                voiceFile = "voice_splash_zone",
                blurb = "Cannonballs into every puddle.",
            ),
            BuddyDef(
                id = BuddyId("pudding_and_proud"),
                name = "Pudding and Proud",
                rarity = Rarity.EPIC,
                baseCoinsPerSec = EPIC_COINS_PER_SEC,
                imageRes = R.drawable.buddy_pudding_and_proud,
                catchphrase = "Wobble wobble! PUDDING POWER!",
                voiceFile = "voice_pudding_and_proud",
                blurb = "Wobbles with confidence.",
            ),
            BuddyDef(
                id = BuddyId("brown_sugar"),
                name = "Brown Sugar",
                rarity = Rarity.EPIC,
                baseCoinsPerSec = EPIC_COINS_PER_SEC,
                imageRes = R.drawable.buddy_brown_sugar,
                catchphrase = "Blub blub... BOBA RAIN!",
                voiceFile = "voice_brown_sugar",
                blurb = "Rains boba wherever she goes.",
            ),
            BuddyDef(
                id = BuddyId("crash_course"),
                name = "Crash Course",
                rarity = Rarity.EPIC,
                baseCoinsPerSec = EPIC_COINS_PER_SEC,
                imageRes = R.drawable.buddy_crash_course,
                catchphrase = "Vroom vroom! NO BRAKES!",
                voiceFile = "voice_crash_course",
                blurb = "Full speed. Brakes are decorative.",
            ),
            BuddyDef(
                id = BuddyId("soup_for_thought"),
                name = "Soup for Thought",
                rarity = Rarity.EPIC,
                baseCoinsPerSec = EPIC_COINS_PER_SEC,
                imageRes = R.drawable.buddy_soup_for_thought,
                catchphrase = "Slurp... THINKING NOODLES!",
                voiceFile = "voice_soup_for_thought",
                blurb = "Thinks deep thoughts between slurps.",
            ),
            BuddyDef(
                id = BuddyId("motion_carried"),
                name = "Motion Carried",
                rarity = Rarity.EPIC,
                baseCoinsPerSec = EPIC_COINS_PER_SEC,
                imageRes = R.drawable.buddy_motion_carried,
                catchphrase = "Jingle jangle! MOTION PASSED!",
                voiceFile = "voice_motion_carried",
                blurb = "The meeting is adjourned. Party time.",
            ),
        )

    val commons: List<BuddyDef> = all.filter { it.rarity == Rarity.COMMON }
    val rares: List<BuddyDef> = all.filter { it.rarity == Rarity.RARE }
    val epics: List<BuddyDef> = all.filter { it.rarity == Rarity.EPIC }

    private val byId: Map<BuddyId, BuddyDef> = all.associateBy { it.id }

    fun find(id: BuddyId): BuddyDef? = byId[id]
}

/** The six heroes, frozen per contract §6. */
object HeroCatalog {
    val all: List<HeroDef> =
        listOf(
            HeroDef(
                id = HeroId("benny"),
                name = "Benny",
                role = "Guide",
                catchphrase = "Bloop bloop, cake for YOU!",
                voiceFile = "voice_benny",
                imageRes = R.drawable.hero_benny,
            ),
            HeroDef(
                id = HeroId("wally"),
                name = "Wally",
                role = "Raid announcer",
                catchphrase = "Wiggle wiggle, can't stop the JIGGLE!",
                voiceFile = "voice_wally",
                imageRes = R.drawable.hero_wally,
            ),
            HeroDef(
                id = HeroId("sammy"),
                name = "Sammy",
                role = "Shield napper",
                catchphrase = "Zzz... five more minutes... zzz...",
                voiceFile = "voice_sammy",
                imageRes = R.drawable.hero_sammy,
            ),
            HeroDef(
                id = HeroId("flip"),
                name = "Flip",
                role = "Weekly gift",
                catchphrase = "Flip flap, SYRUP SLAP!",
                voiceFile = "voice_flip",
                imageRes = R.drawable.hero_flip,
            ),
            HeroDef(
                id = HeroId("milo"),
                name = "Milo",
                role = "Shopkeeper",
                catchphrase = "Munch munch... crunch crunch... (yours now!)",
                voiceFile = "voice_milo",
                imageRes = R.drawable.hero_milo,
            ),
            HeroDef(
                id = HeroId("zara"),
                name = "Zara",
                role = "Saturday event host",
                catchphrase = "Zoom zoom, VROOM VROOM, outta my way-woah!",
                voiceFile = "voice_zara",
                imageRes = R.drawable.hero_zara,
            ),
        )

    private val byId: Map<HeroId, HeroDef> = all.associateBy { it.id }

    fun find(id: HeroId): HeroDef? = byId[id]
}
