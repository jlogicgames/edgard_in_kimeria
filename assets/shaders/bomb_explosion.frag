// Ported from the original Flutter shader (Krapas, shadertoy.com/view/X3dGz2)
// bundled in the Dart source at shaders/bomb_explosion.frag. Same porting
// note as shockwave.frag: FlutterFragCoord()/uSize -> v_texCoords.
#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_texCoords;

uniform vec2 uSize;
uniform float uTime;
uniform float uProgress; // 0..1
uniform float uOriginY; // ground baseline within the quad (Y-down UV)

vec3 n_rand3(vec3 p) {
    vec3 r = fract(sin(vec3(
        dot(p, vec3(127.1, 311.7, 371.8)),
        dot(p, vec3(269.5, 183.3, 456.1)),
        dot(p, vec3(352.5, 207.3, 198.67))
    )) * 43758.5453) * 2.0 - 1.0;
    return normalize(vec3(r.x / cos(r.x), r.y / cos(r.y), r.z / cos(r.z)));
}

float noise(vec3 p) {
    vec3 fv = fract(p);
    vec3 nv = floor(p);
    vec3 u = fv * fv * fv * (fv * (fv * 6.0 - 15.0) + 10.0);
    return mix(
        mix(
            mix(dot(n_rand3(nv + vec3(0.0, 0.0, 0.0)), fv - vec3(0.0, 0.0, 0.0)),
                dot(n_rand3(nv + vec3(1.0, 0.0, 0.0)), fv - vec3(1.0, 0.0, 0.0)), u.x),
            mix(dot(n_rand3(nv + vec3(0.0, 1.0, 0.0)), fv - vec3(0.0, 1.0, 0.0)),
                dot(n_rand3(nv + vec3(1.0, 1.0, 0.0)), fv - vec3(1.0, 1.0, 0.0)), u.x),
            u.y),
        mix(
            mix(dot(n_rand3(nv + vec3(0.0, 0.0, 1.0)), fv - vec3(0.0, 0.0, 1.0)),
                dot(n_rand3(nv + vec3(1.0, 0.0, 1.0)), fv - vec3(1.0, 0.0, 1.0)), u.x),
            mix(dot(n_rand3(nv + vec3(0.0, 1.0, 1.0)), fv - vec3(0.0, 1.0, 1.0)),
                dot(n_rand3(nv + vec3(1.0, 1.0, 1.0)), fv - vec3(1.0, 1.0, 1.0)), u.x),
            u.y),
        u.z);
}

float worley(vec3 s) {
    vec3 si = floor(s);
    vec3 sf = fract(s);
    float m_dist = 1.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                vec3 neighbor = vec3(float(x), float(y), float(z));
                vec3 point = fract(n_rand3(si + neighbor));
                point = 0.5 + 0.5 * sin(uTime + 6.2831 * point);
                vec3 diff = neighbor + point - sf;
                m_dist = min(m_dist, length(diff));
            }
        }
    }
    return m_dist;
}

float boom(vec2 p, float t) {
    float repeat = t;
    float shape = 1.0 - pow(distance(vec3(p, 0.0), vec3(0.0)), 2.0) / (repeat * 12.0) - repeat * 2.0;
    float distortion = noise(vec3(p * 0.5, uTime * 0.5));
    float bubbles = 0.5 - pow(worley(vec3(p * 1.2, uTime * 2.0)), 3.0);
    float bw = 0.5;
    return shape + (bw * bubbles + (1.0 - bw) * distortion);
}

float smoke(vec2 p, float t) {
    float repeat = t;
    vec2 drift = vec2(0.0, 2.0) * pow(repeat / 1.45, 2.0) * 1.5;
    float shape = 1.0 - pow(distance(vec3(p + drift, 0.0), vec3(0.0)), 2.0) / (repeat * 16.0) - pow(repeat * 1.5, 0.5);
    float distortion = noise(vec3(p * 1.5 + drift, uTime * 0.1));
    vec2 drift2 = vec2(0.0, 2.0) * pow(repeat / 1.65, 2.0) * 1.5;
    float bubbles = 0.5 - pow(worley(vec3((p / pow(repeat, 0.35)) + drift2, uTime * 0.1)), 2.0);
    float bw = 0.75;
    return shape + (bw * bubbles + (1.0 - bw) * distortion);
}

float posterize(float v, float n) {
    return floor(v * n) / (n - 1.0);
}

void main() {
    vec3 boomPal0 = vec3(0.2, 0.15, 0.3);
    vec3 boomPal1 = vec3(0.9, 0.15, 0.05);
    vec3 boomPal2 = vec3(0.9, 0.5, 0.1);
    vec3 boomPal3 = vec3(0.95, 0.95, 0.35);
    vec3 smokePal0 = vec3(0.2, 0.15, 0.3);
    vec3 smokePal1 = vec3(0.35, 0.3, 0.45);
    vec3 smokePal2 = vec3(0.5, 0.45, 0.6);

    vec2 uv = v_texCoords;
    vec2 center = vec2(0.5, uOriginY);
    vec2 p = uv - center;
    p.x *= uSize.x / uSize.y;
    p *= 7.0;
    float progress = clamp(uProgress, 0.0, 1.0);
    float t = max(progress, 0.01);

    float bpl = 4.0;
    float spl = 3.0;

    float boomVal = boom(p, t);
    float boomA = step(0.0, boomVal);
    int boomIdx = int(clamp(posterize(boomVal, bpl) * bpl, 0.0, bpl - 1.0));
    vec3 boomCol = boomPal0;
    if (boomIdx == 1) boomCol = boomPal1;
    else if (boomIdx == 2) boomCol = boomPal2;
    else if (boomIdx == 3) boomCol = boomPal3;
    boomCol = boomCol - vec3(1.0 - boomA);

    float smokeVal = smoke(p, t);
    float smokeA = step(0.0, smokeVal);
    int smokeIdx = int(clamp(posterize(smokeVal, spl) * spl, 0.0, spl - 1.0));
    vec3 smokeCol = smokePal0;
    if (smokeIdx == 1) smokeCol = smokePal1;
    else if (smokeIdx == 2) smokeCol = smokePal2;
    smokeCol = smokeCol - vec3(1.0 - smokeA);

    float bw = step(smokeVal * 1.25, boomVal);
    vec3 color = bw * boomCol + (1.0 - bw) * smokeCol;
    float alpha = bw * boomA + (1.0 - bw) * smokeA;

    // Fade just above the visible bomb base; nothing may extend beneath the ground.
    // Reversed smoothstep edges have undefined GLSL behaviour.
    float groundFade = 1.0 - smoothstep(-0.03, 0.0, uv.y - uOriginY);
    vec2 border = min(uv, vec2(1.0) - uv);
    float edgeFade = smoothstep(0.0, 0.04, min(border.x, border.y));
    float lifeFade = 1.0 - smoothstep(0.65, 1.0, progress);
    alpha *= groundFade * edgeFade * lifeFade;

    gl_FragColor = vec4(color, alpha);
}
