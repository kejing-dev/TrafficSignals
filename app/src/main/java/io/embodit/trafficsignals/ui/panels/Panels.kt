package io.embodit.trafficsignals.ui.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.CircularProgressDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.LinearProgressDefaults
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.ListItem
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.SliderDefaults
import com.pico.spatial.ui.design.Text
import io.embodit.trafficsignals.BuildConfig
import io.embodit.trafficsignals.R
import io.embodit.trafficsignals.domain.Answer
import io.embodit.trafficsignals.domain.Compass
import io.embodit.trafficsignals.domain.Content
import io.embodit.trafficsignals.domain.Facing
import io.embodit.trafficsignals.domain.Gesture
import io.embodit.trafficsignals.domain.Judge
import io.embodit.trafficsignals.domain.Lane
import io.embodit.trafficsignals.domain.Levels
import io.embodit.trafficsignals.domain.Tr
import io.embodit.trafficsignals.domain.Volume
import io.embodit.trafficsignals.platform.TsLog
import io.embodit.trafficsignals.ui.Dialog
import io.embodit.trafficsignals.ui.KeyMark
import io.embodit.trafficsignals.ui.Phase
import io.embodit.trafficsignals.ui.Screen
import io.embodit.trafficsignals.ui.TsController
import io.embodit.trafficsignals.ui.TsEvent
import io.embodit.trafficsignals.ui.TsUiState
import io.embodit.trafficsignals.ui.window.WindowCtl
import io.embodit.trafficsignals.ui.debug.probe
import io.embodit.trafficsignals.ui.theme.TsColors
import io.embodit.trafficsignals.ui.theme.TsType

private fun TsUiState.t(tr: Tr): String = tr.of(zh)

private fun TsUiState.f(tr: Tr, vararg args: Any): String = String.format(tr.of(zh), *args)

// =============================================================================================
// small pieces
// =============================================================================================

/** Logs `OVERFLOW name=... text=...` when a single / fixed-line text does not fit (profile D16, QA q1-2 regression guard). */
private fun fits(name: String, text: String): (TextLayoutResult) -> Unit = { r ->
    if (r.hasVisualOverflow) TsLog.w("OVERFLOW name=$name text=\"$text\" lines=${r.lineCount} w=${r.size.width}")
}

@Composable
private fun Key(
    text: String,
    name: String,
    icon: Int? = null,
    primary: Boolean = false,
    danger: Boolean = false,
    height: Dp = 48.dp,
    enabled: Boolean = true,
    outline: Color? = null,
    large: Boolean = false,
    fill: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // q1-3 (profile D13): a danger key is the error colour as the FILL with dark ink on it (6.7:1), never red text on
    // the grey fill (1.27:1 measured by QA); primary = interaction fill + dark ink
    val container = when {
        primary -> TsColors.InteractionFill
        danger -> PicoTheme.colorScheme.error
        else -> PicoTheme.colorScheme.fillTertiary
    }
    val ink = if (primary || danger) TsColors.Ink else PicoTheme.colorScheme.labelPrimary
    val border = if (outline != null) Modifier.border(3.dp, outline, CircleShape) else Modifier
    Box(modifier = modifier.then(border).padding(if (outline != null) 3.dp else 0.dp)) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.probe(name).height(height).then(if (fill) Modifier.fillMaxWidth() else Modifier),
            size = ButtonDefaults.Regular.copy(height = height),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = ink),
        ) {
            if (icon != null) {
                Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp), tint = ink)
                Spacer(Modifier.width(8.dp))
            }
            // [large] = 20 sp: the confirm keys; thin CJK strokes of 退出 measured only 4.2:1 on the 2160 px capture (D13)
            Text(text = text, maxLines = 1, color = ink, style = if (large) TsType.title.copy(fontSize = 20.sp, lineHeight = 26.sp) else TsType.title, onTextLayout = fits(name, text))
        }
    }
}

@Composable
private fun Chip(text: String, color: Color = PicoTheme.colorScheme.fillSecondary, ink: Color = PicoTheme.colorScheme.labelPrimary) {
    Box(modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(color).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text = text, style = TsType.label, color = ink, maxLines = 1, onTextLayout = fits("chip", text))
    }
}

@Composable
private fun segColors() =
    SegmentControlDefaults.colors(
        itemBackgroundColor = Color.Transparent,
        itemContentColor = PicoTheme.colorScheme.labelPrimary,
        selectedItemBackgroundColor = TsColors.InteractionFill,
        selectedItemContentColor = TsColors.Ink,
    )

@Composable
private fun Seg(items: List<Pair<String, Boolean>>, name: String, itemW: Dp, onPick: (Int) -> Unit) {
    SegmentControl(modifier = Modifier.width(itemW * items.size + 8.dp).height(48.dp), backgroundColor = PicoTheme.colorScheme.fillTertiary) {
        items.forEachIndexed { i, (label, selected) ->
            SegmentItem(
                selected = selected,
                onClick = { onPick(i) },
                modifier = Modifier.width(itemW).fillMaxHeight().probe("${name}_$i"),
                textStyle = TsType.label,
                colors = segColors(),
                title = { Text(text = label, maxLines = 1, color = if (selected) TsColors.Ink else PicoTheme.colorScheme.labelPrimary, style = TsType.label, onTextLayout = fits("${name}_$i", label)) },
            )
        }
    }
}

@Composable
private fun SoundKey(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    Key(if (state.sound) state.t(Ui.sound) else state.t(Ui.soundOff), "sound", icon = if (state.sound) R.drawable.ic_volume_up else R.drawable.ic_volume_off) { onEvent(TsEvent.SoundOn(!state.sound)) }
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)?, backLabel: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) Key(backLabel, "back", icon = R.drawable.ic_chevron_left, onClick = onBack)
        Text(text = title, style = TsType.headline, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, onTextLayout = fits("header", title))
        trailing()
    }
}

