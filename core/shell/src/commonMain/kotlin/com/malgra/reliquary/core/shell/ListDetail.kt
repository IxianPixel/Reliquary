package com.malgra.reliquary.core.shell

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.malgra.reliquary.core.ui.rememberPaneDirective

/** A tab's list. Where two panes fit, the screen it opens sits beside it. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val ListPane: Map<String, Any> = ListDetailSceneStrategy.listPane()

/** A screen opened from a list, shown beside the list where two panes fit. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val DetailPane: Map<String, Any> = ListDetailSceneStrategy.detailPane()

/**
 * Lists and their details side by side where the window has two panes, and one
 * screen at a time everywhere else, as the phone always has.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberSceneStrategy(): SceneStrategy<NavKey> =
    rememberListDetailSceneStrategy<NavKey>(directive = rememberPaneDirective()) then SinglePaneSceneStrategy()

/**
 * Opens a channel or playlist from its list. A detail already on top (the list
 * pane's last pick on a wide window) is replaced, so picking another item
 * doesn't stack screens to go back through. On a phone the list is never
 * under a detail, so this is just an add there.
 */
fun MutableList<NavKey>.openFromList(detail: NavKey) {
    if (lastOrNull() is ChannelDetail || lastOrNull() is PlaylistDetail) removeAt(lastIndex)
    add(detail)
}

/**
 * Back for the stack. On a wide window a channel or playlist is shown beside its
 * list, so Back from it leaves the list too, as Back from the list would. On a
 * narrow window the list is a screen of its own, so Back returns to it.
 */
fun MutableList<NavKey>.back(twoPanes: Boolean) {
    if (size <= 1) return
    val removed = removeAt(lastIndex)
    val leavesList = twoPanes && (removed is ChannelDetail || removed is PlaylistDetail)
    if (leavesList && (lastOrNull() is Channels || lastOrNull() is Playlists)) removeAt(lastIndex)
}
