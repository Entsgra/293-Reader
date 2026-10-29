package com.entsgra.reader.feature.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.entsgra.reader.core.data.library.LibraryRepository
import com.entsgra.reader.core.data.lock.AppLocks
import com.entsgra.reader.core.data.lock.FolderLocks
import com.entsgra.reader.core.data.lock.LockCheck
import com.entsgra.reader.core.data.lock.SecretCheck
import com.entsgra.reader.core.data.lock.UnlockResult
import com.entsgra.reader.core.data.lock.checkSecret
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.ErrorNotice
import com.entsgra.reader.core.designsystem.component.FieldLabel
import com.entsgra.reader.core.designsystem.component.InlineControl
import com.entsgra.reader.core.designsystem.component.LiveStatus
import com.entsgra.reader.core.designsystem.component.PrimaryButton
import com.entsgra.reader.core.designsystem.component.ReaderChips
import com.entsgra.reader.core.designsystem.component.ReaderTextField
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.component.SecondaryButton
import com.entsgra.reader.core.designsystem.component.SweepKeyCard
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.SecureWindow
import com.entsgra.reader.core.model.SecretKind
import com.entsgra.reader.core.model.secretKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Sets or changes a lock: a folder's ([folderId]) or, for null, the app's. */
@Serializable
data class LockSetupRoute(val folderId: Long? = null, val change: Boolean = false)

fun NavController.navigateToLockSetup(folderId: Long?, change: Boolean = false) =
	navigate(LockSetupRoute(folderId, change))

fun NavGraphBuilder.lockSetupScreen(onDone: () -> Unit) {
	composable<LockSetupRoute> { LockSetupScreen(onDone = onDone) }
}

sealed interface SetupState {
	data class Editing(
		val refusal: LockCheck? = null,
		val wrongCurrent: Boolean = false,
		val waitSeconds: Int = 0,
	) : SetupState

	data object Working : SetupState

	data object Done : SetupState
}

@HiltViewModel
class LockSetupViewModel @Inject constructor(
	savedState: SavedStateHandle,
	private val folderLocks: FolderLocks,
	private val appLocks: AppLocks,
	library: LibraryRepository,
) : ViewModel() {
	private val route = savedState.toRoute<LockSetupRoute>()
	val folderId: Long? = route.folderId
	val change: Boolean = route.change

	val folderName: StateFlow<String?> = (folderId?.let { id -> library.folder(id).map { it?.name } } ?: flowOf(null))
		.stateIn(viewModelScope, SharingStarted.Eagerly, null)

	/** The kind of the secret being replaced, when changing one. */
	val currentKind: StateFlow<SecretKind?> = (
		if (folderId != null) {
			library.folder(folderId).map { it?.lock?.secretKind() }
		} else {
			appLocks.state.map { it?.kind }
		}
		).stateIn(viewModelScope, SharingStarted.Eagerly, null)

	private val _state = MutableStateFlow<SetupState>(SetupState.Editing())
	val state: StateFlow<SetupState> = _state.asStateFlow()

	init {
		viewModelScope.launch {
			if (folderId == null) {
				appLocks.refresh()
			} else if (!change) {
				val check = folderLocks.lockCheck(folderId)
				if (check != LockCheck.OK) _state.value = SetupState.Editing(refusal = check)
			}
		}
	}

	/** The secrets arrive as strings from the text fields; they go on as char arrays and are zeroed. */
	fun submit(kind: SecretKind, secret: String, current: String) {
		if (_state.value == SetupState.Working) return
		_state.value = SetupState.Working
		viewModelScope.launch {
			val new = secret.toCharArray()
			_state.value = when {
				folderId != null && !change -> when (val check = folderLocks.lock(folderId, kind, new)) {
					LockCheck.OK -> SetupState.Done
					else -> SetupState.Editing(refusal = check)
				}
				folderId != null -> outcome(folderLocks.changeSecret(folderId, current.toCharArray(), kind, new))
				!change -> {
					appLocks.enable(kind, new)
					SetupState.Done
				}
				else -> outcome(appLocks.changeSecret(current.toCharArray(), kind, new))
			}
		}
	}

	suspend fun enrollCipher(): Cipher =
		if (folderId != null) folderLocks.biometricEnrollCipher(folderId) else appLocks.biometricEnrollCipher()

	fun enrolled(cipher: Cipher, then: () -> Unit) {
		viewModelScope.launch {
			if (folderId != null) folderLocks.biometricEnroll(folderId, cipher) else appLocks.biometricEnroll(cipher)
			then()
		}
	}

	private fun outcome(result: UnlockResult): SetupState = when (result) {
		UnlockResult.Ok -> SetupState.Done
		is UnlockResult.Wrong -> SetupState.Editing(wrongCurrent = true, waitSeconds = seconds(result.waitMs))
		is UnlockResult.Wait -> SetupState.Editing(wrongCurrent = false, waitSeconds = seconds(result.waitMs))
	}

	private fun seconds(ms: Long) = ((ms + 999) / 1000).toInt()
}