@Composable
private fun PausedBar(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    if (!state.paused) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PicoTheme.colorScheme.fillSecondary).padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(text = state.t(Ui.paused), style = TsType.body, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.weight(1f))
        Key(state.t(Ui.resume), "resume", icon = R.drawable.ic_play_arrow, primary = true) { onEvent(TsEvent.Resume) }
    }
    Spacer(Modifier.height(8.dp))
}

// =============================================================================================
// The one system window (D8): every state draws in here, one at a time, so nothing can sit in front of anything
// =============================================================================================

@Composable
internal fun WindowPage(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        PausedBar(state, onEvent)
        when {
            state.screen == Screen.BLOCKED || state.dialog != Dialog.NONE -> Confirm(state, onEvent)
            state.screen == Screen.STARTING -> Starting(state)
            state.screen == Screen.LESSONS -> LessonList(state, onEvent)
            state.screen == Screen.LEVELS -> LevelList(state, onEvent)
            state.screen == Screen.REVIEW -> Review(state, onEvent)
            state.screen == Screen.SETTINGS -> Settings(state, onEvent)
            state.screen == Screen.LESSON -> Lesson(state, onEvent)
            state.screen == Screen.INTRO -> Intro(state, onEvent)
            state.screen == Screen.RESULT -> Result(state, onEvent)
            state.screen == Screen.QUIZ && state.breakdown -> Lesson(state, onEvent)
            state.screen == Screen.QUIZ && state.judged -> Verdict(state, onEvent)
            state.screen == Screen.QUIZ -> Question(state, onEvent)
            else -> Home(state, onEvent)
        }
    }
}

@Composable
private fun ColumnScope.Starting(state: TsUiState) {
    Header(state.t(Ui.appName), null, "")
    // v0.1.5 (D44 short pages): a big app disc and the loading line centred, the one-liner on the bottom edge
    Column(modifier = Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BigDisc(R.drawable.ic_traffic, TsColors.InteractionFill, 150.dp)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp), color = PicoTheme.colorScheme.labelPrimary, progressSize = CircularProgressDefaults.circleProgressSize(36.dp))
            Spacer(Modifier.width(16.dp))
            Text(text = state.t(Ui.loading), style = TsType.title, color = PicoTheme.colorScheme.labelPrimary)
        }
    }
    Text(text = state.t(Ui.oneLiner), style = TsType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

/** v0.1.5 (D44): the big round icon of the short pages (confirmations, results, empty states): ink on a colour fill (D13). */
@Composable
private fun BigDisc(icon: Int, fill: Color, size: Dp, name: String? = null) {
    Box(modifier = Modifier.size(size).clip(CircleShape).background(fill).then(if (name != null) Modifier.probe(name) else Modifier), contentAlignment = Alignment.Center) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(size * 0.6f), tint = TsColors.Ink)
    }
}

@Composable
private fun ColumnScope.Home(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    Header(state.t(Ui.appName), null, "")
    Text(text = state.t(Ui.oneLiner), style = TsType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2)
    Spacer(Modifier.height(16.dp))
    val next = Levels.recommended(state.best)
    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BigCard(state.t(Ui.learn), state.t(Ui.learnSub), R.drawable.ic_school, "card_learn", Modifier.weight(1f)) { onEvent(TsEvent.OpenLessons) }
        BigCard(state.t(Ui.judge), state.f(Ui.judgeSub, Content.levelName[next - 1].of(state.zh)), R.drawable.ic_traffic, "card_judge", Modifier.weight(1f)) { onEvent(TsEvent.OpenLevels) }
        BigCard(
            state.t(Ui.review),
            if (state.mistakes.isEmpty()) state.t(Ui.reviewEmptySub) else state.f(Ui.reviewSub, state.mistakes.map { it.question }.distinct().size),
            R.drawable.ic_menu_book,
            "card_review",
            Modifier.weight(1f),
        ) { onEvent(TsEvent.OpenReview) }
    }
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Seg(listOf(state.t(Ui.langZh) to state.zh, state.t(Ui.langEn) to !state.zh), "lang", 60.dp) { onEvent(TsEvent.Lang(it == 0)) }
        SoundKey(state, onEvent)
        Spacer(Modifier.weight(1f))
        Key(state.t(Ui.recenter), "recenter", icon = R.drawable.ic_recenter) { onEvent(TsEvent.Recenter) }
    }
}

@Composable
private fun BigCard(title: String, sub: String, icon: Int, name: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight().probe(name),
        size = ButtonDefaults.Regular.copy(height = 200.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            // D13: a blue icon straight on the grey card is about 2:1; the blue goes into a disc with dark ink on it (5.6:1)
            // v0.1.5 (D44): 112 dp disc, the card is no longer half empty under its two lines
            Box(modifier = Modifier.size(112.dp).clip(CircleShape).background(TsColors.InteractionFill), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(64.dp), tint = TsColors.Ink)
            }
            Spacer(Modifier.height(6.dp))
            Text(text = title, style = TsType.title.copy(fontSize = 20.sp, lineHeight = 26.sp), color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits(name, title))
            // two lines reserved: the three titles stay level whichever card wraps
            Text(text = sub, style = TsType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(44.dp), onTextLayout = fits("${name}_sub", sub))
        }
    }
}

