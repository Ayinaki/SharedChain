package me.ayinaki.sharedchain.stats;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RunStatsServiceTest {

    /** Minimal Player stand-in: only getUniqueId/getName are ever invoked. */
    private static Player player(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(name.getBytes());
        return (Player) Proxy.newProxyInstance(
                RunStatsServiceTest.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getName")) return name;
                    Class<?> returnType = method.getReturnType();
                    if (!returnType.isPrimitive()) return null;
                    if (returnType == boolean.class) return false;
                    if (returnType == char.class) return '\0';
                    return 0;
                });
    }

    @Test
    void tracksTopSponsorAndSponge() {
        RunStatsService stats = new RunStatsService();
        Player a = player("a");
        Player b = player("b");

        stats.addHeal(a, 5);
        stats.addHeal(b, 3);
        stats.addDamage(a, 2);
        stats.addDamage(b, 9);

        assertEquals(a.getUniqueId(), stats.getTopSponsor());
        assertEquals(b.getUniqueId(), stats.getTopSponge());
        assertEquals(5.0, stats.getHealedAmount(a.getUniqueId()), 0.0001);
        assertEquals(9.0, stats.getDamageAmount(b.getUniqueId()), 0.0001);
    }

    @Test
    void emptyStatsHaveNoTop() {
        RunStatsService stats = new RunStatsService();
        assertNull(stats.getTopSponsor());
        assertNull(stats.getTopSponge());
    }

    @Test
    void resetClearsStats() {
        RunStatsService stats = new RunStatsService();
        stats.addHeal(player("a"), 5);
        stats.addDamage(player("a"), 5);
        stats.reset();
        assertNull(stats.getTopSponsor());
        assertNull(stats.getTopSponge());
    }

    @Test
    void nullPlayerIsIgnored() {
        RunStatsService stats = new RunStatsService();
        stats.addHeal(null, 5);
        stats.addDamage(null, 5);
        assertNull(stats.getTopSponsor());
        assertNull(stats.getTopSponge());
    }
}
