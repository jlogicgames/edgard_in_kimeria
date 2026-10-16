package com.jlogicsoftware.kimeria.effects;

/** Cosmetic feedback only. Real energy and combat timing remain owned by Boss. */
public final class BossFeedback {
    public static final float RESTORE_SECONDS = .25f;
    public static final float DAMAGE_SECONDS = .25f;
    private float restoreRemaining, damageRemaining;
    private int restoreFrom, restoreTo, damageFrom, damageTo;

    public void damaged(int from, int to) {
        restoreRemaining = 0;
        damageFrom = from;
        damageTo = to;
        damageRemaining = DAMAGE_SECONDS;
    }

    public void restored(int from, int maximum) {
        restoreFrom = from;
        restoreTo = maximum;
        restoreRemaining = RESTORE_SECONDS;
        damageRemaining = 0;
    }

    /** Real gameplay time, unaffected by death slow motion; the caller freezes this on pause. */
    public void update(float realDt) {
        float dt = Math.max(0, realDt);
        restoreRemaining = Math.max(0, restoreRemaining - dt);
        damageRemaining = Math.max(0, damageRemaining - dt);
    }

    public float displayedEnergy(int actualEnergy) {
        if (restoreRemaining <= 0) return actualEnergy;
        return restoreTo - (restoreTo - restoreFrom) * restoreRemaining / RESTORE_SECONDS;
    }

    /** Recently lost tiles fade over the empty slots, making even one point of damage visible. */
    public float lostSegmentAlpha(int index) {
        return index >= damageTo && index < damageFrom ? damageRemaining / DAMAGE_SECONDS : 0;
    }

    public float restoreAlpha() { return restoreRemaining / RESTORE_SECONDS; }
}
