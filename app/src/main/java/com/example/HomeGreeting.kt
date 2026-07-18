package com.example

import java.util.Calendar

/** Six slices of the day the Home header's greeting rotates through, keyed off the device's
 * current wall-clock hour - not the user's declared timezone/locale, just whatever the phone
 * itself currently reads, same as any other "good morning" style greeting. */
private enum class GreetingBucket { EARLY_MORNING, MORNING, NOON, AFTERNOON, EVENING, LATE_NIGHT }

private fun bucketForHour(hour: Int): GreetingBucket = when (hour) {
    in 4..6 -> GreetingBucket.EARLY_MORNING
    in 7..10 -> GreetingBucket.MORNING
    in 11..13 -> GreetingBucket.NOON
    in 14..17 -> GreetingBucket.AFTERNOON
    in 18..21 -> GreetingBucket.EVENING
    else -> GreetingBucket.LATE_NIGHT // 22:00-03:59
}

/** A handful of 2-3 sentence variants per [GreetingBucket], so the header doesn't say the exact
 * same thing on every visit within the same part of the day. */
private val greetingMessages: Map<GreetingBucket, List<String>> = mapOf(
    GreetingBucket.EARLY_MORNING to listOf(
        "Up before the sun. The world's quiet right now - let something soft keep you company while it wakes up.",
        "Early start today. A slow, easy playlist might be the best way to ease into it.",
        "Still dark out, but you're already moving. Here's something gentle to match the calm."
    ),
    GreetingBucket.MORNING to listOf(
        "Good morning. Whatever's on the list today, a good soundtrack makes it easier to get going.",
        "Morning's here. Pick something upbeat and let it carry you into the day.",
        "Fresh coffee, fresh playlist. Let's find the right track to kick things off."
    ),
    GreetingBucket.NOON to listOf(
        "Midday check-in. A quick listen is a good excuse for a break, even a short one.",
        "The day's in full swing. Something with a little energy might help push through the rest of it.",
        "Lunchtime, or close to it. Good moment to hit play and reset for a few minutes."
    ),
    GreetingBucket.AFTERNOON to listOf(
        "Afternoon slump incoming - a good track or two can help fight it off.",
        "Still a few hours left in the day. Let's find something to keep the momentum going.",
        "The afternoon stretch. Time for a playlist that keeps you focused without dragging."
    ),
    GreetingBucket.EVENING to listOf(
        "Evening's here. Time to wind down, or maybe wind up - your call on what tonight calls for.",
        "The day's winding down. Something warm and familiar might hit just right about now.",
        "Night's settling in. Good time to put something on and just let it play."
    ),
    GreetingBucket.LATE_NIGHT to listOf(
        "Up late again? No judgment - here's something for the quiet hours.",
        "Late night, low lights. Something mellow could be exactly what this moment needs.",
        "Most people are asleep by now. Let's find a track that suits the stillness."
    )
)

/** Picks a random message from whichever [GreetingBucket] the current device hour falls into -
 * called once per Home header composition (see [HomeHeader]), not re-rolled on every
 * recomposition, so it stays stable for as long as the screen is up. */
fun randomHomeGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return greetingMessages.getValue(bucketForHour(hour)).random()
}
