package com.mindenit.schedule.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.mindenit.schedule.databinding.FragmentHomeBinding
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MonthAdapter
    private var rowsCount: Int = 6
    private var selectedYearMonth: YearMonth = YearMonth.now()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Use the selected month (defaults to current)
        val yearMonth = selectedYearMonth
        setAppBarTitle(yearMonth)

        val days = generateMonthGrid(yearMonth)

        adapter = MonthAdapter(days)
        binding.calendarGrid.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.calendarGrid.setHasFixedSize(true)
        binding.calendarGrid.adapter = adapter

        // After first layout pass, compute row height to evenly fill the RecyclerView
        binding.calendarGrid.post {
            val totalH = binding.calendarGrid.height
            if (totalH > 0 && rowsCount > 0) {
                val rowH = totalH / rowsCount
                adapter.setItemHeight(rowH)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure title remains the selected month when returning to this screen
        setAppBarTitle(selectedYearMonth)
    }

    private fun setAppBarTitle(yearMonth: YearMonth) {
        val headerFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("uk"))
        val headerText = yearMonth.atDay(1).format(headerFormatter)
        (activity as? AppCompatActivity)?.supportActionBar?.title = headerText
    }

    private fun generateMonthGrid(yearMonth: YearMonth): List<CalendarDay> {
        val firstOfMonth = yearMonth.atDay(1)
        val lastOfMonth = yearMonth.atEndOfMonth()

        val start = firstOfMonth.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = lastOfMonth.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

        val days = mutableListOf<CalendarDay>()
        var d = start
        while (!d.isAfter(end)) {
            val inCurrent = d.month == yearMonth.month
            val eventCount = 0
            days += CalendarDay(d, inCurrentMonth = inCurrent, eventCount = eventCount)
            d = d.plusDays(1)
        }
        rowsCount = days.size / 7
        return days
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}