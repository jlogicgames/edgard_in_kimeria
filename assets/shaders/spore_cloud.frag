// The soft lime haze under the false honey fungus's spore particles (#296, D63): a swirling glow drawn on a quad that is
// exactly the lethal rectangle. GLSL ES 1.0: no loops, no texture reads, no hash-by-sin noise (mediump has too few bits for it),
// only sin / cos / smoothstep / mix on small arguments. The quad border fades to zero alpha, so the haze can never reach beyond
// the rectangle that kills (D5): what is drawn is inside what is lethal.
#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform float uTime;     // seconds since the burst, 0..1
uniform float uSwell;    // how far the haze has swelled out of the tuft, 0..1
uniform float uAlpha;    // overall strength from the timeline (swell in, thin out, gone when the hitbox ends)
uniform vec2 uOrigin;    // the cap tuft inside the quad, Y-down UV

const vec3 DARK = vec3(0.373, 0.627, 0.165);   // 5fa02a
const vec3 MID = vec3(0.651, 0.878, 0.290);    // a6e04a
const vec3 LIGHT = vec3(0.902, 0.949, 0.416);  // e6f26a

void main() {
    vec2 uv = v_texCoords;
    vec2 p = uv - uOrigin;
    p.y *= 0.8;
    float r = length(p);

    // Swirl: the further from the tuft, the more the sample is turned, and the whole pattern turns with time.
    float angle = uTime * 2.4 + r * 9.0;
    float c = cos(angle);
    float s = sin(angle);
    vec2 q = vec2(c * p.x - s * p.y, s * p.x + c * p.y);
    float wave = 0.5 + 0.5 * sin(q.x * 16.0 + 2.0 * sin(q.y * 13.0 + uTime * 4.0));
    float density = smoothstep(0.15, 0.85, wave); // sharper swirl arms, so they read through the particles

    float reach = mix(0.16, 0.72, uSwell);
    float body = 1.0 - smoothstep(0.3 * reach, reach, r + (density - 0.5) * 0.14);

    // Fade to nothing at the quad border: sides and top over 12 %, the floor edge over 4 %.
    float edge = smoothstep(0.0, 0.12, uv.x) * smoothstep(0.0, 0.12, 1.0 - uv.x)
               * smoothstep(0.0, 0.12, uv.y) * smoothstep(0.0, 0.04, 1.0 - uv.y);

    float core = 1.0 - smoothstep(0.0, 0.5 * reach, r);
    vec3 color = mix(DARK, MID, density);
    color = mix(color, LIGHT, core * core * 0.7 + density * density * 0.3);

    float alpha = body * edge * uAlpha * (0.2 + 0.8 * density) * v_color.a;
    gl_FragColor = vec4(color, alpha);
}
