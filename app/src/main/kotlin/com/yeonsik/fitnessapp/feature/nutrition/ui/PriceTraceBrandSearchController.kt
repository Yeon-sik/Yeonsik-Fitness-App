package com.yeonsik.fitnessapp.feature.nutrition.ui

import android.os.Handler
import android.os.Looper
import com.yeonsik.fitnessapp.feature.nutrition.api.PriceTraceBrandCatalog
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandRow
import java.util.concurrent.Executors
import java.util.concurrent.Future

data class PriceTraceBrandSearchState(
    val query: String = "",
    val rows: List<PriceTraceBrandRow> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null
)

/** 검색 I/O 수명만 소유한다. 로컬 식품 저장 executor를 네트워크로 막지 않는다. */
internal class PriceTraceBrandSearchController(
    private val catalog: PriceTraceBrandCatalog?,
    private val owner: () -> String,
    private val changed: (PriceTraceBrandSearchState?) -> Unit
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(2)
    private var pending: Runnable? = null
    private var running: Future<*>? = null
    private var version = 0L
    private var state: PriceTraceBrandSearchState? = null
    private var resultScope = -1L
    private var resultOwner = ""
    private val subscription = catalog?.observeBrandCatalogScope(Runnable {
        main.post {
            if (state != null) {
                invalidate()
                publish(state!!.copy(rows = emptyList(), loading = false, hasMore = false,
                    error = "연결 설정 또는 계정이 변경되었습니다. 다시 검색하세요."))
            }
        }
    })

    fun open(query: String) {
        state = PriceTraceBrandSearchState()
        search(query, delay = 0)
    }

    fun search(query: String, append: Boolean = false, delay: Long = 300) {
        val current = state ?: return
        if (append && (current.loading || !current.hasMore)) return
        invalidate()
        val request = version
        val capturedOwner = owner()
        val scope = catalog?.brandCatalogScope() ?: -1L
        val previous = if (append) current.rows else emptyList()
        publish(current.copy(query = query, rows = previous, loading = true, hasMore = false, searched = false, error = null))
        val work = Runnable {
            running = executor.submit {
                val result = runCatching {
                    check(catalog?.isBrandCatalogConfigured() == true) { "설정에서 PriceTrace 공개 DB 연결을 먼저 설정하세요. 연결 없이도 식품을 등록할 수 있습니다." }
                    catalog.searchPriceTraceBrands(query, PAGE_SIZE, previous.size)
                }
                main.post {
                    if (request != version || state == null || owner() != capturedOwner || catalog?.brandCatalogScope() != scope) return@post
                    resultOwner = capturedOwner
                    resultScope = scope
                    result.fold({ rows ->
                        publish(PriceTraceBrandSearchState(query, previous + rows, searched = true, hasMore = rows.size == PAGE_SIZE))
                    }, { error ->
                        publish(PriceTraceBrandSearchState(query, previous, searched = true,
                            error = error.message ?: "브랜드 검색을 완료하지 못했습니다. 다시 시도하세요."))
                    })
                }
            }
        }
        pending = work
        main.postDelayed(work, delay)
    }

    fun isCurrent(row: PriceTraceBrandRow): Boolean = state?.let {
        !it.loading && it.error == null && row in it.rows && owner() == resultOwner && catalog?.brandCatalogScope() == resultScope
    } == true

    fun dismiss(notify: Boolean = true) { invalidate(); state = null; if (notify) changed(null) }
    private fun invalidate() {
        ++version
        pending?.let(main::removeCallbacks)
        pending = null
        running?.cancel(true)
        running = null
    }
    private fun publish(next: PriceTraceBrandSearchState) { state = next; changed(next) }
    override fun close() { dismiss(); subscription?.close(); executor.shutdownNow() }

    companion object { const val PAGE_SIZE = 25 }
}