@Composable
internal fun LockSetupScreen(onDone: () -> Unit, viewModel: LockSetupViewModel = hiltViewModel()) {
	val name by viewModel.folderName.collectAsStateWithLifecycle()
	val currentKind by viewModel.currentKind.collectAsStateWithLifecycle()
	val state by viewModel.state.collectAsStateWithLifecycle()
	val context = LocalContext.current
	val launcher = rememberBiometricLauncher()
	val scope = rememberCoroutineScope()
	val enrollTitle = stringResource(R.string.biometric_prompt_enroll)
	LockSetupContent(
		title = when {
			viewModel.change -> stringResource(R.string.lock_title_change)
			viewModel.folderId == null -> stringResource(R.string.lock_title_app)
			else -> stringResource(R.string.lock_title_folder, name.orEmpty())
		},
		forFolder = viewModel.folderId != null,
		change = viewModel.change,
		currentKind = currentKind,
		state = state,
		biometricsOnDevice = remember(context) { biometricsAvailable(context) },
		onSubmit = viewModel::submit,
		onBiometric = {
			scope.launch {
				launcher.authenticate(viewModel.enrollCipher(), enrollTitle) { viewModel.enrolled(it, onDone) }
			}
		},
		onBack = onDone,
	)
}

@Composable
fun LockSetupContent(
	title: String,
	forFolder: Boolean,
	change: Boolean,
	currentKind: SecretKind?,
	state: SetupState,
	biometricsOnDevice: Boolean,
	onSubmit: (kind: SecretKind, secret: String, current: String) -> Unit,
	onBiometric: () -> Unit,
	onBack: () -> Unit,
) {
	SecureWindow()
	Column(
		Modifier
			.fillMaxSize()
			// The keyboard covers the window edge to edge; keep the fields above it.
			.imePadding()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = ReaderSpacing.space4),
		verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space4),
	) {
		ScreenTitle(title, onBack = onBack, backLabel = stringResource(R.string.back))
		if (state == SetupState.Done) {
			Done(forFolder, change, biometricsOnDevice, onBiometric, onBack)
		} else {
			Editing(forFolder, change, currentKind, state, onSubmit)
		}
	}
}

@Composable
private fun Done(forFolder: Boolean, change: Boolean, biometricsOnDevice: Boolean, onBiometric: () -> Unit, onDone: () -> Unit) {
	SweepKeyCard {
		FieldLabel(
			stringResource(
				when {
					change -> R.string.lock_done_change
					forFolder -> R.string.lock_done_folder
					else -> R.string.lock_done_app
				},
			),
		)
		BodyText(
			stringResource(
				when {
					change -> R.string.lock_done_change_body
					forFolder -> R.string.lock_done_folder_body
					else -> R.string.lock_done_app_body
				},
			),
		)
	}
	if (biometricsOnDevice && !change) {
		PrimaryButton(stringResource(R.string.lock_biometric_offer), onClick = onBiometric, modifier = Modifier.fillMaxWidth())
		SecondaryButton(stringResource(R.string.done), onClick = onDone, modifier = Modifier.fillMaxWidth())
	} else {
		PrimaryButton(stringResource(R.string.done), onClick = onDone, modifier = Modifier.fillMaxWidth())
	}
}

