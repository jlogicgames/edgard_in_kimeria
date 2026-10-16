package com.jlogicsoftware.kimeria.effects;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class BloodBurstTest {
    private static float value(Object drop, String name) throws Exception {
        Field field = drop.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getFloat(drop);
    }

    @Test void sprayFliesInTheHitDirectionFallsAndExpiresWithoutGraphics() throws Exception {
        for (float direction : new float[]{-1, 1}) {
            BloodBurst burst = new BloodBurst(100, 90, direction, 110);
            Field field = BloodBurst.class.getDeclaredField("drops");
            field.setAccessible(true);
            Object[] drops = (Object[]) field.get(burst);
            assertEquals(32, drops.length);
            float initialX = value(drops[1], "x"), initialY = value(drops[1], "y");
            burst.update(.1f);
            assertTrue((value(drops[1], "x") - initialX) * direction > 6);
            assertTrue(value(drops[1], "y") < initialY);
            for (int i = 0; i < 40; i++) {
                burst.update(1f / 60);
                for (Object drop : drops) {
                    assertTrue(Float.isFinite(value(drop, "x")));
                    assertTrue(value(drop, "y") <= 110, "Droplets must not fall through the floor");
                }
            }
            assertFalse(burst.isRemoved());
            burst.update(.2f);
            assertTrue(burst.isRemoved());
            float finalX = value(drops[1], "x");
            burst.update(1);
            assertEquals(finalX, value(drops[1], "x"));
        }
    }
}
