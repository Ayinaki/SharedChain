package me.ayinaki.sharedchain.run;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharedStateTest {

    private static final double EPS = 0.0001;

    @Test
    void startsAtFullHealth() {
        SharedState state = new SharedState(20);
        assertEquals(20.0, state.getHealth(), EPS);
        assertEquals(20.0, state.getMaxHealth(), EPS);
    }

    @Test
    void resetRestoresFullHealth() {
        SharedState state = new SharedState(20);
        state.setHealth(5);
        state.reset(20);
        assertEquals(20.0, state.getHealth(), EPS);
    }

    @Test
    void resetCanChangeMaxHealth() {
        SharedState state = new SharedState(20);
        state.setHealth(5);
        state.reset(40);
        assertEquals(40.0, state.getHealth(), EPS);
        assertEquals(40.0, state.getMaxHealth(), EPS);
    }

    @Test
    void setHealthClampsToRange() {
        SharedState state = new SharedState(20);
        state.setHealth(-3);
        assertEquals(0.0, state.getHealth(), EPS);
        state.setHealth(999);
        assertEquals(20.0, state.getHealth(), EPS);
        state.setHealth(7.5);
        assertEquals(7.5, state.getHealth(), EPS);
    }

    @Test
    void loweringMaxHealthClampsPool() {
        SharedState state = new SharedState(20);
        state.setHealth(15);
        state.setMaxHealth(10);
        assertEquals(10.0, state.getHealth(), EPS);
        assertEquals(10.0, state.getMaxHealth(), EPS);
    }

    @Test
    void addHealthRespectsBounds() {
        SharedState state = new SharedState(20);
        state.setHealth(18);
        state.addHealth(5);
        assertEquals(20.0, state.getHealth(), EPS);
        state.addHealth(-25);
        assertEquals(0.0, state.getHealth(), EPS);
    }
}
