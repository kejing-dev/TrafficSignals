package io.embodit.trafficsignals.ui.window

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.TabBar
import com.pico.spatial.ui.platform.ViewPoint
import io.embodit.trafficsignals.platform.TsLog

/*
 * 主窗口模板（taste D8「合成一个窗口模板」+ D21 拖动交给系统 + D26 设置里退出应用），产线 prompts/snippets/
 * MainWindowTemplate.kt 的本应用版。和产线版的差别：当前页由状态机决定（[current] + [onSelect]），调试广播和按键走同一套事件；
 * 页面内容由 TsWindow 画（本应用的「一页」在答题 / 课程时还要换内容）；退出的二次确认也在状态机里（中英双语）。
 * 应用全部 2D 内容都在这一个系统窗口里；页面用系统 TabBar 切；拖动 / 关闭 / 最小化是系统窗口控件，这里不写任何拖动代码。
 */

/** One TabBar item. [icon]: a Material Symbols vector (taste D23). Settings is the last one (D26). */
class MainWindowPage(val id: String, val label: String, @DrawableRes val icon: Int)

@Composable
fun MainWindow(pages: List<MainWindowPage>, current: String, onSelect: (String) -> Unit, content: @Composable () -> Unit) {
    require(pages.size in 1..7) { "TabBar holds at most 7 items" }
    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp)) { content() }
    // system TabBar: its own small window outside this one (emulator 6.1.0, seen from the front: centred above the window)
    TabBar(followViewpoints = setOf(ViewPoint.Front, ViewPoint.Left)) {
        pages.forEach { p ->
            item(
                selected = current == p.id,
                onClick = { TsLog.i("TAB ${p.id}"); onSelect(p.id) },
                mainContent = { Icon(painter = painterResource(p.icon), contentDescription = p.label) },
                supportContent = { Text(p.label) },
            )
        }
    }
}
