package com.example

/** Traverses the player's real order once. Repeat-one cannot trap recovery on a failed item. */
internal fun nextRecoverableIndex(current: Int, nextIndex: (Int) -> Int, playable: (Int) -> Boolean): Int? {
    val visited = mutableSetOf(current)
    var index = nextIndex(current)
    while (index >= 0 && visited.add(index)) {
        if (playable(index)) return index
        index = nextIndex(index)
    }
    return null
}
