package tv.own.owntv.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Shared per-item focus-target selection for the paged Movies/Series/Live lists and grids:
 * bind the FocusRequester to the item the context menu was opened on, else the last-selected
 * item, else the first item — so "restore focus to id X, else first item" is expressed once
 * instead of copy-pasted per screen/branch.
 */
fun Modifier.gridFocusTarget(
    itemId: Long,
    index: Int,
    contextId: Long?,
    contextFocus: FocusRequester,
    selectedId: Long?,
    selectedFocus: FocusRequester,
    firstItemFocus: FocusRequester,
): Modifier = when {
    itemId == contextId -> focusRequester(contextFocus)
    itemId == selectedId -> focusRequester(selectedFocus)
    index == 0 -> focusRequester(firstItemFocus)
    else -> this
}

/**
 * Same choice as [gridFocusTarget], but reading the ids from [State] through a per-item
 * derivedStateOf. Passing the plain ids made every visible poster recompose on each D-pad move
 * (the selected id changes on every focus change); this way only the two items whose role
 * actually flips recompose, and the grid's item lambda stays the same instance between moves.
 */
@androidx.compose.runtime.Composable
fun Modifier.gridFocusTarget(
    itemId: Long,
    index: Int,
    contextId: androidx.compose.runtime.State<Long?>,
    contextFocus: FocusRequester,
    selectedId: androidx.compose.runtime.State<Long?>,
    selectedFocus: FocusRequester,
    firstItemFocus: FocusRequester,
): Modifier {
    val role by androidx.compose.runtime.remember(itemId, index, contextId, selectedId) {
        androidx.compose.runtime.derivedStateOf {
            when {
                itemId == contextId.value -> FocusRole.CONTEXT
                itemId == selectedId.value -> FocusRole.SELECTED
                index == 0 -> FocusRole.FIRST
                else -> FocusRole.NONE
            }
        }
    }
    return when (role) {
        FocusRole.CONTEXT -> focusRequester(contextFocus)
        FocusRole.SELECTED -> focusRequester(selectedFocus)
        FocusRole.FIRST -> focusRequester(firstItemFocus)
        FocusRole.NONE -> this
    }
}

private enum class FocusRole { CONTEXT, SELECTED, FIRST, NONE }
