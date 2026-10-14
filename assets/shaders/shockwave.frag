// Ported from the original Flutter runtime_effect shader (assets in the
// Dart/Flame source, shaders/shockwave.frag): an expanding thin ring used
// by the F3 debug hotkey. FlutterFragCoord()/uSize is replaced with the quad's own
// interpolated v_texCoords, since this is drawn as a normal textured quad
// through SpriteBatch instead of a Flutter Canvas shader.
#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_texCoords;

uniform vec2 uSize;      // quad size, for aspect correction
uniform float uTime;
uniform float uProgress; // 0..1
uniform float uMaxRadius; // in uv space (0..1)
uniform float uWidth;     // ring width in uv space
uniform vec3 uColor;

void main() {
    vec2 uv = v_texCoords;
    vec2 p = uv - vec2(0.5);
    p.x *= uSize.x / uSize.y;
    float dist = length(p);

    float ramp = smoothstep(0.02, 1.0, uProgress);
    float radius = uMaxRadius * ramp;

    float ripple = (uMaxRadius * 0.01) * sin(40.0 * dist - 6.0 * uProgress + uTime * 6.0);
    float w = max(0.001, uWidth * (1.0 - uProgress * 0.8));

    float d = abs(dist - radius + ripple);
    float ring = 1.0 - smoothstep(0.5 * w, 1.5 * w, d);

    float fade = ramp * (1.0 - uProgress);
    float alpha = clamp(ring * fade, 0.0, 1.0);

    vec3 color = uColor;
    float mask = smoothstep(0.001, 0.004, alpha);
    gl_FragColor = vec4(color * alpha * mask, alpha * mask);
}
