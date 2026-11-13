@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.qingluan.schedule.ui

import android.graphics.RuntimeShader
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*

internal const val EDGE_SHADER =
    """
uniform shader content;
uniform float2 resolution;
uniform float radius;
uniform float strength;
uniform float reflectance;
half4 main(float2 pos) {
    float2 halfSize=resolution*0.5;
    float2 p=pos-halfSize;
    float r=min(radius,min(halfSize.x,halfSize.y));
    float2 q=abs(p)-(halfSize-r);
    float distance=length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
    float edge=smoothstep(-10.0,0.0,distance);
    float2 near=p-clamp(p,-halfSize+r,halfSize-r);
    float2 normal=near/max(length(near),0.001);
    float2 uv=clamp(pos-normal*edge*strength, float2(0.5), resolution-0.5);
    half4 color=content.eval(uv);
    float rim=pow(edge,3.0)*reflectance;
    return half4(color.rgb+half3(rim)*color.a,color.a);
}
"""

/** Shared optics for reflective controls; content text remains outside this layer. */
@Composable
internal fun Modifier.glassOptics(
    radius: Dp,
    strength: Float,
    reflectance: Float = .025f,
): Modifier {
    if (!LocalView.current.isHardwareAccelerated) return this
    val shader = remember { runCatching { RuntimeShader(EDGE_SHADER) }.getOrNull() } ?: return this
    val density = LocalDensity.current
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val effect =
        remember(shader, panelSize, radius, density.density, strength, reflectance) {
            if (panelSize.width == 0 || panelSize.height == 0) null
            else {
                shader.setFloatUniform(
                    "resolution",
                    panelSize.width.toFloat(),
                    panelSize.height.toFloat(),
                )
                shader.setFloatUniform("radius", with(density) { radius.toPx() })
                shader.setFloatUniform("strength", strength)
                shader.setFloatUniform("reflectance", reflectance)
                android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                    .asComposeRenderEffect()
            }
        }
    return onSizeChanged { panelSize = it }.graphicsLayer { renderEffect = effect }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    accent: Boolean = false,
    tint: Color = Color.Unspecified,
    pressed: Boolean = false,
    liquid: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkAppearance.current
    val shape = remember(radius) { RoundedCornerShape(radius) }
    Box(modifier) {
        Box(
            Modifier.matchParentSize()
                .then(
                    if (liquid) Modifier.shadow(if (pressed) 0.dp else .5.dp, shape) else Modifier
                )
                .clip(shape)
                .then(
                    if (liquid) Modifier.glassOptics(radius, if (pressed) 5f else 2f) else Modifier
                )
                .background(
                    if (liquid) colors.surface.copy(alpha = if (dark) .35f else .30f)
                    else colors.surfaceContainerLow.copy(alpha = .88f)
                )
                .then(
                    if (liquid)
                        Modifier.drawWithCache {
                            val sheen =
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (dark) .045f else .20f),
                                        if (tint != Color.Unspecified) tint.copy(alpha = .18f)
                                        else if (accent) colors.primary.copy(alpha = .18f)
                                        else colors.surface.copy(alpha = .06f),
                                        Color.White.copy(alpha = if (dark) .03f else .12f),
                                    )
                                )
                            onDrawBehind { drawRect(sheen) }
                        }
                    else
                        Modifier.background(
                            if (tint != Color.Unspecified)
                                lerp(colors.surfaceContainerLow, tint, if (dark) .055f else .04f)
                            else if (accent) lerp(colors.surfaceContainerLow, colors.primary, .065f)
                            else Color.Transparent
                        )
                )
                .border(
                    .65.dp,
                    if (liquid)
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = if (dark) .18f else .55f),
                                colors.outline.copy(alpha = .12f),
                                Color.White.copy(alpha = if (dark) .08f else .20f),
                            )
                        )
                    else SolidColor(colors.outlineVariant.copy(alpha = if (dark) .60f else .75f)),
                    shape,
                )
        )
        content()
    }
}

@Composable
private fun GlassAction(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    accent: Boolean,
    padding: PaddingValues,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = LocalPreferences.current.animationsEnabled
    val scale by
        animateFloatAsState(
            if (pressed && motion) .96f else 1f,
            if (motion) spring(dampingRatio = .65f, stiffness = 750f) else snap(),
            label = "glass-press",
        )
    GlassPanel(
        modifier
            .heightIn(min = 40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else .45f
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = source,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        accent = accent,
        tint = MaterialTheme.colorScheme.tertiary,
        pressed = pressed,
        liquid = true,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                if (accent) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.tertiary
        ) {
            Row(
                Modifier.align(Alignment.Center).padding(padding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) = GlassAction(onClick, modifier, enabled, true, contentPadding, content)

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) = GlassAction(onClick, modifier, enabled, false, contentPadding, content)

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) = GlassAction(onClick, modifier, enabled, false, contentPadding, content)

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = LocalPreferences.current.animationsEnabled
    val scale by
        animateFloatAsState(
            if (pressed && motion) .92f else 1f,
            if (motion) spring(dampingRatio = .65f, stiffness = 700f) else snap(),
            label = "glass-icon",
        )
    Box(
        modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else .4f
            }
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = source,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlassPanel(Modifier.size(38.dp), radius = 19.dp, pressed = pressed, liquid = true)
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    val motion = LocalPreferences.current.animationsEnabled
    val position by
        animateDpAsState(
            if (checked) 24.dp else 3.dp,
            if (motion) spring(dampingRatio = .7f, stiffness = 650f) else snap(),
            label = "glass-switch",
        )
    GlassPanel(
        Modifier.size(54.dp, 30.dp)
            .then(
                if (onCheckedChange != null) Modifier.clickable { onCheckedChange(!checked) }
                else Modifier
            ),
        radius = 15.dp,
        accent = checked,
        liquid = true,
    ) {
        Box(
            Modifier.offset { IntOffset(position.roundToPx(), 3.dp.roundToPx()) }
                .size(24.dp)
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .background(
                    if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f),
                    RoundedCornerShape(12.dp),
                )
                .border(
                    .5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f),
                    RoundedCornerShape(12.dp),
                )
        )
    }
}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier =
            modifier.border(
                .65.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f),
                RoundedCornerShape(28.dp),
            ),
        icon = icon,
        title = title,
        text = text,
        dismissButton = dismissButton,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .98f),
        tonalElevation = 0.dp,
        properties = properties,
    )
}

@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surface))
}

@Composable
fun GlassPage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        AmbientBackdrop(Modifier.fillMaxSize())
        content()
    }
}

@Composable
fun GlassSnackbar(data: SnackbarData) {
    GlassPanel(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        radius = 20.dp,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                data.visuals.message,
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) { Text(label) }
            }
        }
    }
}
