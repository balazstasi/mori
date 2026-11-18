package app.mori

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

/** How far a setup step is, with its icon and spoken label. */
private enum class Step(val icon: Int, val label: Int) {
    DONE(R.drawable.ic_step_done, R.string.step_done),
    MISSING(R.drawable.ic_step_missing, R.string.step_missing),
    UNKNOWN(R.drawable.ic_step_unknown, R.string.step_unknown),
}

/** Material You: colors from the wallpaper on Android 12 and newer, Material's defaults before. */
@Composable
fun MoriTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun SettingsScreen(
    prefs: Prefs,
    serviceOn: Boolean,
    onTurnOn: () -> Unit,
    onAppInfo: () -> Unit,
    resumes: Int,
) {
    // The surface also sets the text color, so text outside the cards follows dark mode.
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(start = 8.dp, top = 32.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_mori_mark),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(width = 28.dp, height = 42.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SetupCard(serviceOn, onTurnOn, onAppInfo)

            var instagram by remember { mutableStateOf(prefs.instagram) }
            var instagramHome by remember { mutableStateOf(prefs.instagramHome) }
            var instagramReels by remember { mutableStateOf(prefs.instagramReels) }
            var instagramLimit by remember { mutableIntStateOf(prefs.limit(Instagram.PACKAGE)) }
            val withLimit = instagramReels && instagramLimit > 0
            AppCard(
                pkg = Instagram.PACKAGE,
                name = R.string.instagram,
                blocked = when {
                    instagramHome == InstagramHome.OFF && withLimit -> R.string.instagram_blocked_with_limit
                    instagramHome == InstagramHome.OFF -> R.string.instagram_blocked
                    withLimit -> R.string.instagram_blocked_home_with_limit
                    instagramReels -> R.string.instagram_blocked_home
                    else -> R.string.instagram_blocked_home_no_reels
                },
                option = Feature(R.drawable.ic_chat, R.string.instagram_allowed),
                checked = instagram,
                onCheckedChange = {
                    instagram = it
                    prefs.instagram = it
                },
                optionChecked = instagramReels,
                onOptionChange = {
                    instagramReels = it
                    prefs.instagramReels = it
                },
                limit = instagramLimit,
                onLimitChange = {
                    instagramLimit = it
                    prefs.setLimit(Instagram.PACKAGE, it)
                },
                scrolledToday = remember(resumes) { prefs.scrolledToday(Instagram.PACKAGE) },
                home = { enabled ->
                    HomeControl(instagramHome, enabled) {
                        instagramHome = it
                        prefs.instagramHome = it
                    }
                },
            )

            var youtube by remember { mutableStateOf(prefs.youtube) }
            var youtubeShorts by remember { mutableStateOf(prefs.youtubeShorts) }
            var youtubeLimit by remember { mutableIntStateOf(prefs.limit(YouTube.PACKAGE)) }
            AppCard(
                pkg = YouTube.PACKAGE,
                name = R.string.youtube,
                blocked = if (youtubeShorts && youtubeLimit > 0) {
                    R.string.youtube_blocked_with_limit
                } else {
                    R.string.youtube_blocked
                },
                option = Feature(R.drawable.ic_play, R.string.youtube_allowed),
                checked = youtube,
                onCheckedChange = {
                    youtube = it
                    prefs.youtube = it
                },
                optionChecked = youtubeShorts,
                onOptionChange = {
                    youtubeShorts = it
                    prefs.youtubeShorts = it
                },
                limit = youtubeLimit,
                onLimitChange = {
                    youtubeLimit = it
                    prefs.setLimit(YouTube.PACKAGE, it)
                },
                scrolledToday = remember(resumes) { prefs.scrolledToday(YouTube.PACKAGE) },
            )
        }
    }
}

@Composable
private fun SetupCard(serviceOn: Boolean, onTurnOn: () -> Unit, onAppInfo: () -> Unit) {
    SectionCard {
        Text(
            stringResource(R.string.setup_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        SetupRow(
            step = if (serviceOn) Step.DONE else Step.MISSING,
            title = R.string.setup_accessibility,
            button = R.string.setup_accessibility_button,
            onClick = onTurnOn,
        )
        // Android 13 added restricted settings. Apps cannot read it, but Accessibility being on proves it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SetupRow(
                step = if (serviceOn) Step.DONE else Step.UNKNOWN,
                title = R.string.setup_restricted,
                hint = R.string.setup_restricted_hint,
                button = R.string.setup_restricted_button,
                onClick = onAppInfo,
            )
        }
    }
}

/** A done step shows only its check and title. The others get a button to fix them. */
@Composable
private fun SetupRow(step: Step, title: Int, button: Int, onClick: () -> Unit, hint: Int? = null) {
    val tint = when (step) {
        Step.DONE -> MaterialTheme.colorScheme.primary
        Step.MISSING -> MaterialTheme.colorScheme.error
        Step.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(step.icon), stringResource(step.label), tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (hint != null && step != Step.DONE) {
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (step != Step.DONE) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp)) {
                Text(stringResource(button))
            }
        }
    }
}