@Composable
private fun ColumnScope.LessonList(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    Header(state.t(Ui.lessonsTitle), null, "")
    Spacer(Modifier.height(8.dp))
    val list = rememberLazyListState()
    LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp))) {
        itemsIndexed((0..8).toList()) { _, l ->
            val g = if (l == 0) null else Gesture.entries[l - 1]
            val name = g?.let { Content.gestureName[it]!!.of(state.zh) } ?: Content.lesson0Title.of(state.zh)
            val steps = g?.steps ?: 4
            ListItem(
                modifier = Modifier.probe("lesson_$l").clickable { onEvent(TsEvent.OpenLesson(l)) },
                headlineContent = { Text(text = state.f(Ui.lessonRow, l, name), style = TsType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = state.f(Ui.stepsCount, steps), style = TsType.label, color = PicoTheme.colorScheme.labelPrimary)
                        if (l in state.learned) {
                            Icon(painter = painterResource(R.drawable.ic_check_circle), contentDescription = null, modifier = Modifier.size(20.dp), tint = PicoTheme.colorScheme.labelPrimary)
                            Text(text = state.t(Ui.seen), style = TsType.label, color = PicoTheme.colorScheme.labelPrimary)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.LevelList(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    Header(state.t(Ui.levelsTitle), null, "")
    Spacer(Modifier.height(8.dp))
    val next = Levels.recommended(state.best)
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp))) {
        itemsIndexed((1..Levels.COUNT).toList()) { _, l ->
            val total = Levels.questions[l - 1]
            ListItem(
                modifier = Modifier.probe("level_$l").clickable { onEvent(TsEvent.OpenLevel(l)) },
                headlineContent = { Text(text = state.f(Ui.levelRow, l, Content.levelName[l - 1].of(state.zh)), style = TsType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1) },
                supportingContent = { Text(text = state.f(Ui.questionsCount, total) + (state.best[l]?.let { "  ·  " + state.f(Ui.bestScore, it, total) } ?: ""), style = TsType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1) },
                trailingContent = { if (l == next) Chip(state.t(Ui.recommended), TsColors.InteractionFill, TsColors.Ink) },
            )
        }
    }
}

@Composable
private fun ColumnScope.Review(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    Header(state.t(Ui.reviewTitle), null, "") {
        Key(state.t(Ui.drill), "review_drill", icon = R.drawable.ic_replay, primary = true, enabled = state.mistakes.isNotEmpty()) { onEvent(TsEvent.Drill) }
    }
    Spacer(Modifier.height(8.dp))
    if (state.mistakes.isEmpty()) {
        // v0.1.5 (QA q5-2, D44): a centred empty state with a way out, not one line in an empty window
        Column(modifier = Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BigDisc(R.drawable.ic_menu_book, TsColors.InteractionFill, 140.dp, "review_empty_disc")
            Spacer(Modifier.height(24.dp))
            Text(text = state.t(Ui.reviewEmpty), style = TsType.headline, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), onTextLayout = fits("review_empty", state.t(Ui.reviewEmpty)))
            Spacer(Modifier.height(10.dp))
            Text(text = state.t(Ui.reviewEmptyTip), style = TsType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), onTextLayout = fits("review_empty_tip", state.t(Ui.reviewEmptyTip)))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Key(state.t(Ui.judge), "review_go", icon = R.drawable.ic_traffic, primary = true) { onEvent(TsEvent.OpenLevels) }
        }
        return
    }
    val distinct = state.mistakes.reversed().distinctBy { it.question }
    if (distinct.size <= 3) {
        // v0.1.5 (QA q5-2, D44): one to three mistakes are cards with their mini map, centred; the list below only from four
        val h = when (distinct.size) { 1 -> 172.dp; 2 -> 136.dp; else -> 104.dp }
        Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            distinct.forEachIndexed { i, m -> MistakeCard(state, m, h, "mistake_$i") }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(painter = painterResource(R.drawable.ic_lightbulb), contentDescription = null, modifier = Modifier.size(20.dp), tint = PicoTheme.colorScheme.labelPrimary)
                Spacer(Modifier.width(8.dp))
                Text(text = state.t(Ui.reviewFewTip), style = TsType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, onTextLayout = fits("review_tip", state.t(Ui.reviewFewTip)))
            }
        }
    } else {
        val groups = distinct.groupBy { it.question.gesture }
        val rows = groups.flatMap { (g, ms) -> listOf<Any>(g) + ms }
        // v0.1.5 (QA q5-2): the list takes its own height and sits in the middle; long lists still scroll in the full height
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            LazyColumn(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))) {
                itemsIndexed(rows) { _, r ->
                    if (r is Gesture) {
                        Text(text = Content.gestureName[r]!!.of(state.zh), style = TsType.label, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    } else if (r is io.embodit.trafficsignals.domain.Mistake) {
                        Text(text = state.mistakeLine(r), style = TsType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        }
    }
    // v0.1.4: [清空] asks first (critic R3) and sits in the bottom row, away from [再练错题]: in English the TabBar window
    // covers the top row up to x = 1199 px in adb coordinates, so the top-row [清空] was not reachable by a real tap there
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Key(state.t(Ui.clear), "review_clear", icon = R.drawable.ic_delete) { onEvent(TsEvent.AskClear) }
    }
}

private fun TsUiState.mistakeLine(m: io.embodit.trafficsignals.domain.Mistake): String {
    val q = m.question
    return f(Ui.mistakeLine, Content.facing[q.facing]!!.of(zh), Content.side[q.facing.you]!!.of(zh), Content.lane[q.lane]!!.of(zh), m.yours?.let { Content.answer[it]!!.of(zh) } ?: t(Ui.timeoutWord))
}

@Composable
private fun MistakeCard(state: TsUiState, m: io.embodit.trafficsignals.domain.Mistake, h: Dp, name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(18.dp)).background(PicoTheme.colorScheme.fillTertiary).padding(12.dp).probe(name),
    ) {
        MiniMap(m.question.facing, m.question.lane, Modifier.width((h - 24.dp) * 1.25f).fillMaxHeight())
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            val g = Content.gestureName[m.question.gesture]!!.of(state.zh)
            Text(text = g, style = if (h > 120.dp) TsType.headline else TsType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("${name}_title", g))
            Spacer(Modifier.height(6.dp))
            val line = state.mistakeLine(m)
            Text(text = line, style = if (h > 120.dp) TsType.title else TsType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, onTextLayout = fits("${name}_line", line))
        }
    }
}

