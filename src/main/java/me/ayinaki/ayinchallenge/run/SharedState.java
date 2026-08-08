package me.ayinaki.ayinchallenge.run;

/**
 * Holds the shared health and max health state for the participating team.
 */
public class SharedState {
    private double health;
    private double maxHealth;

    public SharedState(double maxHealth) {
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    public synchronized void reset(double maxHealth) {
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    public synchronized double getHealth() {
        return health;
    }

    public synchronized void setHealth(double health) {
        this.health = Math.max(0, Math.min(maxHealth, health));
    }

    public synchronized double getMaxHealth() {
        return maxHealth;
    }

    public synchronized void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
        if (this.health > maxHealth) {
            this.health = maxHealth;
        }
    }
    
    public synchronized void addHealth(double amount) {
        setHealth(this.health + amount);
    }
}
