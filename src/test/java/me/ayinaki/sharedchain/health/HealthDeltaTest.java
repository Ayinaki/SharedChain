package me.ayinaki.sharedchain.health;

import me.ayinaki.sharedchain.run.SharedState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthDeltaTest {

    private static final double EPS = 0.0001;

    @Test
    void damageRemovesOnlyUnabsorbedHealth() {
        assertEquals(4.0, SharedHealthService.computeHealthLoss(10, 4, 8), EPS);   // 4 absorbed, 4 hp lost
        assertEquals(8.0, SharedHealthService.computeHealthLoss(10, 0, 8), EPS);   // no absorption
        assertEquals(0.0, SharedHealthService.computeHealthLoss(10, 8, 8), EPS);   // fully absorbed
        assertEquals(0.0, SharedHealthService.computeHealthLoss(10, 20, 8), EPS);  // absorption overflow
        assertEquals(2.0, SharedHealthService.computeHealthLoss(2, 0, 10), EPS);   // overkill capped at remaining hp
        assertEquals(0.0, SharedHealthService.computeHealthLoss(10, 0, 0), EPS);   // no damage
    }

    @Test
    void healGainIsCappedAtMaxHealth() {
        assertEquals(2.0, SharedHealthService.computeHealthGain(18, 20, 5), EPS);  // only what fits
        assertEquals(5.0, SharedHealthService.computeHealthGain(10, 20, 5), EPS);
        assertEquals(0.0, SharedHealthService.computeHealthGain(20, 20, 5), EPS);  // already full
        assertEquals(0.0, SharedHealthService.computeHealthGain(20, 20, 0), EPS);
    }

    /**
     * Regression test: two players hit in the same tick used to silently lose
     * damage. The pool was a snapshot of whichever player synced last, so the
     * second victim's loss was overwritten before it was ever counted. Deltas
     * accumulate instead.
     */
    @Test
    void simultaneousDamageAccumulatesOnThePool() {
        SharedState pool = new SharedState(20);
        // Both players at 20 hp; a creeper hits each for 8 in the same tick.
        pool.addHealth(-SharedHealthService.computeHealthLoss(20, 0, 8));
        pool.addHealth(-SharedHealthService.computeHealthLoss(20, 0, 8));
        assertEquals(4.0, pool.getHealth(), EPS); // 20 - 8 - 8
    }

    @Test
    void simultaneousHealingAccumulatesOnThePool() {
        SharedState pool = new SharedState(20);
        pool.setHealth(10);
        // Two players' gapple regen ticks land in the same tick; each heals +3.
        pool.addHealth(SharedHealthService.computeHealthGain(10, 20, 3));
        pool.addHealth(SharedHealthService.computeHealthGain(10, 20, 3));
        assertEquals(16.0, pool.getHealth(), EPS); // 10 + 3 + 3
    }

    @Test
    void mixedDamageAndHealInOneTickStillLandCorrectly() {
        SharedState pool = new SharedState(20);
        pool.setHealth(10);
        pool.addHealth(SharedHealthService.computeHealthGain(10, 20, 2));  // +2 heal
        pool.addHealth(-SharedHealthService.computeHealthLoss(12, 0, 5));   // -5 damage
        assertEquals(7.0, pool.getHealth(), EPS);
    }

    @Test
    void totemSaveSnapshotsPoolToOne() {
        SharedState pool = new SharedState(20);
        pool.setHealth(10);
        // Player at 3 hp hit for 10: the pool takes the 3 hp they would have lost...
        pool.addHealth(-SharedHealthService.computeHealthLoss(3, 0, 10));
        assertEquals(7.0, pool.getHealth(), EPS);
        // ...then the totem saves them and snapshots the pool to the survivor's 1 hp.
        pool.setHealth(1);
        assertEquals(1.0, pool.getHealth(), EPS);
    }

    @Test
    void poolClampsAtZeroAndMax() {
        SharedState pool = new SharedState(20);
        pool.setHealth(2);
        pool.addHealth(-5);
        assertEquals(0.0, pool.getHealth(), EPS);
        pool.addHealth(100);
        assertEquals(20.0, pool.getHealth(), EPS);
    }
}