@Composable
private fun ColumnScope.Settings(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    // q1-4 (profile D26): everything on one screen, no scrolling. Two columns of three rows, then a footer row pinned to
    // the bottom with About on the left and Quit app on the right, so Quit app is always in sight.
    Header(state.t(Ui.settings), null, "")
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            SettingRow(state.t(Ui.language), name = "row_lang") { Seg(listOf(state.t(Ui.langZh) to state.zh, state.t(Ui.langEn) to !state.zh), "set_lang", 72.dp) { onEvent(TsEvent.Lang(it == 0)) } }
            SettingRow(state.t(Ui.sound), name = "row_sound") { SoundKey(state, onEvent) }
            // q3-4: three detents (33 / 66 / 100 %) + the mute key above; the first picks up on any tap on the track
            SettingRow(state.t(Ui.volume), name = "row_volume") {
                Slider(
                    state.volume,
                    { v: Float -> onEvent(TsEvent.VolumeSet(v)) },
                    // critic B2 / B19: SpatialUI's Slider is 32 dp tall by default; the hit area must be 48 dp like every key
                    Modifier.width(150.dp).height(48.dp).probe("set_volume"),
                    0f..1f,
                    {},
                    colors = SliderDefaults.sliderColors(trackColor = PicoTheme.colorScheme.fillPrimary, progressColor = PicoTheme.colorScheme.interaction, progressHighColor = PicoTheme.colorScheme.interaction),
                )
                Text(text = Volume.percent(state.volume), style = TsType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, modifier = Modifier.width(48.dp), onTextLayout = fits("volume_pct", Volume.percent(state.volume)))
            }
            SettingRow(state.t(Ui.officerSize), name = "row_size") { Seg(listOf(state.t(Ui.lifeSize) to !state.big, state.t(Ui.bigger) to state.big), "set_size", 100.dp) { onEvent(TsEvent.Big(it == 1)) } }
        }
        Column(modifier = Modifier.weight(1f)) {
            SettingRow(state.t(Ui.gestureSpeed), name = "row_speed") { Seg(listOf(state.t(Ui.speed1) to !state.slow, state.t(Ui.speedHalf) to state.slow), "set_speed", 100.dp) { onEvent(TsEvent.Slow(it == 1)) } }
            SettingRow(state.t(Ui.recenterRow), state.t(Ui.recenterSub), name = "row_recenter") { Key(state.t(Ui.recenter), "set_recenter", icon = R.drawable.ic_recenter) { onEvent(TsEvent.Recenter) } }
            SettingRow(state.t(Ui.resetProgress), state.t(Ui.resetSub), name = "row_reset") { Key(state.t(Ui.reset), "set_reset", icon = R.drawable.ic_refresh, danger = true) { onEvent(TsEvent.AskReset) } }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(PicoTheme.colorScheme.dividerLine))
    Spacer(Modifier.height(8.dp))
    SettingRow(state.t(Ui.about), state.f(Ui.aboutLine, "v" + BuildConfig.VERSION_NAME), name = "row_about") {
        Key(state.t(Ui.exitApp), "set_exit", icon = R.drawable.ic_logout, danger = true) { onEvent(TsEvent.AskExit) }
    }
}

@Composable
private fun SettingRow(title: String, sub: String? = null, name: String = "row", trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(if (sub != null) 80.dp else 64.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = TsType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits(name, title))
            if (sub != null) Text(text = sub, style = TsType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, onTextLayout = fits("${name}_sub", sub))
        }
        trailing()
    }
}

// =============================================================================================
// Quiz (S5 S6 S9 S10 S14 + answers), in the window
// =============================================================================================

private fun TsUiState.levelTitle(): String = if (level == 0) t(Ui.drillName) else String.format(Ui.levelRow.of(zh), level, Content.levelName[level - 1].of(zh))

@Composable
private fun ColumnScope.Intro(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    Header(state.levelTitle(), null, "") {
        SoundKey(state, onEvent)
    }
    val goal = if (state.level in 1..6) Content.levelGoal[state.level - 1].of(state.zh) else state.t(Ui.drillGoal)
    val gestures = state.questions.map { it.gesture }.distinct().sortedBy { it.ordinal }
    // v0.1.5 (QA q5-4, D44): the goal, the signals this level asks and the three reading rules, spread over the free height
    Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = goal, style = TsType.headline, color = PicoTheme.colorScheme.labelPrimary, maxLines = 3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), onTextLayout = fits("intro_goal", goal))
            Spacer(Modifier.height(8.dp))
            Text(text = state.f(Ui.questionsN, state.questions.size) + if (state.timed) " · " + state.t(Ui.timedNote) else "", style = TsType.body, color = PicoTheme.colorScheme.labelPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.probe("intro_signals")) {
            Text(text = state.t(Ui.introSignals), style = TsType.label, color = PicoTheme.colorScheme.labelPrimary)
            gestures.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { Chip(Content.gestureName[it]!!.of(state.zh)) } }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PicoTheme.colorScheme.fillTertiary).padding(horizontal = 20.dp, vertical = 10.dp).probe("intro_rules")) {
            Explain("1", state.t(Ui.rule1))
            Explain("2", state.t(Ui.rule2))
            Explain("3", state.t(Ui.rule3))
        }
    }
    // q3-3: [返回] sits with [开始], out of the corner the TabBar and system windows cover
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Key(state.t(Ui.back), "quiz_back", icon = R.drawable.ic_chevron_left, height = 56.dp) { onEvent(TsEvent.OpenLevels) }
        Spacer(Modifier.weight(1f))
        Key(state.t(Ui.start), "quiz_start", icon = R.drawable.ic_play_arrow, primary = true, height = 56.dp) { onEvent(TsEvent.Start) }
    }
}

/** Level title, counter, Sound, Home: the top row of a question and of its verdict. Two title lines allowed, never an ellipsis (q1-2). */
@Composable
private fun QuizTop(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    val title = state.levelTitle()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(text = title, style = TsType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, modifier = Modifier.weight(1f), onTextLayout = fits("quiz_title", title))
        Chip("${state.index + 1} / ${state.questions.size}")
        // v0.1.4: [声音] left this row: in English the TabBar window covers it in adb coordinates (x < 1199 px, y < 176 px,
        // q3-3 class); it sits in the next row of the question and in the action row of the verdict
        Key(state.t(Ui.home), "quiz_home", icon = R.drawable.ic_home) { onEvent(TsEvent.Home) }
    }
}