@Composable
private fun Editing(
	forFolder: Boolean,
	change: Boolean,
	currentKind: SecretKind?,
	state: SetupState,
	onSubmit: (SecretKind, String, String) -> Unit,
) {
	val editing = state as? SetupState.Editing
	val working = state == SetupState.Working
	var kind by rememberSaveable { mutableStateOf(SecretKind.PASSPHRASE) }
	var acknowledged by rememberSaveable { mutableStateOf(false) }
	// Secrets stay out of saved state.
	var current by remember { mutableStateOf("") }
	var secret by remember { mutableStateOf("") }
	var confirm by remember { mutableStateOf("") }
	var check by remember { mutableStateOf<SecretCheck?>(null) }
	val refused = editing?.refusal != null
	val submit = {
		val result = checkSecret(kind, secret, confirm)
		check = result
		if (result == SecretCheck.OK && acknowledged && !refused) onSubmit(kind, secret, current)
	}

	SweepKeyCard {
		FieldLabel(stringResource(R.string.lock_forget_head))
		BodyText(stringResource(if (forFolder) R.string.lock_forget_folder else R.string.lock_forget_app))
		InlineControl(
			label = stringResource(R.string.lock_understand),
			checked = acknowledged,
			onCheckedChange = { acknowledged = it },
			enabled = !working && !refused,
		)
		if (working) LiveStatus(stringResource(R.string.lock_working))
	}
	editing?.refusal?.let { refusal ->
		ErrorNotice(
			stringResource(
				when (refusal) {
					LockCheck.INSIDE_LOCKED -> R.string.lock_refused_inside
					LockCheck.CONTAINS_LOCKED -> R.string.lock_refused_contains
					else -> R.string.lock_refused_already
				},
			),
		)
	}
	if (change) {
		val was = currentKind ?: SecretKind.PASSPHRASE
		ReaderTextField(
			value = current,
			onValueChange = { current = it },
			label = stringResource(if (was == SecretKind.PIN) R.string.lock_field_current_pin else R.string.lock_field_current_passphrase),
			error = when {
				editing == null -> null
				editing.waitSeconds > 0 -> stringResource(R.string.unlock_wait, formatWait(editing.waitSeconds))
				editing.wrongCurrent -> stringResource(if (was == SecretKind.PIN) R.string.unlock_wrong_pin else R.string.unlock_wrong_passphrase)
				else -> null
			},
			keyboardType = keyboardFor(was),
			secret = true,
			imeAction = ImeAction.Next,
		)
	}
	val labels = SecretKind.entries.associateWith { kindLabel(it) }
	ReaderChips(
		options = SecretKind.entries.reversed(),
		selected = kind,
		onSelect = {
			kind = it
			check = null
		},
		label = labels::getValue,
	)
	if (kind == SecretKind.PIN) BodyText(stringResource(R.string.lock_pin_note))
	ReaderTextField(
		value = secret,
		onValueChange = {
			secret = it
			check = null
		},
		label = kindLabel(kind),
		error = when (check) {
			SecretCheck.PIN_TOO_SHORT -> stringResource(R.string.lock_check_pin_short)
			SecretCheck.PIN_NOT_DIGITS -> stringResource(R.string.lock_check_pin_digits)
			SecretCheck.PASSPHRASE_TOO_SHORT -> stringResource(R.string.lock_check_passphrase_short)
			else -> null
		},
		keyboardType = keyboardFor(kind),
		secret = true,
		imeAction = ImeAction.Next,
	)
	ReaderTextField(
		value = confirm,
		onValueChange = {
			confirm = it
			check = null
		},
		label = stringResource(if (kind == SecretKind.PIN) R.string.lock_field_pin_again else R.string.lock_field_passphrase_again),
		error = if (check == SecretCheck.MISMATCH) stringResource(R.string.lock_check_mismatch) else null,
		keyboardType = keyboardFor(kind),
		secret = true,
		onImeAction = submit,
	)
	PrimaryButton(
		stringResource(
			when {
				change -> R.string.lock_confirm_change
				forFolder -> R.string.lock_confirm_folder
				else -> R.string.lock_confirm_app
			},
		),
		onClick = submit,
		enabled = acknowledged && !working && !refused && secret.isNotEmpty() && confirm.isNotEmpty() &&
			(!change || current.isNotEmpty()),
		modifier = Modifier.fillMaxWidth(),
	)
}
