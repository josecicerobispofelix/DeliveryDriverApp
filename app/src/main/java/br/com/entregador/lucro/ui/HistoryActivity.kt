package br.com.entregador.lucro.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.entregador.lucro.R
import br.com.entregador.lucro.data.repository.DeliveryHistoryRepository
import br.com.entregador.lucro.data.repository.SQLiteDeliveryHistoryRepository
import br.com.entregador.lucro.domain.model.DailyShiftSummary
import br.com.entregador.lucro.domain.model.DeliveryHistoryRecord
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var historyRepository: DeliveryHistoryRepository
    private lateinit var adapter: HistoryAdapter

    private lateinit var btnBack: ImageButton
    private lateinit var btnShareReport: MaterialButton
    private lateinit var tvPeriodSubtitle: TextView
    private lateinit var cgFilters: ChipGroup
    private lateinit var chipToday: Chip
    private lateinit var chipYesterday: Chip
    private lateinit var chipLast7Days: Chip
    private lateinit var chipThisMonth: Chip
    private lateinit var chipAll: Chip

    private lateinit var tvPeriodNetProfit: TextView
    private lateinit var tvPeriodMetrics: TextView
    private lateinit var tvPeriodGreenCount: TextView
    private lateinit var tvPeriodYellowCount: TextView
    private lateinit var tvPeriodRedCount: TextView
    private lateinit var rvHistoryRecords: RecyclerView
    private lateinit var tvEmptyState: TextView

    private var currentSummary: DailyShiftSummary? = null
    private var currentPeriodLabel: String = "Hoje"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        historyRepository = SQLiteDeliveryHistoryRepository(this)

        initViews()
        setupListeners()
        loadDataForFilter(R.id.chipToday)
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnShareReport = findViewById(R.id.btnShareReport)
        tvPeriodSubtitle = findViewById(R.id.tvPeriodSubtitle)
        cgFilters = findViewById(R.id.cgHistoryFilters)
        chipToday = findViewById(R.id.chipToday)
        chipYesterday = findViewById(R.id.chipYesterday)
        chipLast7Days = findViewById(R.id.chipLast7Days)
        chipThisMonth = findViewById(R.id.chipThisMonth)
        chipAll = findViewById(R.id.chipAll)

        tvPeriodNetProfit = findViewById(R.id.tvPeriodNetProfit)
        tvPeriodMetrics = findViewById(R.id.tvPeriodMetrics)
        tvPeriodGreenCount = findViewById(R.id.tvPeriodGreenCount)
        tvPeriodYellowCount = findViewById(R.id.tvPeriodYellowCount)
        tvPeriodRedCount = findViewById(R.id.tvPeriodRedCount)
        rvHistoryRecords = findViewById(R.id.rvHistoryRecords)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        adapter = HistoryAdapter()
        rvHistoryRecords.layoutManager = LinearLayoutManager(this)
        rvHistoryRecords.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        cgFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipToday
            loadDataForFilter(checkedId)
        }

        btnShareReport.setOnClickListener {
            shareReportViaWhatsApp()
        }
    }

    private fun loadDataForFilter(filterId: Int) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val todayStr = dateFormat.format(cal.time)

        var summary: DailyShiftSummary
        var records: List<DeliveryHistoryRecord>

        when (filterId) {
            R.id.chipToday -> {
                currentPeriodLabel = "Hoje ($todayStr)"
                summary = historyRepository.getTodaySummary()
                records = historyRepository.getTodayRecords()
            }
            R.id.chipYesterday -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val yesterdayStr = dateFormat.format(cal.time)
                currentPeriodLabel = "Ontem ($yesterdayStr)"
                summary = historyRepository.getSummaryForDateRange(yesterdayStr, yesterdayStr)
                records = historyRepository.getRecordsForDateRange(yesterdayStr, yesterdayStr)
            }
            R.id.chipLast7Days -> {
                cal.add(Calendar.DAY_OF_YEAR, -6)
                val sevenDaysAgoStr = dateFormat.format(cal.time)
                currentPeriodLabel = "Últimos 7 dias ($sevenDaysAgoStr a $todayStr)"
                summary = historyRepository.getSummaryForDateRange(sevenDaysAgoStr, todayStr)
                records = historyRepository.getRecordsForDateRange(sevenDaysAgoStr, todayStr)
            }
            R.id.chipThisMonth -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val firstDayMonthStr = dateFormat.format(cal.time)
                currentPeriodLabel = "Este Mês ($firstDayMonthStr a $todayStr)"
                summary = historyRepository.getSummaryForDateRange(firstDayMonthStr, todayStr)
                records = historyRepository.getRecordsForDateRange(firstDayMonthStr, todayStr)
            }
            else -> {
                currentPeriodLabel = "Todo o Histórico"
                summary = historyRepository.getAllTimeSummary()
                records = historyRepository.getAllRecords(200)
            }
        }

        currentSummary = summary
        tvPeriodSubtitle.text = currentPeriodLabel

        // Atualiza Card de Resumo
        tvPeriodNetProfit.text = String.format(Locale.getDefault(), "R$ %.2f", summary.totalNetProfit)
        tvPeriodMetrics.text = String.format(
            Locale.getDefault(),
            "%.1f km rodados • Média R$ %.2f/km (%d viagens)",
            summary.totalDistanceKm,
            summary.averageEarningsPerKm,
            summary.acceptedCount
        )
        tvPeriodGreenCount.text = summary.greenCount.toString()
        tvPeriodYellowCount.text = summary.yellowCount.toString()
        tvPeriodRedCount.text = summary.redCount.toString()

        // Atualiza Lista
        adapter.submitList(records)
        if (records.isEmpty()) {
            rvHistoryRecords.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
        } else {
            rvHistoryRecords.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE
        }
    }

    private fun shareReportViaWhatsApp() {
        val summary = currentSummary ?: return

        val hours = summary.totalTimeMinutes / 60.0
        val reportText = buildString {
            appendLine("📊 *RELATÓRIO DE ENTREGAS - KMCERTO*")
            appendLine("📅 *Período:* $currentPeriodLabel")
            appendLine("------------------------------------")
            appendLine("💰 *Lucro Líquido:* R$ ${String.format(Locale.getDefault(), "%.2f", summary.totalNetProfit)}")
            appendLine("🛣️ *Km Rodados:* ${String.format(Locale.getDefault(), "%.1f", summary.totalDistanceKm)} km")
            appendLine("📈 *Média por Km:* R$ ${String.format(Locale.getDefault(), "%.2f", summary.averageEarningsPerKm)}/km")
            appendLine("⏱️ *Tempo Estimado:* ${String.format(Locale.getDefault(), "%.1f", hours)}h")
            appendLine("📦 *Viagens Aceitas:* ${summary.acceptedCount}")
            appendLine("🚦 *Avaliações:* 🟢 ${summary.greenCount} Boas | 🟡 ${summary.yellowCount} Atenção | 🔴 ${summary.redCount} Recusadas")
            appendLine("------------------------------------")
            appendLine("🚀 _Gerado pelo KMCERTO - Assistente do Entregador_")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Relatório de Entregas KMCERTO")
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartilhar Relatório"))
    }
}