@Composable
private fun ColumnScope.Question(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    val q = state.question ?: return
    val watching = state.canAnswer
    QuizTop(state, onEvent)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Chip(state.f(Ui.yourLane, Content.lane[state.lane]!!.of(state.zh)), TsColors.InteractionFill, TsColors.Ink)
        // v0.1.4: [声音] joined this row, so "Changing lane…" takes the free width (two lines if it must) instead of squeezing the keys
        if (state.phase == Phase.SWITCHING) Text(text = state.t(Ui.switching), style = TsType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, modifier = Modifier.weight(1f), onTextLayout = fits("quiz_switching", state.t(Ui.switching)))
        else Spacer(Modifier.weight(1f))
        SoundKey(state, onEvent)
        Key(state.t(Ui.hint), "quiz_hint", icon = R.drawable.ic_lightbulb, enabled = watching, outline = if (state.hint) PicoTheme.colorScheme.interaction else null) { onEvent(TsEvent.Hint) }
        Key(state.t(Ui.replay), "quiz_replay", icon = R.drawable.ic_replay, enabled = state.phase != Phase.SWITCHING) { onEvent(TsEvent.Replay) }
    }
    if (state.hint && watching) {
        Text(text = state.f(Ui.hintLine, Content.facing[q.facing]!!.of(state.zh), Content.side[q.facing.you]!!.of(state.zh)), style = TsType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
    Spacer(Modifier.weight(1f))
    Text(text = state.t(Ui.askLane), style = TsType.headline.copy(fontSize = 30.sp, lineHeight = 38.sp), color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, modifier = Modifier.align(Alignment.CenterHorizontally), onTextLayout = fits("quiz_ask", state.t(Ui.askLane)))
    Spacer(Modifier.weight(1f))
    @Composable
    fun RowScope.answer(a: Answer, w: Float = 1f) {
        // q3-1: nothing marks a key until the question is judged, paused and lane change included (TsUiState.answerMark)
        val outline =
            when (state.answerMark(a)) {
                KeyMark.RIGHT -> PicoTheme.colorScheme.passable
                KeyMark.WRONG -> PicoTheme.colorScheme.error
                KeyMark.NONE -> null
            }
        Key(Content.answer[a]!!.of(state.zh), "answer_${a.id}", height = 72.dp, enabled = watching || outline != null, outline = outline, fill = true, modifier = Modifier.weight(w)) { onEvent(TsEvent.Pick(a)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text = state.t(Ui.groupMove), style = TsType.label, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.width(48.dp))
        // English "Pull into waiting box" is twice as long as its neighbours
        answer(Answer.GO)
        answer(Answer.STOP)
        answer(Answer.WAIT, if (state.zh) 1f else 2f)
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text = state.t(Ui.groupDo), style = TsType.label, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.width(48.dp))
        answer(Answer.SLOW)
        answer(Answer.LANE)
        answer(Answer.PULL)
    }
    if (state.timed && !state.demo) {
        Spacer(Modifier.height(12.dp))
        val left by TsController.countdown
        LinearProgressIndicator(
            progress = { left },
            modifier = Modifier.fillMaxWidth().probe("countdown"),
            colors = LinearProgressDefaults.linearProgressColors(indicatorColor = if (state.phase == Phase.TIMEOUT || left < 0.4f) PicoTheme.colorScheme.alert else PicoTheme.colorScheme.interaction, backgroundColor = PicoTheme.colorScheme.fillTertiary),
            height = LinearProgressDefaults.linearProgressHeight(8.dp),
        )
    }
}

@Composable
private fun ColumnScope.Result(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    val score = state.outcomes.count { it.ok }
    Header(state.f(Ui.scoreTitle, score, state.outcomes.size), null, "") {
        Key(state.t(Ui.home), "result_home", icon = R.drawable.ic_home) { onEvent(TsEvent.Home) }
    }
    val missed = state.outcomes.filter { !it.ok }
    val allRight = missed.isEmpty()
    val rows = if (allRight) state.outcomes else missed
    // v0.1.5 (QA q5-1, D44): a big score block on the left; on the right the round's questions (all right) or the missed
    // ones, centred in the free height, so a short list never leaves a blank band
    Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(250.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (allRight) {
                BigDisc(R.drawable.ic_check_circle, PicoTheme.colorScheme.passable, 150.dp, "result_badge")
            } else {
                val s = "$score/${state.outcomes.size}"
                Box(modifier = Modifier.size(150.dp).clip(CircleShape).background(TsColors.InteractionFill).probe("result_badge"), contentAlignment = Alignment.Center) {
                    Text(text = s, style = TsType.headline.copy(fontSize = 40.sp, lineHeight = 48.sp), color = TsColors.Ink, maxLines = 1, onTextLayout = fits("result_score", s))
                }
            }
            Spacer(Modifier.height(16.dp))
            val line = if (allRight) state.t(Ui.allRight) else state.f(Ui.missedN, missed.size)
            Text(text = line, style = TsType.headline, color = PicoTheme.colorScheme.labelPrimary, maxLines = 3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), onTextLayout = fits("result_line", line))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = if (allRight) state.t(Ui.roundList) else state.t(Ui.wrongList), style = TsType.label, color = PicoTheme.colorScheme.labelPrimary)
            Spacer(Modifier.height(6.dp))
            val few = rows.size <= 4
            LazyColumn(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).probe("result_list")) {
                itemsIndexed(rows) { _, o ->
                    val q = o.question
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = if (few) 10.dp else 4.dp)) {
                        Icon(painter = painterResource(if (o.ok) R.drawable.ic_check_circle else R.drawable.ic_cancel), contentDescription = null, modifier = Modifier.size(if (few) 24.dp else 18.dp), tint = PicoTheme.colorScheme.labelPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Content.gestureName[q.gesture]!!.of(state.zh) + " · " + Content.facing[q.facing]!!.of(state.zh) + " · " + Content.lane[q.lane]!!.of(state.zh) + " → " + Content.answer[o.correct]!!.of(state.zh),
                            style = if (few) TsType.title else TsType.body,
                            color = PicoTheme.colorScheme.labelPrimary,
                            maxLines = if (few) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
        // critic B1 (D43): never a key that does nothing; the drill replays this round, so this is only a guard
        if (!state.canAgain) Text(text = state.t(Ui.drillDone), style = TsType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, modifier = Modifier.align(Alignment.CenterVertically), onTextLayout = fits("result_again_why", state.t(Ui.drillDone)))
        Key(state.t(Ui.again), "result_again", icon = R.drawable.ic_refresh, enabled = state.canAgain) { onEvent(TsEvent.Again) }
        if (state.level in 1 until Levels.COUNT) Key(state.t(Ui.nextLevel), "result_next", icon = R.drawable.ic_arrow_forward, primary = true) { onEvent(TsEvent.NextLevel) }
        else Key(state.t(Ui.toReview), "result_review", icon = R.drawable.ic_menu_book, primary = true) { onEvent(TsEvent.OpenReview) }
    }
}