private class Feature(val icon: Int, val text: Int)

@Composable
private fun AppCard(
    pkg: String,
    name: Int,
    blocked: Int,
    option: Feature,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    optionChecked: Boolean,
    onOptionChange: (Boolean) -> Unit,
    limit: Int,
    onLimitChange: (Int) -> Unit,
    scrolledToday: Int,
    home: (@Composable (enabled: Boolean) -> Unit)? = null,
) {
    val icon = rememberAppIcon(pkg, 48.dp)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(48.dp))
            } else {
                // Not installed: a plain circle with the app's first letter.
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(name).take(1),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(name), style = MaterialTheme.typography.titleLarge)
                if (icon == null) {
                    Text(
                        stringResource(R.string.not_installed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        Spacer(Modifier.height(16.dp))
        // The rules only apply while the app's switch is on, so they fade with it.
        Row(Modifier.alpha(if (checked) 1f else DISABLED_ALPHA), verticalAlignment = Alignment.CenterVertically) {
            FeatureIcon(R.drawable.ic_block, MaterialTheme.colorScheme.error)
            Text(stringResource(blocked), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                home?.invoke(checked)
                OptionRow(option, optionChecked, enabled = checked, onOptionChange)
                // A limit only means something while single reels may open at all.
                AnimatedVisibility(visible = optionChecked) {
                    LimitControl(limit, enabled = checked, onLimitChange, scrolledToday)
                }
            }
        }
    }
}

/** Something the app's rules still allow, with its own switch to turn it off as well. */
@Composable
private fun OptionRow(option: Feature, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).alpha(if (enabled) 1f else DISABLED_ALPHA), verticalAlignment = Alignment.CenterVertically) {
            val tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            FeatureIcon(option.icon, tint)
            Text(stringResource(option.text), style = MaterialTheme.typography.bodyLarge)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** How much of Instagram's Home opens instead of going to Messages. */
@Composable
private fun HomeControl(home: InstagramHome, enabled: Boolean, onHomeChange: (InstagramHome) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA), verticalAlignment = Alignment.CenterVertically) {
            val tint = if (home != InstagramHome.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            FeatureIcon(R.drawable.ic_home, tint)
            Text(stringResource(R.string.instagram_home), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            HOME_CHOICES.forEachIndexed { i, (choice, label) ->
                SegmentedButton(
                    selected = home == choice,
                    onClick = { onHomeChange(choice) },
                    shape = SegmentedButtonDefaults.itemShape(i, HOME_CHOICES.size),
                    enabled = enabled,
                ) {
                    Text(stringResource(label))
                }
            }
        }
    }
}

/** How many reels or shorts a day may be scrolled through, from none up to [LIMITS]' last step. */
@Composable
private fun LimitControl(limit: Int, enabled: Boolean, onLimitChange: (Int) -> Unit, scrolledToday: Int) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Row(Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA), verticalAlignment = Alignment.CenterVertically) {
            val tint = if (limit > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            FeatureIcon(R.drawable.ic_hourglass, tint)
            Text(stringResource(R.string.limit_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (limit > 0) stringResource(R.string.limit_per_day, limit) else stringResource(R.string.limit_off),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = LIMITS.indexOfFirst { it >= limit }.takeIf { it >= 0 }?.toFloat() ?: LIMITS.lastIndex.toFloat(),
            onValueChange = { onLimitChange(LIMITS[it.roundToInt()]) },
            enabled = enabled,
            valueRange = 0f..LIMITS.lastIndex.toFloat(),
            steps = LIMITS.size - 2,
        )
        if (limit > 0) {
            Text(
                stringResource(R.string.limit_today, scrolledToday, limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
            )
        }
    }
}

@Composable
private fun FeatureIcon(icon: Int, tint: Color) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(12.dp))
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) { content() }
    }
}

/** The installed app's own launcher icon, so Mori ships no logos. Null when the app is missing. */
@Composable
private fun rememberAppIcon(pkg: String, size: Dp): ImageBitmap? {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    return remember(pkg, px) {
        try {
            context.packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}

/** Material's opacity for disabled content. */
private const val DISABLED_ALPHA = 0.38f

private val HOME_CHOICES = listOf(
    InstagramHome.OFF to R.string.instagram_home_off,
    InstagramHome.STORIES to R.string.instagram_home_stories,
    InstagramHome.FEED to R.string.instagram_home_feed,
)

/** The daily limits the slider steps through. Zero is no scrolling at all. */
private val LIMITS = listOf(0, 10, 20, 30, 50, 75, 100, 150, 200, 300)
