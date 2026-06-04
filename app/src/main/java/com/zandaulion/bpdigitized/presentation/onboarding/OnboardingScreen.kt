package com.zandaulion.bpdigitized.presentation.onboarding

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.domain.model.BiologicalSex
import com.zandaulion.bpdigitized.presentation.locale.APP_LANGUAGES
import com.zandaulion.bpdigitized.presentation.locale.LocalePrefs
import kotlinx.coroutines.launch

/** Total onboarding pages: Welcome, About you, Reminders. */
private const val PAGE_COUNT = 3

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinish: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val isLastPage = pagerState.currentPage == PAGE_COUNT - 1

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            // Header row: language picker (welcome page only) on the start, Skip on the
            // end (hidden on the last page). Fixed height keeps the layout stable.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
            ) {
                if (pagerState.currentPage == 0) {
                    LanguageSelector(modifier = Modifier.align(Alignment.CenterStart))
                }
                if (!isLastPage) {
                    TextButton(
                        onClick = {
                            viewModel.skipOnboarding()
                            onFinish()
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> ProfilePage(
                        form = form,
                        onBirthYearChange = viewModel::updateBirthYear,
                        onSexChange = viewModel::updateSex,
                    )
                    else -> RemindersPage(
                        enabled = form.remindersEnabled,
                        onEnabledChange = viewModel::setRemindersEnabled,
                    )
                }
            }

            PageIndicator(
                pageCount = PAGE_COUNT,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp),
            )

            Button(
                onClick = {
                    if (isLastPage) {
                        viewModel.completeOnboarding()
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .height(52.dp),
            ) {
                Text(
                    stringResource(
                        if (isLastPage) R.string.onboarding_get_started else R.string.onboarding_next,
                    ),
                )
            }
        }
    }
}

// ── Page 1: Welcome (informational) ─────────────────────────────────────────

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_welcome_overlay),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        IconCircle(Icons.Default.MonitorHeart)
        Spacer(Modifier.height(40.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Page 2: About you (optional profile) ────────────────────────────────────

@Composable
private fun ProfilePage(
    form: OnboardingForm,
    onBirthYearChange: (String) -> Unit,
    onSexChange: (BiologicalSex) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_profile_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_profile_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = form.birthYearText,
            onValueChange = onBirthYearChange,
            label = { Text(stringResource(R.string.profile_birth_year_label)) },
            singleLine = true,
            isError = form.birthYearError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                when {
                    form.birthYearError ->
                        Text(stringResource(R.string.profile_birth_year_error))
                    form.computedAge != null ->
                        Text(stringResource(R.string.profile_age, form.computedAge!!))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.profile_sex_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BiologicalSex.entries.forEach { option ->
                FilterChip(
                    selected = form.sex == option,
                    onClick = { onSexChange(option) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

// ── Page 3: Reminders (optional) ────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RemindersPage(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val notifPermState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconCircle(Icons.Default.NotificationsActive)
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_reminders_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_reminders_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.reminders_enable_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.reminders_enable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { want ->
                        if (want && notifPermState != null && !notifPermState.status.isGranted) {
                            notifPermState.launchPermissionRequest()
                        }
                        onEnabledChange(want)
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_reminders_permission_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Shared bits ─────────────────────────────────────────────────────────────

/** Language picker shown on the welcome page; persists the choice and recreates the
 *  activity so the new locale is applied (mirrors the dashboard's language menu). */
@Composable
private fun LanguageSelector(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val currentCode = remember { LocalePrefs.getLocale(context) }
    val currentLabel = if (currentCode.isEmpty()) {
        stringResource(R.string.language_system)
    } else {
        APP_LANGUAGES.firstOrNull { it.code == currentCode }?.nativeName ?: currentCode
    }

    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.Language,
                contentDescription = stringResource(R.string.dashboard_cd_language),
            )
            Spacer(Modifier.width(6.dp))
            Text(currentLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            APP_LANGUAGES.forEach { lang ->
                val label = if (lang.code.isEmpty()) {
                    stringResource(R.string.language_system)
                } else {
                    lang.nativeName
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = if (lang.code == currentCode) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        expanded = false
                        if (lang.code != currentCode) {
                            LocalePrefs.setLocale(context, lang.code)
                            (context as? Activity)?.recreate()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun IconCircle(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(140.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(72.dp),
        )
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dotWidth")
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}