// =============================================================================================
// Verdict (S7 S8 S14 + demo): stands in for the question in the window, the road shows the flows
// =============================================================================================

@Composable
private fun ColumnScope.Verdict(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    run {
        val q = state.question ?: return
        val v = state.verdict ?: return
        QuizTop(state, onEvent)
        Spacer(Modifier.height(6.dp))
        val (title, color, icon) =
            when (state.phase) {
                Phase.CORRECT -> Triple(state.t(Ui.right), PicoTheme.colorScheme.passable, R.drawable.ic_check_circle)
                Phase.TIMEOUT -> Triple(state.t(Ui.timeUp), PicoTheme.colorScheme.error, R.drawable.ic_timer)
                Phase.DEMO -> Triple(state.t(Ui.demo), TsColors.InteractionFill, R.drawable.ic_swap_horiz)
                else -> Triple(state.t(Ui.wrong), PicoTheme.colorScheme.error, R.drawable.ic_cancel)
            }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(44.dp)) {
            // verdict pill: the state colour as the fill, dark ink on it (contrast >= 4.5:1 on any glass, profile D13)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(color).padding(horizontal = 14.dp, vertical = 4.dp).probe("verdict_title"),
            ) {
                Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp), tint = TsColors.Ink)
                Spacer(Modifier.width(8.dp))
                Text(text = title, style = TsType.headline, color = TsColors.Ink, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Chip(if (v.official) state.t(Ui.official) else state.t(Ui.inferred), if (v.official) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary)
        }
        val correct = Content.answer[v.answer]!!.of(state.zh)
        Text(
            text = state.yours?.let { state.f(Ui.yoursVs, Content.answer[it]!!.of(state.zh), correct) } ?: state.f(Ui.yoursNone, correct),
            style = TsType.title,
            color = PicoTheme.colorScheme.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Explain("1", state.f(Ui.line1, Content.facing[q.facing]!!.of(state.zh), Content.side[q.facing.you]!!.of(state.zh)))
                Explain("2", Content.releaseLine(q.gesture).of(state.zh))
                Explain("3", state.f(Ui.line3, Content.lane[state.lane]!!.of(state.zh), correct))
            }
            MiniMap(q.facing, state.lane, Modifier.width(250.dp).fillMaxHeight())
        }
        Spacer(Modifier.height(12.dp))
        val last = state.index + 1 >= state.questions.size
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            SoundKey(state, onEvent)
            Spacer(Modifier.weight(1f))
            Key(state.t(Ui.breakdown), "verdict_breakdown", icon = R.drawable.ic_format_list_numbered) { onEvent(TsEvent.Breakdown) }
            if (state.level != Levels.LANE_LEVEL && io.embodit.trafficsignals.domain.nextAskableLane(q.copy(lane = state.lane)) != null) {
                Key(state.t(Ui.otherLane), "verdict_lane", icon = R.drawable.ic_swap_horiz) { onEvent(TsEvent.SwitchLane) }
            }
            Key(if (last) state.t(Ui.finish) else state.t(Ui.next), "verdict_next", icon = R.drawable.ic_arrow_forward, primary = true) { onEvent(TsEvent.NextQuestion) }
        }
    }
}

