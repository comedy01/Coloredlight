#ifndef COLOREDLIGHT_COLORED_GLSL
#define COLOREDLIGHT_COLORED_GLSL

#if __VERSION__ >= 130
#define COLOREDLIGHT_TEXTURE texture
#else
#define COLOREDLIGHT_TEXTURE texture2D
#endif

#ifdef COLOREDLIGHT_GLOBALS

#ifdef COLOREDLIGHT_VOLUME

ivec3 coloredlight_size() {
    return ivec3(ColoredLightSize.xyz + 0.5);
}

vec3 coloredlight_cell(ivec3 c, ivec3 size) {
    if (any(lessThan(c, ivec3(0))) || any(greaterThanEqual(c, size))) {
        return vec3(0.0);
    }
    return texelFetch(ColoredLightVolume, c.x + size.x * (c.z + size.z * c.y)).rgb;
}

vec3 coloredlight_tint_at(vec3 pos) {
    if (ColoredLightInfo.y != 1234.5 || ColoredLightInfo.z != 1.0) {
        return vec3(1.0);
    }
    ivec3 size = coloredlight_size();
    vec3 lp = pos - ColoredLightBox.xyz - 0.5;
    vec3 base = floor(lp);
    vec3 f = lp - base;
    ivec3 c = ivec3(base);
    vec3 sum = vec3(0.0);
    float weights = 0.0;
    for (int i = 0; i < 8; i++) {
        ivec3 o = ivec3(i & 1, (i >> 1) & 1, (i >> 2) & 1);
        vec3 cell = coloredlight_cell(c + o, size);
        if (max(cell.r, max(cell.g, cell.b)) <= 0.0) {
            continue;
        }
        vec3 w3 = mix(vec3(1.0) - f, f, vec3(o));
        float w = w3.x * w3.y * w3.z;
        sum += w * cell;
        weights += w;
    }
    float top = max(sum.r, max(sum.g, sum.b));
    if (weights <= 0.0 || top <= 0.0) {
        return vec3(1.0);
    }
    vec3 hue = sum / top;
    float strength = clamp(top / weights * 6.0, 0.0, 1.0);
    vec3 tint = mix(vec3(1.0), hue, strength);
    tint = vec3(1.0) + (tint - vec3(1.0)) * ColoredLightInfo.x;
    vec3 edge = min(lp, vec3(size) - 1.0 - lp);
    float fade = clamp(min(edge.x, min(edge.y, edge.z)) / 16.0, 0.0, 1.0);
    return mix(vec3(1.0), tint, fade);
}

#else

vec3 coloredlight_tint_at(vec3 pos) {
    return vec3(1.0);
}

#endif

vec4 coloredlight_tinted(vec4 full, vec4 sky, vec3 tint) {
    return vec4(full.rgb + max(full.rgb - sky.rgb, vec3(0.0)) * (tint - vec3(1.0)), full.a);
}

#if __VERSION__ >= 130
vec4 coloredlight_sample_at(sampler2D lightMap, vec2 uv, vec3 pos) {
#ifdef COLOREDLIGHT_HALF_TEXEL
    vec2 coord = clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
#else
    vec2 coord = clamp(uv / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
#endif
    vec4 full = texture(lightMap, coord);
    if (uv.x < 8.0) {
        return full;
    }
    vec4 sky = texture(lightMap, vec2(0.5 / 16.0, coord.y));
    return coloredlight_tinted(full, sky, coloredlight_tint_at(pos));
}
#endif

vec4 coloredlight_light(sampler2D lightMap, vec2 coord, vec3 pos) {
    vec4 full = COLOREDLIGHT_TEXTURE(lightMap, coord);
    if (coord.x < 1.0 / 16.0) {
        return full;
    }
    vec4 sky = COLOREDLIGHT_TEXTURE(lightMap, vec2(0.5 / 16.0, coord.y));
    return coloredlight_tinted(full, sky, coloredlight_tint_at(pos));
}

vec3 coloredlight_vertex_tint(vec2 coord, vec3 pos) {
    float bl = max(coord.x, 0.0);
    float sl = max(coord.y, 0.0);
    if (bl <= 0.0) {
        return vec3(1.0);
    }
    return mix(vec3(1.0), coloredlight_tint_at(pos), bl / (bl + sl + 0.001));
}

#else

vec3 coloredlight_tint_at(vec3 pos) {
    return vec3(1.0);
}

#if __VERSION__ >= 130
vec4 coloredlight_sample_at(sampler2D lightMap, vec2 uv, vec3 pos) {
#ifdef COLOREDLIGHT_HALF_TEXEL
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
#else
    return texture(lightMap, clamp(uv / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
#endif
}
#endif

vec4 coloredlight_light(sampler2D lightMap, vec2 coord, vec3 pos) {
    return COLOREDLIGHT_TEXTURE(lightMap, coord);
}

vec3 coloredlight_vertex_tint(vec2 coord, vec3 pos) {
    return vec3(1.0);
}

#endif

#if __VERSION__ >= 130
vec4 coloredlight_sample(sampler2D lightMap, ivec2 uv, vec3 pos) {
    return coloredlight_sample_at(lightMap, vec2(uv), pos);
}

vec4 coloredlight_flicker_sample(sampler2D lightMap, ivec2 uv, vec3 pos) {
    vec2 coord = vec2(uv);
#ifdef FIRELIGHT_GLOBALS
    coord.x *= firelight_factor(pos, coord.x / 16.0);
#endif
    return coloredlight_sample_at(lightMap, coord, pos);
}
#endif

#endif
