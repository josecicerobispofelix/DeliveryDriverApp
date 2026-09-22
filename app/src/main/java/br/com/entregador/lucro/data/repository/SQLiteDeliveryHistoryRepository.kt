package br.com.entregador.lucro.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import br.com.entregador.lucro.domain.model.DailyShiftSummary
import br.com.entregador.lucro.domain.model.DeliveryHistoryRecord
import br.com.entregador.lucro.domain.model.TrafficLightStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Implementação do [DeliveryHistoryRepository] utilizando SQLite nativo do Android.
 * Oferece armazenamento 100% offline, ágil e persistente entre reinicializações do sistema.
 */
class SQLiteDeliveryHistoryRepository(
    context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), DeliveryHistoryRepository {

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_HISTORY (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_DATE TEXT NOT NULL,
                $COL_TIME TEXT NOT NULL,
                $COL_PLATFORM TEXT NOT NULL,
                $COL_GROSS_VALUE REAL NOT NULL,
                $COL_NET_PROFIT REAL NOT NULL,
                $COL_DISTANCE_KM REAL NOT NULL,
                $COL_TIME_MINUTES INTEGER NOT NULL,
                $COL_TRAFFIC_LIGHT TEXT NOT NULL,
                $COL_WAS_AUTO_REJECTED INTEGER NOT NULL DEFAULT 0,
                $COL_ACCEPTED INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent()
        db.execSQL(createTableSql)
        db.execSQL("CREATE INDEX idx_history_date ON $TABLE_HISTORY ($COL_DATE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    override fun recordOffer(record: DeliveryHistoryRecord): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, record.timestamp)
            put(COL_DATE, record.dateStr)
            put(COL_TIME, record.timeStr)
            put(COL_PLATFORM, record.platform)
            put(COL_GROSS_VALUE, record.grossValue)
            put(COL_NET_PROFIT, record.netProfit)
            put(COL_DISTANCE_KM, record.totalDistanceKm)
            put(COL_TIME_MINUTES, record.estimatedTimeMinutes)
            put(COL_TRAFFIC_LIGHT, record.trafficLightStatus.name)
            put(COL_WAS_AUTO_REJECTED, if (record.wasAutoRejected) 1 else 0)
            put(COL_ACCEPTED, if (record.accepted) 1 else 0)
        }
        return db.insert(TABLE_HISTORY, null, values)
    }

    override fun getTodaySummary(): DailyShiftSummary =
        querySummary("$COL_DATE = ?", arrayOf(getTodayDateString()), getTodayDateString())

    override fun getTodayRecords(): List<DeliveryHistoryRecord> =
        queryRecords("$COL_DATE = ?", arrayOf(getTodayDateString()))

    override fun getSummaryForDateRange(startDate: String, endDate: String): DailyShiftSummary =
        querySummary("$COL_DATE BETWEEN ? AND ?", arrayOf(startDate, endDate), "$startDate a $endDate")

    override fun getRecordsForDateRange(startDate: String, endDate: String): List<DeliveryHistoryRecord> =
        queryRecords("$COL_DATE BETWEEN ? AND ?", arrayOf(startDate, endDate))

    override fun getAllRecords(limit: Int): List<DeliveryHistoryRecord> =
        queryRecords(null, null, limit.toString())

    override fun getAllTimeSummary(): DailyShiftSummary =
        querySummary(null, null, "Todo o Período")

    private fun querySummary(selection: String?, selectionArgs: Array<String>?, label: String): DailyShiftSummary {
        val db = readableDatabase

        var greenCount = 0
        var yellowCount = 0
        var redCount = 0
        var totalOffers = 0
        var acceptedCount = 0
        var totalNetProfit = 0.0
        var totalDistanceKm = 0.0
        var totalTimeMinutes = 0

        val whereClause = if (selection != null) "WHERE $selection" else ""

        val countQuery = "SELECT $COL_TRAFFIC_LIGHT, COUNT(*) FROM $TABLE_HISTORY $whereClause GROUP BY $COL_TRAFFIC_LIGHT"
        db.rawQuery(countQuery, selectionArgs).use { cursor ->
            while (cursor.moveToNext()) {
                val status = cursor.getString(0)
                val count = cursor.getInt(1)
                totalOffers += count
                when (status) {
                    TrafficLightStatus.GREEN.name -> greenCount = count
                    TrafficLightStatus.YELLOW.name -> yellowCount = count
                    TrafficLightStatus.RED.name -> redCount = count
                }
            }
        }

        val acceptedWhere = if (selection != null) "WHERE $selection AND $COL_ACCEPTED = 1" else "WHERE $COL_ACCEPTED = 1"
        val sumQuery = "SELECT COUNT(*), SUM($COL_NET_PROFIT), SUM($COL_DISTANCE_KM), SUM($COL_TIME_MINUTES) FROM $TABLE_HISTORY $acceptedWhere"
        db.rawQuery(sumQuery, selectionArgs).use { cursor ->
            if (cursor.moveToFirst()) {
                acceptedCount = cursor.getInt(0)
                totalNetProfit = if (!cursor.isNull(1)) cursor.getDouble(1) else 0.0
                totalDistanceKm = if (!cursor.isNull(2)) cursor.getDouble(2) else 0.0
                totalTimeMinutes = if (!cursor.isNull(3)) cursor.getInt(3) else 0
            }
        }

        return DailyShiftSummary(
            dateStr = label,
            greenCount = greenCount,
            yellowCount = yellowCount,
            redCount = redCount,
            totalOffersCount = totalOffers,
            acceptedCount = acceptedCount,
            totalNetProfit = totalNetProfit,
            totalDistanceKm = totalDistanceKm,
            totalTimeMinutes = totalTimeMinutes
        )
    }

    private fun queryRecords(selection: String?, selectionArgs: Array<String>?, limit: String? = null): List<DeliveryHistoryRecord> {
        val list = mutableListOf<DeliveryHistoryRecord>()
        val db = readableDatabase
        val whereClause = if (selection != null) "WHERE $selection" else ""
        val limitClause = if (limit != null) "LIMIT $limit" else ""
        val query = """
            SELECT $COL_ID, $COL_TIMESTAMP, $COL_DATE, $COL_TIME, $COL_PLATFORM,
                   $COL_GROSS_VALUE, $COL_NET_PROFIT, $COL_DISTANCE_KM, $COL_TIME_MINUTES,
                   $COL_TRAFFIC_LIGHT, $COL_WAS_AUTO_REJECTED, $COL_ACCEPTED
            FROM $TABLE_HISTORY
            $whereClause
            ORDER BY $COL_ID DESC
            $limitClause
        """.trimIndent()

        db.rawQuery(query, selectionArgs).use { cursor ->
            while (cursor.moveToNext()) {
                val statusStr = cursor.getString(9)
                val status = try {
                    TrafficLightStatus.valueOf(statusStr)
                } catch (e: Exception) {
                    TrafficLightStatus.GREEN
                }

                list.add(
                    DeliveryHistoryRecord(
                        id = cursor.getLong(0),
                        timestamp = cursor.getLong(1),
                        dateStr = cursor.getString(2),
                        timeStr = cursor.getString(3),
                        platform = cursor.getString(4),
                        grossValue = cursor.getDouble(5),
                        netProfit = cursor.getDouble(6),
                        totalDistanceKm = cursor.getDouble(7),
                        estimatedTimeMinutes = cursor.getInt(8),
                        trafficLightStatus = status,
                        wasAutoRejected = cursor.getInt(10) == 1,
                        accepted = cursor.getInt(11) == 1
                    )
                )
            }
        }
        return list
    }

    override fun clearTodayRecords() {
        val todayDate = getTodayDateString()
        writableDatabase.delete(TABLE_HISTORY, "$COL_DATE = ?", arrayOf(todayDate))
    }

    companion object {
        private const val DATABASE_NAME = "kmcerto_history.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_HISTORY = "delivery_history"
        private const val COL_ID = "id"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_DATE = "date_str"
        private const val COL_TIME = "time_str"
        private const val COL_PLATFORM = "platform"
        private const val COL_GROSS_VALUE = "gross_value"
        private const val COL_NET_PROFIT = "net_profit"
        private const val COL_DISTANCE_KM = "distance_km"
        private const val COL_TIME_MINUTES = "time_minutes"
        private const val COL_TRAFFIC_LIGHT = "traffic_light"
        private const val COL_WAS_AUTO_REJECTED = "was_auto_rejected"
        private const val COL_ACCEPTED = "accepted"

        fun getTodayDateString(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }

        fun getCurrentTimeString(): String {
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
    }
}