@Composable
private fun Explain(n: String, text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        // D13: the 14 sp digit on the small disc measured 3.6:1; 18 sp semi-bold on a 28 dp disc
        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(PicoTheme.colorScheme.fillSecondary), contentAlignment = Alignment.Center) {
            Text(text = n, style = TsType.title, color = PicoTheme.colorScheme.labelPrimary)
        }
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = TsType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Top-down mini map (design P3: junction, officer facing arrow, your car); north is up, you at the bottom. */
@Composable
private fun MiniMap(facing: Facing, lane: Lane, modifier: Modifier) {
    val asphalt = TsColors.Asphalt
    val white = TsColors.LaneWhite
    val you = PicoTheme.colorScheme.interaction
    val vest = TsColors.Vest
    Canvas(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(PicoTheme.colorScheme.fillTertiary)) {
        val s = size.height / 34f // metres -> px, 34 m tall
        val c = Offset(size.width / 2, size.height / 2 - 2 * s)
        fun p(x: Float, z: Float) = Offset(c.x + x * s, c.y + z * s)
        // roads
        drawRect(asphalt, topLeft = p(-9f, -17f), size = androidx.compose.ui.geometry.Size(18f * s, 40f * s))
        drawRect(asphalt, topLeft = Offset(c.x - 30 * s, c.y - 9 * s), size = androidx.compose.ui.geometry.Size(60f * s, 18f * s))
        // your lane dividers and stop line (south approach)
        for (x in floatArrayOf(3f, 6f)) drawLine(white, p(x, 12.5f), p(x, 20f), strokeWidth = 1.5f)
        drawLine(Color(0xFFF2C230), p(0f, 12.5f), p(0f, 20f), strokeWidth = 2f)
        drawLine(white, p(0.3f, 12.5f), p(8.7f, 12.5f), strokeWidth = 3f)
        // you
        val yx = lane.x
        drawRoundRect(you, topLeft = p(yx - 0.9f, 12.8f), size = androidx.compose.ui.geometry.Size(1.8f * s, 4.4f * s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
        // officer on the post with a facing arrow
        drawCircle(Color.White, radius = 1.4f * s, center = c)
        drawCircle(vest, radius = 0.9f * s, center = c)
        val dir = facing.compass
        rotate(degrees = when (dir) { Compass.NORTH -> 0f; Compass.EAST -> 90f; Compass.SOUTH -> 180f; Compass.WEST -> 270f }, pivot = c) {
            val path = Path().apply {
                moveTo(c.x, c.y - 6f * s)
                lineTo(c.x - 1.6f * s, c.y - 3.2f * s)
                lineTo(c.x + 1.6f * s, c.y - 3.2f * s)
                close()
            }
            drawLine(vest, c, Offset(c.x, c.y - 4f * s), strokeWidth = 0.7f * s)
            drawPath(path, vest)
        }
        drawCircle(white, radius = 1.4f * s, center = c, style = Stroke(width = 2f))
    }
}

// =============================================================================================
// Lesson (S3 S4) and the breakdown stand-in for the verdict
// =============================================================================================

@Composable
private fun ColumnScope.Lesson(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    run {
        val g = state.lessonGesture
        val title = g?.let { Content.gestureName[it]!!.of(state.zh) } ?: Content.lesson0Title.of(state.zh)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(text = title, style = TsType.headline, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), onTextLayout = fits("lesson_title", title))
            Chip(if (g == null) state.t(Ui.lesson0Of) else state.f(Ui.lessonOf, state.lesson))
            if (state.breakdown) {
                Key(state.t(Ui.backToQuestion), "lesson_back", icon = R.drawable.ic_chevron_left) { onEvent(TsEvent.CloseBreakdown) }
            } else {
                Key(state.t(Ui.back), "lesson_back_list", icon = R.drawable.ic_chevron_left) { onEvent(TsEvent.Back) }
            }
        }
        Spacer(Modifier.height(6.dp))
        val steps = g?.let { Content.steps[it]!! } ?: Content.lesson0Steps
        val list = rememberLazyListState()
        // scroll only when the current step is not fully in view: a list moving under the pointer loses taps on its rows
        LaunchedEffect(state.lessonStep) {
            val info = list.layoutInfo
            val row = info.visibleItemsInfo.firstOrNull { it.index == state.lessonStep }
            val inView = row != null && row.offset >= 0 && row.offset + row.size <= info.viewportEndOffset
            if (!inView) runCatching { list.animateScrollToItem((state.lessonStep - 1).coerceAtLeast(0)) }
        }
        // v0.1.5 (QA q5-4, D44): the list takes its own height and sits in the middle; a lesson of <= 4 steps gets big cards
        val few = steps.size <= 4
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))) {
            itemsIndexed(steps) { i, tr ->
                val current = i == state.lessonStep
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (current) PicoTheme.colorScheme.fillSecondary else Color.Transparent)
                            .then(if (current) Modifier.border(2.dp, PicoTheme.colorScheme.interaction, RoundedCornerShape(14.dp)) else Modifier)
                            .clickable { onEvent(TsEvent.LessonStep(i)) }
                            .padding(horizontal = if (few) 16.dp else 10.dp, vertical = if (few) 18.dp else 8.dp)
                            .probe("step_$i"),
                ) {
                    Text(text = "${i + 1}", style = if (few) TsType.headline else TsType.title, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.width(if (few) 34.dp else 26.dp))
                    Column {
                        Text(text = tr.of(state.zh), style = if (few) TsType.title else TsType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = if (current || few) 3 else 2, overflow = TextOverflow.Ellipsis)
                        if (!state.zh) Text(text = tr.zh, style = TsType.label.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)), color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        }
        if (g != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(R.drawable.ic_lightbulb), contentDescription = null, modifier = Modifier.size(20.dp), tint = PicoTheme.colorScheme.labelPrimary)
                Spacer(Modifier.width(8.dp))
                Text(text = Content.mnemonic[g]!!.of(state.zh), style = TsType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Key(state.t(Ui.prevStep), "lesson_prev", icon = R.drawable.ic_skip_previous, enabled = state.lessonStep > 0) { onEvent(TsEvent.LessonStepBy(-1)) }
            Key(if (state.lessonPlaying) state.t(Ui.pause) else state.t(Ui.play), "lesson_play", icon = if (state.lessonPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow, primary = true) { onEvent(TsEvent.LessonPlayPause) }
            Key(state.t(Ui.nextStep), "lesson_next", icon = R.drawable.ic_skip_next, enabled = state.lessonStep < steps.size - 1) { onEvent(TsEvent.LessonStepBy(1)) }
            Spacer(Modifier.weight(1f))
            if (!state.breakdown) Key(state.t(Ui.nextLesson), "lesson_nextlesson", icon = R.drawable.ic_arrow_forward) { onEvent(TsEvent.NextLesson) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (g != null) {
                Text(text = state.t(Ui.angle), style = TsType.label, color = PicoTheme.colorScheme.labelPrimary)
                Seg(
                    listOf(state.t(Ui.front) to (state.lessonAngle == Facing.SOUTH), state.t(Ui.left) to (state.lessonAngle == Facing.WEST), state.t(Ui.rightSide) to (state.lessonAngle == Facing.EAST), state.t(Ui.behind) to (state.lessonAngle == Facing.NORTH)),
                    "angle",
                    64.dp,
                ) { onEvent(TsEvent.LessonAngle(listOf(Facing.SOUTH, Facing.WEST, Facing.EAST, Facing.NORTH)[it])) }
                Seg(listOf(state.t(Ui.speed1) to !state.slow, state.t(Ui.speedHalfShort) to state.slow), "lesson_speed", 84.dp) { onEvent(TsEvent.Slow(it == 1)) }
            }
        }
        if (!state.breakdown) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(R.drawable.ic_3d_rotation), contentDescription = null, modifier = Modifier.size(20.dp), tint = PicoTheme.colorScheme.labelPrimary)
                Spacer(Modifier.width(8.dp))
                Text(text = state.t(Ui.stickTip), style = TsType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1)
            }
        }
    }
}

