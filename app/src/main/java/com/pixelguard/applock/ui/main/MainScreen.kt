package com.pixelguard.applock.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.pixelguard.applock.data.model.isPinOnly
import com.pixelguard.applock.data.model.isNotificationMasked
import com.pixelguard.applock.data.model.getUserCustomTags
import com.pixelguard.applock.data.model.isCustomized
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.pixelguard.applock.R
import androidx.core.graphics.drawable.toBitmap
import com.pixelguard.applock.data.model.AppCategory
import com.pixelguard.applock.data.model.AppItem
import com.pixelguard.applock.data.model.AppSortOrder
import com.pixelguard.applock.ui.theme.semanticColors
import com.pixelguard.applock.ui.common.HapticManager
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToIntruders: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val apps by viewModel.filteredApps.collectAsState()
    val allApps by viewModel.installedApps.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val isOnlyLocked by viewModel.isOnlyLockedFilterActive.collectAsState()
    val isOnlyCustomized by viewModel.isOnlyCustomizedFilterActive.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val intruders by viewModel.intruderRecords.collectAsState()
    val prefs by viewModel.preferences.collectAsState()
    val context = LocalContext.current
    val hapticManager = remember(context) { HapticManager(context) }
    var selectedAppForProfile by remember { mutableStateOf<AppItem?>(null) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var tagToDelete by remember { mutableStateOf<String?>(null) }
    val userTags = remember(prefs.userCustomTagsJson) { prefs.getUserCustomTags() }
    val systemAnimationsEnabled = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) != 0f
        } catch (_: Exception) {
            true
        }
    }
    val effectiveAnimations = prefs.isAnimationsEnabled && systemAnimationsEnabled

    val lockedCount = allApps.count { it.isLocked }
    val customizedCount = remember(allApps, prefs) { allApps.count { prefs.isCustomized(it.packageName) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = if (lockedCount > 0) "$lockedCount apps locked" else "No apps locked",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (intruders.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text(intruders.size.toString())
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = onNavigateToIntruders) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = stringResource(R.string.intruders_title)
                            )
                        }
                    }

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        val graceMap = remember(prefs.appGracePeriodsJson) {
            try {
                val json = JSONObject(prefs.appGracePeriodsJson)
                val map = mutableMapOf<String, Int>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = json.getInt(k)
                }
                map
            } catch (_: Exception) {
                emptyMap()
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Permission Banner (if not fully granted)
            if (!permissionStatus.isAllGranted) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToPermissions() },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Setup Required for Android 17",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Tap to grant Accessibility and Overlay permissions to activate locking.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Hero Security Health Gauge Section
            item {
                SecurityHealthGaugeCard(
                    allApps = allApps,
                    lockedCount = lockedCount,
                    isAnimationsEnabled = effectiveAnimations,
                    onProtectSensitive = {
                        hapticManager.tapClick(prefs.isHapticsEnabled)
                        viewModel.protectAllSensitiveApps()
                    },
                    onUnlockAll = {
                        hapticManager.tapClick(prefs.isHapticsEnabled)
                        viewModel.unlockAllApps()
                    }
                )
            }

            // Search Bar with Integrated Sort Menu
            item {
                var sortMenuExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.main_search_hint),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.common_clear))
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Sort By Dropdown Menu
                    Box {
                        val sortLabel = when (prefs.appSortOrder) {
                            AppSortOrder.NAME_ASC -> stringResource(R.string.main_sort_name_asc)
                            AppSortOrder.LOCKED_FIRST -> stringResource(R.string.main_sort_locked_first)
                            AppSortOrder.CATEGORY -> stringResource(R.string.main_sort_category)
                        }

                        AssistChip(
                            onClick = {
                                hapticManager.tapClick(prefs.isHapticsEnabled)
                                sortMenuExpanded = true
                            },
                            label = {
                                Text(
                                    text = "${stringResource(R.string.main_sort_by)}: $sortLabel",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Change Sort Order",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.main_sort_name_asc)) },
                                leadingIcon = {
                                    if (prefs.appSortOrder == AppSortOrder.NAME_ASC) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(18.dp))
                                    }
                                },
                                onClick = {
                                    hapticManager.tapClick(prefs.isHapticsEnabled)
                                    viewModel.setSortOrder(AppSortOrder.NAME_ASC)
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.main_sort_locked_first)) },
                                leadingIcon = {
                                    if (prefs.appSortOrder == AppSortOrder.LOCKED_FIRST) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(18.dp))
                                    }
                                },
                                onClick = {
                                    hapticManager.tapClick(prefs.isHapticsEnabled)
                                    viewModel.setSortOrder(AppSortOrder.LOCKED_FIRST)
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.main_sort_category)) },
                                leadingIcon = {
                                    if (prefs.appSortOrder == AppSortOrder.CATEGORY) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(18.dp))
                                    }
                                },
                                onClick = {
                                    hapticManager.tapClick(prefs.isHapticsEnabled)
                                    viewModel.setSortOrder(AppSortOrder.CATEGORY)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Category Filter Chips with Pinned "Locked Only" Filter Chip
            item {
                val categoryCounts = remember(allApps) { allApps.groupingBy { it.category }.eachCount() }
                LazyRow(
                    modifier = Modifier.testTag("filter_chips_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = isOnlyLocked,
                            onClick = {
                                hapticManager.tapClick(prefs.isHapticsEnabled)
                                viewModel.toggleOnlyLockedFilter()
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.main_filter_locked_only, lockedCount),
                                    fontWeight = if (isOnlyLocked) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isOnlyLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    item {
                        FilterChip(
                            selected = isOnlyCustomized,
                            onClick = {
                                hapticManager.tapClick(prefs.isHapticsEnabled)
                                viewModel.toggleOnlyCustomizedFilter()
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.main_filter_customized, customizedCount),
                                    fontWeight = if (isOnlyCustomized) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == null && selectedTag == null && !isOnlyLocked && !isOnlyCustomized,
                            onClick = {
                                hapticManager.keypadTick(prefs.isHapticsEnabled)
                                if (isOnlyLocked) viewModel.toggleOnlyLockedFilter()
                                if (isOnlyCustomized) viewModel.toggleOnlyCustomizedFilter()
                                viewModel.onCategorySelected(null)
                                viewModel.onTagSelected(null)
                            },
                            label = { Text("All (${allApps.size})") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    items(userTags) { tag ->
                        val count = remember(allApps, tag) {
                            allApps.count { it.customTags.any { t -> t.equals(tag, ignoreCase = true) } }
                        }
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = {
                                hapticManager.keypadTick(prefs.isHapticsEnabled)
                                viewModel.onTagSelected(if (selectedTag == tag) null else tag)
                            },
                            label = { Text("#$tag ($count)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Label,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                hapticManager.tapClick(prefs.isHapticsEnabled)
                                showCreateTagDialog = true
                            },
                            label = {
                                Text(
                                    text = "+ Tag",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Tag",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    items(AppCategory.values()) { category ->
                        val count = categoryCounts[category] ?: 0
                        if (count > 0) {
                            val catIcon = when (category) {
                                AppCategory.FINANCIAL -> Icons.Default.AccountBalance
                                AppCategory.SOCIAL -> Icons.Default.ChatBubble
                                AppCategory.SENSITIVE -> Icons.Default.PhotoLibrary
                                AppCategory.SYSTEM -> Icons.Default.Settings
                                AppCategory.OTHER -> Icons.Default.Apps
                            }
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = {
                                    hapticManager.keypadTick(prefs.isHapticsEnabled)
                                    viewModel.onCategorySelected(category)
                                },
                                label = { Text("${category.displayName} ($count)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = catIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Contextual Category Batch Action Strip
            if (selectedCategory != null) {
                item {
                    val categoryApps = remember(allApps, selectedCategory) {
                        allApps.filter { it.category == selectedCategory }
                    }
                    val lockedInCategory = categoryApps.count { it.isLocked }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (lockedInCategory > 0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "$lockedInCategory of ${categoryApps.size} protected",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (lockedInCategory < categoryApps.size) {
                                    FilledTonalButton(
                                        onClick = {
                                            hapticManager.tapClick(prefs.isHapticsEnabled)
                                            viewModel.lockCategory(selectedCategory!!)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.main_batch_lock_all),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (lockedInCategory > 0) {
                                    OutlinedButton(
                                        onClick = {
                                            hapticManager.tapClick(prefs.isHapticsEnabled)
                                            viewModel.unlockCategory(selectedCategory!!)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.main_batch_unlock_all),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Contextual Tag Batch Action Strip
            if (selectedTag != null) {
                item {
                    val currentTag = selectedTag!!
                    val tagApps = remember(allApps, currentTag) {
                        allApps.filter { it.customTags.any { t -> t.equals(currentTag, ignoreCase = true) } }
                    }
                    val lockedInTag = tagApps.count { it.isLocked }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (lockedInTag > 0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "$lockedInTag of ${tagApps.size} in #$currentTag",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (lockedInTag < tagApps.size) {
                                    FilledTonalButton(
                                        onClick = {
                                            hapticManager.tapClick(prefs.isHapticsEnabled)
                                            viewModel.lockTag(currentTag)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = stringResource(R.string.main_batch_lock_all), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (lockedInTag > 0) {
                                    OutlinedButton(
                                        onClick = {
                                            hapticManager.tapClick(prefs.isHapticsEnabled)
                                            viewModel.unlockTag(currentTag)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = stringResource(R.string.main_batch_unlock_all), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                IconButton(
                                    onClick = { tagToDelete = currentTag },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Tag",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Empty State Card when filter/search yields 0 results
            if (apps.isEmpty() && allApps.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (query.isNotBlank()) Icons.Default.Search
                                        else if (isOnlyLocked) Icons.Default.LockOpen
                                        else Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (query.isNotBlank()) "No apps found for \"$query\""
                                    else if (isOnlyLocked) "No protected apps yet"
                                    else "No apps match this category",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (query.isNotBlank()) "Check the spelling or clear your search query."
                                    else if (isOnlyLocked) "Tap the lock toggle on any app in your list to protect it."
                                    else "Try selecting another category or resetting active filters.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            FilledTonalButton(
                                onClick = {
                                    hapticManager.tapClick(prefs.isHapticsEnabled)
                                    viewModel.resetFilters()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (query.isNotBlank()) "Clear search" else "Reset filters",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Apps List with O(1) memory lookup & viewpool reuse
            items(
                items = apps,
                key = { it.packageName },
                contentType = { "app_item" }
            ) { app ->
                val appGraceSeconds = graceMap[app.packageName]
                val isPinOnly = remember(prefs.pinOnlyPackagesJson, app.packageName) {
                    prefs.isPinOnly(app.packageName)
                }
                val isNotificationMasked = remember(prefs.notificationExemptPackagesJson, prefs.isNotificationShieldEnabled, app.packageName) {
                    prefs.isNotificationMasked(app.packageName)
                }
                AppListItem(
                    app = app,
                    isAnimationsEnabled = effectiveAnimations,
                    isPerAppGraceEnabled = prefs.isPerAppGraceEnabled,
                    appGraceSeconds = appGraceSeconds,
                    isPinOnly = isPinOnly,
                    isNotificationMasked = isNotificationMasked,
                    onOpenProfile = {
                        hapticManager.tapClick(prefs.isHapticsEnabled)
                        selectedAppForProfile = app
                    },
                    onToggle = {
                        hapticManager.tapClick(prefs.isHapticsEnabled)
                        viewModel.toggleAppLock(app.packageName, app.isLocked)
                    }
                )
            }
        }
    }

    // Material 3 Modal Bottom Sheet for App Protection Profile
    if (selectedAppForProfile != null) {
        val target = selectedAppForProfile!!
        val currentTargetApp = allApps.find { it.packageName == target.packageName } ?: target
        val isTargetLocked = currentTargetApp.isLocked
        val currentSec = remember(prefs.appGracePeriodsJson, target.packageName) {
            try {
                val json = JSONObject(prefs.appGracePeriodsJson)
                if (json.has(target.packageName)) json.getInt(target.packageName) else -1
            } catch (_: Exception) { -1 }
        }
        val isPinOnlyActive = remember(prefs.pinOnlyPackagesJson, target.packageName) {
            prefs.isPinOnly(target.packageName)
        }
        val isNotifMaskedActive = remember(prefs.notificationExemptPackagesJson, prefs.isNotificationShieldEnabled, target.packageName) {
            prefs.isNotificationMasked(target.packageName)
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { selectedAppForProfile = null },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AppSecurityProfileSheetContent(
                target = currentTargetApp,
                isTargetLocked = isTargetLocked,
                currentSec = currentSec,
                isPinOnlyActive = isPinOnlyActive,
                isNotifMaskedActive = isNotifMaskedActive,
                isGlobalNotifShieldEnabled = prefs.isNotificationShieldEnabled,
                onToggleLock = { checked ->
                    hapticManager.tapClick(prefs.isHapticsEnabled)
                    viewModel.toggleAppLock(target.packageName, isTargetLocked)
                },
                onTogglePinOnly = { checked ->
                    hapticManager.tapClick(prefs.isHapticsEnabled)
                    viewModel.togglePinOnly(target.packageName, checked)
                },
                onToggleNotifMasking = { checked ->
                    hapticManager.tapClick(prefs.isHapticsEnabled)
                    viewModel.toggleNotificationMasking(target.packageName, checked)
                },
                onSelectGraceSeconds = { sec ->
                    hapticManager.tapClick(prefs.isHapticsEnabled)
                    viewModel.setAppGracePeriod(target.packageName, sec)
                },
                userCustomTags = userTags,
                onSelectCategory = { cat ->
                    hapticManager.tapClick(prefs.isHapticsEnabled)
                    viewModel.setCategoryOverride(target.packageName, cat)
                },
                onAddTag = { tag ->
                    hapticManager.tapClick(prefs.isHapticsEnabled)
                    viewModel.addTagToApp(target.packageName, tag)
                },
                onRemoveTag = { tag ->
                    hapticManager.tapClick(prefs.isHapticsEnabled)
                    viewModel.removeTagFromApp(target.packageName, tag)
                },
                onCreateAndAddTag = { newTag ->
                    hapticManager.tapClick(prefs.isHapticsEnabled)
                    viewModel.addTagToApp(target.packageName, newTag)
                }
            )
        }
    }

    if (showCreateTagDialog) {
        CreateTagDialog(
            userTags = userTags,
            onDismiss = { showCreateTagDialog = false },
            onCreateTag = { clean ->
                viewModel.createUserTag(clean)
                showCreateTagDialog = false
            }
        )
    }

    if (tagToDelete != null) {
        val tag = tagToDelete!!
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text("Delete Tag #$tag?") },
            text = {
                Text(
                    text = "This will remove the tag from all assigned apps. The apps will not be unlocked.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUserTag(tag)
                        tagToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AppSecurityProfileSheetContent(
    target: AppItem,
    isTargetLocked: Boolean,
    currentSec: Int,
    isPinOnlyActive: Boolean,
    isNotifMaskedActive: Boolean,
    isGlobalNotifShieldEnabled: Boolean,
    onToggleLock: (Boolean) -> Unit,
    onTogglePinOnly: (Boolean) -> Unit,
    onToggleNotifMasking: (Boolean) -> Unit,
    onSelectGraceSeconds: (Int) -> Unit,
    userCustomTags: List<String> = emptyList(),
    onSelectCategory: (AppCategory?) -> Unit = {},
    onAddTag: (String) -> Unit = {},
    onRemoveTag: (String) -> Unit = {},
    onCreateAndAddTag: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header: Icon, Name, Package & Lock Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (target.iconBitmap != null) {
                Image(
                    bitmap = target.iconBitmap.asImageBitmap(),
                    contentDescription = target.appName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else if (target.icon != null) {
                val bmp = remember(target.icon) { target.icon.toBitmap() }
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = target.appName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = target.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isTargetLocked,
                onCheckedChange = onToggleLock
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Category & Tags Section
        Text(
            text = "Category & Tags",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val catIcon = when (target.category) {
                            AppCategory.FINANCIAL -> Icons.Default.AccountBalance
                            AppCategory.SOCIAL -> Icons.Default.ChatBubble
                            AppCategory.SENSITIVE -> Icons.Default.PhotoLibrary
                            AppCategory.SYSTEM -> Icons.Default.Settings
                            AppCategory.OTHER -> Icons.Default.Apps
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = catIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Category",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = target.category.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box {
                        var categoryMenuExpanded by remember { mutableStateOf(false) }
                        AssistChip(
                            onClick = { categoryMenuExpanded = true },
                            label = { Text("Change ▾", style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(10.dp)
                        )

                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false }
                        ) {
                            AppCategory.values().forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.displayName) },
                                    leadingIcon = {
                                        if (target.category == cat) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    onClick = {
                                        categoryMenuExpanded = false
                                        onSelectCategory(cat)
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Reset to Default", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    categoryMenuExpanded = false
                                    onSelectCategory(null)
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Custom Tags Row
                Text(
                    text = "Custom Tags",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                var showAddTagDialog by remember { mutableStateOf(false) }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(target.customTags) { tag ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveTag(tag) },
                            label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove tag",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    item {
                        AssistChip(
                            onClick = { showAddTagDialog = true },
                            label = { Text("+ Add Tag", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                if (showAddTagDialog) {
                    AddTagToAppDialog(
                        existingUserTags = userCustomTags,
                        assignedTags = target.customTags,
                        onDismiss = { showAddTagDialog = false },
                        onAddExistingTag = { tag ->
                            onAddTag(tag)
                            showAddTagDialog = false
                        },
                        onCreateAndAddTag = { newTag ->
                            onCreateAndAddTag(newTag)
                            showAddTagDialog = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        val rulesAlpha by animateFloatAsState(
            targetValue = if (isTargetLocked) 1.0f else 0.38f,
            animationSpec = tween(200),
            label = "rules_alpha"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = rulesAlpha }
        ) {
            // Advanced Security Settings Section
            Text(
                text = "Security Rules",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Require Master PIN Only (Bypass Biometrics)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPinOnlyActive)
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Always Ask for Passcode",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Skip fingerprint and face unlock; always ask for your passcode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPinOnlyActive,
                        onCheckedChange = onTogglePinOnly
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Notification Shield Masking Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNotifMaskedActive && isGlobalNotifShieldEnabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide Notification Previews",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (!isGlobalNotifShieldEnabled)
                                "Notification Privacy is turned off in Settings"
                            else
                                "Hides message details in notifications while this app is locked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isNotifMaskedActive,
                        enabled = isGlobalNotifShieldEnabled,
                        onCheckedChange = onToggleNotifMasking
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Re-lock Timeout (Grace Period) Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "When to Lock This App",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Choose how quickly this app locks after you leave it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            val graceOptions = listOf(
                -1 to Pair("Default", "Uses your global setting from Settings"),
                0 to Pair("Immediately", "Locks after you leave, with a 15-second buffer to copy codes"),
                com.pixelguard.applock.domain.usecase.SessionManager.GRACE_UNTIL_SCREEN_OFF to Pair("When Screen Off", "Stays unlocked while using your phone; locks when the screen turns off"),
                30 to Pair("30s", "Locks 30 seconds after leaving"),
                60 to Pair("1m", "Locks 1 minute after leaving"),
                300 to Pair("5m", "Locks 5 minutes after leaving"),
                900 to Pair("15m", "Locks 15 minutes after leaving")
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                items(graceOptions) { (sec, details) ->
                    val (chipLabel, _) = details
                    FilterChip(
                        selected = currentSec == sec,
                        onClick = { onSelectGraceSeconds(sec) },
                        label = { Text(chipLabel, fontWeight = if (currentSec == sec) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = if (currentSec == sec) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            val currentDesc = graceOptions.find { it.first == currentSec }?.second?.second
                ?: "Uses your global setting from Settings"

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = currentDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AddTagToAppDialog(
    existingUserTags: List<String>,
    assignedTags: List<String>,
    onDismiss: () -> Unit,
    onAddExistingTag: (String) -> Unit,
    onCreateAndAddTag: (String) -> Unit
) {
    var newTagInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val availableExisting = remember(existingUserTags, assignedTags) {
        existingUserTags.filterNot { existing ->
            assignedTags.any { it.equals(existing, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tag to App", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (availableExisting.isNotEmpty()) {
                    Text(
                        text = "Choose existing tag:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableExisting) { tag ->
                            SuggestionChip(
                                onClick = { onAddExistingTag(tag) },
                                label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Text(
                    text = "Or create a new tag:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = {
                        newTagInput = it.filter { char -> char.isLetterOrDigit() || char == '_' || char == '-' }
                        inputError = null
                    },
                    placeholder = { Text("Tag name (e.g. Work)") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                    singleLine = true,
                    isError = inputError != null,
                    supportingText = inputError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = newTagInput.trim()
                    if (clean.isBlank()) {
                        inputError = "Tag name cannot be empty"
                    } else if (assignedTags.any { it.equals(clean, ignoreCase = true) }) {
                        inputError = "App already has this tag"
                    } else {
                        onCreateAndAddTag(clean)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateTagDialog(
    userTags: List<String>,
    onDismiss: () -> Unit,
    onCreateTag: (String) -> Unit
) {
    var newTagName by remember { mutableStateOf("") }
    var tagError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Tag", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Tags let you group apps (e.g. Work, Crypto, Kids) and lock or filter them in 1 tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = {
                        newTagName = it.filter { char -> char.isLetterOrDigit() || char == '_' || char == '-' }
                        tagError = null
                    },
                    placeholder = { Text("Tag name (e.g. Work)") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                    singleLine = true,
                    isError = tagError != null,
                    supportingText = tagError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = newTagName.trim()
                    if (clean.isBlank()) {
                        tagError = "Tag name cannot be empty"
                    } else if (userTags.any { it.equals(clean, ignoreCase = true) }) {
                        tagError = "Tag already exists"
                    } else {
                        onCreateTag(clean)
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SecurityHealthGaugeCard(
    allApps: List<AppItem>,
    lockedCount: Int,
    isAnimationsEnabled: Boolean,
    onProtectSensitive: () -> Unit,
    onUnlockAll: () -> Unit
) {
    val sensitiveApps = remember(allApps) {
        allApps.filter {
            it.category == AppCategory.FINANCIAL ||
            it.category == AppCategory.SENSITIVE ||
            it.category == AppCategory.SOCIAL
        }
    }
    val lockedSensitiveCount = remember(sensitiveApps) { sensitiveApps.count { it.isLocked } }
    val unprotectedSensitiveCount = sensitiveApps.size - lockedSensitiveCount

    val securityPercentage = remember(allApps.size, sensitiveApps.size, lockedSensitiveCount, lockedCount) {
        if (allApps.isEmpty()) 0
        else if (sensitiveApps.isEmpty()) ((lockedCount.toFloat() / allApps.size) * 100).toInt()
        else ((lockedSensitiveCount.toFloat() / sensitiveApps.size) * 100).toInt()
    }

    val animatedAngle by animateFloatAsState(
        targetValue = securityPercentage * 3.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "gauge_angle"
    )

    val amberColor = MaterialTheme.semanticColors.amber
    val gaugeColor = when {
        securityPercentage >= 80 -> MaterialTheme.colorScheme.primary
        securityPercentage >= 40 -> amberColor
        else -> MaterialTheme.colorScheme.error
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact Circular Progress Gauge (54dp)
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(54.dp)) {
                    val strokeWidth = 6.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = gaugeColor,
                        startAngle = -90f,
                        sweepAngle = animatedAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$securityPercentage%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Score",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Status Title & Subtitle Summary
            Column(modifier = Modifier.weight(1f)) {
                val statusText = when {
                    unprotectedSensitiveCount == 0 && lockedCount > 0 -> "Shield Active"
                    lockedCount == 0 -> "Unprotected"
                    else -> "Attention Needed"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = gaugeColor
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "$lockedCount of ${allApps.size} apps secured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (unprotectedSensitiveCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = amberColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$unprotectedSensitiveCount critical at risk",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = amberColor
                        )
                    }
                } else if (lockedCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Critical apps protected",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Inline Compact Action Buttons
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onProtectSensitive,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (unprotectedSensitiveCount > 0) "Lock Critical" else "Protect Sensitive",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (lockedCount > 0) {
                    var showUnlockConfirmDialog by remember { mutableStateOf(false) }

                    OutlinedButton(
                        onClick = { showUnlockConfirmDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "Unlock All",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (showUnlockConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showUnlockConfirmDialog = false },
                            title = { Text("Unlock All Apps?", fontWeight = FontWeight.Bold) },
                            text = { Text("This will disarm locking on all $lockedCount protected applications.") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showUnlockConfirmDialog = false
                                        onUnlockAll()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Confirm")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showUnlockConfirmDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListItem(
    app: AppItem,
    isAnimationsEnabled: Boolean,
    isPerAppGraceEnabled: Boolean,
    appGraceSeconds: Int?,
    isPinOnly: Boolean,
    isNotificationMasked: Boolean,
    onOpenProfile: () -> Unit,
    onToggle: () -> Unit
) {
    val shackleOffset by animateDpAsState(
        targetValue = if (app.isLocked) 0.dp else (-3).dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "shackle_offset"
    )
    val shackleRotation by animateFloatAsState(
        targetValue = if (app.isLocked) 0f else -14f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "shackle_rotation"
    )
    val lockScale by animateFloatAsState(
        targetValue = if (app.isLocked) 1.05f else 0.92f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lock_scale"
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggle()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onOpenProfile()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val bgColor by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> if (app.isLocked) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
                    else -> Color.Transparent
                },
                label = "swipe_bg"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (app.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (app.isLocked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (app.isLocked) "Unlock" else "Lock",
                                fontWeight = FontWeight.Bold,
                                color = if (app.isLocked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Configure",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenProfile() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (app.isLocked)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
            ),
            border = if (app.isLocked)
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            else null
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon
                if (app.iconBitmap != null) {
                    Image(
                        bitmap = app.iconBitmap.asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else if (app.icon != null) {
                    val bitmap = remember(app.icon) { app.icon.toBitmap() }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.category.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (app.customTags.isNotEmpty()) {
                            app.customTags.take(2).forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "#$tag",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            if (app.customTags.size > 2) {
                                Text(
                                    text = "+${app.customTags.size - 2}",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                // Status indicators when app is locked
                if (app.isLocked) {
                    val graceBadge = when (appGraceSeconds) {
                        null -> "Default"
                        com.pixelguard.applock.domain.usecase.SessionManager.GRACE_UNTIL_SCREEN_OFF -> "Screen Off"
                        0 -> "0s"
                        30 -> "30s"
                        60 -> "1m"
                        300 -> "5m"
                        900 -> "15m"
                        else -> "${appGraceSeconds}s"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isPinOnly) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                                else if (appGraceSeconds != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPinOnly) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "PIN Only",
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Timeout",
                                    modifier = Modifier.size(11.dp),
                                    tint = if (appGraceSeconds != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(
                                text = if (isPinOnly && isPerAppGraceEnabled && appGraceSeconds != null) "PIN • $graceBadge ›"
                                    else if (isPinOnly) "PIN ›"
                                    else "$graceBadge ›",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPinOnly) MaterialTheme.colorScheme.tertiary
                                    else if (appGraceSeconds != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (app.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (app.isLocked) "Locked" else "Unlocked",
                        tint = if (app.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = if (isAnimationsEnabled) {
                            Modifier
                                .offset(y = shackleOffset)
                                .graphicsLayer {
                                    scaleX = lockScale
                                    scaleY = lockScale
                                    rotationZ = shackleRotation
                                }
                        } else Modifier
                    )
                }
            }
        }
    }
}
