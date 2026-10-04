package io.github.lookie14.runcash.ui.grandson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.FamilyRepository
import io.github.lookie14.runcash.domain.PointCalculator
import io.github.lookie14.runcash.domain.PointRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class GrandsonUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val paired: Boolean = false,
    /** 연결 전에 보여줄 6자리 코드. */
    val code: String? = null,
    val todaySteps: Long = 0,
    val monthSteps: Long = 0,
    val goalDays: Int = 0,
    val recordedDays: Int = 0,
    val monthWon: Int = 0,
    val dailyGoal: Int = 5_000,
)

class GrandsonViewModel(
    private val repository: FamilyRepository,
    private val rules: PointRules = PointRules(),
) : ViewModel() {

    private val calculator = PointCalculator(rules)
    private val _uiState = MutableStateFlow(GrandsonUiState(dailyGoal = rules.dailyGoal))
    val uiState: StateFlow<GrandsonUiState> = _uiState.asStateFlow()

    private var familyId: String? = null
    private var mainJob: Job? = null

    init {
        start()
    }

    /** 처음 시작할 때와 "다시 시도"에서 쓴다. */
    fun start() {
        mainJob?.cancel()
        _uiState.value = GrandsonUiState(dailyGoal = rules.dailyGoal)
        mainJob = viewModelScope.launch {
            try {
                val id = repository.ensureFamily()
                familyId = id
                var monthJob: Job? = null
                repository.observeFamily(id).collect { family ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            paired = family.paired,
                            code = if (family.paired) null else it.code,
                        )
                    }
                    if (family.paired) {
                        if (monthJob?.isActive != true) monthJob = launch { observeMonth(id) }
                    } else {
                        monthJob?.cancel()
                        monthJob = null
                        if (_uiState.value.code == null) newCode()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "서버에 연결하지 못했어요. (${e.message.orEmpty()})") }
            }
        }
    }

    private suspend fun observeMonth(id: String) {
        val today = LocalDate.now()
        repository.observeMonth(id, YearMonth.from(today)).collect { days ->
            _uiState.update {
                it.copy(
                    todaySteps = days[today] ?: 0L,
                    monthSteps = days.values.sum(),
                    goalDays = days.values.count { steps -> steps >= rules.dailyGoal },
                    recordedDays = days.size,
                    monthWon = calculator.toWon(calculator.pointsForPeriod(days)),
                )
            }
        }
    }

    fun newCode() {
        viewModelScope.launch {
            try {
                val pairCode = repository.createPairCode()
                _uiState.update { it.copy(code = pairCode.code, errorMessage = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "연결 코드를 만들지 못했어요. (${e.message.orEmpty()})") }
            }
        }
    }

    fun unpair() {
        val id = familyId ?: return
        viewModelScope.launch {
            try {
                repository.unpair(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "연결을 끊지 못했어요. (${e.message.orEmpty()})") }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { GrandsonViewModel(AppContainer.familyRepository) }
        }
    }
}