// =============================================================================================
// Confirmations (S12 leave, S13 blocked, exit, reset): take the page's place in the window, no second panel (D8)
// =============================================================================================

@Composable
private fun ColumnScope.Confirm(state: TsUiState, onEvent: (TsEvent) -> Unit) {
    val (title, body) =
        when {
            state.screen == Screen.BLOCKED -> state.t(Ui.blockedTitle) to state.f(Ui.blockedBody, state.blockReason ?: "?")
            state.dialog == Dialog.EXIT -> state.t(Ui.exitTitle) to state.t(Ui.exitBody)
            state.dialog == Dialog.RESET -> state.t(Ui.resetTitle) to state.t(Ui.resetBody)
            state.dialog == Dialog.CLEAR -> state.t(Ui.clearTitle) to state.f(Ui.clearBody, state.mistakes.size)
            else -> state.t(Ui.leaveTitle) to state.t(Ui.leaveBody)
        }
    val (icon, fill) =
        when {
            state.screen == Screen.BLOCKED -> R.drawable.ic_cancel to PicoTheme.colorScheme.error
            state.dialog == Dialog.EXIT -> R.drawable.ic_logout to PicoTheme.colorScheme.error
            state.dialog == Dialog.RESET -> R.drawable.ic_refresh to PicoTheme.colorScheme.error
            state.dialog == Dialog.CLEAR -> R.drawable.ic_delete to PicoTheme.colorScheme.error
            else -> R.drawable.ic_home to TsColors.InteractionFill
        }
    val keyW = 240.dp
    val keyH = 72.dp
    // v0.1.5 (QA q5-3, D44): one template for every confirmation: a big icon on top, the question in 32 sp and its line in
    // 20 sp in the middle, 72 dp keys at the bottom, spread over the whole window (no small card with wide margins)
    Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        BigDisc(icon, fill, 136.dp, "dialog_icon")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = TsType.headline.copy(fontSize = 32.sp, lineHeight = 40.sp), color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, textAlign = TextAlign.Center, onTextLayout = fits("dialog_title", title))
            Spacer(Modifier.height(14.dp))
            Text(text = body, style = TsType.body.copy(fontSize = 20.sp, lineHeight = 28.sp), color = PicoTheme.colorScheme.labelPrimary, maxLines = 3, textAlign = TextAlign.Center, onTextLayout = fits("dialog_body", body))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            val m = Modifier.width(keyW)
            when {
                state.screen == Screen.BLOCKED -> Key(state.t(Ui.exit), "dialog_exit", icon = R.drawable.ic_logout, danger = true, height = keyH, large = true, fill = true, modifier = m) { onEvent(TsEvent.Exit) }
                state.dialog == Dialog.EXIT -> {
                    Key(state.t(Ui.cancel), "dialog_cancel", icon = R.drawable.ic_close, height = keyH, large = true, fill = true, modifier = m) { onEvent(TsEvent.CloseDialog) }
                    Key(state.t(Ui.exit), "dialog_exit", icon = R.drawable.ic_logout, danger = true, height = keyH, large = true, fill = true, modifier = m) { onEvent(TsEvent.Exit) }
                }
                state.dialog == Dialog.RESET -> {
                    Key(state.t(Ui.cancel), "dialog_cancel", icon = R.drawable.ic_close, height = keyH, large = true, fill = true, modifier = m) { onEvent(TsEvent.CloseDialog) }
                    Key(state.t(Ui.reset), "dialog_reset", icon = R.drawable.ic_delete, danger = true, height = keyH, large = true, fill = true, modifier = m) { onEvent(TsEvent.ResetProgress) }
                }
                state.dialog == Dialog.CLEAR -> {
                    Key(state.t(Ui.cancel), "dialog_cancel", icon = R.drawable.ic_close, height = keyH, large = true, fill = true, modifier = m) { onEvent(TsEvent.CloseDialog) }
                    Key(state.t(Ui.clear), "dialog_clear", icon = R.drawable.ic_delete, danger = true, height = keyH, large = true, fill = true, modifier = m) { onEvent(TsEvent.ClearMistakes) }
                }
                else -> {
                    Key(state.t(Ui.leave), "dialog_leave", icon = R.drawable.ic_home, height = keyH, large = true, fill = true, modifier = m) { onEvent(TsEvent.Leave) }
                    Key(state.t(Ui.keepGoing), "dialog_stay", icon = R.drawable.ic_play_arrow, primary = true, height = keyH, large = true, fill = true, modifier = m) { onEvent(TsEvent.CloseDialog) }
                }
            }
        }
    }
}

// =============================================================================================
// On the Stage: only while the window is not there (native-window-move §3.3): "打开面板", or "平视前方" while it waits
// =============================================================================================

@Composable
internal fun OpenChip(zh: Boolean) {
    Box(Modifier.size(ChipSize.W.dp, ChipSize.H.dp), contentAlignment = Alignment.Center) {
        if (WindowCtl.levelHint) {
            // D9: the window waits for a level head; not a key, nothing to press
            Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(TsColors.Ink.copy(alpha = 0.85f)).padding(horizontal = 16.dp, vertical = 6.dp).probe("look_level"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = Ui.lookLevel.of(zh), style = TsType.title, color = TsColors.LaneWhite, maxLines = 1, onTextLayout = fits("chip/look_level", Ui.lookLevel.of(zh)))
                Text(text = Ui.lookLevel2.of(zh), style = TsType.label, color = TsColors.LaneWhite, maxLines = 1, onTextLayout = fits("chip/look_level_2", Ui.lookLevel2.of(zh)))
            }
            return@Box
        }
        Key(Ui.openPanel.of(zh), "open_panel", icon = R.drawable.ic_open_in_new, primary = true, height = 56.dp, large = true) { WindowCtl.reopen("chip") }
    }
}

internal object ChipSize {
    const val W = 340f
    const val H = 96f
}

@Suppress("unused")
private val keepImports = listOf(TextAlign.Center, Judge)
