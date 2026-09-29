// Chromatic-aberration glitch, drawn as a full-screen blit of the frozen world
// while the game is paused. Splits the red and blue channels sideways and tears
// a few horizontal bands out of place; the bands re-roll a few times a second
// so the paused frame has a slow, unsettled idle instead of a dead still.
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
uniform float uIntensity; // 0..1

float hash(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

void main() {
    vec2 uv = v_texCoords;

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
