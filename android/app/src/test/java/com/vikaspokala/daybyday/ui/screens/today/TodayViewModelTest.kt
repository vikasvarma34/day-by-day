package com.vikaspokala.daybyday.ui.screens.today

import com.vikaspokala.daybyday.ui.FakePlannerDatabase
import com.vikaspokala.daybyday.ui.RecordingPlannerRepository
import com.vikaspokala.daybyday.ui.scheduledOccurrence
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TodayViewModelTest {
    private val today = LocalDate.of(2026, 8, 20)

    @Test
    fun selectedDateSwitchesRoomProjection() {
        val tomorrow = today.plusDays(1)
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(
                today to listOf(scheduledOccurrence("today", today)),
                tomorrow to listOf(scheduledOccurrence("tomorrow", tomorrow))
            )
        }
        val viewModel = TodayViewModel(database, RecordingPlannerRepository(database), { today })

        assertEquals(listOf("today"), viewModel.tasks.value.map { it.id })
        viewModel.selectDate(tomorrow)
        assertEquals(listOf("tomorrow"), viewModel.tasks.value.map { it.id })
    }

    @Test
    fun groupsTimedThenAnytimeThenCompleted_withExistingSortWithinTimedGroups() {
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(
                today to listOf(
                    scheduledOccurrence("completed-anytime", today, isCompleted = true),
                    scheduledOccurrence("active-late", today, time = "18:00"),
                    scheduledOccurrence("active-anytime-a", today),
                    scheduledOccurrence("completed-late", today, time = "20:00", isCompleted = true),
                    scheduledOccurrence("active-early", today, time = "07:30"),
                    scheduledOccurrence("completed-early", today, time = "09:00", isCompleted = true),
                    scheduledOccurrence("active-anytime-b", today)
                )
            )
        }
        val viewModel = TodayViewModel(database, RecordingPlannerRepository(database), { today })

        assertEquals(
            listOf(
                "active-early", "active-late",
                "active-anytime-a", "active-anytime-b",
                "completed-early", "completed-late",
                "completed-anytime"
            ),
            viewModel.getTasksForDate(today).map { it.id }
        )
    }

    @Test
    fun nextAndPreviousDayNavigateExactlyOneDay() {
        val database = FakePlannerDatabase()
        val viewModel = TodayViewModel(database, RecordingPlannerRepository(database), { today })

        viewModel.selectNextDay()
        assertEquals(today.plusDays(1), viewModel.selectedDate.value)
        viewModel.selectPreviousDay()
        viewModel.selectPreviousDay()
        assertEquals(today.minusDays(1), viewModel.selectedDate.value)
    }

    @Test
    fun dayNavigationCrossesMonthBoundary() {
        val initial = LocalDate.of(2026, 8, 31)
        val database = FakePlannerDatabase()
        val viewModel = TodayViewModel(database, RecordingPlannerRepository(database), { initial })

        viewModel.selectNextDay()
        assertEquals(LocalDate.of(2026, 9, 1), viewModel.selectedDate.value)
        viewModel.selectPreviousDay()
        assertEquals(initial, viewModel.selectedDate.value)
    }

    @Test
    fun dayNavigationCrossesYearBoundary() {
        val initial = LocalDate.of(2026, 12, 31)
        val database = FakePlannerDatabase()
        val viewModel = TodayViewModel(database, RecordingPlannerRepository(database), { initial })

        viewModel.selectNextDay()
        assertEquals(LocalDate.of(2027, 1, 1), viewModel.selectedDate.value)
        viewModel.selectPreviousDay()
        assertEquals(initial, viewModel.selectedDate.value)
    }

    @Test
    fun greetingRangesKeepAcceptedBoundaries() {
        assertEquals("Good morning", TodayViewModel.getGreeting(LocalTime.of(5, 0)))
        assertEquals("Good morning", TodayViewModel.getGreeting(LocalTime.of(11, 59)))
        assertEquals("Good afternoon", TodayViewModel.getGreeting(LocalTime.of(12, 0)))
        assertEquals("Good afternoon", TodayViewModel.getGreeting(LocalTime.of(16, 59)))
        assertEquals("Good evening", TodayViewModel.getGreeting(LocalTime.of(17, 0)))
        assertEquals("Good evening", TodayViewModel.getGreeting(LocalTime.of(20, 59)))
        assertEquals("Good night", TodayViewModel.getGreeting(LocalTime.of(21, 0)))
        assertEquals("Good night", TodayViewModel.getGreeting(LocalTime.of(21, 59)))
        assertEquals("Time to rest", TodayViewModel.getGreeting(LocalTime.of(22, 0)))
        assertEquals("Time to rest", TodayViewModel.getGreeting(LocalTime.of(4, 59)))
    }

    @Test
    fun greeting_withNonNullNickname_usesNickname() {
        val user = com.vikaspokala.daybyday.data.remote.dto.AuthUserDto(
            id = "11111111-2222-3333-4444-555555555555",
            email = "alice@example.com",
            firstName = "Alice",
            lastName = "Smith",
            nickname = "Ali"
        )
        val displayName = user.nickname?.takeIf { it.isNotBlank() } ?: user.firstName
        val greeting = TodayViewModel.getGreeting(LocalTime.of(9, 0), displayName)
        assertEquals("Good morning, Ali", greeting)
    }

    @Test
    fun greeting_withNullNickname_fallsBackToRealFirstName() {
        val user = com.vikaspokala.daybyday.data.remote.dto.AuthUserDto(
            id = "11111111-2222-3333-4444-555555555555",
            email = "alice@example.com",
            firstName = "Alice",
            lastName = "Smith",
            nickname = null
        )
        val displayName = user.nickname?.takeIf { it.isNotBlank() } ?: user.firstName
        val greeting = TodayViewModel.getGreeting(LocalTime.of(9, 0), displayName)
        assertEquals("Good morning, Alice", greeting)
    }

    @Test
    fun greeting_clearingNickname_cannotResurrectFakeNickname() {
        val userAfterClear = com.vikaspokala.daybyday.data.remote.dto.AuthUserDto(
            id = "11111111-2222-3333-4444-555555555555",
            email = "dev.user@example.com",
            firstName = "Vikram",
            lastName = "Pokala",
            nickname = null
        )
        val displayName = userAfterClear.nickname?.takeIf { it.isNotBlank() } ?: userAfterClear.firstName
        val greeting = TodayViewModel.getGreeting(LocalTime.of(14, 0), displayName)
        assertEquals("Good afternoon, Vikram", greeting)
        assertFalse(greeting.contains("Vicky"))
        assertFalse(greeting.contains("Wiki"))
    }

    @Test
    fun twentyOneFiftyNineDoesNotShowTimeToRest() {
        val greeting = TodayViewModel.getGreeting(LocalTime.of(21, 59), "Alex")
        assertEquals("Good night, Alex", greeting)
        assertFalse(greeting.contains("Time to rest"))
    }

    @Test fun twentyTwoHundredShowsTimeToRest() =
        assertEquals("Time to rest, Alex", TodayViewModel.getGreeting(LocalTime.of(22, 0), "Alex"))

    @Test fun twentyThreeFiftyNineShowsTimeToRest() =
        assertEquals("Time to rest, Alex", TodayViewModel.getGreeting(LocalTime.of(23, 59), "Alex"))

    @Test
    fun overnightPeriodUsesTimeToRest() {
        assertEquals("Time to rest, Alex", TodayViewModel.getGreeting(LocalTime.MIDNIGHT, "Alex"))
        assertEquals("Time to rest, Alex", TodayViewModel.getGreeting(LocalTime.of(2, 30), "Alex"))
        assertEquals("Time to rest, Alex", TodayViewModel.getGreeting(LocalTime.of(4, 59), "Alex"))
    }

    @Test fun morningGreetingRemainsUnchanged() =
        assertEquals("Good morning, Alex", TodayViewModel.getGreeting(LocalTime.of(8, 30), "Alex"))

    @Test fun afternoonGreetingRemainsUnchanged() =
        assertEquals("Good afternoon, Alex", TodayViewModel.getGreeting(LocalTime.of(14, 15), "Alex"))

    @Test fun eveningGreetingRemainsUnchanged() =
        assertEquals("Good evening, Alex", TodayViewModel.getGreeting(LocalTime.of(19, 0), "Alex"))

    @Test
    fun customAndBlankNamesRemainSupported() {
        assertEquals("Time to rest, CustomNickname", TodayViewModel.getGreeting(LocalTime.of(22, 30), "CustomNickname"))
        assertEquals("Time to rest", TodayViewModel.getGreeting(LocalTime.of(22, 30), "  "))
        assertEquals("Good morning", TodayViewModel.getGreeting(LocalTime.of(9, 0), ""))
    }
}
