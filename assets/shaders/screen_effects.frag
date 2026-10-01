// Full-screen blit of the offscreen world, with two optional effects folded into
// one pass (as Rust's screen_effects.wgsl does):
//   1. the coin-pickup ripple: the scene is sampled at a UV pushed radially in and
//      out around an expanding ring (Dart's ripple.frag / Rust's ripple_offset);
//   2. the pause chromatic-aberration glitch: red and blue split sideways and a few
//      horizontal bands tear out of place; the bands re-roll a few times a second so
//      the paused frame has a slow, unsettled idle instead of a dead still.
// The ripple offset is applied first, then the chroma shift samples at the offset UV.
// Each effect is off when its strength uniform is 0, which leaves a straight copy.
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float uTime;
uniform float uIntensity; // glitch, 0..1; 0 = off

uniform vec2 uRippleCenter;   // ring origin, in v_texCoords space
uniform float uRippleAspect;  // width / height
uniform float uRippleTime;    // drives the travelling wave phase
uniform float uRippleProgress; // 0..1
uniform float uRippleMaxRadius; // in uv units (relative to the view width)
uniform float uRippleStrength;  // in uv units, already faded by (1 - progress); 0 = off
uniform float uRippleFrequency;
uniform float uRippleDecay;

float hash(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

vec2 rippleOffset(vec2 uv) {
    vec2 p = uv - uRippleCenter;
    p.x *= uRippleAspect;
    float dist = length(p);
    float radius = uRippleMaxRadius * uRippleProgress;
    float d = dist - radius; // signed distance from the ring
    float wave = sin(d * uRippleFrequency - uRippleTime * 6.0);
    float env = exp(-abs(d) * uRippleDecay); // strongest at the ring, fades either side
    float disp = uRippleStrength * wave * env;
    vec2 dir = dist > 0.0001 ? p / dist : vec2(0.0);
    vec2 offset = dir * disp;
    offset.x /= uRippleAspect; // back into uv space
    return offset;
}

void main() {
    vec2 uv = v_texCoords;
    if (uRippleStrength > 0.0) {
        uv = clamp(uv + rippleOffset(uv), 0.0, 1.0);
    }

    float band = floor(uv.y * 36.0);
    float tick = floor(uTime * 3.0);
    float torn = step(0.88, hash(band + tick * 17.0));
    float tear = torn * (hash(band * 3.1 + tick) - 0.5) * 0.06 * uIntensity;

    float split = (0.007 + abs(tear) * 0.3) * uIntensity;
    float x = uv.x + tear;

    float r = texture2D(u_texture, vec2(x + split, uv.y)).r;
    float g = texture2D(u_texture, vec2(x, uv.y)).g;
    float b = texture2D(u_texture, vec2(x - split, uv.y)).b;

    gl_FragColor = vec4(r, g, b, 1.0);
}
