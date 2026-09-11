// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.rockchip;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The 19 effects ported from davidcoulson/kiosk-satellite's led_effects.dart
 * (feature/rgb-led-support), on top of this plugin's own original five
 * (None/Pulse/Blink/Rainbow/Candle/Random — see LedMath.frame, unchanged).
 * A fresh instance per lookup so (re)selecting an effect always starts from
 * clean state, exactly as LedManager.dart's ledEffectRunners does.
 */
final class LedEffectRegistry {
    private LedEffectRegistry() {}

    static final Map<String, Supplier<LedRichEffect>> RICH_EFFECTS = new LinkedHashMap<>();
    static {
        RICH_EFFECTS.put("Sunrise/Sunset", SunriseSunsetEffect::new);
        RICH_EFFECTS.put("Moonlight Glow", MoonlightGlowEffect::new);
        RICH_EFFECTS.put("Lightning Storm", LightningStormEffect::new);
        RICH_EFFECTS.put("Wake-Up Alarm", WakeUpAlarmEffect::new);
        RICH_EFFECTS.put("Candle Flicker", CandleFlickerEffect::new);
        RICH_EFFECTS.put("Fairytwinkle", FairytwinkleEffect::new);
        RICH_EFFECTS.put("Fireworks Burst", FireworksBurstEffect::new);
        RICH_EFFECTS.put("Beacon Pulse", BeaconPulseEffect::new);
        RICH_EFFECTS.put("Heartbeat Pulse", HeartbeatPulseEffect::new);
        RICH_EFFECTS.put("Soft Glow", SoftGlowEffect::new);
        RICH_EFFECTS.put("Rolling Fog (Pronounced)", RollingFogEffect::new);
        RICH_EFFECTS.put("Pacifica (Calm Lagoon)", PacificaCalmLagoonEffect::new);
        RICH_EFFECTS.put("Pacifica (Storm)", PacificaStormEffect::new);
        RICH_EFFECTS.put("Pacifica (Deep Current)", PacificaDeepCurrentEffect::new);
        RICH_EFFECTS.put("Aurora (Solar Storm)", AuroraSolarStormEffect::new);
        RICH_EFFECTS.put("Aurora (Pastel Dream)", AuroraPastelDreamEffect::new);
        RICH_EFFECTS.put("Aurora (Red Sky)", AuroraRedSkyEffect::new);
        RICH_EFFECTS.put("Bubbles", BubblesEffect::new);
        RICH_EFFECTS.put("Disco Sparkle", DiscoSparkleEffect::new);
    }
}
